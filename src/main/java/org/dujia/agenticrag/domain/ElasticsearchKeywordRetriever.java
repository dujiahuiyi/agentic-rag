package org.dujia.agenticrag.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.dujia.agenticrag.mapper.ChatSessionMapper;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
    public class ElasticsearchKeywordRetriever implements ContentRetriever {

    private final RestClient restClient;
    private final ChatSessionMapper chatSessionMapper;
    @Value("${app.search.elasticsearch.index-name}")
    private String indexName;
    private final ObjectMapper objectMapper;

    @Autowired
    public ElasticsearchKeywordRetriever(RestClient restClient,
                                         ChatSessionMapper chatSessionMapper,
                                         ObjectMapper objectMapper){
        this.restClient = restClient;
        this.chatSessionMapper = chatSessionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Content> retrieve(Query query) {

        String queryText = query.text();
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        Object memoryId = query.metadata().chatMemoryId();
        if (!(memoryId instanceof Long sessionId)) {
            return List.of();
        }

        ChatSession chatSession = chatSessionMapper.selectById(sessionId);
        if (chatSession == null || chatSession.getAssistantId() == null) {
            return List.of();
        }

        Map<String, Object> requestMap = getRequestMap(chatSession, queryText);

        Response response = null;
        try {
            String requestJson = objectMapper.writeValueAsString(requestMap);

            Request request = new Request("POST", "/" + indexName + "/_search");
            request.setJsonEntity(requestJson);
            response = restClient.performRequest(request);
        } catch (JsonProcessingException e) {
            log.warn("json转换失败，请查看日志；", e);
            return List.of();
        } catch (IOException e) {
            log.warn("请求es失败，具体原因请查看日志：", e);
            return List.of();
        }

        String responseBody = "";

        try {
            responseBody  = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            log.warn("Failed to get body: {}", responseBody);
            return List.of();
        }

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            log.warn("es响应错误，响应码为: {}，响应体为: {} ", statusCode, responseBody);
            return List.of();
        }

        try {
            return parseResponse(responseBody);
        } catch (IOException e) {
            log.warn("es结果转为Content失败，具体原因请查看日志：", e);
            return List.of();
        }

    }

    @NotNull
    private static Map<String, Object> getRequestMap(ChatSession chatSession, String queryText) {
        Long assistantId = chatSession.getAssistantId();

        Map<String, Object> queryBody = Map.of(
                "bool", Map.of(
                        "filter", List.of(Map.of("term", Map.of("assistant_id", assistantId))),
                        "should", List.of(
                                Map.of("match", Map.of("chunk_text", Map.of("query", queryText))),
                                Map.of("match", Map.of("section_title", Map.of("query", queryText))),
                                Map.of("match", Map.of("heading_path", Map.of("query", queryText)))
                        ),
                        "minimum_should_match", 1
                )
        );

        return Map.of(
                "size", 8,
                "_source", List.of("doc_id", "assistant_id", "chunk_index",
                        "section_title", "source_type", "file_type", "chunk_text",
                        "page_no", "content_type", "heading_path"),
                "query", queryBody
        );
    }

    private List<Content> parseResponse(String responseBody) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode hitNode = jsonNode.path("hits").path("hits");

        if (!hitNode.isArray() || hitNode.isEmpty()) {
            return List.of();
        }

        List<Content> contents = new ArrayList<>();

        for (JsonNode hit : hitNode) {
            JsonNode source = hit.path("_source");

            String chunkText = source.path("chunk_text").asText("");

            if (chunkText.isBlank()) {
                continue;
            }

            String sectionTitle = source.path("section_title").asText("");
            String sourceType = source.path("source_type").asText("");
            String docId = source.path("doc_id").asText("");
            String chunkIndex = source.path("chunk_index").asText("");
            double score = hit.path("_score").asDouble(0D);

            String text;
            if (sectionTitle.isBlank()) {
                text = """
                  类型: %s
                  内容: %s
                  """.formatted(sourceType, chunkText);
            } else {
                text = """
                  标题: %s
                  类型: %s
                  内容: %s
                  """.formatted(sectionTitle, sourceType, chunkText);
            }

            TextSegment segment = TextSegment.from(text);
            segment.metadata()
                    .put("section_title", sectionTitle)
                    .put("source_type", sourceType)
                    .put("doc_id", docId)
                    .put("chunk_index", chunkIndex)
                    .put("es_score", score)
                    .put("retriever", "elasticsearch");

            contents.add(Content.from(segment));
        }

        return contents;
    }
}
