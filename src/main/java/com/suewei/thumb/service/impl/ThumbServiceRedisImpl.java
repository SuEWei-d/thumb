package com.suewei.thumb.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suewei.thumb.constant.RedisLuaScriptConstant;
import com.suewei.thumb.mapper.ThumbMapper;
import com.suewei.thumb.model.dto.cache.ThumbCache;
import com.suewei.thumb.model.dto.thumb.DoThumbRequest;
import com.suewei.thumb.model.entity.Thumb;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.model.enums.LuaStatusEnum;
import com.suewei.thumb.model.enums.ThumbTypeEnum;
import com.suewei.thumb.service.BlogService;
import com.suewei.thumb.service.ThumbService;
import com.suewei.thumb.service.UserService;
import com.suewei.thumb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;

/**
 * @author ASUS
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2026-03-31 18:39:15
 */
@Service("thumbServiceRedis")
@Primary // 当
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
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

        // 用户id
        User user = userService.getLoginer(request);
        Long userId = user.getId();
        // blogId
        Long blogId = doThumbRequest.getBlogId();
        // 获取时间片
        String time = getTimeSlice();
        // 临时键
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(time);
        // thumb用户点赞记录键
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        // 点赞记录中的thumbId
//        Thumb thumb = lambdaQuery()
//                .eq(Thumb::getUserid, userId)
//                .eq(Thumb::getBlogid, blogId)
//                .one();
//        Long thumbId = thumb.getId();
        // 过期时间
        Date createTime = blogService.getById(blogId).getCreateTime();
        long expireTime = createTime
                .toInstant()
                .plus(30, ChronoUnit.DAYS)
                .toEpochMilli();

//        ThumbCache thumbCache = new ThumbCache();
//        thumbCache.setThumbId(thumbId);
//        thumbCache.setExpireTime(expireTime);

//        String thumbCacheJson;
//        try {
//            thumbCacheJson = objectMapper.writeValueAsString(thumbCache);
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException("序列化失败", e);
//        }

        // 执行Lua脚本
        Long result = stringRedisTemplate.execute(RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                String.valueOf(userId),
                String.valueOf(blogId),
                String.valueOf(expireTime)
        );

        if (ThumbTypeEnum.DECR.getValue() == result){
            throw new RuntimeException("用户已点赞");
        }

        // 更新成功才执行
        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    // 获取时间片
    private String getTimeSlice() {
        DateTime nowDate = DateUtil.date();
        // 获取到当前时间最近的整数秒，比如当前 11:20:23， 获取为11:20:20
        return DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10) * 10;
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

        // 用户id
        User user = userService.getLoginer(request);
        Long userId = user.getId();
        // blogId
        Long blogId = doThumbRequest.getBlogId();
        // 获取时间片
        String time = getTimeSlice();
        // 临时键
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(time);
        // thumb用户点赞记录键
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        // 执行Lua脚本
        Long result = stringRedisTemplate.execute(RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                String.valueOf(userId),
                String.valueOf(blogId)
        );

        if (ThumbTypeEnum.DECR.getValue() == result){
            throw new RuntimeException("用户未点赞");
        }

        return ThumbTypeEnum.INCR.getValue() == result;
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
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        Object cacheObj = redisTemplate.opsForHash().get(userThumbKey, blogId.toString());

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

        String key = RedisKeyUtil.getUserThumbKey(userId);
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
                String key = RedisKeyUtil.getUserThumbKey(userId);
                String blogIdStr = blogId.toString();

                redisTemplate.opsForHash().delete(key, blogIdStr);
            }catch (Exception e){
                log.error("异步删除过期缓存失败");
            }
        });
    }

}




