package com.ruc.college.module.qa.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("qa_document")
public class QaDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String category;
    /** 文档类型: policy=政策文件, template=办公模板 */
    private String docType;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private Integer downloadCount;
    private Integer status;
    private Long createdBy;
    /** 适用范围 / 填写说明（主要用于模板） */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
