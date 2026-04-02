package org.dujia.agenticrag.domain;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface Assistant {
    // todo: 不要写死，从ai_assistant表里的system_prompt获取，或者根据上传的文档自动生成
    // todo: 把userId当MemoryId，会导致同一个用户的所有会话串上下文，可改为sessionId
    @SystemMessage("你是一个智能助手，能根据提供的上下文回答问题")
    TokenStream streamChat(@MemoryId Long sessionId, @UserMessage String userMessage);

    @SystemMessage("你是一个智能助手，能根据提供的上下文回答问题")
    String chat(@MemoryId Long sessionId, @UserMessage String userMessage);
}
