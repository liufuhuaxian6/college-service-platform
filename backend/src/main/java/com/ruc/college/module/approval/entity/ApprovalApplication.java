package com.ruc.college.module.approval.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ruc.college.common.mybatis.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "approval_application", autoResultMap = true)
public class ApprovalApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appNo;
    private Long userId;
    private Long typeId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> formData;

    private String status;
    private Integer currentApproverLevel;
    private String certFilePath;
    private LocalDateTime downloadedAt;
    private LocalDateTime withdrawDeadline;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
