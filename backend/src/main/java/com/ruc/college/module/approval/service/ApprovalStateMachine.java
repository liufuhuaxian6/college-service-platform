package com.ruc.college.module.approval.service;

import com.ruc.college.common.enums.ApprovalStatus;
import com.ruc.college.common.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 审批状态机
 *
 * 状态流转:
 * draft → pending → approved → downloaded (锁定)
 *                 → rejected
 *         approved → withdrawn (1-2天内且未下载)
 * pending → withdrawn (学生撤回)
 */
public class ApprovalStateMachine {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            ApprovalStatus.DRAFT.getCode(), Set.of(ApprovalStatus.PENDING.getCode()),
            ApprovalStatus.PENDING.getCode(), Set.of(
                    ApprovalStatus.APPROVED.getCode(),
                    ApprovalStatus.REJECTED.getCode(),
                    ApprovalStatus.WITHDRAWN.getCode()
            ),
            ApprovalStatus.APPROVED.getCode(), Set.of(
                    ApprovalStatus.DOWNLOADED.getCode(),
                    ApprovalStatus.WITHDRAWN.getCode()
            ),
            ApprovalStatus.REJECTED.getCode(), Set.of(ApprovalStatus.DRAFT.getCode()),
            ApprovalStatus.WITHDRAWN.getCode(), Set.of(ApprovalStatus.DRAFT.getCode())
    );

    /**
     * 校验状态流转是否合法
     */
    public static void validateTransition(String from, String to) {
        Set<String> allowedTargets = TRANSITIONS.get(from);
        if (allowedTargets == null || !allowedTargets.contains(to)) {
            throw new BusinessException("不允许从状态[" + from + "]变更为[" + to + "]");
        }
    }

    /**
     * 校验撤回操作是否合法
     * approved → withdrawn: 必须在截止时间内且未下载
     */
    public static void validateWithdraw(String currentStatus,
                                         LocalDateTime withdrawDeadline,
                                         LocalDateTime downloadedAt) {
        if (ApprovalStatus.DOWNLOADED.getCode().equals(currentStatus)) {
            throw new BusinessException("证明已下载锁定，严禁撤回操作");
        }

        if (ApprovalStatus.APPROVED.getCode().equals(currentStatus)) {
            if (downloadedAt != null) {
                throw new BusinessException("证明已下载，无法撤回");
            }
            if (withdrawDeadline != null && LocalDateTime.now().isAfter(withdrawDeadline)) {
                throw new BusinessException("已超过撤回期限，无法撤回");
            }
        }

        validateTransition(currentStatus, ApprovalStatus.WITHDRAWN.getCode());
    }

    /**
     * 校验下载操作：只有 approved 状态才能下载
     */
    public static void validateDownload(String currentStatus) {
        if (!ApprovalStatus.APPROVED.getCode().equals(currentStatus)) {
            throw new BusinessException("当前状态不允许下载证明");
        }
    }
}
