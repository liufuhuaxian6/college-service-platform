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
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
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
        Page<ApprovalApplication> result = applicationMapper.selectPage(new Page<>(page, size), wrapper);
        enrichApplicationList(result.getRecords());
        return result;
    }

    public ApprovalApplication getApplicationDetail(Long id) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        // 学生只能看自己的
        if (UserContext.getRoleLevel() == 4 && !app.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权查看他人申请");
        }
        enrichApplicationList(List.of(app));
        return app;
    }

    /**
     * 批量回填 typeName / userName / studentId 关联字段, 避免前端只看到裸的外键 ID.
     * 一次性 selectBatchIds 类型表和用户表, 避免 N+1.
     */
    private void enrichApplicationList(List<ApprovalApplication> apps) {
        if (apps == null || apps.isEmpty()) return;

        Set<Long> typeIds = apps.stream().map(ApprovalApplication::getTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = apps.stream().map(ApprovalApplication::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, ApprovalType> typeMap = typeIds.isEmpty() ? Map.of()
                : typeMapper.selectBatchIds(typeIds).stream()
                        .collect(Collectors.toMap(ApprovalType::getId, Function.identity(), (a, b) -> a));
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        for (ApprovalApplication a : apps) {
            ApprovalType t = typeMap.get(a.getTypeId());
            if (t != null) a.setTypeName(t.getName());
            SysUser u = userMap.get(a.getUserId());
            if (u != null) {
                a.setUserName(u.getName());
                a.setStudentId(u.getStudentId());
            }
        }
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
        Page<ApprovalApplication> result = applicationMapper.selectPage(new Page<>(page, size), wrapper);
        enrichApplicationList(result.getRecords());
        return result;
    }

    public Page<ApprovalApplication> getAllPage(int page, int size, String status, Long userId) {
        LambdaQueryWrapper<ApprovalApplication> wrapper = new LambdaQueryWrapper<ApprovalApplication>()
                .eq(status != null, ApprovalApplication::getStatus, status)
                .eq(userId != null, ApprovalApplication::getUserId, userId)
                .orderByDesc(ApprovalApplication::getCreatedAt);
        Page<ApprovalApplication> result = applicationMapper.selectPage(new Page<>(page, size), wrapper);
        enrichApplicationList(result.getRecords());
        return result;
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

    /** 系统中文 TTF/TTC 路径缓存: -1=未查找, null=找不到, 否则=路径 */
    private static volatile File CJK_FONT_FILE;
    private static volatile boolean CJK_FONT_LOOKED_UP = false;

    /** 在常见 OS 字体目录里查找一份可用的中文 TTF/OTF, 找到第一份就返回. */
    private static File findCjkFont() {
        if (CJK_FONT_LOOKED_UP) return CJK_FONT_FILE;
        synchronized (ApprovalService.class) {
            if (CJK_FONT_LOOKED_UP) return CJK_FONT_FILE;
            String[] candidates = {
                    // Windows
                    "C:/Windows/Fonts/msyh.ttc",       // 微软雅黑
                    "C:/Windows/Fonts/simsun.ttc",     // 宋体
                    "C:/Windows/Fonts/simhei.ttf",     // 黑体
                    // Linux (Ubuntu/Debian 安装 fonts-noto-cjk / fonts-wqy 后)
                    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                    "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                    "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                    "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc",
                    // macOS
                    "/System/Library/Fonts/PingFang.ttc",
                    "/System/Library/Fonts/STHeiti Light.ttc",
                    "/Library/Fonts/Arial Unicode.ttf"
            };
            for (String path : candidates) {
                File f = new File(path);
                if (f.exists() && f.isFile() && f.length() > 0) {
                    CJK_FONT_FILE = f;
                    break;
                }
            }
            CJK_FONT_LOOKED_UP = true;
            if (CJK_FONT_FILE == null) {
                log.warn("未在常见路径找到中文字体, PDF 证明将退化为 ASCII (中文显示为 ?). 服务器请安装 fonts-noto-cjk");
            } else {
                log.info("PDF 证明使用中文字体: {}", CJK_FONT_FILE.getAbsolutePath());
            }
            return CJK_FONT_FILE;
        }
    }

    /**
     * 用 PDFBox 渲染证明 PDF. 找到中文 TTF 时嵌入子集真正显示中文; 否则用 Helvetica 退化为 ASCII.
     */
    private static byte[] buildSimplePdfBytes(List<String> lines) {
        if (lines == null || lines.isEmpty()) return new byte[0];
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            File cjkFile = findCjkFont();
            PDFont titleFont;
            PDFont bodyFont;
            boolean usingCjk = false;
            if (cjkFile != null) {
                try {
                    // embedSubset=true: 仅嵌入用到的字形, 输出小且不侵权
                    PDFont cjk = loadCjkFont(doc, cjkFile);
                    titleFont = cjk;
                    bodyFont = cjk;
                    usingCjk = true;
                } catch (Exception ex) {
                    log.warn("加载中文字体失败 ({}), 退化为 Helvetica: {}",
                            cjkFile.getName(), ex.toString());
                    titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                    bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                }
            } else {
                titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            }

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 780f;
                // 标题
                cs.beginText();
                cs.setFont(titleFont, 20);
                cs.newLineAtOffset(60, y);
                cs.showText(safePdfText(lines.get(0), usingCjk));
                cs.endText();
                y -= 36;

                // 副信息正文
                for (int i = 1; i < lines.size(); i++) {
                    cs.beginText();
                    cs.setFont(bodyFont, 13);
                    cs.newLineAtOffset(60, y);
                    cs.showText(safePdfText(lines.get(i), usingCjk));
                    cs.endText();
                    y -= 24;
                }

                // 落款
                cs.beginText();
                cs.setFont(bodyFont, 12);
                cs.newLineAtOffset(60, 100);
                cs.showText(safePdfText("中国人民大学信息学院 (盖章)", usingCjk));
                cs.endText();
            }

            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDFBox 生成证明 PDF 失败", e);
            return new byte[0];
        }
    }

    /**
     * 加载 TTF / OTF / TTC 中文字体. TTC 是字体集合, PDType0Font.load(File) 无法直接处理,
     * 需要先通过 TrueTypeCollection 取出第一份单字体再 load.
     */
    private static PDFont loadCjkFont(PDDocument doc, File file) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".ttc")) {
            try (TrueTypeCollection ttc = new TrueTypeCollection(file)) {
                final PDFont[] result = new PDFont[1];
                final Exception[] err = new Exception[1];
                ttc.processAllFonts(ttf -> {
                    if (result[0] != null) return;
                    try {
                        result[0] = PDType0Font.load(doc, ttf, true);
                    } catch (Exception ex) {
                        err[0] = ex;
                    }
                });
                if (result[0] != null) return result[0];
                throw err[0] != null ? err[0] : new RuntimeException("TTC 内无可用字体");
            }
        }
        // 单字体 (.ttf / .otf)
        return PDType0Font.load(doc, file);
    }

    /** 没有 CJK 字体时把非 ASCII 字符替换成 ?, 防止 Helvetica 抛 IllegalArgumentException. */
    private static String safePdfText(String s, boolean cjkAvailable) {
        if (s == null) return "";
        if (cjkAvailable) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c < 0x80 ? c : '?');
        }
        return sb.toString();
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
