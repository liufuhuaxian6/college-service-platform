package com.ruc.college.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.enums.ApprovalStatus;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.module.approval.entity.*;
import com.ruc.college.module.approval.mapper.*;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.mapper.SysUserMapper;
import com.ruc.college.module.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalApplicationMapper applicationMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalTypeMapper typeMapper;
    private final SysUserMapper userMapper;
    private final SystemService systemService;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 10000);

    // ==================== 学生端 ====================

    public List<ApprovalType> getApprovalTypes() {
        return typeMapper.selectList(
                new LambdaQueryWrapper<ApprovalType>().eq(ApprovalType::getStatus, 1)
        );
    }

    @Transactional
    public ApprovalApplication apply(Long typeId, java.util.Map<String, Object> formData) {
        if (typeId == null) throw new BusinessException("审批类型不能为空");
        ApprovalType type = typeMapper.selectById(typeId);
        if (type == null) throw new BusinessException("审批类型不存在");
        if (!StringUtils.hasText(type.getApprovalChain())) throw new BusinessException("审批链配置为空");

        ApprovalApplication app = new ApprovalApplication();
        app.setAppNo(generateAppNo());
        app.setUserId(UserContext.getUserId());
        app.setTypeId(typeId);
        app.setFormData(formData);
        app.setStatus(ApprovalStatus.PENDING.getCode());

        // 设置第一级审批人角色
        String[] chain = type.getApprovalChain().split(",");
        app.setCurrentApproverLevel(Integer.parseInt(chain[0].trim()));

        applicationMapper.insert(app);

        systemService.sendNotification(
                app.getUserId(),
                "申请已提交",
                "你的申请已提交，编号: " + app.getAppNo() + "。请等待审批结果。",
                "system"
        );
        return app;
    }

    public Page<ApprovalApplication> getMyApplications(int page, int size, String status) {
        LambdaQueryWrapper<ApprovalApplication> wrapper = new LambdaQueryWrapper<ApprovalApplication>()
                .eq(ApprovalApplication::getUserId, UserContext.getUserId())
                .eq(status != null, ApprovalApplication::getStatus, status)
                .orderByDesc(ApprovalApplication::getCreatedAt);
        return applicationMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public ApprovalApplication getApplicationDetail(Long id) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        // 学生只能看自己的
        if (UserContext.getRoleLevel() == 4 && !app.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权查看他人申请");
        }
        return app;
    }

    public List<ApprovalRecord> getApprovalRecords(Long applicationId) {
        return recordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getApplicationId, applicationId)
                        .orderByAsc(ApprovalRecord::getCreatedAt)
        );
    }

    /**
     * 学生撤回申请
     */
    @Transactional
    public void withdraw(Long id) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!app.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "只能撤回自己的申请");
        }
        ApprovalStateMachine.validateWithdraw(app.getStatus(), app.getWithdrawDeadline(), app.getDownloadedAt());

        app.setStatus(ApprovalStatus.WITHDRAWN.getCode());
        applicationMapper.updateById(app);

        // 记录撤回操作
        insertRecord(id, "withdraw", "学生主动撤回");

        systemService.sendNotification(
                app.getUserId(),
                "申请已撤回",
                "你的申请已撤回，编号: " + app.getAppNo() + "。",
                "system"
        );
    }

    /**
     * 学生下载证明 → 触发锁定!
     */
    @Transactional
    public ApprovalApplication downloadCert(Long id) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!app.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "只能下载自己的证明");
        }
        ApprovalStateMachine.validateDownload(app.getStatus());

        // 锁定状态!
        app.setStatus(ApprovalStatus.DOWNLOADED.getCode());
        app.setDownloadedAt(LocalDateTime.now());
        applicationMapper.updateById(app);

        return app;
    }

    @Transactional
    public ResponseEntity<Resource> downloadCertFile(Long id) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!app.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "只能下载自己的证明");
        }
        ApprovalStateMachine.validateDownload(app.getStatus());

        ApprovalType type = typeMapper.selectById(app.getTypeId());
        if (type == null) throw new BusinessException("审批类型不存在");
        SysUser user = userMapper.selectById(app.getUserId());
        if (user == null) throw new BusinessException("用户不存在");

        String relativePath = ensureCertFile(app, type, user);
        File file = new File(System.getProperty("user.dir"), relativePath);
        if (!file.exists() || !file.isFile()) throw new BusinessException("证明文件不存在");

        app.setCertFilePath(relativePath);
        app.setStatus(ApprovalStatus.DOWNLOADED.getCode());
        app.setDownloadedAt(LocalDateTime.now());
        applicationMapper.updateById(app);

        Resource resource = new FileSystemResource(file);
        String encodedName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    // ==================== 管理端 ====================

    public Page<ApprovalApplication> getPendingPage(int page, int size, Long typeId) {
        int roleLevel = UserContext.getRoleLevel();
        LambdaQueryWrapper<ApprovalApplication> wrapper = new LambdaQueryWrapper<ApprovalApplication>()
                .eq(ApprovalApplication::getStatus, ApprovalStatus.PENDING.getCode())
                .eq(typeId != null, ApprovalApplication::getTypeId, typeId)
                .eq(roleLevel != 1, ApprovalApplication::getCurrentApproverLevel, roleLevel)
                .orderByAsc(ApprovalApplication::getCreatedAt);
        return applicationMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Page<ApprovalApplication> getAllPage(int page, int size, String status, Long userId) {
        LambdaQueryWrapper<ApprovalApplication> wrapper = new LambdaQueryWrapper<ApprovalApplication>()
                .eq(status != null, ApprovalApplication::getStatus, status)
                .eq(userId != null, ApprovalApplication::getUserId, userId)
                .orderByDesc(ApprovalApplication::getCreatedAt);
        return applicationMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Transactional
    public void approve(Long id, String comment) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!ApprovalStatus.PENDING.getCode().equals(app.getStatus())) throw new BusinessException("当前状态不允许审批");
        assertApproverPermission(app);

        ApprovalType type = typeMapper.selectById(app.getTypeId());
        if (type == null) throw new BusinessException("审批类型不存在");
        if (!StringUtils.hasText(type.getApprovalChain())) throw new BusinessException("审批链配置为空");
        String[] chain = type.getApprovalChain().split(",");

        // 检查是否还有下一级审批
        int currentIndex = -1;
        for (int i = 0; i < chain.length; i++) {
            if (Integer.parseInt(chain[i].trim()) == app.getCurrentApproverLevel()) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) throw new BusinessException("审批链配置与当前审批层级不匹配");

        if (currentIndex < chain.length - 1) {
            // 还有下一级
            app.setCurrentApproverLevel(Integer.parseInt(chain[currentIndex + 1].trim()));
            // 状态保持 pending
        } else {
            // 最后一级审批通过
            app.setStatus(ApprovalStatus.APPROVED.getCode());
            app.setWithdrawDeadline(LocalDateTime.now().plusDays(2));
        }
        applicationMapper.updateById(app);

        insertRecord(id, "approve", comment);

        if (ApprovalStatus.APPROVED.getCode().equals(app.getStatus())) {
            systemService.sendNotification(
                    app.getUserId(),
                    "申请已通过",
                    "你的申请已通过，编号: " + app.getAppNo() + "。你可以在小程序中下载证明。",
                    "system"
            );
        } else {
            systemService.sendNotification(
                    app.getUserId(),
                    "申请已进入下一步审批",
                    "你的申请已通过当前审批，编号: " + app.getAppNo() + "。请等待下一步审批结果。",
                    "system"
            );
        }
    }

    @Transactional
    public void reject(Long id, String comment) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!ApprovalStatus.PENDING.getCode().equals(app.getStatus())) throw new BusinessException("当前状态不允许审批");
        assertApproverPermission(app);
        if (!StringUtils.hasText(comment)) throw new BusinessException("驳回原因不能为空");

        app.setStatus(ApprovalStatus.REJECTED.getCode());
        applicationMapper.updateById(app);

        insertRecord(id, "reject", comment);

        systemService.sendNotification(
                app.getUserId(),
                "申请被驳回",
                "你的申请被驳回，编号: " + app.getAppNo() + "。原因: " + comment,
                "system"
        );
    }

    @Transactional
    public void adminWithdraw(Long id, String comment) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        if (!ApprovalStatus.APPROVED.getCode().equals(app.getStatus())) throw new BusinessException("仅已通过的申请允许管理员撤回");
        ApprovalStateMachine.validateWithdraw(app.getStatus(), app.getWithdrawDeadline(), app.getDownloadedAt());

        app.setStatus(ApprovalStatus.WITHDRAWN.getCode());
        applicationMapper.updateById(app);

        insertRecord(id, "withdraw", "管理员撤回: " + comment);

        systemService.sendNotification(
                app.getUserId(),
                "申请被管理员撤回",
                "你的申请已被管理员撤回，编号: " + app.getAppNo() + "。",
                "system"
        );
    }

    // ==================== 辅助方法 ====================

    private void insertRecord(Long applicationId, String action, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setApplicationId(applicationId);
        record.setApproverId(UserContext.getUserId());
        record.setApproverLevel(UserContext.getRoleLevel());
        record.setAction(action);
        record.setComment(comment);
        recordMapper.insert(record);
    }

    private String generateAppNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "CERT-" + date + "-" + String.format("%04d", SEQ.incrementAndGet() % 10000);
    }

    private void assertApproverPermission(ApprovalApplication app) {
        if (app.getCurrentApproverLevel() == null) throw new BusinessException("当前审批层级为空");
        int roleLevel = UserContext.getRoleLevel();
        if (roleLevel != 1 && roleLevel != app.getCurrentApproverLevel()) {
            throw new BusinessException(403, "无权审批该申请");
        }
    }

    private String ensureCertFile(ApprovalApplication app, ApprovalType type, SysUser user) {
        String normalizedUploadPath = normalizeRelativePath(uploadPath);
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileName = app.getAppNo() + ".pdf";
        String relativePath = normalizedUploadPath + "/certs/" + dateDir + "/" + fileName;

        File file = new File(System.getProperty("user.dir"), relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (file.exists() && file.isFile()) {
            return relativePath;
        }

        byte[] pdf = buildSimplePdfBytes(buildPdfLines(app, type, user));
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(pdf);
        } catch (Exception e) {
            throw new BusinessException("生成证明文件失败");
        }
        return relativePath;
    }

    private static List<String> buildPdfLines(ApprovalApplication app, ApprovalType type, SysUser user) {
        List<String> lines = new ArrayList<>();
        lines.add("学院学生综合服务平台 - 电子证明");
        lines.add("申请编号: " + safe(app.getAppNo()));
        lines.add("证明类型: " + safe(type.getName()));
        lines.add("学号: " + safe(user.getStudentId()));
        lines.add("姓名: " + safe(user.getName()));
        lines.add("生成日期: " + LocalDate.now());
        return lines;
    }

    private static byte[] buildSimplePdfBytes(List<String> lines) {
        try {
            String content = buildPdfContent(lines);
            byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();

            writeAscii(out, "%PDF-1.4\n");

            offsets.add(out.size());
            writeAscii(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            offsets.add(out.size());
            writeAscii(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

            offsets.add(out.size());
            writeAscii(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] ");
            writeAscii(out, "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");

            offsets.add(out.size());
            writeAscii(out, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

            offsets.add(out.size());
            writeAscii(out, "5 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            out.write(contentBytes);
            writeAscii(out, "\nendstream\nendobj\n");

            int xrefStart = out.size();
            writeAscii(out, "xref\n0 6\n");
            writeAscii(out, "0000000000 65535 f \n");
            for (int i = 0; i < offsets.size(); i++) {
                writeAscii(out, String.format("%010d 00000 n \n", offsets.get(i)));
            }
            writeAscii(out, "trailer\n<< /Size 6 /Root 1 0 R >>\n");
            writeAscii(out, "startxref\n" + xrefStart + "\n%%EOF\n");

            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static String buildPdfContent(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("BT\n");
        sb.append("/F1 18 Tf\n");
        sb.append("50 800 Td\n");
        if (lines != null && !lines.isEmpty()) {
            sb.append("(").append(escapePdfText(lines.get(0))).append(") Tj\n");
            sb.append("0 -30 Td\n");
            sb.append("/F1 12 Tf\n");
            for (int i = 1; i < lines.size(); i++) {
                sb.append("(").append(escapePdfText(lines.get(i))).append(") Tj\n");
                sb.append("0 -18 Td\n");
            }
        }
        sb.append("ET");
        return sb.toString();
    }

    private static String escapePdfText(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) throws Exception {
        out.write(s.getBytes(StandardCharsets.US_ASCII));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeRelativePath(String raw) {
        String value = StringUtils.hasText(raw) ? raw.trim() : "uploads";
        value = value.replace("\\", "/");
        if (value.startsWith("./")) {
            value = value.substring(2);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!StringUtils.hasText(value)) {
            value = "uploads";
        }
        return value;
    }
}
