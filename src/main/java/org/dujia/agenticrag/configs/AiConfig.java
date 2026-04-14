package org.dujia.agenticrag.configs;

import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.domain.Assistant;
import org.dujia.agenticrag.domain.ChatSession;
import org.dujia.agenticrag.domain.GatedWebFallbackRetriever;
import org.dujia.agenticrag.domain.GoogleSearch;
import org.dujia.agenticrag.mapper.ChatSessionMapper;
import org.dujia.agenticrag.service.AiAssistantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
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


    // study: 不需要定义出来，可在下面的要用到的方法里使用参数直接传入，spring boot会自己处理导入 （Spring IoC）
//    private final EmbeddingStore<TextSegment> embeddingStore;
//    private final EmbeddingModel embeddingModel;
//    private final GoogleSearch googleSearch;
//    private final ContentRetriever contentRetriever;
//    private final OpenAiChatModel openAiChatModel;
//    private final ScoringModel scoringModel;
//    private final ContentAggregator contentAggregator;
//    private final QueryRouter queryRouter;

//    public AiConfig(EmbeddingStore<TextSegment> embeddingStore,
//                    EmbeddingModel embeddingModel,
//                    ContentRetriever contentRetriever, // study: 这个依赖了embeddingModel，会造成循环依赖
//                    OpenAiChatModel openAiChatModel,
//                    ScoringModel scoringModel,
//                    ContentAggregator contentAggregator,
//                    QueryRouter queryRouter) {
//        this.embeddingStore = embeddingStore;
//        this.contentRetriever = contentRetriever;
//        this.embeddingModel = embeddingModel;
//        this.openAiChatModel = openAiChatModel;
//        this.scoringModel = scoringModel;
//        this.contentAggregator = contentAggregator;
//        this.queryRouter = queryRouter;
//        this.googleSearch = new GoogleSearch();
//    }

    @Bean
    public ApacheTikaDocumentParser documentParser() {
        return new ApacheTikaDocumentParser();
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
    public ContentRetriever localContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                                  OpenAiEmbeddingModel embeddingModel,
                                                  ChatSessionMapper chatSessionMapper) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .maxResults(6)
                .minScore(0.60)
                .embeddingStore(embeddingStore)
//                .filter(metadataKey("assistant_id").isEqualTo(1))
                // study: lambda表达式在spring创建时不会立刻执行，只有当真正被调用的时候才会执行
                .dynamicFilter(query -> {
                    Object memoryId = query.metadata().chatMemoryId();
                    // study: 检查memoryId是不是Long类型，是就把值赋给sessionId
                    if (!(memoryId instanceof Long sessionId)) {
                        // 新对话
                        // study: 不加过滤，有机会把别的助手的知识块也检索进来
//                        return null;
                        // 直接抛异常
//                        throw new IllegalStateException("非法检索请求：缺失有效的 Session ID");
                        log.warn("非法检索请求：缺失有效的 Session ID");
                        return metadataKey("assistant_id").isEqualTo(-1L);
                    }
                    // study: 可以不查表，把memoryId设置成一个复合对象
                    ChatSession chatSession = chatSessionMapper.selectById(sessionId);
                    if (chatSession == null) {
//                        throw new IllegalStateException("非法检索请求：找不到对应的会话记录 [" + sessionId + "]");
                        log.warn("非法检索请求：找不到对应的会话记录 [\" + sessionId + \"]");
                        return metadataKey("assistant_id").isEqualTo(-1L);
                    }
                    return metadataKey("assistant_id").isEqualTo(chatSession.getAssistantId());
                })
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
    public QueryRouter queryRouter(OpenAiChatModel openAiChatModel,
                                   ContentRetriever localContentRetriever,
                                   GoogleSearch googleSearch) {
        Map<ContentRetriever, String> map = new HashMap<>();
        map.put(googleSearch, "谷歌搜索，遇到本地知识库里没有的问题时可以上网搜索");
        map.put(localContentRetriever, "本地向量数据库，优先检索");
        // todo: 会增加RT，可以使用多路召回，然后给ScoringModel重排
        // todo: 多路召回的情况下，要是问的是自己上传的文件，又去谷歌搜索了怎么办
        return new LanguageModelQueryRouter(openAiChatModel, map);
    }

    @Bean
    public ContentAggregator contentAggregator() {
        return new DefaultContentAggregator();
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor(OpenAiChatModel openAiChatModel,
                                                 ContentAggregator contentAggregator,
                                                 GatedWebFallbackRetriever gatedWebFallbackRetriever) {
        //study: 使用queryRouter还需要contentRetriever吗
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(new CompressingQueryTransformer(openAiChatModel))
//                .queryRouter(queryRouter)
                .contentRetriever(gatedWebFallbackRetriever)
                .contentAggregator(contentAggregator)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // todo: 会造成内存溢出，可以存到redis和mysql
        ConcurrentHashMap<Object, ChatMemory> map = new ConcurrentHashMap<>();
        return memoryId -> map.computeIfAbsent(memoryId, id ->
                MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(30)
                        .build());
    }

    @Bean
    public Assistant assistant(StreamingChatLanguageModel streamingChatLanguageModel,
                               OpenAiChatModel chatModel,
                               ChatMemoryProvider chatMemoryProvider,
                               RetrievalAugmentor retrievalAugmentor,
                               AiAssistantService aiAssistantService) {

        return AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor)
                //study: 动态生成system prompt
                .systemMessageProvider(memoryId -> {
                    if (!(memoryId instanceof Long sessionId)) {
                        // 新对话
                        return aiAssistantService.buildDefaultPrompt(null);
                    }
                    return aiAssistantService.getSystemPromptBySessionId(sessionId);
                })
                .build();
    }

    @Bean
    public TavilyWebSearchEngine tavilyWebSearchEngine(
            @Value("${app.search.tavily.api-key}") String apiKey,
            @Value("${app.search.tavily.timeout-seconds}") long timeSeconds,
            @Value("${app.search.tavily.search-depth}") String searchDepth,
            @Value("${app.search.tavily.include-answer}") boolean includeAnswer,
            @Value("${app.search.tavily.include-raw-content}") boolean includeRawContent) {
        return TavilyWebSearchEngine.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(timeSeconds))
                .searchDepth(searchDepth)
                .includeAnswer(includeAnswer)
                .includeRawContent(includeRawContent)
                .build();
    }

}
