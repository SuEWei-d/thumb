package com.suewei.thumb.util;

import com.suewei.thumb.constant.ThumbConstant;

public class RedisKeyUtil {

    /**
     * 用户是否点赞记录的key
     * @param userId
     * @return
     */
    public static String getUserThumbKey(Long userId){
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
    }

    /**
     * 获取 临时点赞记录 key
     * @param time
     * @return
     */
    public static String getTempThumbKey(String time){
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(time);
    }
}
