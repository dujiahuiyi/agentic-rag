package org.dujia.agenticrag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户表
 * @TableName sys_user
 */
@TableName(value ="sys_user")
@Data
public class SysUser implements Serializable {
    /**
     * 主键ID
     */
    @TableId
    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 密码哈希值
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 逻辑删除 (0正常 1删除)
     */
    private Integer isDeleted;

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