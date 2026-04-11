package org.dujia.agenticrag.domain;

import dev.langchain4j.rag.content.Content;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LocalRecallResult {

    /**
     * 本地召回质量等级，由 reranker top1 分数决定
     */
    public enum RecallLevel {
        STRONG,   // top1 score ≥ 0.85，高置信
        MEDIUM,   // top1 score ∈ [0.65, 0.85)，中置信
        WEAK,     // top1 score ∈ [0.50, 0.65)，低置信
        EMPTY     // 无结果 或 全部 chunk score < 0.50，直接 fallback
    }

    private List<Content> contents;
    /**
     * 向后兼容字段：level == WEAK || level == EMPTY 时为 true
     */
    private boolean weak;
    private double recallStrength;
    private String reason;
    private int hitCount;
    /** 新增：强弱等级（由 reranker top1 分数驱动） */
    private RecallLevel level;
    /** 新增：reranker top1 分数（EMPTY 时为 0.0） */
    private double topScore;
}
