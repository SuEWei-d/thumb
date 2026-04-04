package com.suewei.thumb.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManger {

    private TopK hotKeyDetetor;
    private Cache<String, Object> localCache;

    @Bean
    public TopK getHotKeyDetetor(){
        hotKeyDetetor = new HeavyKeeper(
                // 监控 Top 100 key
                100,
                // 宽度
                100000,
                // 深度
                5,
                // 衰减系数
                0.92,
                // 最小出现10次才记录
                10
        );
        return hotKeyDetetor;
    }

    @Bean
    public Cache<String, Object> localCache(){
        return localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    // 必要方法
    @Resource
    private RedisTemplate redisTemplate;

    // 辅助方法：构造复合 key
    private String builCacheKey(String hashKey, String key){
        return hashKey + ":" + key;
    }

    public Object get(String hashKey, String key) {
        // 构造唯一的 composite key
        String compositeKey = builCacheKey(hashKey, key);

        // 1.先查本地缓存
        Object valude = localCache.getIfPresent(compositeKey);
        if (valude != null) {
            log.info("本地缓存获取到数据 {} = {}", compositeKey, valude);
            // 记录访问次数（每次访问计数 +1）
            hotKeyDetetor.add(key, 1);
            return valude;
        }
        // 2.本地缓存未命中，查询Redis
        Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
        if (redisValue == null) {
            return null;
        }
        // 3.记录访问（计数 + 1）
        AddResult addResult = hotKeyDetetor.add(key, 1);
        // 4.如果是热key，且不在本地缓存，则缓存数据
        if (addResult.isHotKey()) {
            localCache.put(compositeKey, redisValue);
        }

        return redisValue;
    }

    public void putIfPresent(String hashKey, String key, Object value){
        String compositeKey = builCacheKey(hashKey, key);
        Object object = localCache.getIfPresent(compositeKey);
        if (object == null){
            return;
        }
        localCache.put(compositeKey, value);
    }

    // 定时清理过期的热 key 检测数据
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void cleanHotKeys(){
        hotKeyDetetor.fading();
    }

    public void put(String hashKey, String key, Object jsonCache) {
        String compositeKey = builCacheKey(hashKey, key);
        localCache.put(compositeKey, jsonCache);
    }
}
