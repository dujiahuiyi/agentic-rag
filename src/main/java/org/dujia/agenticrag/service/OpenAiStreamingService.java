package org.dujia.agenticrag.service;

import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.domain.Assistant;
import org.dujia.agenticrag.domain.ChatCompletionRequest;
import org.dujia.agenticrag.domain.ChatMessage;
import org.dujia.agenticrag.mapper.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class OpenAiStreamingService {

    private final ChatMessageMapper chatMessageMapper;
    private final Assistant assistant;

    @Autowired
    public OpenAiStreamingService(ChatMessageMapper chatMessageMapper,
                                  Assistant assistant) {
        this.chatMessageMapper = chatMessageMapper;
        this.assistant = assistant;
    }

    // study: 流式/非流输出
    public SseEmitter ChatResponse(ChatCompletionRequest request, Long userId) {

        String message = request.getMessage();
        Long sessionId = request.getSessionId();

        // todo: 落库失败了怎么办，使用事务？但这是另一个方法
        insertChatMessageByRole(sessionId, message, "user");
        // study: 用户消息 1 次插入 + 助手消息 1 次占位插入 + 后续更新 : 可防止用户问了，ai答了，但数据库没有数据的问题

        SseEmitter emitter = new SseEmitter(300000L);
        emitter.onCompletion(() -> {
            log.info("SSE 连接已完成: User ID {}", userId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: User ID {}", userId);
            emitter.complete();
        });

        if (request.isStream()) {
            // 流式
            TokenStream tokenStream = assistant.streamChat(sessionId, message);
            StringBuilder aiResponse = new StringBuilder();
            tokenStream
                    .onPartialResponse(consumer -> {
                        // study: 一边send，一边拼StringBuilder
                        aiResponse.append(consumer);
                        try {
                            emitter.send(SseEmitter.event().data(consumer));
                        } catch (IOException e) {
                            log.warn("发送 Token 失败，客户端可能已断开: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    })
                    .onCompleteResponse(response -> {
                        // study: 助手回复落库，发一个completion事件把session_id给前端
                        saveAiMessageAndComplete(sessionId, aiResponse.toString(), emitter);
                    })
                    .onError(error -> {
                        handlerError(error, emitter);
                    })
                    .start();
        } else {
            // 非流
            // todo: 默认无边界线程池会不会有什么问题？
            CompletableFuture.runAsync(() -> {
                try {
                    String aiResponse = assistant.chat(sessionId, message);
                    // todo: 这里和流式的响应要不要将aiResponse转为json
                    emitter.send(SseEmitter.event().data(aiResponse));
                    saveAiMessageAndComplete(sessionId, aiResponse, emitter);
                } catch (Exception error) {
                    handlerError(error, emitter);
                }
            });
        }
        return emitter;
    }

    private void handlerError(Throwable error, SseEmitter emitter) {
        log.error("大模型生成发生异常", error);
        try {
            emitter.send(SseEmitter.event().name("error").data("大模型服务异常，请稍后再试。"));
        } catch (IOException e) {
            log.warn("发送异常信息失败", e);
        } finally {
            // study: 至少要 completeWithError，别让 SSE 班开着挂住
            emitter.completeWithError(error);
        }
    }

    private void saveAiMessageAndComplete(Long sessionId, String message, SseEmitter emitter) {
        insertChatMessageByRole(sessionId, message, "assistant");
        try {
            emitter.send(SseEmitter.event().name("completion").data(Map.of(
                    "session_id", sessionId
            )));
            emitter.complete();
        } catch (IOException e) {
            log.error("完成事件处理失败", e);
            emitter.completeWithError(e);
        }
    }

    private void insertChatMessageByRole(Long sessionId, String message, String role) {
        try {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSessionId(sessionId);
            chatMessage.setContent(message);
            // study: 数据库里默认role为user，也需要在这里指定
            chatMessage.setRole(role);
            // todo: 以后上缓存时需要传值
            chatMessage.setIsCached(0L); // 是否命中缓存
            //todo: 落库是否需要异步
            chatMessageMapper.insert(chatMessage);
        } catch (Exception e) {
            log.error("会话消息落库失败，请查看日志 ", e);
        }
    }


//    public SseEmitter streamChatResponse(ChatCompletionRequest request, Long userId) {
//
//
//        ChatMessage chatMessage = new ChatMessage();
//        chatMessage.setSessionId(request.getSessionId());
//        chatMessage.setContent(request.getMessage());
//        chatMessage.setIsCached(0L); // 是否命中缓存
//        chatMessageMapper.insert(chatMessage);
//
//
//
//        SseEmitter emitter = new SseEmitter(300000L);
//        emitter.onCompletion(() -> log.info("SSE 连接已完成: User ID {}", userId));
//        emitter.onTimeout(() -> {
//            log.warn("SSE 连接超时: User ID {}", userId);
//            emitter.complete();
//        });
//
//        TokenStream tokenStream = assistant.streamChat(request.getSessionId(), request.getMessage());
//        StringBuilder aiResponseBuilder = new StringBuilder();
//
//        tokenStream
//                .onPartialResponse(token -> {
//                    try {
//
//                        aiResponseBuilder.append(token);
//                        emitter.send(SseEmitter.event().data(token));
//                    } catch (IOException e) {
//                        log.warn("发送 Token 失败，客户端可能已断开: {}", e.getMessage());
//                        emitter.completeWithError(e);
//                    }
//                }).onCompleteResponse(chatResponse -> {
//                    try {
//
//                        ChatMessage aiMessage = new ChatMessage();
//                        aiMessage.setSessionId(request.getSessionId());
//                        aiMessage.setContent(aiResponseBuilder.toString());
//                        aiMessage.setRole("assistant");
//                        aiMessage.setIsCached(0L);
//                        chatMessageMapper.insert(aiMessage);
//                    } catch (Exception e) {
//                        log.error("[OpenAiStreamingService::streamChatResponse] 大模型回复落库失败，请查看日志, ", e);
//                    }
//                    try {
//                        emitter.send(SseEmitter
//                                .event()
//                                .name("completion")
//                                .data(Map.of("session_id", request.getSessionId())));
//                    } catch (IOException e) {
//                        log.warn("发送 completion 事件失败: {}", e.getMessage());
//                    }
//                    if (chatResponse.tokenUsage() != null) {
//                        log.info("回答生成完毕，总消耗 Token: {}", chatResponse.tokenUsage().totalTokenCount());
//                    } else {
//                        log.info("回答生成完毕，未能获取 Token 消耗统计");
//                    }
//                    emitter.complete();
//                }).onError(error -> {
//
//                    log.error("大模型生成流发生异常", error);
//                    try {
//                        emitter.send(SseEmitter.event().name("error").data("大模型服务异常，请稍后再试。"));
//                    } catch (IOException e) {
//                        log.warn("给客户端发送异常信息失败", e);
//                    } finally {
//                        emitter.completeWithError(error);
//                    }
//                }).start();
//        return emitter;
//    }



    //    private final StreamingChatLanguageModel streamingChatLanguageModel;
//    private final RetrievalAugmentor retrievalAugmentor;
    // todo: 需不需独立出来一个类
//    private final ConcurrentHashMap<Object, ChatMemory> memoryMap;
//    @Autowired
//    public OpenAiStreamingService(Assistant streamingAssistant) {
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
//        Assistant streamingAssistant = AiServices.builder(Assistant.class)
//                // 直接传 streamingChatLanguageModel 就可以流式输出了吗？需不需要重写哪三个方法
//                   study: 不需要了，只需要调用 tokenStream.onNext(...) 并最后调用 .start() 即可
//                .streamingChatLanguageModel(streamingChatLanguageModel)
//                .chatMemoryProvider(chatMemory)
//                .retrievalAugmentor(retrievalAugmentor)
//                .build();
//
//        TokenStream chat = streamingAssistant.chat(userId, userMessage);
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

}
