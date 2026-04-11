package org.dujia.agenticrag.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.domain.LocalHybridRetriever;
import org.dujia.agenticrag.domain.LocalRecallResult;
import org.dujia.agenticrag.domain.LocalRecallResult.RecallLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class LocalRecallEvaluator {

    private static final double FILTER_THRESHOLD = 0.50;  // 低于此分数的 chunk 直接丢弃
    private static final double WEAK_THRESHOLD   = 0.65;  // 低于此为 WEAK
    private static final double STRONG_THRESHOLD = 0.85;  // 高于此为 STRONG

    private final LocalHybridRetriever localHybridRetriever;
    private final ScoringModel scoringModel;

    @Autowired
    public LocalRecallEvaluator(LocalHybridRetriever localHybridRetriever,
                                ScoringModel scoringModel) {
        this.localHybridRetriever = localHybridRetriever;
        this.scoringModel = scoringModel;
    }

    // study: 本地召回 + Cross-Encoder 重排 + 强弱等级判断
    public LocalRecallResult evaluate(Query query) {
        String queryText = query.text();
        if (queryText == null || queryText.isBlank()) {
            return buildEmptyResult("EMPTY_QUERY");
        }

        List<Content> contents = localHybridRetriever.retrieve(query);
        if (contents == null || contents.isEmpty()) {
            return buildEmptyResult("NO_RESULT");
        }

        // 1. 将 Content 列表转为 TextSegment 列表，用于 scoreAll
        List<TextSegment> segments = contents.stream()
                .map(Content::textSegment)
                .toList();

        // 2. Cross-Encoder 批量打分（segments 在前，query 在后）
        List<Double> scores;
        try {
            scores = scoringModel.scoreAll(segments, queryText).content();
        } catch (Exception e) {
            log.warn("ScoringModel.scoreAll 调用失败，降级为 EMPTY，query: {}", queryText, e);
            return buildEmptyResult("SCORING_ERROR");
        }

        // 3. 将分数与 content 按下标对齐，过滤低分 chunk
        List<double[]> indexedScores = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            double score = scores.get(i);
            if (score >= FILTER_THRESHOLD) {
                indexedScores.add(new double[]{i, score});
            }
        }

        if (indexedScores.isEmpty()) {
            return buildEmptyResult("ALL_BELOW_THRESHOLD");
        }

        // 4. 按 score 降序排序
        indexedScores.sort(Comparator.comparingDouble(a -> -a[1]));

        List<Content> ranked = indexedScores.stream()
                .map(pair -> contents.get((int) pair[0]))
                .toList();

        // 5. 取 top1 score，判断等级
        double topScore = indexedScores.get(0)[1];
        RecallLevel level;
        if (topScore >= STRONG_THRESHOLD) {
            level = RecallLevel.STRONG;
        } else if (topScore >= WEAK_THRESHOLD) {
            level = RecallLevel.MEDIUM;
        } else {
            level = RecallLevel.WEAK;
        }

        boolean weak = (level == RecallLevel.WEAK);
        log.info("本地召回重排完成，等级={}，topScore={}，有效chunk数={}，query: {}",
                level, topScore, ranked.size(), queryText);

        LocalRecallResult result = new LocalRecallResult();
        result.setContents(ranked);
        result.setWeak(weak);
        result.setRecallStrength(topScore);
        result.setReason(level.name());
        result.setHitCount(ranked.size());
        result.setLevel(level);
        result.setTopScore(topScore);
        return result;
    }

    private LocalRecallResult buildEmptyResult(String reason) {
        LocalRecallResult result = new LocalRecallResult();
        result.setContents(List.of());
        result.setWeak(true);
        result.setRecallStrength(0D);
        result.setReason(reason);
        result.setHitCount(0);
        result.setLevel(RecallLevel.EMPTY);
        result.setTopScore(0.0);
        return result;
    }







//    public LocalRecallResult evaluate(Query query) {
//        String queryText = query.text();
//        if (queryText == null || queryText.isBlank()) {
//            return new LocalRecallResult(List.of(), true, 0D, "EMPTY_QUERY", 0);
//        }
//
//        Object memoryId = query.metadata().chatMemoryId();
//        if (!(memoryId instanceof Long sessionId)) {
//            return new LocalRecallResult(List.of(), true, 0D, "INVALID_SESSION", 0);
//        }
//
//        ChatSession chatSession = chatSessionMapper.selectById(sessionId);
//        if (chatSession == null) {
//            return new LocalRecallResult(List.of(), true, 0D, "SESSION_NOT_FOUND", 0);
//        }
//
//        Long assistantId = chatSession.getAssistantId();
//
//        List<EmbeddingMatch<TextSegment>> matches = null;
//        try {
//            Embedding content = openAiEmbeddingModel.embed(queryText).content();
//
//            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest
//                    .builder()
//                    .queryEmbedding(content)
//                    .maxResults(3)
//                    .minScore(0.75)
//                    // 避免去找其他助手的知识库
//                    .filter(metadataKey("assistant_id").isEqualTo(assistantId))
//                    .build();
//
//            EmbeddingSearchResult<TextSegment> searchResult = null;
//
//            searchResult = embeddingStore.search(searchRequest);
//
//            matches = searchResult.matches();
//        } catch (Exception e) {
//            log.warn("查找向量数据库异常，请查看日志：", e);
//            return new LocalRecallResult(List.of(), true, 0D, "RECALL_ERROR", 0);
//        }
//
//        if (matches == null || matches.isEmpty()) {
//            return new LocalRecallResult(List.of(), true, 0D, "NO_RESULT", 0);
//        }
//
//        Double topScore = matches.get(0).score();
//        int hitCount = matches.size();
//
//        List<Content> contents = matches.stream().map(
//                match -> {
//            return Content.from(match.embedded());
//        }).toList();
//
//        boolean weak = topScore < 0.82;
//        String reason = weak ? "LOW_SCORE" : "STRONG";
//
//        return new LocalRecallResult(contents, weak, topScore, reason, hitCount);
//
//    }

}
