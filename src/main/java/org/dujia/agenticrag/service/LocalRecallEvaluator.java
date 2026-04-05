package org.dujia.agenticrag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.domain.ChatSession;
import org.dujia.agenticrag.domain.LocalRecallResult;
import org.dujia.agenticrag.mapper.ChatSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class LocalRecallEvaluator {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final OpenAiEmbeddingModel openAiEmbeddingModel;
    private final ChatSessionMapper chatSessionMapper;

    @Autowired
    public LocalRecallEvaluator(EmbeddingStore<TextSegment> embeddingStore,
                                OpenAiEmbeddingModel openAiEmbeddingModel,
                                ChatSessionMapper chatSessionMapper) {
        this.embeddingStore = embeddingStore;
        this.openAiEmbeddingModel = openAiEmbeddingModel;
        this.chatSessionMapper = chatSessionMapper;
    }

    // study: 评估本地召回的强弱
    public LocalRecallResult evaluate(Query query) {
        String queryText = query.text();
        if (queryText == null || queryText.isBlank()) {
            return new LocalRecallResult(List.of(), true, 0D, "EMPTY_QUERY", 0);
        }

        Object memoryId = query.metadata().chatMemoryId();
        if (!(memoryId instanceof Long sessionId)) {
            return new LocalRecallResult(List.of(), true, 0D, "INVALID_SESSION", 0);
        }

        ChatSession chatSession = chatSessionMapper.selectById(sessionId);
        if (chatSession == null) {
            return new LocalRecallResult(List.of(), true, 0D, "SESSION_NOT_FOUND", 0);
        }

        Long assistantId = chatSession.getAssistantId();

        List<EmbeddingMatch<TextSegment>> matches = null;
        try {
            Embedding content = openAiEmbeddingModel.embed(queryText).content();

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest
                    .builder()
                    .queryEmbedding(content)
                    .maxResults(3)
                    .minScore(0.75)
                    // 避免去找其他助手的知识库
                    .filter(metadataKey("assistant_id").isEqualTo(assistantId))
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = null;

            searchResult = embeddingStore.search(searchRequest);

            matches = searchResult.matches();
        } catch (Exception e) {
            log.warn("查找向量数据库异常，请查看日志：", e);
            return new LocalRecallResult(List.of(), true, 0D, "RECALL_ERROR", 0);
        }

        if (matches == null || matches.isEmpty()) {
            return new LocalRecallResult(List.of(), true, 0D, "NO_RESULT", 0);
        }

        Double topScore = matches.get(0).score();
        int hitCount = matches.size();

        List<Content> contents = matches.stream().map(
                match -> {
            return Content.from(match.embedded());
        }).toList();

        boolean weak = topScore < 0.82;
        String reason = weak ? "LOW_SCORE" : "STRONG";

        return new LocalRecallResult(contents, weak, topScore, reason, hitCount);

    }

}
