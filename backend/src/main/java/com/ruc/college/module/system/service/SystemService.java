package com.ruc.college.module.system.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.common.util.EncryptUtil;
import com.ruc.college.module.approval.entity.ApprovalApplication;
import com.ruc.college.module.approval.mapper.ApprovalApplicationMapper;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.mapper.SysUserMapper;
import com.ruc.college.module.party.entity.PartyProcessInstance;
import com.ruc.college.module.party.mapper.PartyProcessInstanceMapper;
import com.ruc.college.module.system.entity.SysNotification;
import com.ruc.college.module.system.entity.SysOperationLog;
import com.ruc.college.module.system.mapper.SysNotificationMapper;
import com.ruc.college.module.system.mapper.SysOperationLogMapper;
import cn.hutool.crypto.digest.BCrypt;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final SysUserMapper userMapper;
    private final SysOperationLogMapper logMapper;
    private final SysNotificationMapper notificationMapper;
    private final ApprovalApplicationMapper approvalApplicationMapper;
    private final PartyProcessInstanceMapper partyProcessInstanceMapper;

    // ==================== 用户管理 ====================

    public Page<SysUser> getUserPage(int page, int size, String grade, String major) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(grade != null, SysUser::getGrade, grade)
                .eq(major != null, SysUser::getMajor, major)
                .orderByAsc(SysUser::getStudentId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }

    public SysUser getUserDetail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setPassword(null);
        return user;
    }

    public void updateUser(Long id, SysUser user) {
        SysUser existing = userMapper.selectById(id);
        if (existing == null) throw new BusinessException("用户不存在");
        user.setId(id);
        user.setPassword(null);
        userMapper.updateById(user);
    }

    public void setUserRole(Long id, int roleLevel) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setRoleLevel(roleLevel);
        userMapper.updateById(user);
    }

    public void changePassword(String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);
    }

    // ==================== Excel 批量导入 ====================

    /**
     * Excel 批量导入学生名单
     * Excel 列格式: 学号 | 姓名 | 年级 | 专业 | 班级 | 手机号 | 身份证号
     */
    public Map<String, Object> importStudents(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        List<String> errors = new ArrayList<>();
        int[] counts = {0, 0}; // [success, fail]

        try {
            EasyExcel.read(file.getInputStream(), StudentImportRow.class, new ReadListener<StudentImportRow>() {
                @Override
                public void invoke(StudentImportRow row, AnalysisContext context) {
                    int rowNum = context.readRowHolder().getRowIndex() + 1;

                    if (row.getStudentId() == null || row.getStudentId().isBlank()) {
                        errors.add("第" + rowNum + "行: 学号不能为空");
                        counts[1]++;
                        return;
                    }
                    if (row.getName() == null || row.getName().isBlank()) {
                        errors.add("第" + rowNum + "行: 姓名不能为空");
                        counts[1]++;
                        return;
                    }

                    // 检查学号是否已存在
                    SysUser existing = userMapper.selectOne(
                            new LambdaQueryWrapper<SysUser>().eq(SysUser::getStudentId, row.getStudentId()));
                    if (existing != null) {
                        errors.add("第" + rowNum + "行: 学号 " + row.getStudentId() + " 已存在");
                        counts[1]++;
                        return;
                    }

                    SysUser user = new SysUser();
                    user.setStudentId(row.getStudentId().trim());
                    user.setName(row.getName().trim());
                    user.setPassword(BCrypt.hashpw("123456")); // 默认密码
                    user.setRoleLevel(4); // 默认普通学生
                    user.setGrade(row.getGrade());
                    user.setMajor(row.getMajor());
                    user.setClassName(row.getClassName());
                    user.setPhone(row.getPhone());
                    user.setStatus(1);

                    // 身份证号加密存储
                    if (row.getIdCard() != null && !row.getIdCard().isBlank()) {
                        user.setIdCardEnc(EncryptUtil.encrypt(row.getIdCard().trim()));
                    }

                    userMapper.insert(user);
                    counts[0]++;
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 读取完毕
                }
            }).sheet().doRead();
        } catch (IOException e) {
            throw new BusinessException("读取Excel文件失败: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", counts[0]);
        result.put("fail", counts[1]);
        result.put("errors", errors);
        return result;
    }

    // ==================== Excel 导出 ====================

    /**
     * 导出学生名单为 Excel
     */
    public void exportStudents(String grade, String major, HttpServletResponse response) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleLevel, 4)
                .eq(SysUser::getStatus, 1)
                .eq(grade != null, SysUser::getGrade, grade)
                .eq(major != null, SysUser::getMajor, major)
                .orderByAsc(SysUser::getStudentId);

        List<SysUser> users = userMapper.selectList(wrapper);

        List<StudentExportRow> rows = users.stream().map(u -> {
            StudentExportRow row = new StudentExportRow();
            row.setStudentId(u.getStudentId());
            row.setName(u.getName());
            row.setGrade(u.getGrade());
            row.setMajor(u.getMajor());
            row.setClassName(u.getClassName());
            row.setPhone(u.getPhone());
            return row;
        }).toList();

        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = URLEncoder.encode("学生名单.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

            EasyExcel.write(response.getOutputStream(), StudentExportRow.class)
                    .sheet("学生名单")
                    .doWrite(rows);
        } catch (IOException e) {
            throw new BusinessException("导出Excel失败: " + e.getMessage());
        }
    }

    // ==================== 数据概览 ====================

    public Map<String, Object> getDashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalStudents", userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleLevel, 4).eq(SysUser::getStatus, 1)));
        data.put("totalUsers", userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)));
        data.put("pendingApprovals", approvalApplicationMapper.selectCount(
                new LambdaQueryWrapper<ApprovalApplication>().eq(ApprovalApplication::getStatus, "pending")));
        data.put("activeProcesses", partyProcessInstanceMapper.selectCount(
                new LambdaQueryWrapper<PartyProcessInstance>().eq(PartyProcessInstance::getStatus, "active")));
        return data;
    }

    // ==================== 操作日志 ====================

    public Page<SysOperationLog> getLogPage(int page, int size, String module, String startDate, String endDate) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(module != null, SysOperationLog::getModule, module)
                .ge(startDate != null, SysOperationLog::getCreatedAt, startDate)
                .le(endDate != null, SysOperationLog::getCreatedAt, endDate)
                .orderByDesc(SysOperationLog::getCreatedAt);
        return logMapper.selectPage(new Page<>(page, size), wrapper);
    }

    // ==================== 通知消息 ====================

    public Page<SysNotification> getNotifications(int page, int size, String type, String tag) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, UserContext.getUserId())
                .eq(org.springframework.util.StringUtils.hasText(type), SysNotification::getType, type)
                // tag 用模糊匹配, 兼容 "就业,实习" 这种逗号分隔的存储格式
                .like(org.springframework.util.StringUtils.hasText(tag), SysNotification::getTags, tag)
                .orderByDesc(SysNotification::getCreatedAt);
        return notificationMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 兼容旧调用 */
    public Page<SysNotification> getNotifications(int page, int size, String type) {
        return getNotifications(page, size, type, null);
    }

    public long getUnreadCount() {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getUserId, UserContext.getUserId())
                        .eq(SysNotification::getIsRead, false)
        );
    }

    public void markRead(Long id) {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<SysNotification>()
                        .eq(SysNotification::getId, id)
                        .eq(SysNotification::getUserId, UserContext.getUserId())
                        .set(SysNotification::getIsRead, true)
        );
    }

    public void markAllRead() {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<SysNotification>()
                        .eq(SysNotification::getUserId, UserContext.getUserId())
                        .eq(SysNotification::getIsRead, false)
                        .set(SysNotification::getIsRead, true)
        );
    }

    /**
     * 发送通知（供其他 Service 调用）
     */
    public void sendNotification(Long userId, String title, String content, String type) {
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setIsRead(false);
        notificationMapper.insert(notification);
    }

    // ==================== Excel DTO ====================

    @Data
    public static class StudentImportRow {
        @com.alibaba.excel.annotation.ExcelProperty("学号")
        private String studentId;
        @com.alibaba.excel.annotation.ExcelProperty("姓名")
        private String name;
        @com.alibaba.excel.annotation.ExcelProperty("年级")
        private String grade;
        @com.alibaba.excel.annotation.ExcelProperty("专业")
        private String major;
        @com.alibaba.excel.annotation.ExcelProperty("班级")
        private String className;
        @com.alibaba.excel.annotation.ExcelProperty("手机号")
        private String phone;
        @com.alibaba.excel.annotation.ExcelProperty("身份证号")
        private String idCard;
    }

    @Data
    public static class StudentExportRow {
        @com.alibaba.excel.annotation.ExcelProperty("学号")
        private String studentId;
        @com.alibaba.excel.annotation.ExcelProperty("姓名")
        private String name;
        @com.alibaba.excel.annotation.ExcelProperty("年级")
        private String grade;
        @com.alibaba.excel.annotation.ExcelProperty("专业")
        private String major;
        @com.alibaba.excel.annotation.ExcelProperty("班级")
        private String className;
        @com.alibaba.excel.annotation.ExcelProperty("手机号")
        private String phone;
    }
}
