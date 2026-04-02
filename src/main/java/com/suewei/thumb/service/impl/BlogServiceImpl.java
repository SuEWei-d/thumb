package com.suewei.thumb.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suewei.thumb.constant.ThumbConstant;
import com.suewei.thumb.model.dto.cache.ThumbCache;
import com.suewei.thumb.model.dto.vo.BlogVO;
import com.suewei.thumb.model.entity.Blog;
import com.suewei.thumb.model.entity.Thumb;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.service.BlogService;
import com.suewei.thumb.mapper.BlogMapper;
import com.suewei.thumb.service.ThumbService;
import com.suewei.thumb.service.UserService;
import com.suewei.thumb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public BlogVO getById(Long id, HttpServletRequest request) {
        Blog blog = getById(id);
        User user = userService.getLoginer(request);
        return this.getBlogVO(blog, user);
    }

    private BlogVO getBlogVO(Blog blog, User user) {
        // 查询thumb表获取当前用户是否在这个blog下点赞
//        Thumb thumb = thumbService.lambdaQuery()
//                .eq(Thumb::getUserid, user.getId())
//                .eq(Thumb::getBlogid, blog.getId())
//                .one();

        Boolean exists = thumbService.hasThumb(user.getId(), blog.getId());


        BlogVO blogVO = new BlogVO();
        BeanUtils.copyProperties(blog, blogVO);

        blogVO.setHasThumb(exists);

        return blogVO;
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        User user = userService.getLoginer(request);
        Map<Long, Boolean> blogIdHasThumpMap = new HashMap<>();
        if (ObjUtil.isNotEmpty(user)){
            // 提取博客Id和发布时间
            List<Long> blogIdList = blogList.stream().map(Blog::getId).collect(Collectors.toList());

            //List<String> blogIdStrList = blogIdList.stream().map(String::valueOf).collect(Collectors.toList());

            // 获取点赞
//            List<Thumb> thumbList = thumbService.lambdaQuery()
//                    .eq(Thumb::getUserid, user.getId())
//                    .eq(Thumb::getBlogid, blogIdList)
//                    .list();

            Map<Long, Date> blogPublisTimeMap = new HashMap<>();
            for (Blog blog : blogList) {
                blogPublisTimeMap.put(blog.getId(), blog.getCreateTime());
            }
            blogIdHasThumpMap.putAll(checkThumbed(user.getId(), blogIdList, blogPublisTimeMap));
//            List<Object> thumbIdObjList = redisTemplate.opsForHash()
//                    .multiGet((ThumbConstant.USER_THUMB_KEY_PREFIX + user.getId()).toString(), blogIdStrList);

//            for (int i = 0; i < thumbIdObjList.size(); i++){
//                if (thumbIdObjList.get(i) == null){
//                    continue;
//                }
//                blogIdHasThumpMap.put(Long.valueOf(blogIdList.get(i).toString()), true);
//            }

        }
        // 组转返回结果
        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(blogIdHasThumpMap.getOrDefault(blog.getId(), false));
                    return blogVO;
                }).toList();

//        return blogList.stream()
//                .map(blog -> {
//                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
//                    blogVO.setHasThumb(blogIdHasThumpMap.get(blog.getId()));
//                    return blogVO;
//                }).toList();
    }


    /**
     * 批量读取并过滤过期
     * @param userId
     * @param blogIds
     * @param blogPublishTimeMap
     * @return
     */
    public Map<Long, Boolean> checkThumbed(Long userId, List<Long> blogIds, Map<Long, Date> blogPublishTimeMap){
        Map<Long, Boolean> result = new HashMap<>();
        List<Long> needDeleteBlogIds = new ArrayList<>(); // 需要异步删除

        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        // 批量从Reids获取
        List<String> blogIdStrList = blogIds.stream().map(String::valueOf).collect(Collectors.toList());
        List<Object> cacheList = redisTemplate.opsForHash().multiGet(userThumbKey, blogIdStrList);

        // 遍历处理
        int index = 0;
        for (Long blogId : blogIds){
            Object cacheObj = cacheList.get(index++);

            if (cacheObj == null){
                // 缓存不存在，判断是否需要查缓存
                if (isHotBlog(blogPublishTimeMap.get(blogId))){
                    // 热数据但缓存不存在 = 未点赞
                    result.put(blogId, false);
                }else {
                    // 冷数据，查MySQl
                    result.put(blogId, checkFromMySQL(userId, blogId));
                }
            }else {
                try {
                    ThumbCache cache = objectMapper.readValue(cacheObj.toString(), ThumbCache.class);
                    if (cache.isExpired()){
                        // 已过期，需要删除 + 查 MySQL
                        needDeleteBlogIds.add(blogId);
                        result.put(blogId, checkFromMySQL(userId, blogId));
                    }else {
                        // 未过期，有效缓存
                        result.put(blogId, cache.getThumbId() != null);
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // 3.异步删除过期数据
        if (!needDeleteBlogIds.isEmpty()){
            asycDeleteExpiredCache(userId, needDeleteBlogIds);
        }
        return result;
    }

    /**
     * 判断当前用户在这个博客下是否点赞
     * @param userId
     * @param blogId
     * @return
     */
    private Boolean checkFromMySQL(Long userId, Long blogId) {
        boolean exists = thumbService.lambdaQuery().eq(Thumb::getUserid, userId)
                .eq(Thumb::getBlogid, blogId)
                .exists();
        return exists;
    }

    /**
     * 虚拟线程异步删除
     * @param userId
     * @param needDeleteBlogIds
     */
    private void asycDeleteExpiredCache(Long userId, List<Long> needDeleteBlogIds) {
        Thread.startVirtualThread(() -> {
            try {
                String key = RedisKeyUtil.getUserThumbKey(userId);
                Object[] hashKeys = needDeleteBlogIds.stream()
                        .map(String::valueOf)
                        .toArray();

                redisTemplate.opsForHash().delete(key, hashKeys);
            }catch (Exception e){
                log.error("异步删除过期缓存失败");
            }
        });
    }

    /**
     * 判读是否是热数据
     * @param publishTime
     * @return
     */
    private boolean isHotBlog(Date publishTime) {
        if (publishTime == null) return false;
        // 发布时间 + 1个月 是否在当前时间之后
        Instant exprieInstant = publishTime.toInstant().plus(30, ChronoUnit.DAYS);
        return exprieInstant.isAfter(Instant.now());
    }
}




