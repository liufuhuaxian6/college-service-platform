package com.ruc.college.module.party.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruc.college.module.party.entity.PartyProcessInstance;
import com.ruc.college.module.party.entity.PartyProcessStep;
import com.ruc.college.module.party.entity.PartyProcessTemplate;
import com.ruc.college.module.party.mapper.PartyProcessInstanceMapper;
import com.ruc.college.module.party.mapper.PartyProcessStepMapper;
import com.ruc.college.module.party.mapper.PartyProcessTemplateMapper;
import com.ruc.college.module.system.entity.SysNotification;
import com.ruc.college.module.system.mapper.SysNotificationMapper;
import com.ruc.college.module.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PartyReminderScheduler {

    private final PartyProcessInstanceMapper instanceMapper;
    private final PartyProcessTemplateMapper templateMapper;
    private final PartyProcessStepMapper stepMapper;
    private final SysNotificationMapper notificationMapper;
    private final SystemService systemService;

    private static final int REMIND_DAYS = 3;

    @Scheduled(cron = "0 0 9 * * ?")
    public void sendUpcomingStepReminders() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();

        List<PartyProcessInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<PartyProcessInstance>().eq(PartyProcessInstance::getStatus, "active")
        );
        if (instances.isEmpty()) {
            return;
        }

        Map<Long, PartyProcessTemplate> templateMap = new HashMap<>();
        Map<Long, List<PartyProcessStep>> stepsByTemplate = new HashMap<>();

        for (PartyProcessInstance instance : instances) {
            if (instance.getTemplateId() == null || instance.getUserId() == null || instance.getStartDate() == null || instance.getCurrentStep() == null) {
                continue;
            }

            PartyProcessTemplate template = templateMap.computeIfAbsent(instance.getTemplateId(), templateMapper::selectById);
            if (template == null) {
                continue;
            }

            List<PartyProcessStep> steps = stepsByTemplate.computeIfAbsent(instance.getTemplateId(), id -> stepMapper.selectList(
                    new LambdaQueryWrapper<PartyProcessStep>()
                            .eq(PartyProcessStep::getTemplateId, id)
                            .orderByAsc(PartyProcessStep::getStepOrder)
            ));
            if (steps == null || steps.isEmpty()) {
                continue;
            }

            PartyProcessStep current = steps.stream()
                    .filter(s -> s.getStepOrder() != null && s.getStepOrder().equals(instance.getCurrentStep()))
                    .findFirst()
                    .orElse(null);
            if (current == null) {
                continue;
            }

            LocalDate dueDate = calcDueDate(instance.getStartDate(), steps, instance.getCurrentStep());
            if (dueDate == null) {
                continue;
            }

            if (dueDate.isAfter(today.plusDays(REMIND_DAYS))) {
                continue;
            }

            String key = buildKey(instance.getId(), instance.getCurrentStep());
            boolean alreadySentToday = notificationMapper.selectCount(
                    new LambdaQueryWrapper<SysNotification>()
                            .eq(SysNotification::getUserId, instance.getUserId())
                            .eq(SysNotification::getType, "reminder")
                            .ge(SysNotification::getCreatedAt, startOfToday)
                            .like(SysNotification::getContent, key)
            ) > 0;
            if (alreadySentToday) {
                continue;
            }

            String title = "党团流程提醒";
            String content = buildContent(template.getName(), current, dueDate, key);
            systemService.sendNotification(instance.getUserId(), title, content, "reminder");
        }
    }

    private static LocalDate calcDueDate(LocalDate startDate, List<PartyProcessStep> steps, int currentStepOrder) {
        if (startDate == null || steps == null || steps.isEmpty()) {
            return null;
        }
        List<PartyProcessStep> sorted = steps.stream()
                .filter(s -> s.getStepOrder() != null)
                .sorted(Comparator.comparingInt(PartyProcessStep::getStepOrder))
                .toList();

        long days = 0;
        for (PartyProcessStep step : sorted) {
            if (step.getStepOrder() > currentStepOrder) {
                break;
            }
            Integer d = step.getDurationDays();
            if (d != null && d > 0) {
                days += d;
            }
        }
        return startDate.plusDays(days);
    }

    private static String buildKey(Long instanceId, Integer stepOrder) {
        return "[party-instance:" + instanceId + ",step:" + stepOrder + "]";
    }

    private static String buildContent(String templateName, PartyProcessStep step, LocalDate dueDate, String key) {
        String name = StringUtils.hasText(templateName) ? templateName : "党团流程";
        String stepName = step != null && StringUtils.hasText(step.getName()) ? step.getName() : "当前步骤";
        String materials = step != null && StringUtils.hasText(step.getRequiredMaterials()) ? step.getRequiredMaterials() : "";

        StringBuilder sb = new StringBuilder();
        sb.append(key).append("\n");
        sb.append("流程: ").append(name).append("\n");
        sb.append("步骤: ").append(stepName).append("\n");
        sb.append("预计到期日期: ").append(dueDate).append("\n");
        if (StringUtils.hasText(materials)) {
            sb.append("需要材料: ").append(materials).append("\n");
        }
        sb.append("请及时准备并按要求完成。");
        return sb.toString();
    }
}
