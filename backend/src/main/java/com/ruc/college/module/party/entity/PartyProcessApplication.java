package com.ruc.college.module.party.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("party_process_application")
public class PartyProcessApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appNo;
    private Long userId;
    private Long templateId;
    private String reason;
    private String status;
    private Long reviewerId;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private Long instanceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String templateName;

    @TableField(exist = false)
    private String userName;

    @TableField(exist = false)
    private String studentId;

    @TableField(exist = false)
    private String reviewerName;
}
