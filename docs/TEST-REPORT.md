# Báo cáo kiểm thử — 05/10/2026

## Kết quả đã xác minh

- **Ant build thành công**, Java 17, sinh `dist/SuVietTutor.war`.
- **11 kiểm tra lõi**: salt ngẫu nhiên, xác minh mật khẩu, hash lỗi, URL video hợp lệ/giả mạo, URL script, allowlist danh mục, snapshot câu hỏi, đáp án sai định dạng.
- **198 kiểm tra HTTP tích hợp qua Tomcat 10.1.48** (151 kiểm tra luồng chính + 47 kiểm tra bổ sung). Danh sách từng kiểm tra ở `HTTP-CHECKS.txt`.
- Tất cả JSP đã được Tomcat biên dịch khi truy cập các trang tương ứng, với JSTL Jakarta 3.x.
- Trình duyệt xác nhận gửi chat qua JavaScript: hiện “đang suy nghĩ”, khóa nút gửi, hiển thị phản hồi rồi chờ học sinh; không thấy lỗi console trong lượt kiểm tra.
- Bố cục học sinh kiểm tra ở desktop và viewport **390 × 844**. Trang không tràn ngang; thanh điều hướng nhỏ có vùng cuộn riêng.

## Môi trường và giới hạn

Kiểm thử dùng JDK 17.0.20.1, Ant của NetBeans 17 và Tomcat 10.1.48, chạy trong thư mục tạm của chat. CSDL kiểm thử là **H2 2.3.232 in-memory, MODE=MSSQLServer**; script DDL được đổi `NVARCHAR(MAX)` thành `NVARCHAR(1000000)` và `DATETIME2` thành `TIMESTAMP` riêng cho test. **H2 không nằm trong WAR hay thư viện bàn giao.** Ứng dụng thật dùng JDBC SQL Server trong cấu hình mẫu.

API kiểm thử là HTTP stub nội bộ mô phỏng cấu trúc Gemini. Stub trả dữ liệu có kiểm soát cho sinh câu hỏi, hội thoại và chấm ảnh, đồng thời trả JSON lỗi, HTTP 429 và phản hồi chậm để kiểm tra timeout. **Không có phản hồi AI giả được nhúng vào ứng dụng bàn giao.** Các test xác minh vận chuyển ảnh/lịch sử, xử lý dữ liệu và lỗi, không chứng minh chất lượng OCR hoặc cách dạy của model thật.

Ở đợt kiểm thử ban đầu, sandbox Windows từ chối thao tác chuẩn hóa đường dẫn của `javac` khi đọc JAR trong Documents. Ant được chạy với **Eclipse JDTCompilerAdapter từ ECJ 4.27 của Tomcat**, source/target Java 17. Sau lần sửa cấu hình build ngày 05/10/2026, đã xác minh build bằng `javac` chuẩn của JDK 17 trong tiến trình riêng, kể cả khi Ant chạy bằng Java 8, như mô tả bên dưới.

Ở đợt 198 kiểm tra ban đầu, SQL Server local chưa kết nối được bằng thông tin có sẵn và chưa có API key Gemini thật. Sau khi người dùng tạo database và cung cấp cấu hình, đã bổ sung kiểm thử SQL Server thật như mô tả bên dưới. Gemini thật và thao tác Run/F6 trong cửa sổ NetBeans vẫn chưa được tự động kiểm thử.

## Bổ sung sau khi chạy trên máy người dùng — 05/10/2026

- Sửa `minimum-ant-version` trong metadata về giá trị hợp lệ của schema dự án NetBeans 17; kiểm tra XML với XSD từ bản NetBeans cài trên máy.
- Sửa CSS tương thích NetBeans 17; bộ phân tích CSS của IDE không còn lỗi cú pháp. Kiểm tra tô sáng đáp án khi chọn, đổi lựa chọn, khôi phục trang và reset biểu mẫu.
- Tái hiện điều kiện nạp JDBC của Tomcat: `DriverManager` khởi tạo trước khi driver của ứng dụng web có thể được nhìn thấy. `DBContext` đã nạp driver SQL Server tường minh và listener hủy đăng ký đúng driver do ứng dụng sở hữu khi dừng.
- Ant build bản sửa và 11 kiểm tra lõi đều thành công, bằng ECJ như môi trường kiểm thử ban đầu.
- Kết nối SQL Server thật qua class loader riêng của ứng dụng, đọc đủ 3 tài khoản mẫu; kiểm tra hủy đăng ký driver thành công.
- Khởi động Tomcat 10.1.48 kiểm thử tại cổng cục bộ 18086, bật `JreMemoryLeakPreventionListener`, giữ driver SQL Server chỉ trong WAR (`WEB-INF/lib`), không chép vào Tomcat `lib`. Đăng nhập tài khoản Student trên SQL Server thật và mở `/student/learn` thành công, HTTP 200. Tomcat kiểm thử đã dừng sau kiểm tra.
- Cần khởi động lại/triển khai lại Tomcat đang chạy của người dùng để dùng mã đã sửa. Không có mật khẩu hoặc API key thật trong tài liệu, WAR hay ZIP bàn giao.
- Sửa tác vụ build tùy chỉnh để đọc Java Platform của NetBeans và fork đúng `javac`, truyền `--release 17` trực tiếp, đồng thời chạy test bằng cùng JDK. Xác minh với Ant chạy bằng Java 8.0.202 và trình biên dịch chuẩn `C:/Program Files/Java/jdk-17/bin/javac.exe`: biên dịch đủ 26 file Java, sinh WAR, 11 kiểm tra lõi thành công. Lỗi `invalid target release: 17` được xử lý mà không cần đổi JDK đang chạy IDE.
- Bản dự án `ProjectPrj_GiaSu` đã cập nhật tên thư mục, metadata NetBeans, tên Ant project, tiêu đề Javadoc và file ZIP. Ant build tại thư mục mới và 11 kiểm tra lõi thành công; database, tên WAR, context path và cấu hình bên ngoài dự án được giữ nguyên.

