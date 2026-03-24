package org.dujia.agenticrag.consumers;

import com.rabbitmq.client.Channel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.commons.Common;
import org.dujia.agenticrag.domain.DocumentMessage;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class DocumentUploadConsumer {

    @Autowired
    private OpenAiEmbeddingModel openAiEmbeddingModel;
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;


    @RabbitListener(queues = Common.UPLOAD_QUEUE)
    public void handleLikeMessage(DocumentMessage documentMessage, Channel channel, Message message) {
        // todo: 可加上redis
        String fileUrl = documentMessage.getFileUrl();
        Long assistantId = documentMessage.getAssistantId();
        Long taskId = documentMessage.getTaskId();
        ApacheTikaDocumentParser documentParser = new ApacheTikaDocumentParser();
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(fileUrl, documentParser);
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> textSegments = splitter.splitAll(documents);
        for (int i = 0; i < textSegments.size(); i++) {
            textSegments.get(i).metadata()
                    .put("doc_id", taskId)
                    .put("assistant_id", assistantId)
                    .put("chunk_index", i);
        }

        try {
            // study: 不能直接写在这，需要单例
//        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
//                .host("192.168.65.128")
//                .port(19530)
//                .collectionName("kb_document_chunks")
//                .dimension(1024)
//                .build();
//        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
//                .apiKey(apiKey)
//                .baseUrl("https://api.siliconflow.cn/v1")
//                .modelName("BAAI/bge-m3")
//                .timeout(Duration.ofSeconds(60))
//                .build();
            // todo: 如果 textSegments 特别多，应分批次（Batch）处理，以防触发硅基流动的 API 超时或频率限制，写入milvus也是
            List<Embedding> content = openAiEmbeddingModel.embedAll(textSegments).content();
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            embeddingStore.addAll(content, textSegments);
        } catch (Exception e) {
            log.error("文件传入向量数据库失败, taskId: {}, 请查看日志：", taskId);
            throw new RuntimeException(e);
        }
        // todo: 能不能使用这个往向量数据库里塞
//        EmbeddingStoreIngestor storeIngestor = EmbeddingStoreIngestor.builder()
//                .documentSplitter(DocumentSplitters.recursive(500, 50))
//                .embeddingStore(embeddingStore)
//                .embeddingModel()
//                .build();
//        storeIngestor.ingest(documents);
    }
}
