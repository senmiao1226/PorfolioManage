-- =====================================================================
-- Portfolio API 建库脚本（Java / Spring Boot 使用）
--
-- 用法：在 MySQL 8 客户端或 Workbench 中执行本文件一次，再启动应用。
-- 表结构由 spring.jpa.hibernate.ddl-auto 维护，无需在此建表。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS portfolio_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 可选（命令行 mysql 客户端中后续操作）：
-- USE portfolio_db;
