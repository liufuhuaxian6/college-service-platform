package com.ruc.college.module.system.controller;

import com.ruc.college.common.result.Result;
import com.ruc.college.common.security.RequireRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    private static final long MAX_FILE_SIZE = 30L * 1024 * 1024;

    @PostMapping("/upload")
    @RequireRole(minLevel = 2)
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.fail("上传文件不能为空");
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                return Result.fail("文件大小不能超过30MB");
            }

            String originalFilename = file.getOriginalFilename();
            String cleanName = StringUtils.cleanPath(originalFilename == null ? "unknown" : originalFilename);

            String suffix = "";
            int dotIndex = cleanName.lastIndexOf(".");
            if (dotIndex >= 0) {
                suffix = cleanName.substring(dotIndex);
            }

            String datePath = LocalDate.now().toString();
            String uploadDirPath = System.getProperty("user.dir")
                    + File.separator + "uploads"
                    + File.separator + datePath;

            File uploadDir = new File(uploadDirPath);
            if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                return Result.fail("创建上传目录失败");
            }

            String storedName = UUID.randomUUID().toString().replace("-", "") + suffix;
            File targetFile = new File(uploadDir, storedName);

            file.transferTo(targetFile);

            String relativePath = "uploads/" + datePath + "/" + storedName;

            return Result.ok(Map.of(
                    "fileName", cleanName,
                    "filePath", relativePath,
                    "fileSize", file.getSize(),
                    "fileType", file.getContentType() == null ? "" : file.getContentType()
            ));
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.fail("文件上传失败：" + e.getMessage());
        }
    }
}