package org.dujia.agenticrag.service;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Service
public class OpenAiStreamingService {
    private StreamingChatLanguageModel streamingChatLanguageModel;

    @Autowired
    public OpenAiStreamingService(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
    }

    public SseEmitter streamChatResponse(String userMessage) {
        SseEmitter emitter = new SseEmitter(300000L); // 超时时间

        streamingChatLanguageModel.chat(userMessage, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String s) {
                try {
                    emitter.send(SseEmitter.event().data(s));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                emitter.complete();
                log.info("生成完毕，总消耗Token: {}", chatResponse.tokenUsage().totalTokenCount());
            }

            @Override
            public void onError(Throwable throwable) {
                emitter.completeWithError(throwable);
                // 打印错误堆栈便于排查
                throwable.printStackTrace();
            }
        });

        return emitter;
    }
}
