package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliyunOSSOperatorV2;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
@Slf4j
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
public class CommonController {
    @Autowired
    private AliyunOSSOperatorV2 aliyunOSSOperatorV2;
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file){
        try {
            log.info("文件上传{}",file.getOriginalFilename());
            String url= null;
            url = aliyunOSSOperatorV2.upload(file.getBytes(), file.getOriginalFilename());
            log.info("文件上传OSS，url：{}",url);
            return Result.success(url);
        } catch (Exception e) {
            log.error("文件上传失败");
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}
