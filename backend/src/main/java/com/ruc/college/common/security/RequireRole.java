package com.ruc.college.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解
 * minLevel: 最低角色等级 (1=院领导, 2=管理老师, 3=班团骨干, 4=普通学生)
 * 数字越小权限越高
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    int minLevel() default 4;
}
