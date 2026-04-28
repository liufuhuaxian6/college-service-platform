package com.ruc.college.module.qa.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.log.OperationLog;
import com.ruc.college.common.result.Result;
import com.ruc.college.common.security.RequireRole;
import com.ruc.college.module.qa.entity.QaChatLog;
import com.ruc.college.module.qa.entity.QaDocument;
import com.ruc.college.module.qa.entity.QaKnowledge;
import com.ruc.college.module.qa.service.QaService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/document/{id}/download")
    public Result<QaDocument> downloadDocument(@PathVariable Long id) {
        return Result.ok(qaService.getDocumentForDownload(id));
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
