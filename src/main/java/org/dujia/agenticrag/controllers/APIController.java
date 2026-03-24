package org.dujia.agenticrag.controllers;

import org.dujia.agenticrag.enums.ErrorCode;
import org.dujia.agenticrag.exceptions.BaseException;
import org.dujia.agenticrag.service.KbDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class APIController {

    @Autowired
    private KbDocumentService kbDocumentService;

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
}
