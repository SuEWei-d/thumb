package com.suewei.thumb.service.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suewei.thumb.constant.ThumbConstant;
import com.suewei.thumb.manager.cache.CacheManger;
import com.suewei.thumb.model.dto.cache.ThumbCache;
import com.suewei.thumb.model.dto.thumb.DoThumbRequest;
import com.suewei.thumb.model.entity.Blog;
import com.suewei.thumb.model.entity.Thumb;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.service.BlogService;
import com.suewei.thumb.service.ThumbService;
import com.suewei.thumb.mapper.ThumbMapper;
import com.suewei.thumb.service.UserService;
import com.suewei.thumb.util.RedisKeyUtil;
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
@Service("thumbService")
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

                String userThumbKey = RedisKeyUtil.getUserThumbKey(user.getId());

                Thumb thumb = null;

                // 判断用户是否点赞
                // 获取当前用户点赞的记录
                Object object = cacheManger.get(userThumbKey, blogId.toString());
                if (object == null) {
                    Long userId = user.getId();
                    // 当前本地缓存为空，可能是冷数据，查询MYSQL数据库
                    thumb = lambdaQuery()
                            .eq(Thumb::getUserid, userId)
                            .eq(Thumb::getBlogid, blogId)
                            .one();
                    if (thumb == null) {
                        throw new RuntimeException("用户尚未点赞");
                    }
                } else {
                    // 从缓存中解析点赞记录
                    try {
                        ThumbCache cache = objectMapper.readValue(object.toString(), ThumbCache.class);
                        if (cache.getThumbId().equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                            throw new RuntimeException("用户尚未点赞");
                        }
                        thumb = new Thumb();
                        thumb.setId(cache.getThumbId());
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("解析缓存失败", e);
                    }
                }

                // 删除thumb记录
                boolean removeSuccess = removeById(thumb.getId());
                // 删除Redis中该blogId的hash field（而非整个key）
                redisTemplate.opsForHash().delete(userThumbKey, blogId.toString());
                // 将本地缓存标记为未点赞（存JSON格式，避免反序列化报错）
                try {
                    ThumbCache unThumbCache = new ThumbCache(ThumbConstant.UN_THUMB_CONSTANT, 0L);
                    String unThumbJson = objectMapper.writeValueAsString(unThumbCache);
                    cacheManger.putIfPresent(RedisKeyUtil.getUserThumbKey(user.getId()), blogId.toString(), unThumbJson);
                } catch (JsonProcessingException e) {
                    log.error("序列化失败", e);
                }
                // 修改blog中表的点赞数量
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();
                return update && removeSuccess;
            });
        }
    }



                // 查找当前thumb中是否存在
//                Thumb thumb = this.lambdaQuery()
//                        .eq(Thumb::getBlogid, blogId)
//                        .eq(Thumb::getUserid, user.getId())
//                        .one();
                // 不能强转，需要判断是否为空，否则会报空指针异常
//                Object thumbIdObj = redisTemplate.opsForHash().get(userThumbKey, blogId.toString());
//
//                if (thumbIdObj == null) {
//                    throw new RuntimeException("用户尚未点赞");
//                }
//
//                Long thumbId;
//                try {
//                    thumbId = objectMapper.readValue(thumbIdObj.toString(), ThumbCache.class).getThumbId();
//                } catch (JsonProcessingException e) {
//                    throw new RuntimeException("解析缓存失败", e);
//                }
//
//                // 更新当前blog的点赞数量
//                boolean update = blogService.lambdaUpdate()
//                        .eq(Blog::getId, blogId)
//                        .setSql("thumbCount = thumbCount - 1")
//                        .update();
//
//                // 删除当前thumb数据
//                boolean success = update && removeById(thumbId);
//                if (success) {
//                    // 删除redis中的thumb记录
//                    redisTemplate.opsForHash()
//                            .delete(userThumbKey, blogId.toString());
//                }



    @Resource
    private CacheManger cacheManger;

    /**
     * 判断用户是否在该博客下是否点赞
     *
     * @param userId
     * @param blogId
     * @return
     */
    @Override
    public Boolean hasThumb(Long userId, Long blogId) {
        Object thumbObj = cacheManger.get(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
        ThumbCache cache = null;
        if (thumbObj == null) {
            // 判断是否是冷数据
            // 若是冷数据从MYSQL中查找防止误判
            Thumb thumb = lambdaQuery()
                    .eq(Thumb::getUserid, userId)
                    .eq(Thumb::getBlogid, blogId)
                    .one();
            if (thumb == null) {
                return false;
            } else {
                // 在MySQL中存在，将其添加进入本地缓存中
                ThumbCache thumbCache = new ThumbCache();
                thumbCache.setThumbId(thumb.getId());
                // 获取当前时间的下一个月时间
                DateTime date = DateUtil.date();
                long expireTime = DateUtil.date().offsetNew(DateField.MONTH, 1).getTime();
                // 格式化缓存
                thumbCache.setExpireTime(Long.valueOf(String.valueOf(expireTime)));
                try {
                    String josnCache = objectMapper.writeValueAsString(thumbCache);
                    // 缓存到本地缓存
                    cacheManger.put(RedisKeyUtil.getUserThumbKey(userId), blogId.toString(), josnCache);
                    return true;
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("本地缓存失败", e);
                }
            }
        }
        try {
            cache = objectMapper.readValue(thumbObj.toString(), ThumbCache.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Long thumbId = cache.getThumbId();

        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
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

        String key = RedisKeyUtil.getUserThumbKey(userId);
        String hashKey = String.valueOf(blogId);

        try {
            // 确保使用JSON序列化
            String jsonValue = objectMapper.writeValueAsString(cache);
            redisTemplate.opsForHash().put(key, hashKey, jsonValue);
            // 将该点赞记录存入本地缓存中
            cacheManger.put(key, hashKey, jsonValue);
        } catch (JsonProcessingException e) {
            log.error("序列化失败", e);
        }


    }

    /**
     * 虚拟线程异步删除
     *
     * @param userId
     * @param blogId
     */
    private void asycDeleteExpiredCache(Long userId, Long blogId) {
        Thread.startVirtualThread(() -> {
            try {
                String key = RedisKeyUtil.getUserThumbKey(userId);
                String blogIdStr = blogId.toString();

                redisTemplate.opsForHash().delete(key, blogIdStr);
            } catch (Exception e) {
                log.error("异步删除过期缓存失败");
            }
        });
    }

}




