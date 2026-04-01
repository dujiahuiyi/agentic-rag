package org.dujia.agenticrag.controllers;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.domain.UserDto;
import org.dujia.agenticrag.domain.UserRequest;
import org.dujia.agenticrag.domain.UserResponse;
import org.dujia.agenticrag.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/user")
public class UserController {

    @Autowired
    private SysUserService sysUserService;

    /**
     * 注册
     *
     * @param userRequest
     * @return
     */
    @PostMapping("/register")
    @Operation(summary = "注册")
    public UserDto register(@RequestBody @Validated UserRequest userRequest) {
        log.info("用户: {} 注册", userRequest.getUsername());
        return sysUserService.register(userRequest);
    }

    /**
     * 登录
     *
     * @param userRequest
     * @return
     */
    @PostMapping("login")
    @Operation(summary = "登录")
    public UserResponse login(@RequestBody @Validated UserRequest userRequest) {
        // todo: 可新增输错5次密码后锁定账户，但需新增解锁方法
        return sysUserService.login(userRequest);
    }
}
