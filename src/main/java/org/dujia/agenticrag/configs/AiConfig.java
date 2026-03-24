package org.dujia.agenticrag.configs;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(openAiModelApiKey)
                .baseUrl(openAiBaseUrl)
                .modelName(openAiModelName)
                .build();
    }
}
