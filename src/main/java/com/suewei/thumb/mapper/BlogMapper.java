package com.suewei.thumb.mapper;

import com.suewei.thumb.model.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author ASUS
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2026-03-31 18:39:15
* @Entity com.suewei.thumb.model.entity.Blog
*/
@Mapper
public interface BlogMapper extends BaseMapper<Blog> {
    void batchUpdateThumbCount(@Param("countMap")Map<Long, Long> countMap);
}