## Các tình huống quan trọng đã qua

- Đăng nhập đủ 3 role; guest chuyển về login; student bị chặn cả `/admin`, `/admin/dashboard`, `/teacher` và trang quản lý; teacher không sửa cấu hình Admin.
- POST thiếu CSRF bị chặn. Đăng ký luôn tạo Student, dù gửi thêm tham số role=ADMIN.
- Admin thêm/sửa/khóa/mở khóa/xóa user; phân trang; tìm kiếm chứa chuỗi SQL injection vẫn được xử lý như dữ liệu; email trùng trả 409.
- CRUD đầy đủ chuỗi môn → bài → chủ đề → bài giảng/câu hỏi; cản xóa mục cha còn nội dung con; kiểm tra URL; text chứa script được escape.
- YouTube tạo iframe từ ID hợp lệ trên host tin cậy; URL ngoài không biến thành iframe tùy ý. Không kiểm thử phát một video bên thứ ba thật.
- AI sinh bản nháp đủ số lượng, giáo viên chỉnh sửa rồi duyệt; token cũ không duyệt lại được.
- Trắc nghiệm không gửi đáp án đúng vào HTML trước khi nộp; lựa chọn ngoài A–D bị chặn; nộp trùng position bị chặn; hoàn thành và lưu lịch sử.
- Sửa đáp án ngân hàng sau khi bắt đầu không thay đổi điểm của snapshot. Xóa câu hỏi/mục cha sau đó không làm mất bài đã nộp.
- Chat gửi lịch sử nhiều lượt và system instruction; JSON hỏng, 429, timeout trả lỗi có thể thử lại.
- Ảnh giả có đuôi JPG bị chặn; PNG hợp lệ đi qua Gemini inlineData; điểm/nhận xét được lưu; owner và teacher xem được ảnh, student khác nhận 404.
- Khóa user có hiệu lực với session đang mở.
- Admin lưu và đọc lại system prompt.

## Lỗi đã sửa trong kiểm thử

1. Khai báo UTF-8 riêng cho các JSP/fragments để chữ tiếng Việt trong source được dịch đúng.
2. Chặn quyền ở cả route gốc `/admin`/`teacher` và các route con.
3. So sánh điểm hoàn hảo bằng `score ge 10` thay cho equality khác scale của DECIMAL trong JSP; điểm 10 hiện đúng tiêu đề hoàn thành.
4. Lỗi ràng buộc SQL được trả thông báo xung đột dữ liệu, không hiện stack trace ra giao diện.

## Ảnh kiểm thử

`student-desktop.jpg` và `student-mobile.jpg` là giao diện học tập trên trình duyệt. `chat-test.jpg` dùng **API stub**, chỉ minh họa luồng giao diện và trạng thái chờ học sinh.

## Chạy lại kiểm tra lõi

### Hỗ trợ VS Code

Đã kiểm tra cú pháp PowerShell và JSON của `.vscode/`. Chạy thật `scripts/run-vscode.ps1` với JDK 17, Ant của NetBeans 17 và Tomcat cài trên máy (10.1.59): build `clean dist` thành công, đăng nhập tài khoản học sinh qua SQL Server thật và trang JSP học tập trả HTTP 200; CSS mới được phục vụ từ WAR chưa giải nén. Kiểm tra HTTP dùng cổng 8081. Bản hướng dẫn cuối cùng mặc định cổng 8082 để tránh phiên kiểm tra đang chiếm 8081. Không kiểm thử cài đặt Java extension hoặc menu Run Task trong giao diện VS Code. Phiên kiểm tra 8081 chưa dừng được do môi trường từ chối thao tác điều khiển/dừng tiến trình.

Trong thư mục dự án, dùng JDK 17 và chạy `ant clean dist test`. Các kiểm tra lõi không cần database hay API key. Sau khi cấu hình SQL Server và Gemini, dùng các tình huống trên làm danh sách kiểm thử nghiệm thu trên môi trường thật.
