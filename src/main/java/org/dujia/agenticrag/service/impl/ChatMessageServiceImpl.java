package org.dujia.agenticrag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dujia.agenticrag.domain.ChatMessage;
import org.dujia.agenticrag.service.ChatMessageService;
import org.dujia.agenticrag.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;

/**
* @author 夜聆秋雨
* @description 针对表【chat_message(聊天记录明细表)】的数据库操作Service实现
* @createDate 2026-03-23 16:19:09
*/
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService{

}




