package com.ruc.college.common.enums;

import lombok.Getter;

@Getter
public enum RoleLevel {

    LEADER(1, "院领导"),
    TEACHER(2, "管理老师/辅导员"),
    CADRE(3, "班团骨干"),
    STUDENT(4, "普通学生");

    private final int level;
    private final String description;

    RoleLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public static RoleLevel fromLevel(int level) {
        for (RoleLevel role : values()) {
            if (role.level == level) {
                return role;
            }
        }
        throw new IllegalArgumentException("Invalid role level: " + level);
    }
}
