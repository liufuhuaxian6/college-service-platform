package com.ruc.college.common.enums;

import lombok.Getter;

@Getter
public enum ApprovalStatus {

    DRAFT("draft", "草稿"),
    PENDING("pending", "待审批"),
    APPROVED("approved", "已通过"),
    REJECTED("rejected", "已驳回"),
    WITHDRAWN("withdrawn", "已撤回"),
    DOWNLOADED("downloaded", "已下载/已锁定");

    private final String code;
    private final String description;

    ApprovalStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ApprovalStatus fromCode(String code) {
        for (ApprovalStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid approval status: " + code);
    }
}
