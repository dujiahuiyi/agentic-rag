package org.dujia.agenticrag.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatCompletionRequest {
    // study: 绑定正确的字段
    @JsonProperty("assistant_id")
    @NotNull(message = "助手Id不能为空")
    private Long assistantId;
    @JsonProperty("session_id")
    private Long sessionId;
    @NotBlank(message = "消息不能为空")
    private String message;
    private boolean stream = true; // 前端默认为true，前端也可传false
}
