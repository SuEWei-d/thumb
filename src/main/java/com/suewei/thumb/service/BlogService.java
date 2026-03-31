package com.suewei.thumb.service;

import com.suewei.thumb.model.dto.vo.BlogVO;
import com.suewei.thumb.model.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author ASUS
* @description 针对表【blog】的数据库操作Service
* @createDate 2026-03-31 18:39:15
*/
public interface BlogService extends IService<Blog> {

    public BlogVO getById(Long id, HttpServletRequest request);

    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}
