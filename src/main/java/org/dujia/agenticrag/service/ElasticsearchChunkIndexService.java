package org.dujia.agenticrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchChunkIndexService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${app.search.elasticsearch.index-name}")
    private String indexName;

    public void deleteByDocId(Long docId) throws IOException {
        if (docId == null) {
            return;
        }

        Request request = new Request("POST", "/" + indexName + "/_delete_by_query");

        String body = """
          {
            "query": {
              "term": {
                "doc_id": %d
              }
            }
          }
          """.formatted(docId);

        request.setJsonEntity(body);

        try {
            Response response = restClient.performRequest(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new IOException("Failed to delete chunks by docId, status=" + statusCode + ", body=" + responseBody);
            }
        } catch (ResponseException e) {
            throw new IOException("请求ElasticSearch失败：" + EntityUtils.toString(e.getResponse().getEntity()) + " 请查看日志: ", e);
        }

    }

    public void indexBatch(Long docId, Long assistantId,
                           String fileType, int startIndex,
                           List<TextSegment> batchSegments) throws IOException {
        if (docId == null || assistantId == null
                || batchSegments == null || batchSegments.isEmpty()) {
            return;
        }

        log.info("doc_id:{}, assistantId:{}正在存入es", docId, assistantId);

        Request request = new Request("POST", "/_bulk");
        request.addParameter("refresh", "false");
        request.setOptions(
                request.getOptions().toBuilder()
                        .addHeader("Content-Type", "application/x-ndjson")
                        .build()
        );

        StringBuilder ndjson = new StringBuilder();

        for (int i = 0; i < batchSegments.size(); i++) {
            int globalIndex = startIndex + i;
            String esId = docId + "_" + globalIndex;

            TextSegment segment = batchSegments.get(i);
            String sectionTitle = segment.metadata().getString("section_title");
            String sourceType = segment.metadata().getString("source_type");
            Map<Object, Object> action = Map.of(
                    "index", Map.of(
                            "_index", indexName,
                            "_id", esId
                    )
            );

            Map<String, Object> source = new HashMap<>();
            source.put("doc_id", docId);
            source.put("assistant_id", assistantId);
            source.put("chunk_index", globalIndex);
            source.put("section_title", sectionTitle == null ? "" : sectionTitle);
            source.put("source_type", sourceType == null ? "" : sourceType);
            source.put("file_type", fileType == null ? "" : fileType);
            source.put("chunk_text", segment.text() == null ? "" : segment.text());
            source.put("create_time", Instant.now().toString());

            try {
                ndjson.append(objectMapper.writeValueAsString(action)).append("\n");
                ndjson.append(objectMapper.writeValueAsString(source)).append("\n");
            } catch (JsonProcessingException e) {
                throw new IOException("json写入异常，请查看日志：", e);
            }
        }

        request.setJsonEntity(ndjson.toString());

        try {
            Response response = restClient.performRequest(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                String responseBody = EntityUtils.toString(response.getEntity());
                log.error("doc_id:{}, assistantId:{}无法存入es", docId, assistantId);
                throw new IOException("Failed to bulk index chunks, status=" + statusCode + ", body=" + responseBody);
            }
            log.info("doc_id:{}, assistantId:{}已存入es", docId, assistantId);
        } catch (ResponseException e) {
            String responseBody = EntityUtils.toString(e.getResponse().getEntity());
            throw new IOException("Failed to bulk index chunks. ES Response: " + responseBody, e);
        }

    }

}
