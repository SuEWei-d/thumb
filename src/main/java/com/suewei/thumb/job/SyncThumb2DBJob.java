package com.suewei.thumb.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suewei.thumb.mapper.BlogMapper;
import com.suewei.thumb.model.entity.Thumb;
import com.suewei.thumb.model.enums.ThumbTypeEnum;
import com.suewei.thumb.service.ThumbService;
import com.suewei.thumb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时将Redis中的临时点赞数据同步到数据库
 *
 */
@Component
@Slf4j
public class SyncThumb2DBJob {

    @Resource
    private ThumbService thumbService;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedRate = 10000) // 每10秒
    @Transactional(rollbackFor = Exception.class)
    public void run(){
        log.info("开始执行");
        DateTime nowDate = DateUtil.date();
        // 如果秒数为 0 ~ 9 秒，则回到上一分钟的50秒
        int second = (DateUtil.second(nowDate) / 10 - 1) * 10;
        if (second == -10) {
            second = 50;
            // 回到上一分钟
            nowDate = DateUtil.offsetMinute(nowDate, -1);
        }
        String date = DateUtil.format(nowDate, "HH:mm:") + second;
        syncThumb2DBByDate(date);
        log.info("临时数据同步完成");
    }

    public void syncThumb2DBByDate(String date) {
        // 获取到临时点赞和取消点赞数据
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumbMap);

        // 同步 点赞到数据库
        // 构建列表收集blogId
        Map<Long, Long> blogThumbCountMap = new HashMap<>();
        if (thumbMapEmpty){
            return;
        }
        ArrayList<Thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        boolean needRemove = false;

        for (Object userIdBlogIdObj : allTempThumbMap.keySet()) {
            // 提取出userId 和 blogId
            String userIdBlogId = (String) userIdBlogIdObj;
            String[] userIdAndBlogId = userIdBlogId.split(StrPool.COLON);
            Long userId = Long.valueOf(userIdAndBlogId[0]);
            Long blogId = Long.valueOf(userIdAndBlogId[1]);

            // 判断是点赞还是取消点赞
            // -1 取消点赞 1点赞
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdBlogId).toString());
            if (thumbType == ThumbTypeEnum.INCR.getValue()){
                // 点赞
                // 点赞将thumb存入thumbList
                Thumb thumb = new Thumb();
                thumb.setUserid(userId);
                thumb.setBlogid(blogId);
                thumbList.add(thumb);
            }else if (thumbType == ThumbTypeEnum.DECR.getValue()){
                // 取消点赞
                // 取消点赞拼接查询条件，批量删除（将所有待删除条件拼到一个wrapper），在最后发出一个remove
                needRemove = true;
                wrapper.or().eq(Thumb::getUserid, userId).eq(Thumb::getBlogid, blogId);
            }else {
                if (thumbType != ThumbTypeEnum.NON.getValue()){
                    // 类型全不符合，判断错误
                    log.warn("数据异常：{}", userId + "," + blogId + "," + thumbType);
                }
                continue;
            }
            // 计算点赞增量(先拿出原来的，如果原来没有就为0，再加上thumbType得到新的点赞值存入map)
            // 先看之前这篇博客之前有没有统计过，如果没有就是0
            // 计算新的统计数， + thumbType
            // 再把新的增量放回map
            blogThumbCountMap.put(blogId, blogThumbCountMap.getOrDefault(blogId, 0L) + thumbType);
        }
        // 批量插入
        thumbService.saveBatch(thumbList);
        // 批量删除
        if (needRemove){
            thumbService.remove(wrapper);
        }
        // 批量更新博客点赞量
        if (!blogThumbCountMap.isEmpty()){
            blogMapper.batchUpdateThumbCount(blogThumbCountMap);
        }
        // 异步删除
        Thread.startVirtualThread(() -> {
            redisTemplate.delete(tempThumbKey);
        });
    }


}


