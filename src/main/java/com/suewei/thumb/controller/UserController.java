package com.suewei.thumb.controller;

import com.suewei.thumb.common.BaseResponse;
import com.suewei.thumb.common.ResultUtils;
import com.suewei.thumb.constant.UserConstant;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("user")
@Tag(name = "用户相关接口")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户登录
     * @param userId
     * @param request
     * @return
     */
    @GetMapping("/login")
    @Operation(summary = "用户登录")
    public BaseResponse<User> login(Long userId, HttpServletRequest request){

        User user = userService.login(userId, request);
        return ResultUtils.success(user);
    }

    /**
     * 获取登录用户
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    @Operation(summary = "获取当前登录用户")
    public BaseResponse<User> getLoginer(HttpServletRequest request){
        User user = userService.getLoginer(request);
        return ResultUtils.success(user);
    }

}
