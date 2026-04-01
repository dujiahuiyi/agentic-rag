package org.dujia.agenticrag.domain;

import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class UserDto {
    private Long id;

    /**
     * 登录账号
     */
    private String username;


    /**
     * 注册时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    public static UserDto of(SysUser user) {
        UserDto userDto = new UserDto();
        // todo: 高并发情况下有性能损耗，可考虑 MapStruct
        BeanUtils.copyProperties(user, userDto);
        return userDto;
    }
}
