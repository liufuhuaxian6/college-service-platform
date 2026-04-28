package com.ruc.college.module.party.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("party_process_template")
public class PartyProcessTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Integer totalSteps;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
