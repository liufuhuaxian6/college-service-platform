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
    private String filePath;
    private Long fileSize;
    private String fileType;
    private Integer downloadCount;
    private Integer status;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
