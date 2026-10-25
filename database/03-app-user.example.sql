-- Ví dụ: sửa mật khẩu trước khi chạy bằng tài khoản quản trị SQL Server.
-- Không chạy nếu đã có login/user cùng tên.
USE master;
GO
CREATE LOGIN suviet_app WITH PASSWORD = 'REPLACE_WITH_YOUR_STRONG_PASSWORD';
GO
USE SuVietTutor;
GO
CREATE USER suviet_app FOR LOGIN suviet_app;
ALTER ROLE db_datareader ADD MEMBER suviet_app;
ALTER ROLE db_datawriter ADD MEMBER suviet_app;
GO
