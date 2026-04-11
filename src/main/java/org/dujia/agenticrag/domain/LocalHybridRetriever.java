package org.dujia.agenticrag.domain;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class LocalHybridRetriever implements ContentRetriever {
    //study: 本地混合检索

    private final ContentRetriever localContentRetriever;
    private final ElasticsearchKeywordRetriever elasticsearchKeywordRetriever;

    @Autowired
    public LocalHybridRetriever(@Qualifier("localContentRetriever") ContentRetriever localContentRetriever,
                                ElasticsearchKeywordRetriever elasticsearchKeywordRetriever) {
        this.localContentRetriever = localContentRetriever;
        this.elasticsearchKeywordRetriever = elasticsearchKeywordRetriever;
    }

    @Override
    public List<Content> retrieve(Query query) {
        // 1. 多路检索
        List<Content> vectorContents = localContentRetriever.retrieve(query);
        List<Content> keywordContents = elasticsearchKeywordRetriever.retrieve(query);

        if (vectorContents == null) {
            vectorContents = List.of();
        }
        
        if (keywordContents == null) {
            keywordContents = List.of();
        }

        // 2. 合并
        List<Content> merged = new ArrayList<>();
        merged.addAll(tagSource(vectorContents, "milvus"));
        merged.addAll(keywordContents);

        // 3. 去重
        return deduplicate(merged);
    }

    private List<Content> tagSource(List<Content> contents, String source) {
        return contents.stream().map(content -> {
            if (content == null) {
                return null;
            }
            TextSegment segment = content.textSegment();
            if (segment == null) {
                return null;
            }
            Metadata copyMetaData = segment.metadata().copy();
            copyMetaData.put("retriever", source);
            return Content.from(TextSegment.from(segment.text(), copyMetaData));
        }).toList();
    }

    private List<Content> deduplicate(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return List.of();
        }
        Map<String, Content> seen = new LinkedHashMap<>();

        for (Content content : contents) {
            String key = buildDedupKey(content);

            if (key == null) {
                continue;
            }

            if (seen.containsKey(key)) {
                Content existing = seen.get(key);
                Content merged = mergeRetrieverMetadata(existing, content);
                seen.put(key, merged);
            } else {
                seen.put(key, content);
            }
        }

        return new ArrayList<>(seen.values());
    }

    private Content mergeRetrieverMetadata(Content existing, Content incoming) {
        Metadata existMetadata = existing.textSegment().metadata();
        Metadata comingMetadata = incoming.textSegment().metadata();
        String existRetriever = existMetadata.getString("retriever");
        String comingRetriever = comingMetadata.getString("retriever");

        LinkedHashSet<String> set = new LinkedHashSet<>();

        if (existRetriever != null && !existRetriever.isBlank()) {
            set.addAll(Arrays.asList(existRetriever.split(",")));
        }
        if (comingRetriever != null && !comingRetriever.isBlank()) {
            set.addAll(Arrays.asList(comingRetriever.split(",")));
        }

        String mergedRetrieverString  = String.join(",", set);

        Metadata copy = existMetadata.copy();
        copy.put("retriever", mergedRetrieverString);
        return Content.from(TextSegment.from(existing.textSegment().text(), copy));
    }

    // study: 使用docId和chunk_index去重
    private String buildDedupKey(Content content) {
        if (content == null || content.textSegment() == null) {
            log.warn("召回到无效的 content, 已过滤");
            return null;
        }

        Metadata metadata = content.textSegment().metadata();

        Long rawDocId = metadata.getLong("doc_id");
        Integer rawChunkIndex = metadata.getInteger("chunk_index");

        String docId = rawDocId != null ? String.valueOf(rawDocId) : null;
        String chunkIndex = rawChunkIndex != null ? String.valueOf(rawChunkIndex) : null;

        if (docId != null && !docId.isBlank() && chunkIndex != null && !chunkIndex.isBlank()) {
            return docId + "_" + chunkIndex;
        }

        String text = content.textSegment().text();
        return (text == null || text.isBlank()) ? null : text;
    }

}
