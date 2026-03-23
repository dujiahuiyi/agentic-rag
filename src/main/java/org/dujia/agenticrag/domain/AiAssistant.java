package org.dujia.agenticrag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 助手配置表
 * @TableName ai_assistant
 */
@TableName(value ="ai_assistant")
@Data
public class AiAssistant implements Serializable {
    /**
     * 助手ID
     */
    @TableId
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 助手名称
     */
    private String name;

    /**
     * 助手头像URL
     */
    private String avatar;

    /**
     * 用户自定义的 System Prompt
     */
    private String systemPrompt;

    /**
     * 绑定的模型
     */
    private String modelName;

    /**
     * 是否为默认助手 (1是 0否)
     */
    private Long isDefault;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}