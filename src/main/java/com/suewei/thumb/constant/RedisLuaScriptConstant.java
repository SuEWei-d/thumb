package com.suewei.thumb.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public class RedisLuaScriptConstant {

    public static final RedisScript<Long> THUMB_SCRIPT = new DefaultRedisScript<>("""
            local tempThumbKey = KEYS[1] -- 临时计数键(如thumb:temp:{time})
            local userThumbKey = KEYS[2] -- 用户点赞状态键(如thumb:{userId})
            local userId = ARGV[1] --用户ID
            local blogId = ARGV[2] --博客ID
            -- local thumbId = ARGV[3] -- 点赞记录Id
            local expireTime = ARGV[3] -- 过期时间
            -- local thumbValue = ARGV[5] -- 完整Json值
            
            -- 1.检查是否已点赞（避免重复操作）
            if redis.call('HEXISTS', userThumbKey, blogId) == 1 then
                return -1 -- 已点赞，返回-1表示失败
            end
            
            -- 2.获取旧值（不存在默认为0）
            local hashKey = userId ..':'.. blogId
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)
            
            -- 3.计算新值
            local newNumber = oldNumber + 1
            
            -- 4.原子性计算：写入临时计数 + 标记用户已点赞
            redis.call('HSET', tempThumbKey, hashKey, newNumber)
            redis.call('HSET', userThumbKey, blogId, expireTime)
            
            return 1 -- 返回1 表示成功
            """, Long.class);

    public static final RedisScript<Long> UNTHUMB_SCRIPT = new DefaultRedisScript<>("""
            local tempThumbKey = KEYS[1] -- 临时计数键(如thumb:temp:{time})
            local userThumbKey = KEYS[2] -- 用户点赞状态键(如thumb:{userId})
            local userId = ARGV[1] --用户ID
            local blogId = ARGV[2] --博客ID
            
            -- 1.检查是否已点赞（避免重复操作） 不等于1时，为未点赞
            if redis.call('HEXISTS', userThumbKey, blogId) ~= 1 then
                return -1 -- 未点赞，返回-1表示失败
            end
            
            -- 2.获取旧值（不存在默认为0）
            local hashKey = userId ..':'.. blogId
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)
            
            -- 3.计算新值
            local newNumber = oldNumber - 1
            
            -- 4.原子性计算：写入临时计数 + 标记用户已点赞
            redis.call('HSET', tempThumbKey, hashKey, newNumber)
            redis.call('HDEL', userThumbKey, blogId)
            
            return 1 -- 返回1 表示成功
            """, Long.class);
}
