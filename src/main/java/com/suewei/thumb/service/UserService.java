package com.suewei.thumb.service;

import com.suewei.thumb.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author ASUS
* @description 针对表【user】的数据库操作Service
* @createDate 2026-03-31 18:39:15
*/
public interface UserService extends IService<User> {

    /**
     * 获取当前登录用户
     * @param request
     * @return
     */
    User getLoginer(HttpServletRequest request);

}
