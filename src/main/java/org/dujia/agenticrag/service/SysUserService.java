package org.dujia.agenticrag.service;

import org.dujia.agenticrag.domain.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;
import org.dujia.agenticrag.domain.UserDto;
import org.dujia.agenticrag.domain.UserRequest;
import org.dujia.agenticrag.domain.UserResponse;

/**
* @author 夜聆秋雨
* @description 针对表【sys_user(用户表)】的数据库操作Service
* @createDate 2026-03-23 16:19:09
*/
public interface SysUserService extends IService<SysUser> {

    UserDto register(UserRequest userRequest);

    UserResponse login(UserRequest userRequest);
}
