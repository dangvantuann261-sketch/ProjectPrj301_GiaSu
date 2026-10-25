# Chạy ProjectPrj_GiaSu bằng Visual Studio Code trên máy của bạn

Máy đã có JDK 17, Ant đi kèm NetBeans 17, Tomcat 10.1 và database SQL Server. Bạn dùng lại database và file cấu hình đã tạo.

## 1. Mở đúng thư mục

Trong VS Code chọn **File → Open Folder**, dán đường dẫn sau vào ô đường dẫn rồi Enter:

```text
C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\outputs\ProjectPrj_GiaSu
```

Nhấn **Select Folder**. Explorer bên trái phải có `build.xml`, `src`, `web`, `lib` và `scripts`. Nếu VS Code hỏi có tin cậy thư mục này không, chọn tin cậy vì đây là dự án của bạn.

## 2. Cài hỗ trợ Java

Nhấn **Ctrl+Shift+X**, tìm **Extension Pack for Java** của **Microsoft**, chọn **Install**. Cấu hình trong `.vscode/settings.json` đã chọn JDK 17 và các JAR trong `lib/`.

Extension hỗ trợ đọc/sửa Java; script chạy website dùng JDK và Ant trên máy, không cần chờ extension hoàn tất lập chỉ mục.

## 3. Kiểm tra SQL Server

Mở SSMS, kết nối instance đã tạo database và kiểm tra database `SuVietTutor` có trong **Databases**. Dịch vụ SQL Server cần đang chạy. Không chạy lại các script tạo bảng/dữ liệu trên database đã tạo.

File cấu hình trên máy hiện tại:

```text
C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\SuVietTutorData\config.properties
```

Script tự tìm file này. Khi chuyển dự án đi nơi khác, truyền tham số `-ConfigPath` như phần dưới. Không đưa file chứa mật khẩu/API key vào thư mục web.

## 4. Chạy website

Chọn **Terminal → New Terminal**, dùng **PowerShell**. Terminal phải đang ở thư mục `ProjectPrj_GiaSu`. Dán lệnh sau rồi Enter:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-vscode.ps1
```

Script thực hiện `clean dist` bằng Ant/JDK 17, tạo `dist/SuVietTutor.war`, chép WAR vào `.local/tomcat/webapps` rồi chạy Tomcat trong Terminal. Chờ thấy **BUILD SUCCESSFUL**, tiếp theo **Server startup in [...] milliseconds**. Giữ Terminal mở.

Mở trình duyệt:

**http://127.0.0.1:8082/SuVietTutor/login**

Tomcat này dùng cổng **8082** và thư mục cấu hình riêng; Tomcat đã cấu hình trong NetBeans dùng cổng 8080 vẫn là phiên chạy khác. Chỉ mở URL 8082 để xem thay đổi từ VS Code.

Cách dùng menu thay cho gõ lệnh: **Terminal → Run Task → Run website (Tomcat 8082)**.

## 5. Đăng nhập thử

| Vai trò | Email | Mật khẩu mẫu |
|---|---|---|
| Học sinh | student@suviet.local | DemoStudent#2026 |
| Giáo viên | teacher@suviet.local | DemoTeacher#2026 |
| Admin | admin@suviet.local | DemoAdmin#2026 |

Đây là tài khoản website trong dữ liệu mẫu; tài khoản kết nối SQL Server được đọc từ `config.properties`. AI cần key hợp lệ và model có quyền truy cập. Xem [cấu hình Gemini](GEMINI-SETUP.md); nếu Google báo `API_KEY_INVALID`, cần thay key và khởi động lại Tomcat.

## 6. Dừng và chạy lại sau khi sửa

Nhấp vào Terminal đang chạy Tomcat, nhấn **Ctrl+C**, chờ server dừng. Sửa code, nhấn **Ctrl+S**, chạy lại lệnh ở bước 4 rồi tải lại trình duyệt. Mỗi lần chạy script sẽ build lại Java, JSP, CSS và JavaScript.

Chạy website bằng script hoặc Run Task ở trên. Các servlet của dự án được Tomcat gọi và không có hàm `main()` để chạy riêng bằng nút Run Java.

## Khi gặp lỗi

- **Không tìm thấy `run-vscode.ps1`:** kiểm tra đã mở đúng thư mục `ProjectPrj_GiaSu`. Có thể chuyển Terminal về thư mục bằng `Set-Location 'C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\outputs\ProjectPrj_GiaSu'`.
- **Port 8082 is unavailable:** dừng phiên chạy trước bằng Ctrl+C, hoặc chạy `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-vscode.ps1 -Port 8083` rồi mở URL với cổng 8083.
- **Không kết nối được cơ sở dữ liệu:** xem lỗi SQL chi tiết trong Terminal; kiểm tra dịch vụ SQL, cổng JDBC, login và mật khẩu trong file cấu hình. Khởi động lại script sau khi sửa cấu hình.
- **Java báo đỏ import `jakarta` hoặc `gson`:** đợi Java extension tải dự án. Nếu vẫn còn, nhấn Ctrl+Shift+P → `Java: Clean Java Language Server Workspace` → xác nhận tải lại; các JAR đã có trong `lib/`.
- **File not found:** đường dẫn cài JDK, Ant, Tomcat hoặc config đã thay đổi. Truyền đường dẫn đúng qua tham số:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-vscode.ps1 -JavaHome 'C:\Program Files\Java\jdk-17' -TomcatHome 'C:\Program Files\Apache Software Foundation\Tomcat 10.1' -AntHome 'C:\Program Files\NetBeans-17\netbeans\extide\ant' -ConfigPath 'C:\SuVietTutorData\config.properties'
```

Sửa đường dẫn JDK trong `.vscode/settings.json` tương ứng nếu đổi JDK.

Tài liệu chính thức: [Java trong VS Code](https://code.visualstudio.com/docs/java/java-tutorial), [cấu hình JDK và thư viện](https://code.visualstudio.com/docs/java/java-project), [Tomcat CATALINA_BASE](https://tomcat.apache.org/tomcat-10.1-doc/introduction.html).
