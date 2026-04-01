package org.dujia.agenticrag.controllers;

import org.dujia.agenticrag.annotations.CurrentUserId;
import org.dujia.agenticrag.domain.KbDocument;
import org.dujia.agenticrag.enums.ErrorCode;
import org.dujia.agenticrag.exceptions.BaseException;
import org.dujia.agenticrag.service.KbDocumentService;
import org.dujia.agenticrag.service.OpenAiStreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class APIController {

    @Autowired
    private KbDocumentService kbDocumentService;
    @Autowired
    private OpenAiStreamingService openAiStreamingService;

    @PostMapping("/kb/upload")
    public Map<String, Long> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("assistant_id") Long assistantId) {
        if (file == null) {
            throw new BaseException(ErrorCode.THE_FILE_CANNOT_BE_EMPTY);
        }
        if (assistantId == null) {
            throw new RuntimeException("助手名不能为空");
        }
        Long taskId = kbDocumentService.uploadDocument(file, assistantId);
        return Map.of("task_id", taskId);
    }

    @RequestMapping("/chat/completions")
    public SseEmitter chat(String message, @CurrentUserId Long userId) {
        return openAiStreamingService.streamChatResponse(message, userId);
    }

    @GetMapping("/kb/status/{task_id}")
    public Map<String, Object> getUploadStatus(@PathVariable("task_id") Long taskId) {
        // todo: 是否真的需要加redis
        KbDocument doc = kbDocumentService.getById(taskId);

        if (doc == null) {
            throw new BaseException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        return Map.of(
                "status", doc.getParseStatus(),
                "error_msg", doc.getErrorMsg() == null ? "" : doc.getErrorMsg()
        );
    }
}
