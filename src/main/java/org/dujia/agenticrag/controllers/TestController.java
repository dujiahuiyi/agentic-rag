package org.dujia.agenticrag.controllers;

import org.dujia.agenticrag.service.OpenAiStreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/test")
public class TestController {
    @Autowired
    private OpenAiStreamingService openAiStreamingService;

    @RequestMapping("/stream")
    public SseEmitter chat(String message) {
        return openAiStreamingService.streamChatResponse(message);
    }
}
