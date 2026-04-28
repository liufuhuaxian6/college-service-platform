package com.ruc.college.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.log.OperationLog;
import com.ruc.college.common.result.Result;
import com.ruc.college.common.security.RequireRole;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.system.entity.SysNotification;
import com.ruc.college.module.system.entity.SysOperationLog;
import com.ruc.college.module.system.service.SystemService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    // ==================== 用户管理 /system ====================

    @GetMapping("/system/user/page")
    @RequireRole(minLevel = 2)
    public Result<Page<SysUser>> userPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String major) {
        return Result.ok(systemService.getUserPage(page, size, grade, major));
    }

    @GetMapping("/system/user/{id}")
    @RequireRole(minLevel = 2)
    public Result<SysUser> userDetail(@PathVariable Long id) {
        return Result.ok(systemService.getUserDetail(id));
    }

    @PutMapping("/system/user/{id}")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "用户管理", action = "修改用户信息")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody SysUser user) {
        systemService.updateUser(id, user);
        return Result.ok();
    }

    @PutMapping("/system/user/{id}/role")
    @RequireRole(minLevel = 1)
    @OperationLog(module = "用户管理", action = "设置用户角色")
    public Result<Void> setRole(@PathVariable Long id, @RequestBody RoleLevelRequest request) {
        systemService.setUserRole(id, request.getRoleLevel());
        return Result.ok();
    }

    @GetMapping("/system/dashboard")
    @RequireRole(minLevel = 2)
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(systemService.getDashboard());
    }

    // ==================== 密码修改 /auth ====================

    @PutMapping("/auth/password")
    public Result<Void> changePassword(@RequestBody PasswordRequest request) {
        systemService.changePassword(request.getOldPassword(), request.getNewPassword());
        return Result.ok();
    }

    // ==================== 操作日志 /log ====================

    @GetMapping("/log/page")
    @RequireRole(minLevel = 1)
    public Result<Page<SysOperationLog>> logPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(systemService.getLogPage(page, size, module, startDate, endDate));
    }

    // ==================== 通知消息 /notify ====================

    @GetMapping("/notify/page")
    public Result<Page<SysNotification>> notifyPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        return Result.ok(systemService.getNotifications(page, size, type));
    }

    @GetMapping("/notify/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        return Result.ok(Map.of("count", systemService.getUnreadCount()));
    }

    @PutMapping("/notify/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        systemService.markRead(id);
        return Result.ok();
    }

    @PutMapping("/notify/read-all")
    public Result<Void> markAllRead() {
        systemService.markAllRead();
        return Result.ok();
    }

    @Data
    public static class RoleLevelRequest {
        private int roleLevel;
    }

    @Data
    public static class PasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}
