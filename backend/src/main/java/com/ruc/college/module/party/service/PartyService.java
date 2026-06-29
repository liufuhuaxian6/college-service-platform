package com.ruc.college.module.party.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.mapper.SysUserMapper;
import com.ruc.college.module.party.entity.*;
import com.ruc.college.module.party.mapper.*;
import com.ruc.college.module.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyProcessTemplateMapper templateMapper;
    private final PartyProcessStepMapper stepMapper;
    private final PartyProcessInstanceMapper instanceMapper;
    private final PartyStepRecordMapper stepRecordMapper;
    private final PartyProcessApplicationMapper applicationMapper;
    private final SysUserMapper userMapper;
    private final SystemService systemService;

    // ==================== 学生端 ====================

    public List<PartyProcessTemplate> getAllTemplates() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<PartyProcessTemplate>().eq(PartyProcessTemplate::getStatus, 1)
        );
    }

    /** 学生端: 按模板查官方节点列表 (与"我的流程"同源于 DB, 避免小程序端硬编码导致节点数对不上) */
    public List<PartyProcessStep> getTemplateSteps(Long templateId) {
        return stepMapper.selectList(
                new LambdaQueryWrapper<PartyProcessStep>()
                        .eq(PartyProcessStep::getTemplateId, templateId)
                        .orderByAsc(PartyProcessStep::getStepOrder)
        );
    }

    public List<Map<String, Object>> getMyProgress() {
        Long userId = UserContext.getUserId();
        List<PartyProcessInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<PartyProcessInstance>().eq(PartyProcessInstance::getUserId, userId)
        );
        return instances.stream().map(this::buildProgressDetail).toList();
    }

    public Map<String, Object> getProgressDetail(Long instanceId) {
        PartyProcessInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) throw new BusinessException("流程实例不存在");
        // 学生只能查看自己的
        if (UserContext.getRoleLevel() > 2 && !instance.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权查看他人流程");
        }
        return buildProgressDetail(instance);
    }

    private Map<String, Object> buildProgressDetail(PartyProcessInstance instance) {
        PartyProcessTemplate template = templateMapper.selectById(instance.getTemplateId());
        List<PartyProcessStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<PartyProcessStep>()
                        .eq(PartyProcessStep::getTemplateId, instance.getTemplateId())
                        .orderByAsc(PartyProcessStep::getStepOrder)
        );
        List<PartyStepRecord> records = stepRecordMapper.selectList(
                new LambdaQueryWrapper<PartyStepRecord>().eq(PartyStepRecord::getInstanceId, instance.getId())
        );

        Map<String, Object> result = new HashMap<>();
        result.put("id", instance.getId());
        result.put("templateName", template != null ? template.getName() : "");
        result.put("currentStep", instance.getCurrentStep());
        result.put("status", instance.getStatus());
        result.put("startDate", instance.getStartDate());

        LocalDate startDate = instance.getStartDate();
        List<Map<String, Object>> stepList = steps.stream().map(step -> {
            Map<String, Object> s = new HashMap<>();
            s.put("stepOrder", step.getStepOrder());
            s.put("name", step.getName());
            s.put("description", step.getDescription());
            s.put("durationDays", step.getDurationDays());
            s.put("requiredMaterials", step.getRequiredMaterials());
            boolean completed = records.stream().anyMatch(r -> r.getStepId().equals(step.getId()) && r.getCompletedAt() != null);
            s.put("completed", completed);
            records.stream()
                    .filter(r -> r.getStepId().equals(step.getId()) && r.getCompletedAt() != null)
                    .findFirst()
                    .ifPresent(r -> s.put("completedAt", r.getCompletedAt()));
            LocalDate expectedEnd = calcExpectedEnd(startDate, steps, step.getStepOrder());
            if (expectedEnd != null) {
                s.put("expectedEnd", expectedEnd);
            }
            return s;
        }).toList();

        result.put("steps", stepList);
        return result;
    }

    private LocalDate calcExpectedEnd(LocalDate startDate, List<PartyProcessStep> steps, Integer stepOrder) {
        if (startDate == null || steps == null || steps.isEmpty() || stepOrder == null) {
            return null;
        }
        long days = 0;
        for (PartyProcessStep step : steps) {
            if (step.getStepOrder() == null) {
                continue;
            }
            if (step.getStepOrder() > stepOrder) {
                continue;
            }
            Integer d = step.getDurationDays();
            if (d != null && d > 0) {
                days += d;
            }
        }
        return startDate.plusDays(days);
    }

    // ==================== 流程申请 ====================

    public Page<PartyProcessApplication> getMyApplications(int page, int size, String status) {
        LambdaQueryWrapper<PartyProcessApplication> wrapper = new LambdaQueryWrapper<PartyProcessApplication>()
                .eq(PartyProcessApplication::getUserId, UserContext.getUserId())
                .eq(StringUtils.hasText(status), PartyProcessApplication::getStatus, status)
                .orderByDesc(PartyProcessApplication::getCreatedAt);
        Page<PartyProcessApplication> result = applicationMapper.selectPage(new Page<>(page, size), wrapper);
        enrichApplications(result.getRecords());
        return result;
    }

    @Transactional
    public PartyProcessApplication applyForProcess(Long templateId, String reason) {
        if (UserContext.getRoleLevel() <= 2) {
            throw new BusinessException("管理端账号请直接创建学生流程");
        }
        if (templateId == null) {
            throw new BusinessException("请选择流程模板");
        }
        PartyProcessTemplate template = templateMapper.selectById(templateId);
        if (template == null || template.getStatus() == null || template.getStatus() != 1) {
            throw new BusinessException("流程模板不存在或未启用");
        }

        Long userId = UserContext.getUserId();
        PartyProcessInstance activeInstance = instanceMapper.selectOne(
                new LambdaQueryWrapper<PartyProcessInstance>()
                        .eq(PartyProcessInstance::getUserId, userId)
                        .eq(PartyProcessInstance::getTemplateId, templateId)
                        .eq(PartyProcessInstance::getStatus, "active")
                        .last("LIMIT 1")
        );
        if (activeInstance != null) {
            throw new BusinessException("你已有进行中的同类流程");
        }

        PartyProcessApplication pending = applicationMapper.selectOne(
                new LambdaQueryWrapper<PartyProcessApplication>()
                        .eq(PartyProcessApplication::getUserId, userId)
                        .eq(PartyProcessApplication::getTemplateId, templateId)
                        .eq(PartyProcessApplication::getStatus, "pending")
                        .last("LIMIT 1")
        );
        if (pending != null) {
            throw new BusinessException("你已有待审核的同类流程申请");
        }

        PartyProcessApplication app = new PartyProcessApplication();
        app.setAppNo(buildPartyApplicationNo(userId));
        app.setUserId(userId);
        app.setTemplateId(templateId);
        app.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
        app.setStatus("pending");
        applicationMapper.insert(app);
        enrichApplications(List.of(app));
        return app;
    }

    @Transactional
    public void withdrawMyApplication(Long id) {
        PartyProcessApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!app.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权撤回他人申请");
        }
        if (!"pending".equals(app.getStatus())) {
            throw new BusinessException("仅待审核申请可以撤回");
        }
        app.setStatus("withdrawn");
        applicationMapper.updateById(app);
    }

    // ==================== 管理端 ====================

    public Page<PartyProcessTemplate> getTemplatePage(int page, int size) {
        return templateMapper.selectPage(new Page<>(page, size), null);
    }

    public Map<String, Object> getTemplateDetail(Long id) {
        PartyProcessTemplate template = templateMapper.selectById(id);
        if (template == null) throw new BusinessException("流程模板不存在");
        List<PartyProcessStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<PartyProcessStep>()
                        .eq(PartyProcessStep::getTemplateId, id)
                        .orderByAsc(PartyProcessStep::getStepOrder)
        );
        Map<String, Object> result = new HashMap<>();
        result.put("template", template);
        result.put("steps", steps);
        return result;
    }

    @Transactional
    public Long createTemplate(PartyProcessTemplate template, List<PartyProcessStep> steps) {
        if (template == null) throw new BusinessException("模板信息不能为空");
        if (!StringUtils.hasText(template.getName())) throw new BusinessException("模板名称不能为空");
        if (steps == null || steps.isEmpty()) throw new BusinessException("步骤不能为空");

        template.setStatus(1);
        template.setTotalSteps(steps.size());
        templateMapper.insert(template);
        for (int i = 0; i < steps.size(); i++) {
            PartyProcessStep step = steps.get(i);
            if (step == null) throw new BusinessException("步骤不能为空");
            if (!StringUtils.hasText(step.getName())) throw new BusinessException("步骤名称不能为空");
            if (step.getDurationDays() != null && step.getDurationDays() < 0) throw new BusinessException("预计天数不能为负数");
            step.setTemplateId(template.getId());
            step.setStepOrder(i + 1);
            stepMapper.insert(step);
        }
        return template.getId();
    }

    @Transactional
    public void updateTemplate(Long id, PartyProcessTemplate template, List<PartyProcessStep> steps) {
        PartyProcessTemplate existing = templateMapper.selectById(id);
        if (existing == null) throw new BusinessException("流程模板不存在");
        if (template == null) throw new BusinessException("模板信息不能为空");
        if (!StringUtils.hasText(template.getName())) throw new BusinessException("模板名称不能为空");
        if (steps == null || steps.isEmpty()) throw new BusinessException("步骤不能为空");

        template.setId(id);
        template.setTotalSteps(steps.size());
        templateMapper.updateById(template);
        // 删除旧步骤，重建新步骤
        stepMapper.delete(new LambdaQueryWrapper<PartyProcessStep>().eq(PartyProcessStep::getTemplateId, id));
        for (int i = 0; i < steps.size(); i++) {
            PartyProcessStep step = steps.get(i);
            if (step == null) throw new BusinessException("步骤不能为空");
            if (!StringUtils.hasText(step.getName())) throw new BusinessException("步骤名称不能为空");
            if (step.getDurationDays() != null && step.getDurationDays() < 0) throw new BusinessException("预计天数不能为负数");
            step.setId(null);
            step.setTemplateId(id);
            step.setStepOrder(i + 1);
            stepMapper.insert(step);
        }
    }

    @Transactional
    public void deleteTemplate(Long id) {
        PartyProcessTemplate existing = templateMapper.selectById(id);
        if (existing == null) throw new BusinessException("流程模板不存在");

        Long instanceCount = instanceMapper.selectCount(
                new LambdaQueryWrapper<PartyProcessInstance>()
                        .eq(PartyProcessInstance::getTemplateId, id)
        );
        if (instanceCount != null && instanceCount > 0) {
            throw new BusinessException("该模板已有学生流程，不能删除，以免影响历史进度数据");
        }

        Long applicationCount = applicationMapper.selectCount(
                new LambdaQueryWrapper<PartyProcessApplication>()
                        .eq(PartyProcessApplication::getTemplateId, id)
        );
        if (applicationCount != null && applicationCount > 0) {
            throw new BusinessException("该模板已有学生申请记录，不能删除，以免影响历史申请数据");
        }

        stepMapper.delete(new LambdaQueryWrapper<PartyProcessStep>().eq(PartyProcessStep::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    public Page<PartyProcessInstance> getInstancePage(int page, int size, Long templateId, Long userId, String status) {
        LambdaQueryWrapper<PartyProcessInstance> wrapper = new LambdaQueryWrapper<PartyProcessInstance>()
                .eq(templateId != null, PartyProcessInstance::getTemplateId, templateId)
                .eq(userId != null, PartyProcessInstance::getUserId, userId)
                .eq(status != null, PartyProcessInstance::getStatus, status)
                .orderByDesc(PartyProcessInstance::getCreatedAt);
        return instanceMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Page<PartyProcessApplication> getApplicationPage(int page, int size, Long templateId, Long userId, String status) {
        LambdaQueryWrapper<PartyProcessApplication> wrapper = new LambdaQueryWrapper<PartyProcessApplication>()
                .eq(templateId != null, PartyProcessApplication::getTemplateId, templateId)
                .eq(userId != null, PartyProcessApplication::getUserId, userId)
                .eq(StringUtils.hasText(status), PartyProcessApplication::getStatus, status)
                .orderByDesc(PartyProcessApplication::getCreatedAt);
        Page<PartyProcessApplication> result = applicationMapper.selectPage(new Page<>(page, size), wrapper);
        enrichApplications(result.getRecords());
        return result;
    }

    @Transactional
    public void approveApplication(Long id, String comment) {
        PartyProcessApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!"pending".equals(app.getStatus())) throw new BusinessException("仅待审核申请可以通过");

        Long instanceId = createInstance(app.getUserId(), app.getTemplateId(), LocalDate.now());
        app.setStatus("approved");
        app.setReviewerId(UserContext.getUserId());
        app.setReviewComment(StringUtils.hasText(comment) ? comment.trim() : null);
        app.setReviewedAt(LocalDateTime.now());
        app.setInstanceId(instanceId);
        applicationMapper.updateById(app);

        PartyProcessTemplate template = templateMapper.selectById(app.getTemplateId());
        String templateName = template != null ? template.getName() : "党团流程";
        systemService.sendNotification(
                app.getUserId(),
                "党团流程申请已通过",
                "你的「" + templateName + "」申请已通过，系统已创建个人流程，请在党团进度中查看。",
                "system"
        );
    }

    @Transactional
    public void rejectApplication(Long id, String comment) {
        PartyProcessApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!"pending".equals(app.getStatus())) throw new BusinessException("仅待审核申请可以驳回");
        if (!StringUtils.hasText(comment)) throw new BusinessException("驳回原因不能为空");

        app.setStatus("rejected");
        app.setReviewerId(UserContext.getUserId());
        app.setReviewComment(comment.trim());
        app.setReviewedAt(LocalDateTime.now());
        applicationMapper.updateById(app);

        PartyProcessTemplate template = templateMapper.selectById(app.getTemplateId());
        String templateName = template != null ? template.getName() : "党团流程";
        systemService.sendNotification(
                app.getUserId(),
                "党团流程申请被驳回",
                "你的「" + templateName + "」申请被驳回，原因：" + comment.trim(),
                "system"
        );
    }

    public Long createInstance(Long userId, Long templateId, LocalDate startDate) {
        PartyProcessTemplate template = templateMapper.selectById(templateId);
        if (template == null) throw new BusinessException("流程模板不存在");
        if (userId == null) throw new BusinessException("学生不能为空");

        PartyProcessInstance existingActive = instanceMapper.selectOne(
                new LambdaQueryWrapper<PartyProcessInstance>()
                        .eq(PartyProcessInstance::getUserId, userId)
                        .eq(PartyProcessInstance::getTemplateId, templateId)
                        .eq(PartyProcessInstance::getStatus, "active")
                        .last("LIMIT 1")
        );
        if (existingActive != null) {
            throw new BusinessException("该学生已有进行中的同类流程");
        }

        PartyProcessInstance instance = new PartyProcessInstance();
        instance.setUserId(userId);
        instance.setTemplateId(templateId);
        instance.setCurrentStep(1);
        instance.setStartDate(startDate != null ? startDate : LocalDate.now());
        instance.setStatus("active");
        instanceMapper.insert(instance);
        return instance.getId();
    }

    @Transactional
    public void advanceStep(Long instanceId, String remark) {
        PartyProcessInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) throw new BusinessException("流程实例不存在");
        if (!"active".equals(instance.getStatus())) throw new BusinessException("流程已结束或暂停");

        // 记录当前步骤完成
        PartyProcessStep currentStep = stepMapper.selectOne(
                new LambdaQueryWrapper<PartyProcessStep>()
                        .eq(PartyProcessStep::getTemplateId, instance.getTemplateId())
                        .eq(PartyProcessStep::getStepOrder, instance.getCurrentStep())
        );
        if (currentStep == null) throw new BusinessException("当前步骤不存在");
        PartyStepRecord existingRecord = stepRecordMapper.selectOne(
                new LambdaQueryWrapper<PartyStepRecord>()
                        .eq(PartyStepRecord::getInstanceId, instanceId)
                        .eq(PartyStepRecord::getStepId, currentStep.getId())
                        .isNotNull(PartyStepRecord::getCompletedAt)
                        .last("LIMIT 1")
        );
        if (existingRecord != null) throw new BusinessException("当前步骤已完成，不能重复推进");

        PartyStepRecord record = new PartyStepRecord();
        record.setInstanceId(instanceId);
        record.setStepId(currentStep.getId());
        record.setCompletedAt(LocalDateTime.now());
        record.setRemark(remark);
        record.setOperatorId(UserContext.getUserId());
        stepRecordMapper.insert(record);

        // 推进到下一步或完成
        PartyProcessTemplate template = templateMapper.selectById(instance.getTemplateId());
        if (template == null) throw new BusinessException("流程模板不存在");
        if (template.getTotalSteps() == null || template.getTotalSteps() <= 0) throw new BusinessException("流程模板步骤数异常");
        if (instance.getCurrentStep() >= template.getTotalSteps()) {
            instance.setStatus("completed");
        } else {
            instance.setCurrentStep(instance.getCurrentStep() + 1);
        }
        instanceMapper.updateById(instance);
    }

    public void suspendInstance(Long instanceId, String remark) {
        PartyProcessInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) throw new BusinessException("流程实例不存在");
        if ("completed".equals(instance.getStatus())) throw new BusinessException("流程已完成，不能暂停");
        if ("suspended".equals(instance.getStatus())) throw new BusinessException("流程已是暂停状态");
        instance.setStatus("suspended");
        instanceMapper.updateById(instance);
    }

    public void resumeInstance(Long instanceId, String remark) {
        PartyProcessInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) throw new BusinessException("流程实例不存在");
        if (!"suspended".equals(instance.getStatus())) throw new BusinessException("仅暂停中的流程可恢复");
        instance.setStatus("active");
        instanceMapper.updateById(instance);
    }

    @Transactional
    public void deleteInstance(Long instanceId) {
        PartyProcessInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) throw new BusinessException("流程实例不存在");
        stepRecordMapper.delete(
                new LambdaQueryWrapper<PartyStepRecord>().eq(PartyStepRecord::getInstanceId, instanceId)
        );
        instanceMapper.deleteById(instanceId);
    }

    private String buildPartyApplicationNo(Long userId) {
        return "PT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + userId;
    }

    private void enrichApplications(List<PartyProcessApplication> apps) {
        if (apps == null || apps.isEmpty()) {
            return;
        }

        Set<Long> templateIds = apps.stream()
                .map(PartyProcessApplication::getTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, PartyProcessTemplate> templateMap = templateIds.isEmpty() ? Map.of()
                : templateMapper.selectBatchIds(templateIds).stream()
                .collect(Collectors.toMap(PartyProcessTemplate::getId, t -> t, (a, b) -> a));

        Set<Long> userIds = apps.stream()
                .flatMap(a -> {
                    List<Long> ids = new ArrayList<>();
                    ids.add(a.getUserId());
                    ids.add(a.getReviewerId());
                    return ids.stream();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        for (PartyProcessApplication app : apps) {
            PartyProcessTemplate template = templateMap.get(app.getTemplateId());
            if (template != null) {
                app.setTemplateName(template.getName());
            }
            SysUser user = userMap.get(app.getUserId());
            if (user != null) {
                app.setUserName(user.getName());
                app.setStudentId(user.getStudentId());
            }
            SysUser reviewer = userMap.get(app.getReviewerId());
            if (reviewer != null) {
                app.setReviewerName(reviewer.getName());
            }
        }
    }
}
