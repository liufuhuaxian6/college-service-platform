package com.ruc.college.module.party.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.log.OperationLog;
import com.ruc.college.common.result.Result;
import com.ruc.college.common.security.RequireRole;
import com.ruc.college.module.party.entity.PartyProcessInstance;
import com.ruc.college.module.party.entity.PartyProcessStep;
import com.ruc.college.module.party.entity.PartyProcessTemplate;
import com.ruc.college.module.party.service.PartyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/party")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    // ==================== 学生端 ====================

    @GetMapping("/templates")
    public Result<List<PartyProcessTemplate>> templates() {
        return Result.ok(partyService.getAllTemplates());
    }

    @GetMapping("/my-progress")
    public Result<List<Map<String, Object>>> myProgress() {
        return Result.ok(partyService.getMyProgress());
    }

    @GetMapping("/my-progress/{instanceId}")
    public Result<Map<String, Object>> progressDetail(@PathVariable Long instanceId) {
        return Result.ok(partyService.getProgressDetail(instanceId));
    }

    // ==================== 管理端 ====================

    @GetMapping("/template/page")
    @RequireRole(minLevel = 2)
    public Result<Page<PartyProcessTemplate>> templatePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(partyService.getTemplatePage(page, size));
    }

    @PostMapping("/template")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "党团流程", action = "创建流程模板")
    public Result<Map<String, Object>> createTemplate(@RequestBody CreateTemplateRequest request) {
        Long id = partyService.createTemplate(request.getTemplate(), request.getSteps());
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/template/{id}")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "党团流程", action = "修改流程模板")
    public Result<Void> updateTemplate(@PathVariable Long id, @RequestBody CreateTemplateRequest request) {
        partyService.updateTemplate(id, request.getTemplate(), request.getSteps());
        return Result.ok();
    }

    @GetMapping("/instance/page")
    @RequireRole(minLevel = 2)
    public Result<Page<PartyProcessInstance>> instancePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return Result.ok(partyService.getInstancePage(page, size, templateId, userId, status));
    }

    @PostMapping("/instance")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "党团流程", action = "为学生创建流程")
    public Result<Map<String, Object>> createInstance(@RequestBody CreateInstanceRequest request) {
        Long id = partyService.createInstance(request.getUserId(), request.getTemplateId(), request.getStartDate());
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/instance/{id}/advance")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "党团流程", action = "推进流程步骤")
    public Result<Void> advance(@PathVariable Long id, @RequestBody(required = false) RemarkRequest request) {
        partyService.advanceStep(id, request != null ? request.getRemark() : null);
        return Result.ok();
    }

    @PutMapping("/instance/{id}/suspend")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "党团流程", action = "暂停流程")
    public Result<Void> suspend(@PathVariable Long id, @RequestBody(required = false) RemarkRequest request) {
        partyService.suspendInstance(id, request != null ? request.getRemark() : null);
        return Result.ok();
    }

    @Data
    public static class CreateTemplateRequest {
        private PartyProcessTemplate template;
        private List<PartyProcessStep> steps;
    }

    @Data
    public static class CreateInstanceRequest {
        private Long userId;
        private Long templateId;
        private LocalDate startDate;
    }

    @Data
    public static class RemarkRequest {
        private String remark;
    }
}
