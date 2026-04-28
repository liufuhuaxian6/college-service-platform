package com.ruc.college.common.security;

import lombok.Data;

/**
 * 当前登录用户信息（存放在 ThreadLocal 中）
 */
@Data
public class LoginUser {

    private Long userId;
    private String studentId;
    private int roleLevel;
    private String name;
}
