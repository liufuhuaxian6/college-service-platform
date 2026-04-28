package com.ruc.college.module.approval.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("approval_type")
public class ApprovalType {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String approvalChain;
    private String templatePath;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
