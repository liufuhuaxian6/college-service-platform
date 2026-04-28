package com.ruc.college.module.party.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.module.party.entity.*;
import com.ruc.college.module.party.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyProcessTemplateMapper templateMapper;
    private final PartyProcessStepMapper stepMapper;
    private final PartyProcessInstanceMapper instanceMapper;
    private final PartyStepRecordMapper stepRecordMapper;

    // ==================== 学生端 ====================

    public List<PartyProcessTemplate> getAllTemplates() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<PartyProcessTemplate>().eq(PartyProcessTemplate::getStatus, 1)
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
        if (UserContext.getRoleLevel() == 4 && !instance.getUserId().equals(UserContext.getUserId())) {
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
            return s;
        }).toList();

        result.put("steps", stepList);
        return result;
    }

    // ==================== 管理端 ====================

    public Page<PartyProcessTemplate> getTemplatePage(int page, int size) {
        return templateMapper.selectPage(new Page<>(page, size), null);
    }

    @Transactional
    public Long createTemplate(PartyProcessTemplate template, List<PartyProcessStep> steps) {
        template.setStatus(1);
        template.setTotalSteps(steps.size());
        templateMapper.insert(template);
        for (int i = 0; i < steps.size(); i++) {
            PartyProcessStep step = steps.get(i);
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
        template.setId(id);
        template.setTotalSteps(steps.size());
        templateMapper.updateById(template);
        // 删除旧步骤，重建新步骤
        stepMapper.delete(new LambdaQueryWrapper<PartyProcessStep>().eq(PartyProcessStep::getTemplateId, id));
        for (int i = 0; i < steps.size(); i++) {
            PartyProcessStep step = steps.get(i);
            step.setId(null);
            step.setTemplateId(id);
            step.setStepOrder(i + 1);
            stepMapper.insert(step);
        }
    }

    public Page<PartyProcessInstance> getInstancePage(int page, int size, Long templateId, Long userId, String status) {
        LambdaQueryWrapper<PartyProcessInstance> wrapper = new LambdaQueryWrapper<PartyProcessInstance>()
                .eq(templateId != null, PartyProcessInstance::getTemplateId, templateId)
                .eq(userId != null, PartyProcessInstance::getUserId, userId)
                .eq(status != null, PartyProcessInstance::getStatus, status)
                .orderByDesc(PartyProcessInstance::getCreatedAt);
        return instanceMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Long createInstance(Long userId, Long templateId, java.time.LocalDate startDate) {
        PartyProcessTemplate template = templateMapper.selectById(templateId);
        if (template == null) throw new BusinessException("流程模板不存在");

        PartyProcessInstance instance = new PartyProcessInstance();
        instance.setUserId(userId);
        instance.setTemplateId(templateId);
        instance.setCurrentStep(1);
        instance.setStartDate(startDate);
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
        if (currentStep != null) {
            PartyStepRecord record = new PartyStepRecord();
            record.setInstanceId(instanceId);
            record.setStepId(currentStep.getId());
            record.setCompletedAt(LocalDateTime.now());
            record.setRemark(remark);
            record.setOperatorId(UserContext.getUserId());
            stepRecordMapper.insert(record);
        }

        // 推进到下一步或完成
        PartyProcessTemplate template = templateMapper.selectById(instance.getTemplateId());
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
        instance.setStatus("suspended");
        instanceMapper.updateById(instance);
    }
}
