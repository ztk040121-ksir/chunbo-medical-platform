package com.chunbo.medical.controller;

import com.chunbo.medical.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 多模态附件上传 Controller（图片 / Excel）
 * POST /api/upload/file  上传附件，返回 fileId（聊天接口用 attachmentId 引用）
 */
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    /**
     * 上传图片 / Excel / 表格文件
     * 返回 { success, fileId, fileName, fileType, size }
     */
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        return fileUploadService.upload(file);
    }
}
