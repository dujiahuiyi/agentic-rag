package org.dujia.agenticrag.service;

import org.dujia.agenticrag.domain.KbDocument;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
* @author 夜聆秋雨
* @description 针对表【kb_document(知识库文档表(MQ异步任务表))】的数据库操作Service
* @createDate 2026-03-23 16:19:09
*/
public interface KbDocumentService extends IService<KbDocument> {

    Long uploadDocument(MultipartFile file, Long assistantId);
}
