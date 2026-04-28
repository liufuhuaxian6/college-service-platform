package com.ruc.college.module.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("student_honor")
public class StudentHonor {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String honorName;
    private String honorLevel;
    private LocalDate awardDate;
    private String certFile;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
