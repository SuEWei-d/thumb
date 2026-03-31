package com.suewei.thumb.controller;

import com.suewei.thumb.common.BaseResponse;
import com.suewei.thumb.common.ResultUtils;
import com.suewei.thumb.model.dto.thumb.DoThumbRequest;
import com.suewei.thumb.service.ThumbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("thumb")
@Tag(name = "点赞相关接口")
public class thumbController {

    @Resource
    private ThumbService thumbService;

    @PostMapping("/do")
    @Operation(summary = "点赞")
    public BaseResponse doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request){
        Boolean success = thumbService.doThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }

    @PostMapping("/undo")
    @Operation(summary = "取消点赞")
    public BaseResponse undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request){
        Boolean success = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }
}
