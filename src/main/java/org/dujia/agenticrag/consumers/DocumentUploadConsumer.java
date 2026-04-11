package org.dujia.agenticrag.consumers;

import cn.hutool.core.util.StrUtil;
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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.commons.Common;
import org.dujia.agenticrag.domain.DocumentMessage;
import org.dujia.agenticrag.domain.KbDocument;
import org.dujia.agenticrag.service.ElasticsearchChunkIndexService;
import org.dujia.agenticrag.service.KbDocumentService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class DocumentUploadConsumer {

    @Autowired
    private OpenAiEmbeddingModel openAiEmbeddingModel;
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;
    @Autowired
    private ApacheTikaDocumentParser documentParser;
    @Autowired
    private KbDocumentService kbDocumentService;
    @Autowired
    private ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    @Data
    private static class StructureBlock {
        private final String title;
        private final String content;
        private final String sourceType;
    }


    @RabbitListener(queues = Common.UPLOAD_QUEUE)
    public void handleLikeMessage(DocumentMessage documentMessage, Channel channel, Message message) throws IOException {
        // todo: 该方法可加上redis

        String fileUrl = documentMessage.getFileUrl();
        Long assistantId = documentMessage.getAssistantId();
        Long taskId = documentMessage.getTaskId();
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {

            // study: 消息幂等
            KbDocument kbDocument = kbDocumentService.getById(taskId);
            if (kbDocument == null) {
                log.warn("消费端幂等拦截: 根据taskId未查询到文档记录, taskId: {}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            Integer parseStatus = kbDocument.getParseStatus();

            if (parseStatus != null && parseStatus == 2) {
                log.info("消费端幂等拦截: 文档已经被成功处理过, taskId: {}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (parseStatus != null && parseStatus != 0) {
                // todo: 失败也会丢弃，这需要改进
                log.warn("消费端幂等拦截: 文档处于非待处理状态(当前状态:{}), 跳过执行, taskId: {}", parseStatus, taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // todo: 三次入库，后续可改进
            // study: 乐观锁 CAS
            boolean update = kbDocumentService.lambdaUpdate()
                    .eq(KbDocument::getId, taskId)
                    .eq(KbDocument::getParseStatus, 0)
                    .set(KbDocument::getParseStatus, 1)
                    .update();

            if (!update) {
                log.warn("消费端并发拦截: 未能抢占到文档处理权, taskId: {}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // study：包裹进来，避免在这里出现报错，导致rabbitMQ无限重试
            // study: 一次只加载一个文件
            // study: 有表格等的pdf怎么切，如果是个800字符的代码，会不会直接从中间截断

            log.info("正在将{}上传到milvus和es", fileUrl);

            // 固定大小切片
            Document document = FileSystemDocumentLoader.loadDocument(fileUrl, documentParser);
//            DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
//            List<TextSegment> textSegments = splitter.split(document);

            // study: 按结果拆分，超长文本递归处理
            List<TextSegment> textSegments = splitByStructureThenRecursive(document.text());

            int totalSegments = textSegments.size();
            log.info("文档解析完成, taskId: {}, 共切分为 {} 个片段, 准备分批处理", taskId, totalSegments);
            int batchSize = 50;

            // study: 分批处理前先清理旧向量
            clearVectorsByDocId(taskId);
            elasticsearchChunkIndexService.deleteByDocId(taskId);

            long sleepMs = 100L;

            // study: 如果 textSegments 特别多，应分批次（Batch）处理，以防触发硅基流动的 API 超时或频率限制，写入milvus也是
            for (int i = 0; i < totalSegments; i += batchSize) {

                int end = Math.min(i + batchSize, totalSegments);
                List<TextSegment> subList = textSegments.subList(i, end);

                processInBatches(subList, i, taskId, assistantId);
                elasticsearchChunkIndexService.indexBatch(taskId, assistantId,
                        kbDocument.getFileType(), i, subList);

                if (end < totalSegments) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("批处理被中断", e);
                    }
                }

            }

            kbDocumentService.lambdaUpdate()
                    .eq(KbDocument::getId, taskId)
                    .set(KbDocument::getParseStatus, 2)
                    .set(KbDocument::getChunkCount, totalSegments)
                    .update();

            log.info("{}上传到milvus和es成功", fileUrl);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("文件传入向量数据库失败, taskId: {}, 请查看日志：", taskId, e);

            // study: 先清理向量，再更新状态为失败
            try {
                clearVectorsByDocId(taskId);
            } catch (Exception ex) {
                log.warn("清理milvus失败，请查看日志：", e);
            }
            try {
                elasticsearchChunkIndexService.deleteByDocId(taskId);
            } catch (Exception ex) {
                log.warn("清理elasticsearch失败，请查看日志：", ex);
            }

            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }

            kbDocumentService.lambdaUpdate()
                    .eq(KbDocument::getId, taskId)
                    .set(KbDocument::getParseStatus, 3)
                    .set(KbDocument::getErrorMsg, "文件传入向量数据库失败: " + errorMsg)
                    .update();

            //todo: 可加入死信队列，但要设计好处理逻辑
            channel.basicNack(deliveryTag, false, false);
        }
    }

    List<TextSegment> splitByStructureThenRecursive(String text) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }

        // study: 先按结构分块
        List<StructureBlock> blocks = extractStructureBlocks(text);

        List<TextSegment> result = new ArrayList<>();
        for (StructureBlock block : blocks) {
            if (StrUtil.isBlank(block.getContent())) {
                continue;
            }
            // study：生成结果，并负责超长文本递归
            result.addAll(splitOversizedBlock(block));
        }

        return result;
    }

    private List<TextSegment> splitOversizedBlock(StructureBlock block) {
        String chunkText;
        if (StrUtil.isNotBlank(block.getTitle())) {
            chunkText = "标题:" + block.getTitle() + "\n" + block.getContent();
        } else {
            chunkText = block.getContent();
        }

        if (chunkText.length() <= 500) {
            TextSegment segment = TextSegment.from(chunkText);
            segment.metadata()
                    .put("section_title", block.getTitle())
                    .put("source_type", block.getSourceType());
            return List.of(segment);
        }

        // 只对这个文本进行递归切片
        DocumentSplitter splitter = DocumentSplitters.recursive(200, 20);
        List<TextSegment> textSegments = splitter.split(Document.document(chunkText));
        for (TextSegment segment : textSegments) {
            segment.metadata()
                    .put("section_title", block.getTitle())
                    .put("source_type", "fallback_recursive");
        }

        return textSegments;
    }

    // 按结构分段
    private List<StructureBlock> extractStructureBlocks(String text) {
        String currentTitle = "";
        StringBuilder currentParagraph = new StringBuilder();
        List<StructureBlock> blocks = new ArrayList<>();

        String replace = text.replace("\r\n", "\n");
        String[] lines = replace.split("\n");

        for (String line : lines) {
            // 新标题
            if (isMarkdownHeading(line)) {
                // 把当前段落提交成块
                flushParagraphBlock(blocks, currentTitle, currentParagraph);
                currentTitle = normalizeHeading(line);
                continue;
            }
            // 空行
            if (line.trim().isEmpty()) {
                flushParagraphBlock(blocks, currentTitle, currentParagraph);
                continue;
            }
            // 普通正文
            if (!currentParagraph.isEmpty()) {
                currentParagraph.append("\n");
            }
            currentParagraph.append(line);
        }

        flushParagraphBlock(blocks, currentTitle, currentParagraph);

        return blocks;
    }

    private void flushParagraphBlock(List<StructureBlock> blocks,
                                     String currentTitle,
                                     StringBuilder currentParagraph) {
        if (currentParagraph.isEmpty()) {
            return;
        }
        String content = currentParagraph.toString().trim();
        if (StrUtil.isNotBlank(content)) {
            blocks.add(new StructureBlock(currentTitle, content, "paragraph"));
        }
        // 清空currentParagraph
        currentParagraph.setLength(0);
    }

    private String normalizeHeading(String line) {
        return line.replaceFirst("^#{1,3}\\s+", "").trim();
    }

    private boolean isMarkdownHeading(String line) {
        return line.matches("^#{1,3}\\s+.+$");
    }

    private void processInBatches(List<TextSegment> subList, int index,
                                  Long taskId, Long assistantId) {

        for (int i = 0; i < subList.size(); i++) {
            int globalIndex = i + index;
            subList.get(i).metadata()
                    .put("doc_id", taskId)
                    .put("assistant_id", assistantId)
                    .put("chunk_index", globalIndex);
        }
        List<Embedding> content = openAiEmbeddingModel.embedAll(subList).content();
        embeddingStore.addAll(content, subList);
        log.info("第{}批文本块已存入milvus", index);
    }

    private void clearVectorsByDocId(Long taskId) {
        embeddingStore.removeAll(
                metadataKey("doc_id").isEqualTo(taskId)
        );
    }
}

// study:
//  1. txt/md 之外，针对 pdf/doc/docx 增加按文档结构切片能力
//  2. pdf 先做版面恢复，按页码/标题/段落/表格分块，再对超长块递归切片
//  3. doc/docx 按标题层级、段落、列表、表格切片，保留标题路径 metadata
//  4. 为 chunk 增加 page_no、section_title、source_type、file_type 等 metadata
//  5. 表格、代码块采用专用切片策略，避免语义被截断
//  6. 后续可升级为 parent-child chunk 与 token 级切片



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

// todo: 能不能使用这个往向量数据库里塞
//        EmbeddingStoreIngestor storeIngestor = EmbeddingStoreIngestor.builder()
//                .documentSplitter(DocumentSplitters.recursive(500, 50))
//                .embeddingStore(embeddingStore)
//                .embeddingModel()
//                .build();
//        storeIngestor.ingest(documents);
