package org.dujia.agenticrag.domain;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.domain.LocalRecallResult.RecallLevel;
import org.dujia.agenticrag.service.LocalRecallEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class GatedWebFallbackRetriever implements ContentRetriever {

    private final GoogleSearch googleSearch;
    private final LocalRecallEvaluator localRecallEvaluator;

    @Autowired
    public GatedWebFallbackRetriever(GoogleSearch googleSearch,
                                     LocalRecallEvaluator localRecallEvaluator) {
        this.googleSearch = googleSearch;
        this.localRecallEvaluator = localRecallEvaluator;
    }

    @Override
    public List<Content> retrieve(Query query) {
        LocalRecallResult recallResult = localRecallEvaluator.evaluate(query);
        RecallLevel level = recallResult.getLevel();

        if (level == RecallLevel.STRONG || level == RecallLevel.MEDIUM) {
            // 本地有质量结果，直接返回，不 fallback
            return recallResult.getContents();
        }

        // WEAK 或 EMPTY，fallback 到 Tavily
        log.info("本地召回等级为 {}（topScore={}），fallback 到 Tavily，query: {}",
                level, recallResult.getTopScore(), query.text());
        return googleSearch.retrieve(query);
    }
}
