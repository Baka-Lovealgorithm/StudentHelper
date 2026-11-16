-- CR-014 / TCR-035：历史桩服务调用日志精确清理（2026-11-10）
-- 用户裁决范围：只删 model_name LIKE 'stub-%' 或 IN ('unreachable','hang-model') 的行。
-- 保留：真实配置的 85 次成功、7 次失败，以及 model_name IS NULL 的 5 条前置校验日志。
-- 执行前必须备份（示例，按需自行执行，本脚本不自动建备份表）：
--   mysql ... -e "SELECT * FROM student_helper.t_call_log WHERE model_name LIKE 'stub-%' OR model_name IN ('unreachable','hang-model')" > backup-debug-logs.tsv
-- 可重复执行：第二次 DELETE 影响 0 行。

USE student_helper;

-- 1) 预览（执行前先看清楚要删什么）
SELECT COALESCE(model_name, '<NULL>') AS model_name, status, COUNT(*) AS rows_to_delete
FROM t_call_log
WHERE model_name LIKE 'stub-%' OR model_name IN ('unreachable', 'hang-model')
GROUP BY model_name, status
ORDER BY model_name, status;

-- 2) 精确删除（不使用 TRUNCATE、不带宽泛通配、不动其它行）
DELETE FROM t_call_log
WHERE model_name LIKE 'stub-%' OR model_name IN ('unreachable', 'hang-model');

-- 3) 复核：真实调用与前置校验日志必须仍在
SELECT 'remaining_total' AS item, COUNT(*) AS value FROM t_call_log
UNION ALL SELECT 'real_success', COUNT(*) FROM t_call_log WHERE model_name = 'deepseek-v4.1-flash-1017' AND status = 'SUCCESS'
UNION ALL SELECT 'real_failed', COUNT(*) FROM t_call_log WHERE model_name = 'deepseek-v4.1-flash-1017' AND status = 'FAILED'
UNION ALL SELECT 'precheck_null_model', COUNT(*) FROM t_call_log WHERE model_name IS NULL
UNION ALL SELECT 'debug_markers', COUNT(*) FROM t_call_log WHERE is_debug = 1;
