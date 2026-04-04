package org.dujia.agenticrag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.commons.Common;
import org.dujia.agenticrag.domain.DocumentMessage;
import org.dujia.agenticrag.domain.KbDocument;
import org.dujia.agenticrag.service.KbDocumentService;
import org.dujia.agenticrag.mapper.KbDocumentMapper;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
* @author 夜聆秋雨
* @description 针对表【kb_document(知识库文档表(MQ异步任务表))】的数据库操作Service实现
* @createDate 2026-03-23 16:19:09
*/
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument>
    implements KbDocumentService{

    @Value("${app.file.upload-dir}")
    private String uploadUrl;
    @Autowired
    private KbDocumentMapper kbDocumentMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public Long uploadDocument(MultipartFile file, Long assistantId) {
        // todo: 需不要加入redis？加入了能干嘛

        // study: 本地文件写入
        String fileName = file.getOriginalFilename();
        String fileExtension = "";
        if (fileName != null && fileName.contains(".")) {
            fileExtension = fileName.substring(fileName.lastIndexOf("."));
        }
        String newFileName = UUID.randomUUID().toString().replace("-", "") + fileExtension;
        File dir = new File(uploadUrl);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File descFile = new File(uploadUrl + newFileName);
        try {
            // study: 不能也在后台进行，当接口结束，spring会自己清理临时文件
            file.transferTo(descFile);
        } catch (IOException e) {
            throw new RuntimeException("文件储存到本地失败");
        }
        String absolutePath = descFile.getAbsolutePath();

        KbDocument kbDocument = new KbDocument();
        kbDocument.setAssistantId(assistantId);
        kbDocument.setFileName(fileName);
        kbDocument.setFileType(fileExtension.replace(".", ""));
        kbDocument.setFileUrl(absolutePath);
        kbDocument.setParseStatus(0);

        // study: 生产端一致性，入库成功之后，再发mq
        // todo: 可后台进行
        kbDocumentMapper.insert(kbDocument);

        // study: mybatis-plus雪花算法生成 id 后自动回填主键 id
        Long takeId = kbDocument.getId();

        // 后台mq往milvus里塞
        DocumentMessage message = new DocumentMessage(takeId, absolutePath, assistantId);

        // study: 事务同步
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 注册回调返回
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendUploadMessage(message);
                }
            });
        } else {
            // 事务未启动，直接返回
            sendUploadMessage(message);
        }

        return takeId;
    }

    private void sendUploadMessage(DocumentMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    Common.UPLOAD_EXCHANGE,
                    Common.UPLOAD_ROUTING_KEY,
                    message
            );
        } catch (AmqpException e) {
            // todo: 先记录错误日志，后续再考虑重试
            log.error("MQ发送失败, message: {}", message, e);
        }
    }
}




