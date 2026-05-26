package com.ruc.college.module.system.service;

import com.ruc.college.module.auth.entity.SysUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 邮件服务: 走 SMTP 发送真实邮件; 配置缺失或调用异常时降级为只写日志, 不阻塞业务流程.
 *
 * <p>邮箱派生规则: 优先取 sys_user.email; 为空时使用 学号@${notify.email.default-domain:ruc.edu.cn}.</p>
 */
@Service
@Slf4j
public class EmailService {

    /** 可能为 null: 当 spring.mail.* 没配齐时 Spring 不会装配该 bean */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${notify.email.default-domain:ruc.edu.cn}")
    private String defaultDomain;

    @Value("${notify.email.batch-size:50}")
    private int batchSize;

    /**
     * 派生用户邮箱: 优先用户填写, 否则默认 学号@ruc.edu.cn.
     */
    public String resolveEmail(SysUser user) {
        if (user == null) return null;
        if (StringUtils.hasText(user.getEmail())) {
            return user.getEmail().trim();
        }
        if (!StringUtils.hasText(user.getStudentId())) return null;
        return user.getStudentId() + "@" + defaultDomain;
    }

    /**
     * 邮件渠道是否真实可用 (依赖 spring.mail.host 是否配置 + JavaMailSender bean 是否装配成功).
     */
    public boolean isAvailable() {
        return mailSender != null && StringUtils.hasText(fromAddress);
    }

    /**
     * 异步批量发送邮件. 不影响主流程; 单封失败仅记 warn 日志, 不抛出.
     * @return 成功发送的封数 (因为 @Async 立即返回, 这里同步统计仅供单元测试用; 生产请通过持久化 broadcast.emailSent 看实际值)
     */
    @Async
    public int sendBatch(List<SysUser> users, String subject, String body) {
        if (!isAvailable()) {
            log.info("EmailService 未配置 SMTP, 跳过 {} 个收件人的邮件发送 (主题: {})",
                    users == null ? 0 : users.size(), subject);
            return 0;
        }
        if (users == null || users.isEmpty()) return 0;

        int success = 0;
        int sentInBatch = 0;
        for (SysUser u : users) {
            String addr = resolveEmail(u);
            if (!StringUtils.hasText(addr)) continue;
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(addr);
                msg.setSubject(subject == null ? "" : subject);
                msg.setText(body == null ? "" : body);
                mailSender.send(msg);
                success++;
            } catch (Exception e) {
                log.warn("发送邮件失败 to={} err={}", addr, e.getMessage());
            }
            // 简单节流: 每 batchSize 封小睡 100ms, 避免被 SMTP 限流
            if (++sentInBatch >= batchSize) {
                sentInBatch = 0;
                try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        log.info("邮件批量发送结束: {}/{} 成功 (主题: {})", success, users.size(), subject);
        return success;
    }

    /**
     * 同步发送(单封). 失败时返回 false. 用于群发任务里精确统计 emailSent 数.
     */
    public boolean sendOne(String to, String subject, String body) {
        if (!isAvailable()) return false;
        if (!StringUtils.hasText(to)) return false;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject == null ? "" : subject);
            msg.setText(body == null ? "" : body);
            mailSender.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("发送邮件失败 to={} err={}", to, e.getMessage());
            return false;
        }
    }
}
