package com.suewei.thumb.mapper;

import com.suewei.thumb.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author ASUS
* @description 针对表【user】的数据库操作Mapper
* @createDate 2026-03-31 18:39:15
* @Entity com.suewei.thumb.model.entity.User
*/
@Mapper
public interface UserMapper extends BaseMapper<User> {

}




