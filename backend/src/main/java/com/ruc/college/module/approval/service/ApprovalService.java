package com.ruc.college.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.enums.ApprovalStatus;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.module.approval.entity.*;
import com.ruc.college.module.approval.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalApplicationMapper applicationMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalTypeMapper typeMapper;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 10000);

    // ==================== 学生端 ====================

    public List<ApprovalType> getApprovalTypes() {
        return typeMapper.selectList(
                new LambdaQueryWrapper<ApprovalType>().eq(ApprovalType::getStatus, 1)
        );
    }

    @Transactional
    public ApprovalApplication apply(Long typeId, java.util.Map<String, Object> formData) {
        ApprovalType type = typeMapper.selectById(typeId);
        if (type == null) throw new BusinessException("审批类型不存在");

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

    // ==================== 管理端 ====================

    public Page<ApprovalApplication> getPendingPage(int page, int size, Long typeId) {
        LambdaQueryWrapper<ApprovalApplication> wrapper = new LambdaQueryWrapper<ApprovalApplication>()
                .eq(ApprovalApplication::getStatus, ApprovalStatus.PENDING.getCode())
                .eq(typeId != null, ApprovalApplication::getTypeId, typeId)
                .le(ApprovalApplication::getCurrentApproverLevel, UserContext.getRoleLevel())
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
        ApprovalStateMachine.validateTransition(app.getStatus(), ApprovalStatus.APPROVED.getCode());

        ApprovalType type = typeMapper.selectById(app.getTypeId());
        String[] chain = type.getApprovalChain().split(",");

        // 检查是否还有下一级审批
        int currentIndex = -1;
        for (int i = 0; i < chain.length; i++) {
            if (Integer.parseInt(chain[i].trim()) == app.getCurrentApproverLevel()) {
                currentIndex = i;
                break;
            }
        }

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
    }

    @Transactional
    public void reject(Long id, String comment) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        ApprovalStateMachine.validateTransition(app.getStatus(), ApprovalStatus.REJECTED.getCode());

        app.setStatus(ApprovalStatus.REJECTED.getCode());
        applicationMapper.updateById(app);

        insertRecord(id, "reject", comment);
    }

    @Transactional
    public void adminWithdraw(Long id, String comment) {
        ApprovalApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("申请不存在");
        ApprovalStateMachine.validateWithdraw(app.getStatus(), app.getWithdrawDeadline(), app.getDownloadedAt());

        app.setStatus(ApprovalStatus.WITHDRAWN.getCode());
        applicationMapper.updateById(app);

        insertRecord(id, "withdraw", "管理员撤回: " + comment);
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
}
