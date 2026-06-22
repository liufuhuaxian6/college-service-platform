package com.ruc.college.module.student.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.common.util.EncryptUtil;
import com.ruc.college.module.approval.entity.ApprovalApplication;
import com.ruc.college.module.approval.entity.ApprovalType;
import com.ruc.college.module.approval.mapper.ApprovalApplicationMapper;
import com.ruc.college.module.approval.mapper.ApprovalTypeMapper;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.mapper.SysUserMapper;
import com.ruc.college.module.party.entity.PartyProcessInstance;
import com.ruc.college.module.party.entity.PartyProcessTemplate;
import com.ruc.college.module.party.mapper.PartyProcessInstanceMapper;
import com.ruc.college.module.party.mapper.PartyProcessTemplateMapper;
import com.ruc.college.module.student.entity.StudentHonor;
import com.ruc.college.module.student.mapper.StudentHonorMapper;
import com.ruc.college.module.system.service.EmailService;
import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final SysUserMapper userMapper;
    private final StudentHonorMapper honorMapper;
    private final PartyProcessInstanceMapper partyInstanceMapper;
    private final PartyProcessTemplateMapper partyTemplateMapper;
    private final ApprovalApplicationMapper approvalApplicationMapper;
    private final ApprovalTypeMapper approvalTypeMapper;
    private final EmailService emailService;

    // ==================== 学生端 ====================

    public Map<String, Object> getMyProfile() {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (user == null) throw new BusinessException("用户不存在");
        return buildProfile(user, true);
    }

    public List<StudentHonor> getMyHonors() {
        return honorMapper.selectList(
                new LambdaQueryWrapper<StudentHonor>()
                        .eq(StudentHonor::getUserId, UserContext.getUserId())
                        .orderByDesc(StudentHonor::getAwardDate)
        );
    }

    // ==================== 管理端 ====================

    public Page<SysUser> getStudentPage(int page, int size, String grade, String major, String className, String roleLevel) {
        // 学生 = 普通学生(4) + 学生骨干(3); 骨干也是学生, 一并纳入学生信息
        // 支持逗号分隔多值; roleLevel 仅在 3/4 内生效, 否则两类都看
        List<String> grades = splitCsv(grade);
        List<String> majors = splitCsv(major);
        List<String> classes = splitCsv(className);
        List<Integer> roleLevels = splitCsvInt(roleLevel).stream()
                .filter(r -> r == 3 || r == 4)
                .toList();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .in(!roleLevels.isEmpty(), SysUser::getRoleLevel, roleLevels)
                .in(roleLevels.isEmpty(), SysUser::getRoleLevel, 3, 4)
                .in(!grades.isEmpty(), SysUser::getGrade, grades)
                .in(!majors.isEmpty(), SysUser::getMajor, majors)
                .in(!classes.isEmpty(), SysUser::getClassName, classes)
                .orderByAsc(SysUser::getStudentId);

        // 数据隔离: 3级只看本班
        if (UserContext.getRoleLevel() == 3) {
            SysUser currentUser = userMapper.selectById(UserContext.getUserId());
            if (currentUser != null) {
                wrapper.eq(SysUser::getClassName, currentUser.getClassName());
            }
        }

        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        // 脱敏处理
        result.getRecords().forEach(u -> {
            u.setPassword(null);
            u.setEmail(emailService.resolveEmail(u));
            if (u.getIdCardEnc() != null) {
                try {
                    String decrypted = EncryptUtil.decrypt(u.getIdCardEnc());
                    u.setIdCardEnc(EncryptUtil.desensitize(decrypted, 3, 4));
                } catch (Exception e) {
                    u.setIdCardEnc("******");
                }
            }
            u.setOriginEnc(null); // 管理端也脱敏
        });
        return result;
    }

    /**
     * 导出学生名单为 Excel (学生信息页).
     * 学生 = 普通学生(4) + 学生骨干(3), 用"身份"列区分; 只导启用状态;
     * 支持 身份/年级/专业/班级 多值筛选; 3级骨干受数据隔离只导本班.
     */
    public void exportStudents(String grade, String major, String className, String roleLevel, HttpServletResponse response) {
        List<String> grades = splitCsv(grade);
        List<String> majors = splitCsv(major);
        List<String> classes = splitCsv(className);
        List<Integer> roleLevels = splitCsvInt(roleLevel).stream()
                .filter(r -> r == 3 || r == 4)
                .toList();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .in(!roleLevels.isEmpty(), SysUser::getRoleLevel, roleLevels)
                .in(roleLevels.isEmpty(), SysUser::getRoleLevel, 3, 4)
                .eq(SysUser::getStatus, 1)
                .in(!grades.isEmpty(), SysUser::getGrade, grades)
                .in(!majors.isEmpty(), SysUser::getMajor, majors)
                .in(!classes.isEmpty(), SysUser::getClassName, classes)
                .orderByAsc(SysUser::getGrade)
                .orderByAsc(SysUser::getClassName)
                .orderByAsc(SysUser::getStudentId);

        // 数据隔离: 3级骨干只导本班
        if (UserContext.getRoleLevel() == 3) {
            SysUser current = userMapper.selectById(UserContext.getUserId());
            if (current != null) {
                wrapper.eq(SysUser::getClassName, current.getClassName());
            }
        }

        List<SysUser> users = userMapper.selectList(wrapper);
        List<StudentExportRow> rows = users.stream().map(u -> {
            StudentExportRow r = new StudentExportRow();
            r.setStudentId(u.getStudentId());
            r.setName(u.getName());
            r.setIdentity(Integer.valueOf(3).equals(u.getRoleLevel()) ? "学生骨干" : "普通学生");
            r.setGrade(u.getGrade());
            r.setMajor(u.getMajor());
            r.setClassName(u.getClassName());
            r.setPhone(u.getPhone());
            return r;
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

    public Map<String, Object> getStudentDetail(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("学生不存在");

        Map<String, Object> detail = buildProfile(user, UserContext.getRoleLevel() <= 2);

        List<StudentHonor> honors = honorMapper.selectList(
                new LambdaQueryWrapper<StudentHonor>()
                        .eq(StudentHonor::getUserId, userId)
                        .orderByDesc(StudentHonor::getAwardDate)
        );
        detail.put("honors", honors);

        List<PartyProcessInstance> processes = partyInstanceMapper.selectList(
                new LambdaQueryWrapper<PartyProcessInstance>()
                        .eq(PartyProcessInstance::getUserId, userId)
                        .orderByDesc(PartyProcessInstance::getUpdatedAt)
                        .last("LIMIT 50")
        );
        final Map<Long, PartyProcessTemplate> templateMap;
        if (processes != null && !processes.isEmpty()) {
            Set<Long> templateIds = processes.stream()
                    .map(PartyProcessInstance::getTemplateId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (templateIds.isEmpty()) {
                templateMap = Map.of();
            } else {
                List<PartyProcessTemplate> templates = partyTemplateMapper.selectBatchIds(templateIds);
                templateMap = templates.stream()
                        .filter(t -> t.getId() != null)
                        .collect(Collectors.toMap(PartyProcessTemplate::getId, Function.identity(), (a, b) -> a));
            }
        } else {
            templateMap = Map.of();
        }
        List<Map<String, Object>> processList = processes.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("templateId", p.getTemplateId());
            PartyProcessTemplate tpl = templateMap.get(p.getTemplateId());
            m.put("templateName", tpl != null ? tpl.getName() : null);
            m.put("currentStep", p.getCurrentStep());
            m.put("startDate", p.getStartDate());
            m.put("status", p.getStatus());
            m.put("createdAt", p.getCreatedAt());
            m.put("updatedAt", p.getUpdatedAt());
            return m;
        }).toList();
        detail.put("processes", processList);

        List<ApprovalApplication> approvals = approvalApplicationMapper.selectList(
                new LambdaQueryWrapper<ApprovalApplication>()
                        .eq(ApprovalApplication::getUserId, userId)
                        .orderByDesc(ApprovalApplication::getUpdatedAt)
                        .last("LIMIT 50")
        );
        final Map<Long, ApprovalType> typeMap;
        if (approvals != null && !approvals.isEmpty()) {
            Set<Long> typeIds = approvals.stream()
                    .map(ApprovalApplication::getTypeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (typeIds.isEmpty()) {
                typeMap = Map.of();
            } else {
                List<ApprovalType> types = approvalTypeMapper.selectBatchIds(typeIds);
                typeMap = types.stream()
                        .filter(t -> t.getId() != null)
                        .collect(Collectors.toMap(ApprovalType::getId, Function.identity(), (a, b) -> a));
            }
        } else {
            typeMap = Map.of();
        }
        List<Map<String, Object>> approvalList = approvals.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("appNo", a.getAppNo());
            m.put("typeId", a.getTypeId());
            ApprovalType type = typeMap.get(a.getTypeId());
            m.put("typeName", type != null ? type.getName() : null);
            m.put("status", a.getStatus());
            m.put("currentApproverLevel", a.getCurrentApproverLevel());
            m.put("withdrawDeadline", a.getWithdrawDeadline());
            m.put("downloadedAt", a.getDownloadedAt());
            m.put("certFilePath", a.getCertFilePath());
            m.put("createdAt", a.getCreatedAt());
            m.put("updatedAt", a.getUpdatedAt());
            return m;
        }).toList();
        detail.put("approvals", approvalList);

        return detail;
    }

    public Long addHonor(Long userId, StudentHonor honor) {
        if (honor == null) throw new BusinessException("荣誉信息不能为空");
        if (!StringUtils.hasText(honor.getHonorName())) throw new BusinessException("荣誉名称不能为空");
        if (honor.getAwardDate() == null) throw new BusinessException("获奖日期不能为空");
        // 获奖日期不能晚于今天 (防止录入 2035 等未来时间)
        if (honor.getAwardDate().isAfter(java.time.LocalDate.now())) {
            throw new BusinessException("获奖日期不能晚于当前日期");
        }
        honor.setUserId(userId);
        honor.setCreatedBy(UserContext.getUserId());
        honorMapper.insert(honor);
        return honor.getId();
    }

    public void updateHonor(Long honorId, StudentHonor honor) {
        StudentHonor existing = honorMapper.selectById(honorId);
        if (existing == null) throw new BusinessException("荣誉记录不存在");
        if (honor != null && honor.getAwardDate() != null && honor.getAwardDate().isAfter(java.time.LocalDate.now())) {
            throw new BusinessException("获奖日期不能晚于当前日期");
        }
        honor.setId(honorId);
        honorMapper.updateById(honor);
    }

    public void deleteHonor(Long honorId) {
        honorMapper.deleteById(honorId);
    }

    // ==================== 辅助方法 ====================

    /** 逗号分隔字符串 -> 去重去空的字符串列表 (兼容单值与多值) */
    private static List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /** 逗号分隔字符串 -> 整数列表 (非法值跳过) */
    private static List<Integer> splitCsvInt(String csv) {
        List<Integer> out = new java.util.ArrayList<>();
        for (String s : splitCsv(csv)) {
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignore) {
                // 跳过非数字
            }
        }
        return out;
    }

    private Map<String, Object> buildProfile(SysUser user, boolean showSensitive) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getId());
        profile.put("studentId", user.getStudentId());
        profile.put("name", user.getName());
        profile.put("grade", user.getGrade());
        profile.put("major", user.getMajor());
        profile.put("className", user.getClassName());
        profile.put("phone", user.getPhone());
        profile.put("email", emailService.resolveEmail(user));
        profile.put("emailCustom", StringUtils.hasText(user.getEmail()));
        profile.put("roleLevel", user.getRoleLevel());

        if (showSensitive && user.getIdCardEnc() != null) {
            // 个别历史数据可能不是合法密文 (手工导入/换过密钥), 解密失败时降级显示, 不让详情接口 500
            try {
                String decrypted = EncryptUtil.decrypt(user.getIdCardEnc());
                profile.put("idCard", EncryptUtil.desensitize(decrypted, 3, 4));
            } catch (Exception e) {
                profile.put("idCard", "******");
            }
        }
        return profile;
    }

    /** 学生信息导出行: 学号/姓名/身份/年级/专业/班级/手机号 */
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
