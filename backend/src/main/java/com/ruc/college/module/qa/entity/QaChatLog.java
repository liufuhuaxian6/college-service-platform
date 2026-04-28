package com.ruc.college.module.qa.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("qa_chat_log")
public class QaChatLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String question;
    private String answer;
    private String sourceType;
    private Boolean matched;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
