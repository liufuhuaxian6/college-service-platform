package com.ruc.college.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_notification")
public class SysNotification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    /** system / sms_sim / reminder / email_sim */
    private String type;
    private Boolean isRead;

    /** 逗号分隔, 例如 "就业,实习" */
    private String tags;
    /** 来源: 后勤处 / 保卫处 / 就业办 / 学院 / 其他 */
    private String source;
    /** 公众号 / 原文链接 */
    private String sourceUrl;
    /** 所属广播任务 id, NULL=单条直发 */
    private Long broadcastId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
