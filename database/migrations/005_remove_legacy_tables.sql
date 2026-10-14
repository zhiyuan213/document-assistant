-- 先部署纯PDF工具版本并确认已执行004。此脚本会永久删除旧模块数据。
-- 云端执行前请在控制台备份数据库。不会删除任何 pdf_* 表。
USE dengke;
DROP TABLE IF EXISTS resource_favorite;
DROP TABLE IF EXISTS resource_download;
DROP TABLE IF EXISTS user_feedback;
DROP TABLE IF EXISTS login_session;
DROP TABLE IF EXISTS ocr_record;
DROP TABLE IF EXISTS file_task;
DROP TABLE IF EXISTS file_info;
DROP TABLE IF EXISTS order_info;
DROP TABLE IF EXISTS user_rights;
DROP TABLE IF EXISTS user_quota;
DROP TABLE IF EXISTS resource_item;
DROP TABLE IF EXISTS resource_category;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS app_user;
