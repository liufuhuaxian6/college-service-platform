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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public Page<SysUser> getStudentPage(int page, int size, String grade, String major, String className) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleLevel, 4)
                .eq(grade != null, SysUser::getGrade, grade)
                .eq(major != null, SysUser::getMajor, major)
                .eq(className != null, SysUser::getClassName, className)
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
            if (u.getIdCardEnc() != null) {
                String decrypted = EncryptUtil.decrypt(u.getIdCardEnc());
                u.setIdCardEnc(EncryptUtil.desensitize(decrypted, 3, 4));
            }
            u.setOriginEnc(null); // 管理端也脱敏
        });
        return result;
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
            List<PartyProcessTemplate> templates = partyTemplateMapper.selectBatchIds(templateIds);
            templateMap = templates.stream()
                    .filter(t -> t.getId() != null)
                    .collect(Collectors.toMap(PartyProcessTemplate::getId, Function.identity(), (a, b) -> a));
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
            List<ApprovalType> types = approvalTypeMapper.selectBatchIds(typeIds);
            typeMap = types.stream()
                    .filter(t -> t.getId() != null)
                    .collect(Collectors.toMap(ApprovalType::getId, Function.identity(), (a, b) -> a));
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
        honor.setUserId(userId);
        honor.setCreatedBy(UserContext.getUserId());
        honorMapper.insert(honor);
        return honor.getId();
    }

    public void updateHonor(Long honorId, StudentHonor honor) {
        StudentHonor existing = honorMapper.selectById(honorId);
        if (existing == null) throw new BusinessException("荣誉记录不存在");
        honor.setId(honorId);
        honorMapper.updateById(honor);
    }

    public void deleteHonor(Long honorId) {
        honorMapper.deleteById(honorId);
    }

    // ==================== 辅助方法 ====================

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
            String decrypted = EncryptUtil.decrypt(user.getIdCardEnc());
            profile.put("idCard", EncryptUtil.desensitize(decrypted, 3, 4));
        }
        return profile;
    }
}
