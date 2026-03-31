package com.suewei.thumb.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suewei.thumb.model.dto.vo.BlogVO;
import com.suewei.thumb.model.entity.Blog;
import com.suewei.thumb.model.entity.Thumb;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.service.BlogService;
import com.suewei.thumb.mapper.BlogMapper;
import com.suewei.thumb.service.ThumbService;
import com.suewei.thumb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
* @author ASUS
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2026-03-31 18:39:15
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

    @Resource
    private UserService userService;

    @Lazy
    @Resource
    private ThumbService thumbService;

    @Override
    public BlogVO getById(Long id, HttpServletRequest request) {
        Blog blog = getById(id);
        User user = userService.getLoginer(request);
        return this.getBlogVO(blog, user);
    }

    private BlogVO getBlogVO(Blog blog, User user) {
        // 查询thumb表获取当前用户是否在这个blog下点赞
        Thumb thumb = thumbService.lambdaQuery()
                .eq(Thumb::getUserid, user.getId())
                .eq(Thumb::getBlogid, blog.getId())
                .one();

        BlogVO blogVO = new BlogVO();
        BeanUtils.copyProperties(blog, blogVO);

        blogVO.setHasThumb(thumb != null);

        return blogVO;
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        User user = userService.getLoginer(request);
        Map<Long, Boolean> blogIdHasThumpMap = new HashMap<>();
        if (ObjUtil.isNotEmpty(user)){
            Set<Long> blogIdSet = blogList.stream().map(Blog::getId).collect(Collectors.toSet());

            // 获取点赞
            List<Thumb> thumbList = thumbService.lambdaQuery()
                    .eq(Thumb::getUserid, user.getId())
                    .eq(Thumb::getBlogid, blogIdSet)
                    .list();

            thumbList.forEach(blogThump -> blogIdHasThumpMap.put(blogThump.getBlogid(), true));
        }

        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(blogIdHasThumpMap.get(blog.getId()));
                    return blogVO;
                }).toList();
    }
}




