-- 只读诊断，不包含凭证、原文件名或文件正文，不修改任何数据。
USE dengke;
SELECT tool,status,created_at,expires_at,
       TIMESTAMPDIFF(SECOND,NOW(),expires_at) AS remaining_seconds,
       error_message
FROM pdf_tool_job
ORDER BY created_at DESC
LIMIT 10;
SELECT NOW() AS database_time, UTC_TIMESTAMP() AS utc_time,
       @@session.time_zone AS session_timezone;
