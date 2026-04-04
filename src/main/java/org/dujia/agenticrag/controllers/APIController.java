package org.dujia.agenticrag.controllers;

import org.dujia.agenticrag.annotations.CurrentUserId;
import org.dujia.agenticrag.domain.AiAssistant;
import org.dujia.agenticrag.domain.ChatCompletionRequest;
import org.dujia.agenticrag.domain.ChatSession;
import org.dujia.agenticrag.domain.KbDocument;
import org.dujia.agenticrag.enums.ErrorCode;
import org.dujia.agenticrag.exceptions.BaseException;
import org.dujia.agenticrag.mapper.ChatSessionMapper;
import org.dujia.agenticrag.service.AiAssistantService;
import org.dujia.agenticrag.service.KbDocumentService;
import org.dujia.agenticrag.service.OpenAiStreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

// - ai_assistant 是私有资源
//  - 只有助手拥有者本人才能：
//      - 发起聊天
//      - 上传文档到这个助手
//      - 查询这个助手下文档任务状态
//      - 使用这个助手对应的知识库


@RestController
@RequestMapping("/v1")
public class APIController {

    @Autowired
    private KbDocumentService kbDocumentService;
    @Autowired
    private OpenAiStreamingService openAiStreamingService;
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private AiAssistantService aiAssistantService;

    @PostMapping("/kb/upload")
    public Map<String, Long> uploadDocument(
            @RequestParam("file") @Validated MultipartFile file,
            @RequestParam("assistant_id") @Validated Long assistantId,
            @CurrentUserId Long userId) {
        // study: 检验助手归属，下面的接口都要
        // todo: 三个接口都调用，都需要查库，后面可以改进
        // todo: aiAssistant不往下传，改为boolean会不会好点
        AiAssistant aiAssistant = aiAssistantService.checkAssistant(assistantId, userId);
        if (aiAssistant == null) {
            throw new BaseException(ErrorCode.UNABLE_TO_ACCESS_THIS_ASSISTANT);
        }

        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.THE_FILE_CANNOT_BE_EMPTY);
        }
        if (assistantId == null) {
            throw new RuntimeException("助手名不能为空");
        }
        Long taskId = kbDocumentService.uploadDocument(file, assistantId);
        return Map.of("task_id", taskId);
    }

//    @RequestMapping("/chat/completions")
//    public SseEmitter chat(String message, @CurrentUserId Long userId) {
//        return openAiStreamingService.streamChatResponse(message, userId);
//    }

    // study: 真非流可返回 Object
    @PostMapping("/chat/completions")
    public SseEmitter chat(@RequestBody @Validated ChatCompletionRequest request,
                           @CurrentUserId Long userId) {

        AiAssistant aiAssistant = aiAssistantService.checkAssistant(request.getAssistantId(), userId);
        if (aiAssistant == null) {
            throw new BaseException(ErrorCode.UNABLE_TO_ACCESS_THIS_ASSISTANT);
        }

        Long sessionId = request.getSessionId();

        if (sessionId == null) {
            // todo: 新会话，要创建新的sessionId，需不需要创建一张新的chat_session表？ chat_session表和chat_message有什么区别？
            // todo: 并发上来了会不会拉低性能？
            ChatSession chatSession = new ChatSession();
            chatSession.setAssistantId(request.getAssistantId());
            chatSession.setTitle("新会话"); // 一般由首个问题自动生成
            chatSession.setUserId(userId);
            chatSessionMapper.insert(chatSession);
            request.setSessionId(chatSession.getId());
            // todo: 要不然下面的if会报空指针异常
            sessionId = chatSession.getId();
        } else {
            if (sessionId < 0) {
                throw new BaseException(ErrorCode.INVALID_SESSION_ID);
            }
            // study: 需校验当前sessionId是属于当前用户和当前会话的
            // todo: 查数据库，需注意效率，加redis啥的？还要注意sql注入
            ChatSession existingSession = chatSessionMapper.selectById(sessionId);
            if (existingSession == null
                    || !existingSession.getUserId().equals(userId)
                    || !existingSession.getAssistantId().equals(request.getAssistantId())) {
                throw new BaseException(ErrorCode.UNAUTHORIZED_ACCESS);
            }
        }

        return openAiStreamingService.ChatResponse(request, userId);
    }

    @GetMapping("/kb/status/{task_id}")
    public Map<String, Object> getUploadStatus(@PathVariable("task_id") Long taskId,
                                               @CurrentUserId Long userId) {

        // todo: 是否真的需要加redis
        KbDocument doc = kbDocumentService.getById(taskId);
        if (doc == null) {
            throw new BaseException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        AiAssistant aiAssistant = aiAssistantService.checkAssistant(doc.getAssistantId(), userId);
        if (aiAssistant == null) {
            throw new BaseException(ErrorCode.UNABLE_TO_ACCESS_THIS_ASSISTANT);
        }

        return Map.of(
                "status", doc.getParseStatus(),
                "error_msg", doc.getErrorMsg() == null ? "" : doc.getErrorMsg()
        );
    }
}
