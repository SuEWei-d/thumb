package com.suewei.thumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suewei.thumb.model.dto.thumb.DoThumbRequest;
import com.suewei.thumb.model.entity.Blog;
import com.suewei.thumb.model.entity.Thumb;
import com.suewei.thumb.model.entity.User;
import com.suewei.thumb.service.BlogService;
import com.suewei.thumb.service.ThumbService;
import com.suewei.thumb.mapper.ThumbMapper;
import com.suewei.thumb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
* @author ASUS
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2026-03-31 18:39:15
*/
@Service
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 点赞
     *
     * @param doThumbRequest
     * @param request
     * @return
     */
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new RuntimeException("参数错误");
        }
        // 获取当前用户
        User user = userService.getLoginer(request);

        // 锁里进行点赞操作
        synchronized (user.getId().toString().intern()){
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                boolean exists = this.lambdaQuery()
                        .eq(Thumb::getBlogid, blogId)
                        .eq(Thumb::getUserid, user.getId())
                        .exists();

                if (exists){
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
                return update && save(thumb);
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new RuntimeException("参数错误");
        }
        // 获取当前用户
        User user = userService.getLoginer(request);

        // 加锁
        synchronized (user.getId().toString().intern()){
            // 事务式编程
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();

                // 查找当前thumb中是否存在
                Thumb thumb = this.lambdaQuery()
                        .eq(Thumb::getBlogid, blogId)
                        .eq(Thumb::getUserid, user.getId())
                        .one();

                if (thumb == null){
                    throw new RuntimeException("用户尚未点赞");
                }

                // 更新当前blog的点赞数量
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();

                // 删除当前thumb数据
                return update && removeById(thumb.getId());
            });
        }

    }
}




