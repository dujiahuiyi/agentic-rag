package org.dujia.agenticrag.service;

import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.domain.Assistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Service
public class OpenAiStreamingService {
//    private final StreamingChatLanguageModel streamingChatLanguageModel;
//    private final RetrievalAugmentor retrievalAugmentor;
    // todo: 需不需独立出来一个类
//    private final ConcurrentHashMap<Object, ChatMemory> memoryMap;

    private final Assistant assistant;
    private final View error;

    @Autowired
    public OpenAiStreamingService(Assistant assistant, View error) {
        this.assistant = assistant;
        this.error = error;
    }

//    @Autowired
//    public OpenAiStreamingService(Assistant assistant) {
//        this.streamingChatLanguageModel = streamingChatLanguageModel;
//        this.retrievalAugmentor = retrievalAugmentor;
//        this.memoryMap = new ConcurrentHashMap<>();
//    }

//    public SseEmitter streamChatResponse(String userMessage, Long userId) {
//        SseEmitter emitter = new SseEmitter(300000L); // 超时时间
//
//        // study: 每次调用都需要new出来，可以放进配置类里
//        ChatMemoryProvider chatMemory = memoryId ->
//                memoryMap.computeIfAbsent(memoryId, id ->
//                        MessageWindowChatMemory.builder().maxMessages(30).id(id).build());
//
//        Assistant assistant = AiServices.builder(Assistant.class)
//                // 直接传 streamingChatLanguageModel 就可以流式输出了吗？需不需要重写哪三个方法
//                   study: 不需要了，只需要调用 tokenStream.onNext(...) 并最后调用 .start() 即可
//                .streamingChatLanguageModel(streamingChatLanguageModel)
//                .chatMemoryProvider(chatMemory)
//                .retrievalAugmentor(retrievalAugmentor)
//                .build();
//
//        TokenStream chat = assistant.chat(userId, userMessage);
//
//        streamingChatLanguageModel.chat(userMessage, new StreamingChatResponseHandler() {
//
//            @Override
//            public void onPartialResponse(String s) {
//                try {
//                    emitter.send(SseEmitter.event().data(s));
//                } catch (IOException e) {
//                    emitter.completeWithError(e);
//                }
//            }
//
//            @Override
//            public void onCompleteResponse(ChatResponse chatResponse) {
//                emitter.complete();
//                log.info("生成完毕，总消耗Token: {}", chatResponse.tokenUsage().totalTokenCount());
//            }
//
//            @Override
//            public void onError(Throwable throwable) {
//                emitter.completeWithError(throwable);
//                // 打印错误堆栈便于排查
//                throwable.printStackTrace();
//            }
//        });
//
//        return emitter;
//    }

    // study: 流式输出
    public SseEmitter streamChatResponse(String userMessage, Long userId) {
        SseEmitter emitter = new SseEmitter(300000L);
        emitter.onCompletion(() -> log.info("SSE 连接已完成: User ID {}", userId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: User ID {}", userId);
            emitter.complete();
        });

        TokenStream tokenStream = assistant.chat(userId, userMessage);

        tokenStream
                .onPartialResponse(token -> {
            try {
                emitter.send(SseEmitter.event().data(token));
            } catch (IOException e) {
                log.warn("发送 Token 失败，客户端可能已断开: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }).onCompleteResponse(chatResponse -> {
                    if (chatResponse.tokenUsage() != null) {
                        log.info("回答生成完毕，总消耗 Token: {}", chatResponse.tokenUsage().totalTokenCount());
                    } else {
                        log.info("回答生成完毕，未能获取 Token 消耗统计");
                    }
                    emitter.complete();
        }).onError(error -> {
                    log.error("大模型生成流发生异常", error);
                    try {
                        emitter.send(SseEmitter.event().name("error").data("大模型服务异常，请稍后再试。"));
                    } catch (IOException e) {
                        emitter.completeWithError(error);

                    }
                }).start();
        return emitter;
    }
}
