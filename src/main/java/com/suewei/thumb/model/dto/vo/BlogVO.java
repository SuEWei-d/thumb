package com.suewei.thumb.model.dto.vo;

import lombok.Data;

import java.util.Date;

@Data
public class BlogVO {

    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 封面
     */
    private String coverimg;

    /**
     * 内容
     */
    private String content;

    /**
     * 点赞数
     */
    private Integer thumbcount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 是否点赞
     */
    private Boolean hasThumb;
}
