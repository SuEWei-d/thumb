package com.suewei.thumb.controller;

import com.suewei.thumb.common.BaseResponse;
import com.suewei.thumb.common.ResultUtils;
import com.suewei.thumb.model.dto.vo.BlogVO;
import com.suewei.thumb.model.entity.Blog;
import com.suewei.thumb.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("blog")
@Tag(name = "博客相关接口")
public class BlogController {

    @Resource
    private BlogService blogService;

    @Operation(summary = "根据id获取博客")
    @GetMapping("/get")
    public BaseResponse<BlogVO> getById(Long id, HttpServletRequest request){
        BlogVO blogVO = blogService.getById(id, request);
        return ResultUtils.success(blogVO);
    }

    @Operation(summary = "获取博客列表")
    @GetMapping("/list")
    public BaseResponse<List<BlogVO>> list(HttpServletRequest request){
        List<Blog> blogList = blogService.list();
        List<BlogVO> blogVOList = blogService.getBlogVOList(blogList, request);
        return ResultUtils.success(blogVOList);
    }


}
