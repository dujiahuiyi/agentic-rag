package org.dujia.agenticrag.domain;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.dujia.agenticrag.service.LocalRecallEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GatedWebFallbackRetriever implements ContentRetriever{

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
        String userMessage = query.text();

        if (isInternalQuestion(userMessage)) {
            return recallResult.getContents();
        }

        if (!recallResult.isWeak()) {
            return recallResult.getContents();
        }

        return googleSearch.retrieve(query);
    }


//    public List<Content> retrieve(Query query) {
//        String userMessage = query.text();
//        List<Content> contents = localContentRetriever.retrieve(query);
//
//        if (contents == null) {
//            contents = List.of();
//        }
//
//        // study: 本地有直接返回
//        if (isInternalQuestion(userMessage)) {
//            return contents;
//        }
//
//        // 非内部问题但本地已有结果，也直接返回本地，不管有没有，都直接返回
//        if (!contents.isEmpty()) {
//            return contents;
//        }
//
//        // 和本地无关的问题，才用谷歌搜索
//        return googleSearch.retrieve(query);
//    }

    private boolean isInternalQuestion(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String text = userMessage.trim();
        return text.contains("公司")
                || text.contains("员工手册")
                || text.contains("制度")
                || text.contains("内部")
                || text.contains("项目文档")
                || text.contains("本系统")
                || text.contains("本助手");
    }
}
