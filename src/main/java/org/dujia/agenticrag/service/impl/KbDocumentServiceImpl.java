package org.dujia.agenticrag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dujia.agenticrag.commons.Common;
import org.dujia.agenticrag.domain.DocumentMessage;
import org.dujia.agenticrag.domain.KbDocument;
import org.dujia.agenticrag.service.KbDocumentService;
import org.dujia.agenticrag.mapper.KbDocumentMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
* @author 夜聆秋雨
* @description 针对表【kb_document(知识库文档表(MQ异步任务表))】的数据库操作Service实现
* @createDate 2026-03-23 16:19:09
*/
@Service
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument>
    implements KbDocumentService{

    @Value("${app.file.upload-dir}")
    private String uploadUrl;
    @Autowired
    private KbDocumentMapper kbDocumentMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // todo: 可后台进行
        kbDocumentMapper.insert(kbDocument);

        // study: mybatis-plus雪花算法生成 id 后自动回填主键 id
        Long takeId = kbDocument.getId();

        // 后台mq往milvus里塞
        DocumentMessage message = new DocumentMessage(takeId, absolutePath, assistantId);
        rabbitTemplate.convertAndSend(
                Common.UPLOAD_EXCHANGE,
                Common.UPLOAD_ROUTING_KEY,
                message
        );

        return takeId;
    }
}




