package com.ruc.college.module.approval.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long approverId;
    private Integer approverLevel;
    private String action;
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
