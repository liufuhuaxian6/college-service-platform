package com.ruc.college.module.approval.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.log.OperationLog;
import com.ruc.college.common.result.Result;
import com.ruc.college.common.security.RequireRole;
import com.ruc.college.module.approval.entity.ApprovalApplication;
import com.ruc.college.module.approval.entity.ApprovalRecord;
import com.ruc.college.module.approval.entity.ApprovalType;
import com.ruc.college.module.approval.service.ApprovalService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    // ==================== 学生端 ====================

    @GetMapping("/types")
    public Result<List<ApprovalType>> types() {
        return Result.ok(approvalService.getApprovalTypes());
    }

    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody ApplyRequest request) {
        ApprovalApplication app = approvalService.apply(request.getTypeId(), request.getFormData());
        Map<String, Object> result = new HashMap<>();
        result.put("id", app.getId());
        result.put("appNo", app.getAppNo());
        return Result.ok(result);
    }

    @GetMapping("/my/page")
    public Result<Page<ApprovalApplication>> myPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return Result.ok(approvalService.getMyApplications(page, size, status));
    }

    @GetMapping("/my/{id}")
    public Result<Map<String, Object>> myDetail(@PathVariable Long id) {
        ApprovalApplication app = approvalService.getApplicationDetail(id);
        List<ApprovalRecord> records = approvalService.getApprovalRecords(id);
        Map<String, Object> result = new HashMap<>();
        result.put("application", app);
        result.put("records", records);
        return Result.ok(result);
    }

    @PutMapping("/my/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        approvalService.withdraw(id);
        return Result.ok();
    }

    @GetMapping("/my/{id}/download")
    public Result<ApprovalApplication> download(@PathVariable Long id) {
        // 触发锁定! 状态变为 downloaded
        ApprovalApplication app = approvalService.downloadCert(id);
        return Result.ok(app);
    }

    // ==================== 管理端 ====================

    @GetMapping("/pending/page")
    @RequireRole(minLevel = 2)
    public Result<Page<ApprovalApplication>> pendingPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long typeId) {
        return Result.ok(approvalService.getPendingPage(page, size, typeId));
    }

    @PutMapping("/{id}/approve")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "审批管理", action = "通过申请")
    public Result<Void> approve(@PathVariable Long id, @RequestBody(required = false) CommentRequest request) {
        approvalService.approve(id, request != null ? request.getComment() : null);
        return Result.ok();
    }

    @PutMapping("/{id}/reject")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "审批管理", action = "驳回申请")
    public Result<Void> reject(@PathVariable Long id, @RequestBody CommentRequest request) {
        approvalService.reject(id, request.getComment());
        return Result.ok();
    }

    @PutMapping("/{id}/admin-withdraw")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "审批管理", action = "管理员撤回")
    public Result<Void> adminWithdraw(@PathVariable Long id, @RequestBody CommentRequest request) {
        approvalService.adminWithdraw(id, request.getComment());
        return Result.ok();
    }

    @GetMapping("/all/page")
    @RequireRole(minLevel = 2)
    public Result<Page<ApprovalApplication>> allPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        return Result.ok(approvalService.getAllPage(page, size, status, userId));
    }

    @Data
    public static class ApplyRequest {
        private Long typeId;
        private Map<String, Object> formData;
    }

    @Data
    public static class CommentRequest {
        private String comment;
    }
}
