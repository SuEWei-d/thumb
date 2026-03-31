package com.suewei.thumb.controller;

import com.suewei.thumb.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("user")
public class UserController {

    @Resource
    private UserService userService;

}
