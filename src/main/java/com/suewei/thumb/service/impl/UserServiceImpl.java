package com.suewei.thumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suewei.thumb.constant.UserConstant;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.service.UserService;
import com.suewei.thumb.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

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
}




