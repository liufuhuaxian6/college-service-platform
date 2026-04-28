package com.ruc.college.module.party.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("party_process_instance")
public class PartyProcessInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long templateId;
    private Integer currentStep;
    private LocalDate startDate;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
