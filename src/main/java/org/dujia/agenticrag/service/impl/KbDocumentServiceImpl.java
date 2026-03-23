package org.dujia.agenticrag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dujia.agenticrag.domain.KbDocument;
import org.dujia.agenticrag.service.KbDocumentService;
import org.dujia.agenticrag.mapper.KbDocumentMapper;
import org.springframework.stereotype.Service;

/**
* @author 夜聆秋雨
* @description 针对表【kb_document(知识库文档表(MQ异步任务表))】的数据库操作Service实现
* @createDate 2026-03-23 16:19:09
*/
@Service
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument>
    implements KbDocumentService{

}




