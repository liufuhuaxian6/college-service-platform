package com.ruc.college.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.mapper.SysUserMapper;
import com.ruc.college.module.system.entity.SysNotification;
import com.ruc.college.module.system.entity.SysNotificationBroadcast;
import com.ruc.college.module.system.mapper.SysNotificationBroadcastMapper;
import com.ruc.college.module.system.mapper.SysNotificationMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知群发 / 撤回核心逻辑.
 * <p>24h 内允许撤回(只删除未读条目, 已读保留学生侧记录).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationBroadcastService {

    private final SysNotificationBroadcastMapper broadcastMapper;
    private final SysNotificationMapper notificationMapper;
    private final SysUserMapper userMapper;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${notify.broadcast.withdraw-window-hours:24}")
    private int withdrawWindowHours;

    @Value("${notify.broadcast.max-targets:5000}")
    private int maxTargets;

    // 角色分组: 学生类 (含学生骨干) 拥有年级/专业/班级, 受这些维度约束; 教职工类没有这些字段.
    private static final Set<Integer> STUDENT_ROLES = Set.of(3, 4); // 3=学生骨干, 4=普通学生
    private static final Set<Integer> STAFF_ROLES = Set.of(1, 2);   // 1=院领导, 2=老师/辅导员

    /**
     * 解析本次群发要发给哪些角色. 兼容旧字段 roleLevel:
     *  - roles 非空 -> 用 roles
     *  - roles 空但 roleLevel 有值 -> [roleLevel]
     *  - 都空 -> 默认普通学生 [4]
     */
    private static Set<Integer> effectiveRoles(BroadcastFilter filter) {
        if (filter != null && filter.getRoles() != null && !filter.getRoles().isEmpty()) {
            return filter.getRoles().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (filter != null && filter.getRoleLevel() != null) {
            return Set.of(filter.getRoleLevel());
        }
        return Set.of(4);
    }

    /**
     * 按筛选条件查目标用户列表.
     *
     * 语义: 年级/专业/班级是学生属性, 只对学生类角色 (普通学生 + 学生骨干) 生效;
     * 教职工类角色 (老师/院领导) 没有这些字段, 一旦被勾选就整组接收, 不受年级/专业/班级影响.
     */
    public List<SysUser> resolveTargets(BroadcastFilter filter) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1);

        Set<Integer> roles = effectiveRoles(filter);
        Set<Integer> studentRoles = roles.stream().filter(STUDENT_ROLES::contains).collect(Collectors.toSet());
        Set<Integer> staffRoles = roles.stream().filter(STAFF_ROLES::contains).collect(Collectors.toSet());

        if (studentRoles.isEmpty() && staffRoles.isEmpty()) {
            // 角色都不合法, 返回空集而不是全表
            wrapper.eq(SysUser::getId, -1L);
            return userMapper.selectList(wrapper);
        }

        wrapper.and(outer -> {
            if (!studentRoles.isEmpty()) {
                outer.or(s -> {
                    s.in(SysUser::getRoleLevel, studentRoles);
                    if (notEmpty(filter.getGrades())) s.in(SysUser::getGrade, filter.getGrades());
                    if (notEmpty(filter.getMajors())) s.in(SysUser::getMajor, filter.getMajors());
                    if (notEmpty(filter.getClassNames())) s.in(SysUser::getClassName, filter.getClassNames());
                });
            }
            if (!staffRoles.isEmpty()) {
                outer.or(s -> s.in(SysUser::getRoleLevel, staffRoles));
            }
        });
        return userMapper.selectList(wrapper);
    }

    /**
     * 仅返回目标人数, 供前端 "预览" 按钮调用.
     */
    public int previewTargetCount(BroadcastFilter filter) {
        return resolveTargets(filter).size();
    }

    /**
     * 预览目标按角色拆分, 帮助操作者确认每个角色实际命中多少人.
     * key: total / student(普通学生) / cadre(学生骨干) / teacher(老师) / leadership(院领导)
     */
    public Map<String, Integer> previewTargetBreakdown(BroadcastFilter filter) {
        List<SysUser> targets = resolveTargets(filter);
        Map<String, Integer> map = new LinkedHashMap<>();
        int student = 0, cadre = 0, teacher = 0, leadership = 0;
        for (SysUser u : targets) {
            Integer lv = u.getRoleLevel();
            if (lv == null) continue;
            switch (lv) {
                case 4 -> student++;
                case 3 -> cadre++;
                case 2 -> teacher++;
                case 1 -> leadership++;
                default -> { }
            }
        }
        map.put("total", targets.size());
        map.put("student", student);
        map.put("cadre", cadre);
        map.put("teacher", teacher);
        map.put("leadership", leadership);
        return map;
    }

    /**
     * 群发: 校验 -> 查目标 -> 写广播记录 -> 批量插 sys_notification -> 异步发邮件.
     */
    @Transactional
    public BroadcastResult broadcast(BroadcastRequest req) {
        if (req == null || !StringUtils.hasText(req.getTitle()) || !StringUtils.hasText(req.getContent())) {
            throw new BusinessException("标题与内容不能为空");
        }
        List<SysUser> targets = resolveTargets(req.getFilter());
        if (targets.isEmpty()) {
            throw new BusinessException("按当前筛选条件没有匹配到任何用户");
        }
        if (targets.size() > maxTargets) {
            throw new BusinessException("目标人数 " + targets.size() + " 超过单次群发上限 " + maxTargets + ", 请收紧筛选条件");
        }

        // 渠道规范化 (system 强制)
        Set<String> chSet = new LinkedHashSet<>();
        chSet.add("system");
        if (req.getChannels() != null) {
            for (String c : req.getChannels()) {
                if (StringUtils.hasText(c)) chSet.add(c.trim().toLowerCase());
            }
        }
        String channels = String.join(",", chSet);

        // 1) 写广播任务记录
        SysNotificationBroadcast bc = new SysNotificationBroadcast();
        bc.setTitle(req.getTitle().trim());
        bc.setContent(req.getContent().trim());
        bc.setTags(normalizeTags(req.getTags()));
        bc.setSource(StringUtils.hasText(req.getSource()) ? req.getSource().trim() : null);
        bc.setSourceUrl(StringUtils.hasText(req.getSourceUrl()) ? req.getSourceUrl().trim() : null);
        bc.setChannels(channels);
        bc.setTargetCount(targets.size());
        bc.setSentCount(0);
        bc.setEmailSent(0);
        bc.setWithdrawn(false);
        bc.setOperatorId(UserContext.getUserId());
        try {
            bc.setTargetFilter(objectMapper.writeValueAsString(req.getFilter()));
        } catch (Exception ignore) {
            bc.setTargetFilter(null);
        }
        broadcastMapper.insert(bc);

        // 2) 批量写 sys_notification (一人一条, system 渠道)
        int sent = 0;
        for (SysUser u : targets) {
            SysNotification row = new SysNotification();
            row.setUserId(u.getId());
            row.setTitle(bc.getTitle());
            row.setContent(bc.getContent());
            row.setType("system");
            row.setIsRead(false);
            row.setTags(bc.getTags());
            row.setSource(bc.getSource());
            row.setSourceUrl(bc.getSourceUrl());
            row.setBroadcastId(bc.getId());
            notificationMapper.insert(row);
            sent++;
        }

        // 2.1) 如果勾了 sms_sim, 额外为每人写一条 sms_sim 类型通知(模拟短信)
        if (chSet.contains("sms_sim")) {
            for (SysUser u : targets) {
                SysNotification sms = new SysNotification();
                sms.setUserId(u.getId());
                sms.setTitle("【短信】" + bc.getTitle());
                sms.setContent(bc.getContent());
                sms.setType("sms_sim");
                sms.setIsRead(false);
                sms.setTags(bc.getTags());
                sms.setSource(bc.getSource());
                sms.setSourceUrl(bc.getSourceUrl());
                sms.setBroadcastId(bc.getId());
                notificationMapper.insert(sms);
            }
        }

        // 3) 邮件渠道: 真实发送, 同步统计成功数, 写回 broadcast.emailSent
        int emailSent = 0;
        if (chSet.contains("email")) {
            if (!emailService.isAvailable()) {
                log.info("SMTP 未配置, 邮件渠道降级为只写 email_sim 通知");
                // 为每人写一条 email_sim 占位
                for (SysUser u : targets) {
                    SysNotification em = new SysNotification();
                    em.setUserId(u.getId());
                    em.setTitle("【邮件(模拟)】" + bc.getTitle());
                    em.setContent(bc.getContent());
                    em.setType("email_sim");
                    em.setIsRead(false);
                    em.setBroadcastId(bc.getId());
                    notificationMapper.insert(em);
                }
            } else {
                for (SysUser u : targets) {
                    String addr = emailService.resolveEmail(u);
                    if (!StringUtils.hasText(addr)) continue;
                    if (emailService.sendOne(addr, bc.getTitle(), buildEmailBody(bc))) {
                        emailSent++;
                    }
                }
            }
        }

        bc.setSentCount(sent);
        bc.setEmailSent(emailSent);
        broadcastMapper.updateById(bc);

        return new BroadcastResult(bc.getId(), targets.size(), sent, emailSent);
    }

    /**
     * 24h 内撤回, 仅删除未读条目, 已读保留.
     */
    @Transactional
    public WithdrawResult withdraw(Long broadcastId) {
        SysNotificationBroadcast bc = broadcastMapper.selectById(broadcastId);
        if (bc == null) throw new BusinessException("广播任务不存在");
        if (Boolean.TRUE.equals(bc.getWithdrawn())) throw new BusinessException("该广播已撤回");

        long hours = Duration.between(bc.getCreatedAt(), LocalDateTime.now()).toHours();
        if (hours > withdrawWindowHours) {
            throw new BusinessException("超过 " + withdrawWindowHours + " 小时, 不可撤回");
        }

        // 删除该广播相关的未读通知
        int removed = notificationMapper.delete(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getBroadcastId, broadcastId)
                        .eq(SysNotification::getIsRead, false)
        );

        // 标记 broadcast 为已撤回
        broadcastMapper.update(null,
                new LambdaUpdateWrapper<SysNotificationBroadcast>()
                        .eq(SysNotificationBroadcast::getId, broadcastId)
                        .set(SysNotificationBroadcast::getWithdrawn, true)
                        .set(SysNotificationBroadcast::getWithdrawnAt, LocalDateTime.now())
        );

        return new WithdrawResult(broadcastId, removed);
    }

    public Page<SysNotificationBroadcast> getBroadcastPage(int page, int size) {
        return broadcastMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<SysNotificationBroadcast>()
                        .orderByDesc(SysNotificationBroadcast::getCreatedAt)
        );
    }

    public SysNotificationBroadcast getBroadcastDetail(Long id) {
        SysNotificationBroadcast bc = broadcastMapper.selectById(id);
        if (bc == null) throw new BusinessException("广播任务不存在");
        return bc;
    }

    /**
     * 拉取所有通知出现过的标签集合, 供前端筛选下拉.
     */
    public List<String> distinctTags() {
        List<SysNotification> rows = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .select(SysNotification::getTags)
                        .isNotNull(SysNotification::getTags)
        );
        Set<String> tags = new LinkedHashSet<>();
        for (SysNotification r : rows) {
            if (StringUtils.hasText(r.getTags())) {
                Arrays.stream(r.getTags().split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .forEach(tags::add);
            }
        }
        return new ArrayList<>(tags);
    }

    private static String normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static String buildEmailBody(SysNotificationBroadcast bc) {
        StringBuilder sb = new StringBuilder();
        sb.append(bc.getContent()).append("\n\n");
        if (StringUtils.hasText(bc.getSource())) {
            sb.append("来源: ").append(bc.getSource()).append("\n");
        }
        if (StringUtils.hasText(bc.getSourceUrl())) {
            sb.append("原文链接: ").append(bc.getSourceUrl()).append("\n");
        }
        sb.append("\n--\n中国人民大学信息学院学生综合服务平台");
        return sb.toString();
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty()
                && list.stream().anyMatch(StringUtils::hasText);
    }

    // ===== DTOs =====

    @Data
    public static class BroadcastFilter {
        /** 目标角色 (可多选): 4=普通学生, 3=学生骨干, 2=老师, 1=院领导. 为空时默认 [4]. */
        private List<Integer> roles;
        /** @deprecated 旧的单选字段, 仅作向后兼容, 新前端用 roles. */
        @Deprecated
        private Integer roleLevel;
        private List<String> grades;
        private List<String> majors;
        private List<String> classNames;
    }

    @Data
    public static class BroadcastRequest {
        private String title;
        private String content;
        private List<String> tags;
        private String source;
        private String sourceUrl;
        private List<String> channels;
        private BroadcastFilter filter;
    }

    @Data
    public static class BroadcastResult {
        private final Long broadcastId;
        private final int targetCount;
        private final int sentCount;
        private final int emailSent;
    }

    @Data
    public static class WithdrawResult {
        private final Long broadcastId;
        private final int removedCount;
    }
}
