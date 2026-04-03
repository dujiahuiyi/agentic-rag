package org.dujia.agenticrag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dujia.agenticrag.domain.AiAssistant;
import org.dujia.agenticrag.domain.ChatSession;
import org.dujia.agenticrag.enums.ErrorCode;
import org.dujia.agenticrag.exceptions.BaseException;
import org.dujia.agenticrag.mapper.ChatSessionMapper;
import org.dujia.agenticrag.service.AiAssistantService;
import org.dujia.agenticrag.mapper.AiAssistantMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
* @author 夜聆秋雨
* @description 针对表【ai_assistant(助手配置表)】的数据库操作Service实现
* @createDate 2026-03-23 16:19:09
*/
@Service
public class AiAssistantServiceImpl extends ServiceImpl<AiAssistantMapper, AiAssistant>
    implements AiAssistantService{

    private final ChatSessionMapper chatSessionMapper;

    @Autowired
    public AiAssistantServiceImpl(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    @Override
    public String getSystemPromptBySessionId(Long sessionId) {
        // todo: 两轮查表，以后可以改进，具体改进思路是什么？
        ChatSession chatSession = chatSessionMapper.selectById(sessionId);
        if (chatSession == null) {
            return buildDefaultPrompt(null);
        }
        AiAssistant assistant = getById(chatSession.getAssistantId());
        if (assistant != null && assistant.getSystemPrompt() != null && !assistant.getSystemPrompt().isBlank()) {
            return assistant.getSystemPrompt();
        }
        return buildDefaultPrompt(assistant);
    }

    @Override
    public String buildDefaultPrompt(AiAssistant assistant) {
        String assistantName = assistant == null ? "智能助手" : assistant.getName();
        // todo: 这个系统提示词对以后使用谷歌搜索不怎么友好
        // todo: 在助手接口可更新落库
        return """
                你是一个%s。
                请优先根据检索到的知识库内容回答问题。
                如果知识库中没有明确答案，就明确说不知道，不要编造。
                回答请使用中文，尽量简洁准确。
                """.formatted(assistantName);
    }

    @Override
    public AiAssistant checkAssistant(Long assistantId, Long userId) {
        if (assistantId == null || assistantId < 0) {
            throw new RuntimeException("助手Id不合法");
        }
        if (userId == null || userId < 0) {
            throw new RuntimeException("用户Id不合法");
        }
        AiAssistant assistant = getById(assistantId);
        if (assistant == null) {
            throw new BaseException(ErrorCode.ASSISTANT_NOT_EXIST);
        }
        if (assistant.getUserId().equals(userId)) {
            return assistant;
        }
        return null;
    }
}




