package org.dujia.agenticrag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dujia.agenticrag.domain.SysUser;
import org.dujia.agenticrag.domain.UserDto;
import org.dujia.agenticrag.domain.UserRequest;
import org.dujia.agenticrag.domain.UserResponse;
import org.dujia.agenticrag.enums.ErrorCode;
import org.dujia.agenticrag.exceptions.BaseException;
import org.dujia.agenticrag.service.SysUserService;
import org.dujia.agenticrag.mapper.SysUserMapper;
import org.dujia.agenticrag.tools.JwtUtils;
import org.dujia.agenticrag.tools.PasswordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
* @author 夜聆秋雨
* @description 针对表【sys_user(用户表)】的数据库操作Service实现
* @createDate 2026-03-23 16:19:09
*/
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
    implements SysUserService{

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public UserDto register(UserRequest userRequest) {
        SysUser existsUser = selectByUserName(userRequest.getUsername());
        if (existsUser != null) {
            throw new BaseException(ErrorCode.USER_ALREADY_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(userRequest.getUsername());
        String encode = PasswordUtils.encode(userRequest.getPassword());
        user.setPassword(encode);
        int insert = sysUserMapper.insert(user);
        if (insert <= 0) {
            throw new BaseException(ErrorCode.FAIL_TO_REGISTER);
        }
        // todo: mybatis-plus并未自动填充创建时间和更新时间
        return UserDto.of(user);
    }

    @Override
    public UserResponse login(UserRequest userRequest) {
        SysUser existsUser = selectByUserName(userRequest.getUsername());
        if (existsUser == null) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }
        if (!PasswordUtils.matches(userRequest.getPassword(), existsUser.getPassword())) {
            throw new BaseException(ErrorCode.INVALID_USERNAME_OR_PASSWORD);
        }
        // 登录成功，发布token
        String token = JwtUtils.generateToken(existsUser.getId());
        return new UserResponse(userRequest.getUsername(), token);
    }

    private SysUser selectByUserName(String username) {
        //study:mybatis-plus查询操作
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, username);
        return sysUserMapper.selectOne(queryWrapper);
    }
}




