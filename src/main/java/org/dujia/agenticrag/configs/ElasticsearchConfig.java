package org.dujia.agenticrag.configs;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {
    @Value("${app.search.elasticsearch.url}")
    private String url;
    @Value("${app.search.elasticsearch.api-key}")
    private String apiKey;

    @Bean
    public RestClient restClient() {
        HttpHost host = HttpHost.create(url);

        // 设置 API Key 认证 Header
        Header[] defaultHeaders = new Header[]{
                new BasicHeader("Authorization", "ApiKey " + apiKey)
        };

        return RestClient.builder(host)
                .setDefaultHeaders(defaultHeaders)
                .build();
    }

}
