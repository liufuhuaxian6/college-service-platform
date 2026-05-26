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
    /** 模板申请: qa_document.id (doc_type='template'), 通过后用此模板生成 PDF */
    private Long templateDocId;

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
    /** 审批类型名称, 来自 approval_type.name (旧申请) */
    @TableField(exist = false)
    private String typeName;
    /** 模板名称, 来自 qa_document.title (新模板申请) */
    @TableField(exist = false)
    private String templateName;
    /** 申请人姓名, 来自 sys_user.name */
    @TableField(exist = false)
    private String userName;
    /** 申请人学号, 来自 sys_user.student_id */
    @TableField(exist = false)
    private String studentId;
}
