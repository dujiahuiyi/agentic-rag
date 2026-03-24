package org.dujia.agenticrag.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库文档表(MQ异步任务表)
 * @TableName kb_document
 */
@TableName(value ="kb_document")
@Data
public class KbDocument implements Serializable {
    /**
     * 文档ID (TaskID)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的助手ID
     */
    private Long assistantId;

    /**
     * 原始文件名称
     */
    private String fileName;

    /**
     * 文件扩展名
     */
    private String fileType;

    /**
     * 存储路径(OSS/本地)
     */
    private String fileUrl;

    /**
     * 解析状态: 0待处理 1解析中 2已完成 3失败
     */
    private Integer parseStatus;

    /**
     * 失败时的异常信息
     */
    private String errorMsg;

    /**
     * 切分出的文本块数量
     */
    private Integer chunkCount;

    /**
     * 上传时间
     */
    private LocalDateTime createTime;

    /**
     * 状态更新时间
     */
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}