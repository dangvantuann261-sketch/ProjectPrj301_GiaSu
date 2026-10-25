package vn.suviet.util;
import java.sql.*;
public final class DBContext {
    private DBContext() {
    }
    public static Connection connect() throws SQLException {
        // Tomcat scans JDBC providers before WEB-INF/lib is visible.
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        }
        catch (ClassNotFoundException e) {
            throw new SQLException("Thiếu driver SQL Server: cần mssql-jdbc trong WEB-INF/lib của ứng dụng.", "08001", e);
        }
        return DriverManager.getConnection(Config.require("db.url"), Config.get("db.user", ""), Config.get("db.password", ""));
    }
}
