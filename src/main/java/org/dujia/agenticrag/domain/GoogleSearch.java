package org.dujia.agenticrag.domain;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// todo: 完善 Google 搜索
@Data
@Configuration
public class GoogleSearch implements ContentRetriever {
    @Override
    public List<Content> retrieve(Query query) {
        return List.of();
    }
}
