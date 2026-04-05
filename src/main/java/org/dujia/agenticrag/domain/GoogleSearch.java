package org.dujia.agenticrag.domain;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

// study: Google 搜索
@Slf4j
@Data
@Component
public class GoogleSearch implements ContentRetriever {

    private TavilyWebSearchEngine tavilyWebSearchEngine;

    @Autowired
    public GoogleSearch(TavilyWebSearchEngine tavilyWebSearchEngine) {
        this.tavilyWebSearchEngine = tavilyWebSearchEngine;
    }

    @Override
    public List<Content> retrieve(Query query) {
        // study: tavily搜索
        String queryText = query.text();
        if (queryText.isBlank()) {
            return List.of();
        }

        WebSearchRequest webSearchRequest = WebSearchRequest
                .builder()
                .searchTerms(queryText)
                .maxResults(3)
                .build();

        WebSearchResults searchResults = null;
        try {
            searchResults = tavilyWebSearchEngine.search(webSearchRequest);
        } catch (Exception e) {
            log.warn("网络搜索失败，请查看日志: ", e);
            return List.of();
        }
        if (searchResults == null || searchResults.results() == null) {
            return List.of();
        }

        return searchResults.results().stream().map(result -> {
            String text = """
                    标题: %s
                    摘要: %s
                    链接: %s
                    """.formatted(
                    defaultString(result.title()),
                    defaultString(result.snippet()),
                    defaultString(result.url() != null ? result.url().toString() : "")
                );
            return Content.from(text);
        }).toList();

    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
