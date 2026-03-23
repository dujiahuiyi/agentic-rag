package org.dujia.agenticrag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聊天会话表
 * @TableName chat_session
 */
@TableName(value ="chat_session")
@Data
public class ChatSession implements Serializable {
    /**
     * 会话记录ID
     */
    @TableId
    private Long id;

    /**
     * 发起用户ID
     */
    private Long userId;

    /**
     * 对话使用的助手ID
     */
    private Long assistantId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 是否已删除
     */
    private Long isDeleted;

    /**
     * 会话创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}