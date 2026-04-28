package com.ruc.college.common.security;

/**
 * 用户上下文，基于 ThreadLocal 存储当前登录用户
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        CURRENT_USER.set(user);
    }

    public static LoginUser get() {
        return CURRENT_USER.get();
    }

    public static Long getUserId() {
        LoginUser user = get();
        return user != null ? user.getUserId() : null;
    }

    public static int getRoleLevel() {
        LoginUser user = get();
        return user != null ? user.getRoleLevel() : 4;
    }

    public static void remove() {
        CURRENT_USER.remove();
    }
}
