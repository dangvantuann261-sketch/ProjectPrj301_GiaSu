# Thư viện JAR chính xác

Đã kèm sẵn các file sau. Đây là tải trực tiếp JAR; dự án không sử dụng Maven.

| File | Vai trò | Nơi tải |
|---|---|---|
| `gson-2.11.0.jar` | Runtime, đóng gói WEB-INF/lib | [Maven Central](https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar) |
| `mssql-jdbc-12.8.1.jre11.jar` | Runtime, đóng gói WEB-INF/lib | [Maven Central](https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.8.1.jre11/mssql-jdbc-12.8.1.jre11.jar) |
| `jakarta.servlet.jsp.jstl-api-3.0.2.jar` | Runtime, đóng gói WEB-INF/lib | [Maven Central](https://repo.maven.apache.org/maven2/jakarta/servlet/jsp/jstl/jakarta.servlet.jsp.jstl-api/3.0.2/jakarta.servlet.jsp.jstl-api-3.0.2.jar) |
| `jakarta.servlet.jsp.jstl-3.0.1.jar` | Runtime, đóng gói WEB-INF/lib | [Maven Central](https://repo.maven.apache.org/maven2/org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1/jakarta.servlet.jsp.jstl-3.0.1.jar) |
| `jakarta.servlet-api-6.0.0.jar` | Compile-only; Tomcat cung cấp khi chạy | [Maven Central](https://repo.maven.apache.org/maven2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar) |

Trong NetBeans: Properties → Libraries → Add JAR/Folder. Các tham chiếu đã khai báo ở `nbproject/project.properties`. Bốn JAR runtime nằm trực tiếp trong `lib`. Servlet API nằm ở `lib/compile-only` và **không** được đóng gói WAR.

JSTL 3.0.2 là API; GlassFish JSTL 3.0.1 là implementation. Cần cả hai. Gson 2.11.0 xử lý JSON. JDBC bản jre11 chạy trên JDK 17. Mật khẩu dùng PBKDF2 có sẵn trong JDK nên không cần jbcrypt.

Không thêm `javax.servlet-api`, JSTL 1.x/2.x, toàn bộ Jakarta EE platform API hoặc Tomcat lib vào WAR. JAR JDBC authentication native DLL không cần khi dùng SQL Authentication như cấu hình mẫu.

SHA-256 của từng JAR được kiểm tra trong `scripts/download-libs.ps1`; chạy script này để tải lại nếu file bị thiếu. Bootstrap 5.3.3 được nhúng CDN trong header JSP với SRI, không có JAR.

Các thư viện giữ giấy phép của nhà phát hành trong META-INF của JAR. Các script Ant được sinh từ Apache NetBeans theo Apache License 2.0; giao diện và mã nghiệp vụ của dự án được viết riêng cho yêu cầu này.
