package org.dujia.agenticrag.domain;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface Assistant {
    @SystemMessage("你是一个智能助手，能根据提供的上下文回答问题")
    TokenStream chat(@MemoryId Long userId, @UserMessage String userMessage);
}
