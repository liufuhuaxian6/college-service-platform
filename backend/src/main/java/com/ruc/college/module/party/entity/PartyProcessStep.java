package com.ruc.college.module.party.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("party_process_step")
public class PartyProcessStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Integer stepOrder;
    private String name;
    private String description;
    private Integer durationDays;
    private String requiredMaterials;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
