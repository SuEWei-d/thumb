package com.suewei.thumb.service;

import com.suewei.thumb.model.dto.thumb.DoThumbRequest;
import com.suewei.thumb.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author ASUS
* @description 针对表【thumb】的数据库操作Service
* @createDate 2026-03-31 18:39:15
*/
public interface ThumbService extends IService<Thumb> {
    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return
     */
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);


    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
}
