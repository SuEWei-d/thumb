package com.suewei.thumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suewei.thumb.constant.ThumbConstant;
import com.suewei.thumb.model.dto.cache.ThumbCache;
import com.suewei.thumb.model.dto.thumb.DoThumbRequest;
import com.suewei.thumb.model.entity.Blog;
import com.suewei.thumb.model.entity.Thumb;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.service.BlogService;
import com.suewei.thumb.service.ThumbService;
import com.suewei.thumb.mapper.ThumbMapper;
import com.suewei.thumb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * @author ASUS
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2026-03-31 18:39:15
 */
@Service
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Resource
    private RedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 点赞
     *
     * @param doThumbRequest
     * @param request
     * @return
     */
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        // 获取当前用户
        User user = userService.getLoginer(request);

        // 锁里进行点赞操作
        synchronized (user.getId().toString().intern()) {
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
//                boolean exists = this.lambdaQuery()
//                        .eq(Thumb::getBlogid, blogId)
//                        .eq(Thumb::getUserid, user.getId())
//                        .exists();

                Boolean exists = hasThumb(user.getId(), blogId);

                if (exists) {
                    throw new RuntimeException("用户已点赞");
                }

                // 更新blog的点赞数量
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();

                // 插入thump表中
                Thumb thumb = new Thumb();
                thumb.setBlogid(blogId);
                thumb.setUserid(user.getId());

                boolean success = update && save(thumb);

                if (success) {
                    cacheThumb(user.getId(), blogId, thumb.getId(), blogService.getById(blogId).getCreateTime());
                }

                return success;
//                return update && save(thumb);
            });
        }
    }

    /**
     * 取消点赞
     *
     * @param doThumbRequest
     * @param request
     * @return
     */
    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        // 获取当前用户
        User user = userService.getLoginer(request);

        // 加锁
        synchronized (user.getId().toString().intern()) {
            // 事务式编程
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();

                // 查找当前thumb中是否存在
//                Thumb thumb = this.lambdaQuery()
//                        .eq(Thumb::getBlogid, blogId)
//                        .eq(Thumb::getUserid, user.getId())
//                        .one();
                // 不能强转，需要判断是否为空，否则会报空指针异常
                Object thumbIdObj = redisTemplate.opsForHash().get((ThumbConstant.USER_THUMB_KEY_PREFIX + user.getId()).toString(), blogId.toString());

                if (thumbIdObj == null) {
                    throw new RuntimeException("用户尚未点赞");
                }

                Long thumbId;
                try {
                    thumbId = objectMapper.readValue(thumbIdObj.toString(), ThumbCache.class).getThumbId();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("解析缓存失败", e);
                }

                // 更新当前blog的点赞数量
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();

                // 删除当前thumb数据
                boolean success = update && removeById(thumbId);
                if (success) {
                    // 删除redis中的thumb记录
                    redisTemplate.opsForHash()
                            .delete((ThumbConstant.USER_THUMB_KEY_PREFIX + user.getId()).toString(), blogId.toString());
                }

                return success;
            });
        }

    }

    /**
     * 判断用户是否在该博客下是否点赞
     *
     * @param userId
     * @param blogId
     * @return
     */
    @Override
    public Boolean hasThumb(Long userId, Long blogId) {
        Object cacheObj = redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());

        // 缓存不存在查mysql
        if (cacheObj == null) {
            return checkFromMySQL(userId, blogId);
        }

        try {
            ThumbCache cache = objectMapper.readValue(cacheObj.toString(), ThumbCache.class);
            if (cache.isExpired()){
                // 冷数据，查找MySQL是否点赞
                asycDeleteExpiredCache(userId, blogId);
                return checkFromMySQL(userId, blogId);
            }
            return cache.getThumbId() != null; // 返回true 已点赞
        } catch (JsonProcessingException e) {
            log.error("解析缓存失败", e);
            return checkFromMySQL(userId, blogId);
        }

    }

    private Boolean checkFromMySQL(Long userId, Long blogId) {
        boolean exists = lambdaQuery()
                .eq(Thumb::getBlogid, blogId)
                .eq(Thumb::getUserid, userId)
                .exists();
        return exists;
    }

    /**
     * 将点赞记录和过期时间存入redis
     *
     * @param userId
     * @param blogId
     * @param thumbId
     * @param blogPublishTime
     */
    public void cacheThumb(Long userId, Long blogId, Long thumbId, Date blogPublishTime) {
        long expireTime = blogPublishTime
                .toInstant()
                .plus(30, ChronoUnit.DAYS)
                .toEpochMilli();

        ThumbCache cache = new ThumbCache(thumbId, expireTime);

        String key = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        String hashKey = String.valueOf(blogId);

        try {
            // 确保使用JSON序列化
            String jsonValue = objectMapper.writeValueAsString(cache);
            redisTemplate.opsForHash().put(key, hashKey, jsonValue);
        } catch (JsonProcessingException e) {
            log.error("序列化失败", e);
        }


    }

    /**
     * 虚拟线程异步删除
     * @param userId
     * @param blogId
     */
    private void asycDeleteExpiredCache(Long userId, Long blogId) {
        Thread.startVirtualThread(() -> {
            try {
                String key = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
                String blogIdStr = blogId.toString();

                redisTemplate.opsForHash().delete(key, blogIdStr);
            }catch (Exception e){
                log.error("异步删除过期缓存失败");
            }
        });
    }

}




