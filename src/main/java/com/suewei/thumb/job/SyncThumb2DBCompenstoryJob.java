package com.suewei.thumb.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.suewei.thumb.constant.ThumbConstant;
import com.suewei.thumb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class SyncThumb2DBCompenstoryJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private SyncThumb2DBJob syncThumb2DBJob;

    @Scheduled(cron = "0 0 2 * * *") // 凌晨2点
    public void run() {
        log.info("开始补偿任务");
        // 查找全部的TempThumbKey
        Set<String> thumbKeys = redisTemplate.keys(RedisKeyUtil.getTempThumbKey("") + "*");

        // 创建一个需要删除的键的集合
        Set<String> needHandleDataSet = new HashSet<>();

        // 通过Stream流筛选掉空的键，并将其提取到去掉前缀的时间格式， 如 2026:4:1等
        thumbKeys.stream().filter(ObjectUtil::isNotNull)
                .forEach(thumbKey -> needHandleDataSet.add(thumbKey
                        .replace(ThumbConstant.TEMP_THUMB_KEY_PREFIX
                                .formatted(""), "")));

        if (CollUtil.isEmpty(needHandleDataSet)){
            log.info("没有需要补常的临时数据");
            return;
        }

        // 补偿数据
        for (String date : needHandleDataSet) {
            syncThumb2DBJob.syncThumb2DBByDate(date);
        }

        log.info("临时数据补充完成");
    }
}
