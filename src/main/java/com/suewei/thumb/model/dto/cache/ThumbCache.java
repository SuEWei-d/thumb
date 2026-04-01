package com.suewei.thumb.model.dto.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThumbCache {

    private Long thumbId;
    private Long expireTime; //过期时间（毫秒）

    @JsonIgnore // Jackson序列化把这个方法忽略
    public boolean isExpired(){
        return System.currentTimeMillis() > expireTime;
    }
}
