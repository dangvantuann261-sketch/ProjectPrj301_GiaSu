IF DB_ID(N'SuVietTutor') IS NULL
BEGIN
    CREATE DATABASE SuVietTutor;
END;
GO
-- Sau đó chọn SuVietTutor trong SSMS trước khi chạy 01-schema.sql và 02-seed.sql.
