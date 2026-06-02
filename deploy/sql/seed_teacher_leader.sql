-- ==================== 老师 / 骨干 种子数据 ====================
-- 用途: 群发通知模块需要老师 (role_level=2) 和班团骨干 (role_level=3) 才能演示 "全部 (含老师/骨干)" 模式
-- 默认密码均为: admin123 (BCrypt 与 schema.sql 中保持一致)
-- 邮箱为演示虚构, 实际推送邮件请改成可送达地址或在管理端逐个修改
-- 重复执行安全: ON CONFLICT (student_id) DO NOTHING

INSERT INTO sys_user (student_id, name, password, role_level, grade, major, class_name, phone, email, status)
VALUES
-- ===== 管理老师 / 辅导员 (role_level=2) =====
-- 老师无年级/专业/班级字段, "全部" 模式下不受这些筛选约束
('T2024001', '张老师', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 2, NULL, NULL, NULL, '13800001001', 'teacher.zhang@example.edu', 1),
('T2024002', '李老师', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 2, NULL, NULL, NULL, '13800001002', 'teacher.li@example.edu', 1),
('T2024003', '王老师', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 2, NULL, NULL, NULL, '13800001003', 'teacher.wang@example.edu', 1),

-- ===== 班团骨干 (role_level=3) =====
-- 骨干同时具备学生身份, 保留年级/专业/班级以便 3 级权限只看本班的数据隔离逻辑生效
('L2024001', '陈骨干', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 3, '2024', '计算机科学与技术', '2024级1班', '13800002001', 'leader.chen@example.edu', 1),
('L2024002', '刘骨干', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 3, '2024', '计算机科学与技术', '2024级2班', '13800002002', 'leader.liu@example.edu', 1),
('L2023001', '赵骨干', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 3, '2023', '软件工程',         '2023级1班', '13800002003', 'leader.zhao@example.edu', 1)
ON CONFLICT (student_id) DO NOTHING;
