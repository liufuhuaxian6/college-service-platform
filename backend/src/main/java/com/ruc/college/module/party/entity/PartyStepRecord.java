package com.ruc.college.module.party.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("party_step_record")
public class PartyStepRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long stepId;
    private LocalDateTime completedAt;
    private String remark;
    private Long operatorId;
}
