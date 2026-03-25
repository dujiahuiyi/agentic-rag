package org.dujia.agenticrag.configs;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.dujia.agenticrag.domain.Assistant;
import org.dujia.agenticrag.domain.GoogleSearch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Configuration
public class AiConfig {

    @Value("${app.ai.embedding_model_api_key}")
    private String embeddingApiKey;
    @Value("${langchain4j.open-ai.streaming-chat-model.api-key}")
    private String openAiModelApiKey;
    @Value("${langchain4j.open-ai.streaming-chat-model.model-name}")
    private String openAiModelName;
    @Value("${langchain4j.open-ai.streaming-chat-model.base-url}")
    private String openAiBaseUrl;
    @Value("${app.ai.reranker_model_model}")
    private String rerankerModel;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final GoogleSearch googleSearch;
    private final ContentRetriever contentRetriever;
    private final OpenAiChatModel openAiChatModel;
    private final ScoringModel scoringModel;
    private final ContentAggregator contentAggregator;
    private final QueryRouter queryRouter;

    public AiConfig(EmbeddingStore<TextSegment> embeddingStore,
                    EmbeddingModel embeddingModel,
                    ContentRetriever contentRetriever,
                    OpenAiChatModel openAiChatModel,
                    ScoringModel scoringModel,
                    ContentAggregator contentAggregator,
                    QueryRouter queryRouter) {
        this.embeddingStore = embeddingStore;
        this.contentRetriever = contentRetriever;
        this.embeddingModel = embeddingModel;
        this.openAiChatModel = openAiChatModel;
        this.scoringModel = scoringModel;
        this.contentAggregator = contentAggregator;
        this.queryRouter = queryRouter;
        this.googleSearch = new GoogleSearch();
    }

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .modelName("BAAI/bge-m3")
                .baseUrl("https://api.siliconflow.cn/v1")
                .apiKey(embeddingApiKey)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return MilvusEmbeddingStore.builder()
                .port(19530)
                .host("192.168.65.128")
                .collectionName("kb_document_chunks")
                .dimension(1024)
                .build();
    }

    // todo: 流式和非流都使用一个模型，并发上来了会不会有问题
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(openAiModelApiKey)
                .baseUrl(openAiBaseUrl)
                .modelName(openAiModelName)
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiModelApiKey)
                .modelName(openAiModelName)
                .baseUrl(openAiBaseUrl)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public ContentRetriever contentRetriever() {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.75)
                .embeddingStore(embeddingStore)
                .filter(metadataKey("assistant_id").isEqualTo(1))
                .build();
    }

    @Bean
    public ScoringModel scoringModel() {
        return CohereScoringModel.builder()
                .apiKey(embeddingApiKey)
                .modelName(rerankerModel)
                .baseUrl("https://api.siliconflow.cn/v1")
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public QueryRouter queryRouter() {
        Map<ContentRetriever, String> map = new HashMap<>();
        map.put(googleSearch, "谷歌搜索，遇到本地知识库里没有的问题时可以上网搜索");
        map.put(contentRetriever, "本地向量数据库，优先检索");
        return new LanguageModelQueryRouter(openAiChatModel, map);
    }

    @Bean
    public ContentAggregator contentAggregator() {
        return ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore(0.75)
                .build();
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor() {
        //study: 使用queryRouter还需要contentRetriever吗
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(new CompressingQueryTransformer(openAiChatModel))
                .queryRouter(queryRouter)
//                .contentRetriever()
                .contentAggregator(contentAggregator)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        ConcurrentHashMap<Object, ChatMemory> map = new ConcurrentHashMap<>();
        return memoryId -> map.computeIfAbsent(memoryId, id ->
                MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(30)
                        .build());
    }

    @Bean
    public Assistant assistant(StreamingChatLanguageModel streamingChatLanguageModel,
                               ChatMemoryProvider chatMemoryProvider,
                               RetrievalAugmentor retrievalAugmentor) {
        return AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor)
                .build();
    }
}
