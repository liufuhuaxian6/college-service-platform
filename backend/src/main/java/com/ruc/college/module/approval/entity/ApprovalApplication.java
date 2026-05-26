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

    // ====== 以下字段非数据库列, 仅用于响应返回时回填关联信息 ======
    /** 审批类型名称, 来自 approval_type.name */
    @TableField(exist = false)
    private String typeName;
    /** 申请人姓名, 来自 sys_user.name */
    @TableField(exist = false)
    private String userName;
    /** 申请人学号, 来自 sys_user.student_id */
    @TableField(exist = false)
    private String studentId;
}
