package com.suewei.thumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suewei.thumb.model.entity.Blog;
import com.suewei.thumb.service.BlogService;
import com.suewei.thumb.mapper.BlogMapper;
import org.springframework.stereotype.Service;

/**
* @author ASUS
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2026-03-31 18:39:15
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

}




