package org.dujia.agenticrag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天记录明细表
 * @TableName chat_message
 */
@TableName(value ="chat_message")
@Data
public class ChatMessage implements Serializable {
    /**
     * 消息ID
     */
    @TableId
    private Long id;

    /**
     * 归属的会话ID
     */
    private Long sessionId;

    /**
     * 角色: system / user / assistant / tool
     */
    private String role;

    /**
     * 消息正文内容
     */
    private String content;

    /**
     * 消耗的Token数
     */
    private Long tokensUsed;

    /**
     * 是否命中Redis缓存直接返回 (1是 0否)
     */
    private Long isCached;

    /**
     * 消息发送时间
     */
    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}