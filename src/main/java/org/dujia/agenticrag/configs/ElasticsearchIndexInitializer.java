package org.dujia.agenticrag.configs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private final RestClient restClient;

    @Value("${app.search.elasticsearch.index-name}")
    private String indexName;

    // 使用 Java 15+ 文本块，保证 Mapping JSON 的可读性
    private static final String MAPPING_JSON = """
            {
              "mappings": {
                "properties": {
                  "doc_id": { "type": "long" },
                  "assistant_id": { "type": "long" },
                  "chunk_index": { "type": "integer" },
                  "section_title": {
                    "type": "text",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "source_type": { "type": "keyword" },
                  "file_type": { "type": "keyword" },
                  "chunk_text": { "type": "text" },
                  "page_no": { "type": "integer" },
                  "content_type": { "type": "keyword" },
                  "heading_path": {
                    "type": "text",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 512 }
                    }
                  },
                  "create_time": {
                    "type": "date",
                    "format": "strict_date_optional_time||epoch_millis"
                  }
                }
              }
            }
            """;

    @Override
    public void run(ApplicationArguments args) {
        log.info("正在检查 Elasticsearch 索引: {}", indexName);
        try {
            if (indexExists(indexName)) {
                log.info("Elasticsearch 索引 '{}' 已存在，跳过初始化。", indexName);
                return;
            }

            log.info("Elasticsearch 索引 '{}' 不存在，正在创建...", indexName);
            createIndex(indexName);
            log.info("Elasticsearch 索引 '{}' 创建成功！", indexName);

        } catch (Exception e) {
            log.error("Elasticsearch 索引初始化失败！应用将停止启动。");
            // Fail-fast 机制：直接抛出 RuntimeException 阻断启动过程
            throw new RuntimeException("Elasticsearch index initialization failed", e);
        }
    }

    /**
     * 检查索引是否存在
     */
    private boolean indexExists(String indexName) throws IOException {
        Request request = new Request("HEAD", "/" + indexName);
        try {
            Response response = restClient.performRequest(request);
            int statusCode = response.getStatusLine().getStatusCode();
            return statusCode == 200;
        } catch (ResponseException e) {
            // Elasticsearch RestClient 在遇到 404 时会抛出 ResponseException
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * 创建索引并设置 Mapping
     */
    private void createIndex(String indexName) throws IOException {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(MAPPING_JSON);

        try {
            Response response = restClient.performRequest(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200 && statusCode != 201) {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new IOException("Unexpected status code " + statusCode + ", body: " + responseBody);
            }
        } catch (ResponseException e) {
            String responseBody = EntityUtils.toString(e.getResponse().getEntity());
            throw new IOException("Failed to create index. ES Response: " + responseBody, e);
        }
    }
}
