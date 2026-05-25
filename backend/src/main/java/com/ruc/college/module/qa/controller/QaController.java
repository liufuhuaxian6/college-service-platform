package com.ruc.college.module.qa.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.log.OperationLog;
import com.ruc.college.common.result.Result;
import com.ruc.college.common.security.RequireRole;
import com.ruc.college.module.qa.entity.QaChatLog;
import com.ruc.college.module.qa.entity.QaDocumentChunk;
import com.ruc.college.module.qa.entity.QaDocument;
import com.ruc.college.module.qa.entity.QaKnowledge;
import com.ruc.college.module.qa.service.QaService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    // ==================== 问答对话 ====================

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody @Validated ChatRequest request) {
        return Result.ok(qaService.chat(request.getQuestion()));
    }

    @GetMapping("/chat/history")
    public Result<Page<QaChatLog>> chatHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(qaService.getChatHistory(page, size));
    }

    // ==================== 知识库管理 (管理端) ====================

    @GetMapping("/knowledge/page")
    @RequireRole(minLevel = 2)
    public Result<Page<QaKnowledge>> knowledgePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return Result.ok(qaService.getKnowledgePage(page, size, category, keyword));
    }

    @GetMapping("/knowledge/{id}")
    @RequireRole(minLevel = 2)
    public Result<QaKnowledge> knowledgeDetail(@PathVariable Long id) {
        return Result.ok(qaService.getKnowledge(id));
    }

    @PostMapping("/knowledge")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "知识库", action = "新增知识条目")
    public Result<Map<String, Object>> addKnowledge(@RequestBody @Validated QaKnowledge knowledge) {
        Long id = qaService.addKnowledge(knowledge);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/knowledge/{id}")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "知识库", action = "修改知识条目")
    public Result<Void> updateKnowledge(@PathVariable Long id, @RequestBody QaKnowledge knowledge) {
        qaService.updateKnowledge(id, knowledge);
        return Result.ok();
    }

    @DeleteMapping("/knowledge/{id}")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "知识库", action = "删除知识条目")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        qaService.deleteKnowledge(id);
        return Result.ok();
    }

    // ==================== 政策文档 ====================

    @GetMapping("/document/list")
    public Result<List<QaDocument>> documentList(@RequestParam(required = false) String category) {
        return Result.ok(qaService.getDocumentList(category));
    }

    @PostMapping("/document")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "文档管理", action = "上传政策文档")
    public Result<Map<String, Object>> addDocument(@RequestBody QaDocument doc) {
        Long id = qaService.addDocument(doc);
        return Result.ok(Map.of("id", id));
    }

    @PostMapping("/document/{id}/index")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "文档管理", action = "解析政策文档向量入库")
    public Result<Map<String, Object>> indexDocument(@PathVariable Long id) {
        return Result.ok(qaService.indexDocument(id));
    }

    @GetMapping("/document/chunk/search")
    @RequireRole(minLevel = 2)
    public Result<List<QaDocumentChunk>> searchChunks(
            @RequestParam String question,
            @RequestParam(required = false) String category) {
        return Result.ok(qaService.retrieveDocumentChunks(question, category));
    }

    @GetMapping("/document/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        QaDocument doc = qaService.getDocumentForDownload(id);
        String cleanPath = StringUtils.cleanPath(doc.getFilePath());
        if (cleanPath.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        File file = new File(System.getProperty("user.dir") + File.separator + cleanPath);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        String extension = "";
        int dotIndex = cleanPath.lastIndexOf('.');
        int slashIndex = Math.max(cleanPath.lastIndexOf('/'), cleanPath.lastIndexOf('\\'));
        if (dotIndex > slashIndex) {
            extension = cleanPath.substring(dotIndex);
        }
        String baseName = StringUtils.hasText(doc.getTitle()) ? doc.getTitle().trim() : file.getName();
        String downloadName = baseName;
        if (StringUtils.hasText(extension)) {
            String lowerBase = baseName.toLowerCase();
            String lowerExt = extension.toLowerCase();
            if (!lowerBase.endsWith(lowerExt)) {
                downloadName = baseName + extension;
            }
        }
        String encodedName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    @DeleteMapping("/document/{id}")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "文档管理", action = "删除政策文档")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        qaService.deleteDocument(id);
        return Result.ok();
    }

    @Data
    public static class ChatRequest {
        @NotBlank(message = "问题不能为空")
        private String question;
    }
}
