-- ============================================================================
-- 增量迁移: 入党流程模板由 8 步升级到 29 步 (按《发展党员工作程序》)
--
-- 适用对象: schema.sql 早期版本 (提交 7c9e58f 之前) 建库的实例
-- 触发条件: SELECT total_steps FROM party_process_template WHERE id = 1 还是 8
--
-- 安全说明:
--   - 脚本会先检测 template_id=1 是否有 in-flight 实例 (current_step 非空且 status='active'),
--     若存在则直接抛错回滚, 不破坏学生进度
--   - 无实例时执行 DELETE 旧 step + 重插 29 条新 step
--
-- 用法 (本地):
--   psql -U postgres -d college_service -f deploy/sql/party_steps_29_migrate.sql
-- 用法 (容器):
--   docker exec -i <pg-container> psql -U postgres -d college_service \
--       < deploy/sql/party_steps_29_migrate.sql
-- ============================================================================

BEGIN;

DO $$
DECLARE
    inflight_count INT;
    current_total  INT;
BEGIN
    SELECT total_steps INTO current_total FROM party_process_template WHERE id = 1;
    IF current_total IS NULL THEN
        RAISE NOTICE '未找到 id=1 的入党流程模板, 跳过';
        RETURN;
    END IF;
    IF current_total = 29 THEN
        RAISE NOTICE '已是 29 步, 无需迁移';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO inflight_count
    FROM party_process_instance
    WHERE template_id = 1 AND status = 'active';
    IF inflight_count > 0 THEN
        RAISE EXCEPTION '检测到 % 个进行中的入党流程实例, 拒绝破坏性升级. 请先暂停/迁移这些实例后再跑',
            inflight_count;
    END IF;

    DELETE FROM party_process_step WHERE template_id = 1;
    UPDATE party_process_template SET total_steps = 29, description = '发展党员工作程序' WHERE id = 1;
END $$;

-- 仅在仍需补数据时插入 (DO 块里如果 RETURN 提前退出, 下面这段也会执行但 DELETE 空集 + 重插也无害)
INSERT INTO party_process_step (template_id, step_order, name, description, duration_days)
SELECT 1, s.ord, s.nm, s.phase, NULL FROM (VALUES
    (1, '教育引导', '入党积极分子确定'),
    (2, '接收入党申请书并派人谈话', '入党积极分子确定'),
    (3, '确定入党积极分子并报党委备案', '入党积极分子确定'),
    (4, '指定培养联系人并进行培养教育', '入党积极分子确定'),
    (5, '考察', '入党积极分子确定'),
    (6, '支部委员会听取意见后讨论', '发展对象确定'),
    (7, '报党委备案后确定发展对象', '发展对象确定'),
    (8, '确定入党介绍人', '发展对象确定'),
    (9, '政治审查', '发展对象确定'),
    (10, '短期集中培训', '发展对象确定'),
    (11, '支部委员会听取意见后讨论', '预备党员接收'),
    (12, '报党委预审', '预备党员接收'),
    (13, '公示', '预备党员接收'),
    (14, '召开支部大会讨论接收预备党员', '预备党员接收'),
    (15, '将有关材料报党委', '预备党员接收'),
    (16, '党委委员或组织员与发展对象谈话', '预备党员接收'),
    (17, '党委审批', '预备党员接收'),
    (18, '党委审批结果通知党支部', '预备党员接收'),
    (19, '报上级党委组织部门备案', '预备党员接收'),
    (20, '编入党支部和党小组', '预备党员教育和转正'),
    (21, '入党宣誓', '预备党员教育和转正'),
    (22, '教育和考察', '预备党员教育和转正'),
    (23, '提交转正申请并征求意见并审查', '预备党员教育和转正'),
    (24, '公示', '预备党员教育和转正'),
    (25, '召开支部大会讨论预备党员转正', '预备党员教育和转正'),
    (26, '将有关材料报党委', '预备党员教育和转正'),
    (27, '党委审批', '预备党员教育和转正'),
    (28, '党委审批结果通知党支部', '预备党员教育和转正'),
    (29, '存档', '正式党员')
) AS s(ord, nm, phase)
WHERE NOT EXISTS (
    SELECT 1 FROM party_process_step WHERE template_id = 1 AND step_order = s.ord
);

COMMIT;

-- 校验
SELECT id, name, total_steps FROM party_process_template WHERE id = 1;
SELECT COUNT(*) AS step_count FROM party_process_step WHERE template_id = 1;
