package com.suewei.thumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suewei.thumb.constant.UserConstant;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.service.UserService;
import com.suewei.thumb.mapper.UserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
* @author ASUS
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-03-31 18:39:15
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public User getLoginer(HttpServletRequest request) {
        // 从session获取user
        User user = (User)request.getSession().getAttribute(UserConstant.LOGIN_USER);

        return user;
    }

    @Override
    public User login(Long userId, HttpServletRequest request) {
        // 从数据库查找用户
        User user = getBaseMapper().selectById(userId);
        if (Objects.isNull(user)){
            throw new RuntimeException("当前用户不存在");
        }
        // 将用户存入Session
        request.getSession().setAttribute(UserConstant.LOGIN_USER, user);

        return user;

    }
}




