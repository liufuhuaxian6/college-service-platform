package com.ruc.college.module.party.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruc.college.module.party.entity.PartyProcessInstance;
import com.ruc.college.module.party.entity.PartyProcessStep;
import com.ruc.college.module.party.entity.PartyProcessTemplate;
import com.ruc.college.module.party.entity.PartyStepRecord;
import com.ruc.college.module.party.mapper.PartyProcessInstanceMapper;
import com.ruc.college.module.party.mapper.PartyProcessStepMapper;
import com.ruc.college.module.party.mapper.PartyProcessTemplateMapper;
import com.ruc.college.module.party.mapper.PartyStepRecordMapper;
import com.ruc.college.module.system.entity.SysNotification;
import com.ruc.college.module.system.mapper.SysNotificationMapper;
import com.ruc.college.module.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 党团流程到期提醒定时任务.
 *
 * <p>每天 09:00 扫描所有 status=active 的流程实例, 对每个实例:
 * 1) 找到最近一次步骤完成记录 (max(completedAt))
 * 2) 取当前步骤的 durationDays
 * 3) 比较 [最近完成时间 + durationDays] 与当前时间:
 *    - 剩余 ≤ remindBeforeDays 且 > 0: 发"即将到期"提醒
 *    - 剩余 ≤ 0: 发"已超期"提醒
 *
 * <p>防重: 24h 内同一 (userId, type=reminder, instanceId in content) 已发过则跳过.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyReminderJob {

    private final PartyProcessInstanceMapper instanceMapper;
    private final PartyProcessTemplateMapper templateMapper;
    private final PartyProcessStepMapper stepMapper;
    private final PartyStepRecordMapper recordMapper;
    private final SysNotificationMapper notificationMapper;
    private final SystemService systemService;

    @Value("${party.reminder.before-days:7}")
    private int remindBeforeDays;

    /**
     * cron: 每天 09:00:00 触发. 开发期可改 application.yml 的
     * party.reminder.cron 覆盖, 或注释掉 @Scheduled 手动通过测试接口触发.
     */
    @Scheduled(cron = "${party.reminder.cron:0 0 9 * * ?}")
    public void run() {
        try {
            int sent = scanAndNotify();
            log.info("党团到期提醒扫描完成, 发出 {} 条提醒", sent);
        } catch (Exception e) {
            log.error("党团到期提醒任务异常", e);
        }
    }

    /**
     * 实际逻辑抽出来, 既给 @Scheduled 用, 也供开发期手动触发调试.
     * @return 本次新增的通知数
     */
    public int scanAndNotify() {
        List<PartyProcessInstance> activeInstances = instanceMapper.selectList(
                new LambdaQueryWrapper<PartyProcessInstance>()
                        .eq(PartyProcessInstance::getStatus, "active"));
        if (activeInstances.isEmpty()) return 0;

        // 批量预加载: 模板 + 所有相关 step + 每个 instance 最近一次 record
        Set<Long> templateIds = activeInstances.stream()
                .map(PartyProcessInstance::getTemplateId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PartyProcessTemplate> templateMap = templateIds.isEmpty() ? Map.of()
                : templateMapper.selectBatchIds(templateIds).stream()
                        .collect(Collectors.toMap(PartyProcessTemplate::getId, Function.identity(), (a, b) -> a));

        List<PartyProcessStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<PartyProcessStep>().in(templateIds.size() > 0,
                        PartyProcessStep::getTemplateId, templateIds));
        // (templateId, stepOrder) -> step
        Map<String, PartyProcessStep> stepKeyMap = new HashMap<>();
        for (PartyProcessStep s : steps) {
            stepKeyMap.put(s.getTemplateId() + "#" + s.getStepOrder(), s);
        }

        Set<Long> instanceIds = activeInstances.stream()
                .map(PartyProcessInstance::getId).collect(Collectors.toSet());
        List<PartyStepRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<PartyStepRecord>()
                        .in(PartyStepRecord::getInstanceId, instanceIds)
                        .orderByDesc(PartyStepRecord::getCompletedAt));
        // instanceId -> 最近一次完成时间
        Map<Long, LocalDateTime> lastAdvanceMap = new HashMap<>();
        for (PartyStepRecord r : records) {
            lastAdvanceMap.putIfAbsent(r.getInstanceId(), r.getCompletedAt());
        }

        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        for (PartyProcessInstance inst : activeInstances) {
            if (inst.getCurrentStep() == null || inst.getTemplateId() == null) continue;
            PartyProcessStep step = stepKeyMap.get(inst.getTemplateId() + "#" + inst.getCurrentStep());
            if (step == null || step.getDurationDays() == null || step.getDurationDays() <= 0) {
                // 该步骤无时长约束, 不需要提醒
                continue;
            }
            // 计时起点: 优先用最近一次 step_record, 否则用实例创建时间 (兼容刚建实例还没推进过)
            LocalDateTime baseTime = lastAdvanceMap.getOrDefault(inst.getId(), inst.getCreatedAt());
            if (baseTime == null) continue;

            LocalDateTime expectedEnd = baseTime.plusDays(step.getDurationDays());
            long daysLeft = Duration.between(now, expectedEnd).toDays();

            String tag;
            String title;
            String content;
            PartyProcessTemplate tpl = templateMap.get(inst.getTemplateId());
            String tplName = tpl != null ? tpl.getName() : "党团流程";

            if (daysLeft < 0) {
                tag = "overdue";
                long overdueDays = -daysLeft;
                title = "【流程超期】" + tplName + " - " + step.getName();
                content = "您的「" + tplName + "」第 " + inst.getCurrentStep() + " 步 "
                        + "「" + step.getName() + "」已超期 " + overdueDays + " 天 "
                        + "(预期完成: " + expectedEnd.toLocalDate() + "). 请尽快联系组织员推进.";
            } else if (daysLeft <= remindBeforeDays) {
                tag = "approaching";
                title = "【流程到期提醒】" + tplName + " - " + step.getName();
                content = "您的「" + tplName + "」第 " + inst.getCurrentStep() + " 步 "
                        + "「" + step.getName() + "」还有 " + daysLeft + " 天到期 "
                        + "(预期完成: " + expectedEnd.toLocalDate() + "). 请准备相关材料.";
            } else {
                continue;
            }

            // 防重: 24h 内同 (userId, broadcastId=null, content 含 [INST-<id>:<tag>]) 已发过则跳过
            String marker = "[INST-" + inst.getId() + ":" + tag + "]";
            String fullContent = marker + " " + content;
            LocalDateTime since = now.minusHours(24);
            Long existed = notificationMapper.selectCount(
                    new LambdaQueryWrapper<SysNotification>()
                            .eq(SysNotification::getUserId, inst.getUserId())
                            .eq(SysNotification::getType, "reminder")
                            .like(SysNotification::getContent, marker)
                            .ge(SysNotification::getCreatedAt, since));
            if (existed != null && existed > 0) continue;

            systemService.sendNotification(inst.getUserId(), title, fullContent, "reminder");
            sent++;
        }
        return sent;
    }
}
