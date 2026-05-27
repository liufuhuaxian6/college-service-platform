package com.ruc.college.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员群发任务记录, 用于审计 + 24h 内撤回.
 */
@Data
@TableName("sys_notification_broadcast")
public class SysNotificationBroadcast {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String content;
    /** 逗号分隔标签 */
    private String tags;
    /** 来源(后勤处/就业办/...) */
    private String source;
    /** 原文链接 */
    private String sourceUrl;

    /** 筛选条件 JSON: 序列化的 BroadcastFilter (年级/专业/班级/角色) */
    private String targetFilter;

    /** 发送渠道: system,email,sms_sim */
    private String channels;

    /** 实际匹配到的目标人数 */
    private Integer targetCount;
    /** 实际写入 sys_notification 的条数 */
    private Integer sentCount;
    /** 邮件实际成功发送数量 */
    private Integer emailSent;

    private Long operatorId;

    private Boolean withdrawn;
    private LocalDateTime withdrawnAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
