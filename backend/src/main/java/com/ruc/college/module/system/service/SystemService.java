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
import com.ruc.college.module.party.entity.PartyProcessTemplate;
import com.ruc.college.module.party.mapper.PartyProcessInstanceMapper;
import com.ruc.college.module.party.mapper.PartyProcessTemplateMapper;
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
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final SysUserMapper userMapper;
    private final SysOperationLogMapper logMapper;
    private final SysNotificationMapper notificationMapper;
    private final ApprovalApplicationMapper approvalApplicationMapper;
    private final PartyProcessInstanceMapper partyProcessInstanceMapper;
    private final PartyProcessTemplateMapper partyProcessTemplateMapper;
    private final EmailService emailService;

    // ==================== 用户管理 ====================

    /**
     * 返回学生维度的可选值 (年级 / 专业 / 班级), 供前端筛选下拉框使用.
     * 只取学生类角色 (普通学生 + 学生骨干) 且启用状态的真实数据, 去重排序,
     * 这样筛选项与库里实际存在的值完全一致, 不会因手输错字/全半角对不上而查不到.
     */
    public Map<String, List<String>> getStudentDimensions() {
        List<SysUser> students = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getRoleLevel, 3, 4)
                        .eq(SysUser::getStatus, 1)
                        .select(SysUser::getGrade, SysUser::getMajor, SysUser::getClassName)
        );
        Map<String, List<String>> result = new HashMap<>();
        result.put("grades", distinctSorted(students.stream().map(SysUser::getGrade)));
        result.put("majors", distinctSorted(students.stream().map(SysUser::getMajor)));
        result.put("classNames", distinctSorted(students.stream().map(SysUser::getClassName)));
        return result;
    }

    private static List<String> distinctSorted(java.util.stream.Stream<String> stream) {
        return stream.filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    public Page<SysUser> getUserPage(int page, int size, String grade, String major, String className, Integer roleLevel) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(roleLevel != null, SysUser::getRoleLevel, roleLevel)
                .eq(StringUtils.hasText(grade), SysUser::getGrade, grade)
                .eq(StringUtils.hasText(major), SysUser::getMajor, major)
                .eq(StringUtils.hasText(className), SysUser::getClassName, className)
                .orderByAsc(SysUser::getStudentId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> {
            u.setPassword(null);
            u.setEmail(emailService.resolveEmail(u));
        });
        return result;
    }

    public SysUser getUserDetail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setPassword(null);
        user.setEmail(emailService.resolveEmail(user));
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
     * 导出学生名单为 Excel.
     * 学生 = 普通学生(4) + 学生骨干(3); 骨干也是学生, 一并导出, 用"身份"列区分.
     * 支持按 年级 / 专业 / 班级 筛选 (空则不限), 只导启用状态.
     */
    public void exportStudents(String grade, String major, String className, Integer roleLevel, HttpServletResponse response) {
        // roleLevel 指定时只导该身份(仅允许 3/4), 否则普通学生+学生骨干全导
        boolean validRole = roleLevel != null && (roleLevel == 3 || roleLevel == 4);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(validRole, SysUser::getRoleLevel, roleLevel)
                .in(!validRole, SysUser::getRoleLevel, 3, 4)
                .eq(SysUser::getStatus, 1)
                .eq(StringUtils.hasText(grade), SysUser::getGrade, grade)
                .eq(StringUtils.hasText(major), SysUser::getMajor, major)
                .eq(StringUtils.hasText(className), SysUser::getClassName, className)
                .orderByAsc(SysUser::getGrade)
                .orderByAsc(SysUser::getClassName)
                .orderByAsc(SysUser::getStudentId);

        List<SysUser> users = userMapper.selectList(wrapper);

        List<StudentExportRow> rows = users.stream().map(u -> {
            StudentExportRow row = new StudentExportRow();
            row.setStudentId(u.getStudentId());
            row.setName(u.getName());
            row.setIdentity(Integer.valueOf(3).equals(u.getRoleLevel()) ? "学生骨干" : "普通学生");
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

        // ---- 基础数字卡片 ----
        data.put("totalStudents", userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleLevel, 4).eq(SysUser::getStatus, 1)));
        data.put("totalUsers", userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)));
        data.put("pendingApprovals", approvalApplicationMapper.selectCount(
                new LambdaQueryWrapper<ApprovalApplication>().eq(ApprovalApplication::getStatus, "pending")));
        data.put("activeProcesses", partyProcessInstanceMapper.selectCount(
                new LambdaQueryWrapper<PartyProcessInstance>().eq(PartyProcessInstance::getStatus, "active")));

        // ---- 审批状态分布 (饼图: 6 种状态) ----
        List<ApprovalApplication> approvals = approvalApplicationMapper.selectList(null);
        Map<String, Long> statusDist = approvals.stream()
                .filter(a -> a.getStatus() != null)
                .collect(Collectors.groupingBy(ApprovalApplication::getStatus, Collectors.counting()));
        // 保证 6 种 status 都有 key (前端饼图无需自己补 0)
        for (String s : new String[]{"draft", "pending", "approved", "rejected", "withdrawn", "downloaded"}) {
            statusDist.putIfAbsent(s, 0L);
        }
        data.put("approvalStatusDist", statusDist);

        // ---- 党团模板分布 (柱图: 每个模板下的实例数) ----
        List<PartyProcessTemplate> templates = partyProcessTemplateMapper.selectList(null);
        List<PartyProcessInstance> instances = partyProcessInstanceMapper.selectList(null);
        Map<Long, Long> instanceCountByTemplate = instances.stream()
                .filter(i -> i.getTemplateId() != null)
                .collect(Collectors.groupingBy(PartyProcessInstance::getTemplateId, Collectors.counting()));
        List<Map<String, Object>> partyDist = new ArrayList<>();
        for (PartyProcessTemplate t : templates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", t.getName());
            row.put("count", instanceCountByTemplate.getOrDefault(t.getId(), 0L));
            partyDist.add(row);
        }
        data.put("partyTemplateDist", partyDist);

        // ---- 最近 7 天审批申请提交数 (折线) ----
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<ApprovalApplication> recentApprovals = approvalApplicationMapper.selectList(
                new LambdaQueryWrapper<ApprovalApplication>()
                        .ge(ApprovalApplication::getCreatedAt, weekAgo));
        data.put("approvalTrend7d", buildDailyTrend(weekAgo.toLocalDate(),
                recentApprovals.stream()
                        .filter(a -> a.getCreatedAt() != null)
                        .map(a -> a.getCreatedAt().toLocalDate())
                        .collect(Collectors.toList())));

        // ---- 最近 7 天通知数 (折线) ----
        List<SysNotification> recentNotifications = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .ge(SysNotification::getCreatedAt, weekAgo));
        data.put("notifyTrend7d", buildDailyTrend(weekAgo.toLocalDate(),
                recentNotifications.stream()
                        .filter(n -> n.getCreatedAt() != null)
                        .map(n -> n.getCreatedAt().toLocalDate())
                        .collect(Collectors.toList())));

        // ---- 待办: 最新 5 条待审批 (替代 EmptyState 占位) ----
        List<ApprovalApplication> pendingList = approvalApplicationMapper.selectList(
                new LambdaQueryWrapper<ApprovalApplication>()
                        .eq(ApprovalApplication::getStatus, "pending")
                        .orderByDesc(ApprovalApplication::getCreatedAt)
                        .last("LIMIT 5"));
        List<Map<String, Object>> todo = new ArrayList<>();
        for (ApprovalApplication a : pendingList) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("appNo", a.getAppNo());
            row.put("currentApproverLevel", a.getCurrentApproverLevel());
            row.put("createdAt", a.getCreatedAt());
            todo.add(row);
        }
        data.put("pendingTodo", todo);

        return data;
    }

    /**
     * 把 dates 列表按"日"聚合成最近 7 天的趋势 (没有数据的日期填 0).
     * 返回: [{date: "2026-05-20", count: 3}, ...] 顺序从早到晚.
     */
    private List<Map<String, Object>> buildDailyTrend(LocalDate from, List<LocalDate> dates) {
        Map<LocalDate, Long> daily = dates.stream()
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = from.plusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.format(DateTimeFormatter.ofPattern("MM-dd")));
            row.put("count", daily.getOrDefault(d, 0L));
            trend.add(row);
        }
        return trend;
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
        @com.alibaba.excel.annotation.ExcelProperty("身份")
        private String identity;
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
