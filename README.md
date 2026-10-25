# ProjectPrj_GiaSu — Sử Việt Tutor

Dự án học Lịch sử lớp 6 theo chương và bài học, kết hợp luyện tập theo từng bài/chương, video nhúng và gia sư AI. Java 17, Servlet/JSP/JSTL Jakarta, JDBC thuần, SQL Server, Bootstrap 5.3.3. NetBeans 17 và Ant; không Maven, Gradle, Spring, JPA hay frontend framework.

## Luồng học theo chương và bài — 22/10/2026

Đã bỏ đăng ký khóa học và thư viện tự học khỏi luồng sử dụng. Học sinh mở bài trực tiếp, luyện tập theo bài hoặc ôn tập chương; học sinh và giáo viên xem tiến độ lý thuyết, số lượt luyện tập và điểm cao nhất theo từng phần.

**Database hiện có:** chạy `database/09-chapter-learning.sql` sau migrations 04, 07, 08 rồi triển khai WAR mới. Hướng dẫn và quy tắc hiện hành: [Học theo chương và bài](docs/CHAPTER-LEARNING.md). Các mục nâng cấp cũ bên dưới mô tả lịch sử dự án; cơ chế đăng ký/chờ duyệt đã được thay thế.

## Bản nâng cấp khóa học — 06/10/2026 (lịch sử)

- Trang công khai, tìm khóa theo tên/môn/khối/giáo viên, chi tiết chương trình và học thử.
- Khóa nháp/xuất bản/lưu trữ, phân công giáo viên, ghi danh trực tiếp hoặc chờ duyệt.
- Dashboard, khóa của tôi, học tiếp, tiến độ theo bài bắt buộc, ghi chú riêng và lưu bài cần ôn.
- Hồ sơ học sinh, môn quan tâm, đổi mật khẩu và vô hiệu hóa phiên cũ khi mật khẩu thay đổi.
- Phân quyền tài nguyên, bảo vệ nội dung dùng trong khóa; nộp ảnh trước khi yêu cầu AI chấm.
- Giao diện responsive mới dùng Bootstrap hiện có; không thêm framework.

**Database hiện hữu:** backup rồi chạy migration `04-courses.sql`, không chạy lại `00`–`02`. Tài khoản ứng dụng vẫn chỉ có quyền đọc/ghi. Xem `docs/COURSE-UPGRADE.md` để biết quy tắc nghiệp vụ, kiểm thử, triển khai và phần chưa hoàn thành.

Tên thư mục và tên dự án trong NetBeans là `ProjectPrj_GiaSu`. Database vẫn là `SuVietTutor`, file triển khai vẫn là `SuVietTutor.war`, URL vẫn là `/SuVietTutor/`; cấu hình JDBC và thư mục ảnh bên ngoài dự án không cần đổi. Khi chuyển từ thư mục cũ, đóng dự án cũ và dùng File → Open Project để mở `ProjectPrj_GiaSu`.

## Chạy nhanh

Phân quyền Admin, cấu hình link/key/prompt AI và luồng tự luận từng bước, làm lại/nộp ảnh: xem [hướng dẫn quản trị và tự luận](docs/ADMIN-ESSAY-WORKFLOW.md). Database hiện hữu cần bổ sung migration `07-admin-essay-workflow.sql` sau migration `04`.

Giáo viên xem tiến độ và lịch sử học tập trong các khóa được phân công tại **Tiến độ học tập**. Xem [hướng dẫn theo dõi lớp](docs/TEACHER-LEARNING-PROGRESS.md); bổ sung migration `08-learning-progress.sql` trước khi chạy bản mới.

Chạy bằng **Visual Studio Code**: xem [hướng dẫn từng bước](docs/CHAY-VSCODE.md). Đã có cấu hình Java và tác vụ `Run website (Tomcat 8082)` trong `.vscode/`; script `scripts/run-vscode.ps1` tự build WAR rồi chạy Tomcat với cấu hình riêng tại `.local/tomcat`.

1. Giải nén dự án vào thư mục có quyền ghi. Có thể dùng `C:/Projects/ProjectPrj_GiaSu`.
2. Cài **JDK 17**, **NetBeans 17 bản Java Web**, **Tomcat 10.1**, **SQL Server 2019/2022** và SSMS. Trong NetBeans: Tools → Java Platforms → Add Platform → chọn JDK 17. Nếu IDE đang dùng JDK 8, khởi động NetBeans bằng `netbeans64.exe --jdkhome "C:\Program Files\Java\jdk-17"`.
3. Chỉ với database mới: trong SSMS chạy `database/00-create-database.sql`, chọn **SuVietTutor**, rồi chạy `01-schema.sql`, `02-seed.sql`, `04-courses.sql`. `01` và `02` chạy một lần, không phải migration. Với database hiện hữu, chỉ chạy migration `04` sau backup.
4. Tạo login SQL riêng và cấp quyền đọc/ghi cho database. `database/03-app-user.example.sql` là mẫu; thay mật khẩu trước khi chạy. Nếu dùng SQL Authentication, SQL Server phải bật Mixed Mode. Bật TCP/IP bằng SQL Server Configuration Manager, chọn cổng phù hợp và khởi động lại dịch vụ nếu vừa đổi cấu hình.
5. Sao chép `config/config.example.properties` thành **`C:/SuVietTutorData/config.properties`**. Điền `db.url`, `db.user`, `db.password`, `ai.apiKey`, `ai.model`. `localhost:1433` chỉ là giá trị mẫu; với instance/cổng khác phải sửa đúng cổng SQL Server. Không dùng cổng 8080 cho JDBC.
6. Đặt `uploads.dir=C:/SuVietTutorData/uploads`; tài khoản chạy Tomcat cần quyền tạo/ghi thư mục. Ảnh nằm ngoài `build/`, `dist/` và Tomcat `webapps/`.
7. Trong NetBeans: File → Open Project → chọn **ProjectPrj_GiaSu**. Project Properties → Libraries → Java Platform: **JDK 17**. Các JAR đã có trong `lib/` và được khai báo sẵn; nếu IDE báo thiếu, dùng Add JAR/Folder theo `lib/README.md`.
8. Services → Servers → Add Server → Apache Tomcat → chọn Tomcat 10.1. Project Properties → Run → chọn server này; context path `/SuVietTutor`. Không chia sẻ `nbproject/private/` vì đây là cấu hình riêng máy.
9. Cấu hình JVM Tomcat với **`-Dtutor.config=C:/SuVietTutorData/config.properties`**. Khi chạy từ NetBeans, mở Properties của server → Platform → VM Options và thêm tùy chọn trên. Khi chạy Tomcat độc lập bằng script, tạo `bin/setenv.bat` từ mẫu `scripts/setenv.bat.example`. Windows Service cần đặt Java Options trong cửa sổ cấu hình dịch vụ; dịch vụ không đọc `setenv.bat`. Khởi động lại Tomcat sau khi đổi file config.
10. Clean and Build, rồi Run. Hoặc chạy Ant và chép `dist/SuVietTutor.war` vào `webapps/` của Tomcat. Mở **http://localhost:8080/SuVietTutor/** (đổi cổng nếu cần).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
& 'C:\Program Files\NetBeans-17\netbeans\extide\ant\bin\ant.bat' clean dist test
```

Lệnh chạy từ thư mục gốc dự án. Build không cần SQL Server hoặc API key. Chạy các trang dữ liệu cần SQL Server; gọi AI cần key/model có quyền truy cập. Không có dữ liệu giả tự động thay thế SQL Server hoặc Gemini trong ứng dụng bàn giao.

Ant đọc Java Platform đã chọn trong NetBeans và chạy `javac` của JDK đó bằng tiến trình riêng; nếu chưa đăng ký platform thì dùng `JAVA_HOME` hoặc JDK đang chạy Ant. Dòng `Compiler: javac 17 (...)` trong Output xác nhận JDK dùng để build. Có thể ghi `platforms.JDK_17.home=C:/Program Files/Java/jdk-17` trong `nbproject/private/private.properties` riêng máy nếu platform của dự án tên `JDK_17`. Không đưa cấu hình đường dẫn riêng máy vào ZIP chia sẻ. Tác vụ test cũng chạy bằng JDK được chọn. Dự án vẫn biên dịch với `--release 17` khi NetBeans/Ant đang chạy bằng Java 8.

## Tài khoản dữ liệu mẫu

| Vai trò | Email | Mật khẩu mẫu |
|---|---|---|
| Admin | admin@suviet.local | DemoAdmin#2026 |
| Teacher | teacher@suviet.local | DemoTeacher#2026 |
| Student | student@suviet.local | DemoStudent#2026 |

Chỉ dùng các tài khoản mẫu cho môi trường bài tập. Học sinh có thể tự đăng ký. Tài khoản Admin không được sửa/xóa qua trang quản lý giáo viên/học sinh. Nếu dùng thực tế, thay hash mật khẩu Admin trong database bằng giá trị tạo bởi `Passwords.hash`, hoặc tạo dữ liệu khởi tạo riêng.

## Chức năng

- **Admin:** thống kê; tìm kiếm và phân trang tài khoản; thêm, sửa, khóa/mở khóa, xóa giáo viên/học sinh; sửa system prompt trong database; truy cập các chức năng quản lý nội dung.
- **Teacher:** CRUD môn học → bài học → chủ đề → bài giảng/câu hỏi; bài giảng text và URL http/https, YouTube được chuyển thành iframe `youtube-nocookie.com`; câu trắc nghiệm 4 lựa chọn, đáp án và giải thích; tự luận có hướng dẫn chấm; xem học sinh và kết quả.
- **AI soạn bài:** chọn chủ đề, loại, 1–10 câu → Gemini JSON → kiểm tra cấu trúc → bản nháp trong session → giáo viên sửa, bỏ chọn, duyệt → lưu transaction. Không tự lưu đề xuất AI. Duyệt xong token mất hiệu lực để chống nộp lại.
- **Student:** chọn môn/bài/chủ đề, xem bài giảng; mỗi lượt trắc nghiệm tối đa 5 câu chọn ngẫu nhiên; mỗi lượt tự luận 1 câu; tiếp tục lượt chưa xong; lịch sử, chi tiết và điểm trung bình trên thang 10 khi hoàn thành.
- **Ảnh tự luận:** JPG/PNG ≤5 MB, ≤16 triệu điểm ảnh; kiểm tra định dạng thật. Lưu ảnh tên UUID và câu trả lời trước, điểm NULL ở trạng thái PENDING. Học sinh chọn “Nhận gợi ý AI” để gửi ảnh đã lưu tới Gemini. AI lỗi chuyển RETRY, không mất ảnh và không ghi điểm 0. AI không đọc được chuyển MANUAL_REQUIRED, chưa có quy trình chấm tay trực tuyến. Ảnh luyện tự do chỉ được đọc bởi chủ bài làm hoặc ADMIN.
- **Gia sư từng bước:** câu sai tự mở chat dưới bài; hỗ trợ nhiều lượt bằng `fetch`; mỗi phản hồi một gợi ý và một câu hỏi rồi chờ. Học sinh có thể yêu cầu đáp án. System prompt gốc nằm ở `src/conf/system-prompt.txt`; giá trị trong `app_settings` do Admin sửa được ưu tiên.

Giáo viên chỉ quản lý khóa được phân công và nội dung riêng chưa chia sẻ. ADMIN quản lý nội dung chia sẻ và xuất bản khóa. Nội dung cũ vẫn nằm trong thư viện tự học; nội dung mới mặc định riêng tư. Bài luyện tự do không chia sẻ cho toàn bộ giáo viên. Nội dung đã gắn vào khóa không sửa/xóa trực tiếp. Xóa danh mục vẫn là xóa mềm; lịch sử bài làm cũ được giữ.

## Dữ liệu mẫu

Đã cập nhật **Lịch sử lớp 6 – Kết nối tri thức**: 20 bài thuộc 5 chương, mỗi bài có video xem trực tiếp và 5 câu trắc nghiệm có đáp án, giải thích (100 câu). Nguồn mục lục sách và bài tham khảo từ Lời giải hay. Lịch sử lớp 7–12 đã được xóa mềm khỏi khu vực học tập, giữ lịch sử cũ. Xem [nội dung, nguồn và cách nhập](docs/HISTORY-GRADE6.md); dùng `scripts/seed-history6.ps1 -Mode Apply` để sao lưu và nhập một lần.

Bộ dữ liệu ban đầu trong `02-seed.sql` gồm môn Lịch sử lớp 6, 4 bài học, 4 chủ đề, các bài đọc, câu trắc nghiệm và tự luận. Bộ này được giữ nguyên khi bổ sung khóa Lịch sử lớp 6 theo SGK. Các bài đọc ban đầu có URL video để trống để giáo viên bổ sung; các bài giảng thuộc bộ khóa mới có nguồn như tài liệu bên trên.

## Cấu trúc mã nguồn

```text
ProjectPrj_GiaSu/
  build.xml                    Ant build và kiểm thử độc lập
  nbproject/                   Metadata Java Web, build-impl và triển khai của NetBeans
  config/                      Cấu hình mẫu, không chứa key thật
  database/                    Tạo database, bảng, dữ liệu mẫu và login mẫu
  lib/                         4 JAR runtime và servlet API compile-only
  src/conf/system-prompt.txt    System prompt dự phòng
  src/java/vn/suviet/
    model/                     User, Question, Entity
    dao/                       Sql, UserDao, CatalogDao, PracticeDao, SettingsDao
    service/                   CatalogService, AIClient, GeminiClient, TutorService, UploadService
    controller/                Auth, Admin, Teacher, Student, Review, Chat servlets
    filter/AppFilter.java      UTF-8, session, role, CSRF, headers, xử lý lỗi
    util/                      Config, DBContext, Passwords, Web
  web/WEB-INF/views/           JSP và fragments, không truy cập trực tiếp từ URL
  web/assets/                 CSS, JavaScript và SVG tự tạo
  test/vn/suviet/CoreTests.java
  scripts/                    Tải JAR, cấu hình Tomcat
  docs/                       Thiết kế và kết quả kiểm thử
  dist/SuVietTutor.war         File triển khai sau khi build
```

Servlet/Filter/multipart dùng **annotation**; `web.xml` chỉ cấu hình phiên, cookie, trang mặc định và lỗi. JSTL dùng `jakarta.tags.core`, `jakarta.tags.fmt`, `jakarta.tags.functions`. `javax.crypto` và `javax.imageio` là API Java SE đúng của JDK, không phải `javax.servlet`.

## Cấu hình AI

Hướng dẫn tạo key, cấu hình trên máy và xử lý lỗi: [Gemini API](docs/GEMINI-SETUP.md). Key phải hợp lệ; `API_KEY_INVALID` cần thay key, không khắc phục chỉ bằng đổi model. Sau khi sửa cấu hình cần khởi động lại Tomcat.

`GeminiClient` dùng `java.net.http.HttpClient`, `systemInstruction`, lịch sử `contents`, ảnh `inlineData` và `responseJsonSchema`. API key nằm trong file ngoài WAR và gửi bằng header `x-goog-api-key`. Model mặc định trong mẫu là `gemini-3.8-flash`; kiểm tra quyền truy cập model trong tài khoản trước khi chạy. Không ghi key, ảnh base64 hay toàn bộ phản hồi API vào thông báo lỗi cho người dùng.

Thay nhà cung cấp: triển khai interface `AIClient`, đổi constructor mặc định trong `TutorService`; các controller không phụ thuộc trực tiếp REST payload của Gemini. Các schema và kiểm tra dữ liệu ở `TutorService` phải được giữ khi chuyển nhà cung cấp.

Chat lưu trong session, phân theo ID bài trả lời; tối đa 12 hội thoại/phiên, 20 lượt/hội thoại, 3.000 ký tự/tin nhắn. Mỗi hội thoại có khóa chống gửi đồng thời, khoảng cách tối thiểu 2 giây. Lịch sử biến mất khi logout/hết phiên/khởi động lại server; kết quả bài làm và ảnh đã nộp vẫn tồn tại. Các giới hạn này phục vụ assignment, không phải hạn mức chi phí toàn hệ thống. System prompt định hướng cách dạy, không đảm bảo AI luôn chính xác hoặc tuyệt đối không tiết lộ đáp án sớm; giáo viên nên kiểm tra hành vi với model thực tế.

Tài liệu chính thức đã tham chiếu:

- [Gemini generateContent](https://ai.google.dev/api/generate-content)
- [Structured output](https://ai.google.dev/gemini-api/docs/structured-output)
- [Image understanding](https://ai.google.dev/gemini-api/docs/image-understanding)
- [Danh sách model hiện hành](https://ai.google.dev/gemini-api/docs/models)

## Bảo vệ dữ liệu và tính nhất quán

PreparedStatement cho mọi giá trị SQL; tên bảng/cột động lấy từ enum cố định. Mật khẩu dùng PBKDF2-HMAC-SHA256 600.000 vòng, salt ngẫu nhiên 16 byte và so sánh constant-time. Đăng nhập tạo session mới, có giới hạn thử sai theo session. Filter đọc lại trạng thái user mỗi request nên khóa/xóa/đổi role có hiệu lực với phiên đang mở.

POST yêu cầu CSRF token. JSP dùng `c:out`/`fn:escapeXml`; chat hiển thị bằng `textContent`. URL chỉ nhận http/https, YouTube phải đúng hostname. Cookies HttpOnly, SameSite=Lax; khi triển khai HTTPS thật cần bật cờ Secure trong cấu hình cookie và dùng chứng chỉ SQL hợp lệ (`trustServerCertificate=false`). Cấu hình mẫu ưu tiên khả năng chạy SQL Server local có chứng chỉ tự ký.

Câu hỏi được chụp vào `attempt_items` khi bắt đầu. Chấm trắc nghiệm chỉ diễn ra ở server; không gửi đáp án đúng vào HTML trước khi nộp. Nộp bài tăng tiến độ và thêm answer trong một transaction; unique constraint ngăn nộp trùng. Thang điểm trắc nghiệm mỗi câu là 0/10, điểm lượt là trung bình. Tự luận chấm bằng AI và có nhãn điểm gợi ý. File ảnh ngoài WAR không mất khi Clean and Build; ảnh không được liệt kê công khai.

## Xử lý lỗi thường gặp

- **Invalid target release: 17:** dùng `build.xml` đã sửa, chọn JDK 17 tại Project Properties → Libraries, kiểm tra dòng `Compiler: javac 17` rồi Clean and Build. **Unsupported class version:** chọn JDK 17 cho JVM Tomcat, khởi động lại server.
- **Không tìm thấy `jakarta.tags.core`:** thiếu một trong hai JAR JSTL Jakarta 3.x. Không dùng JSTL 1.2 hay JAR `javax.servlet`.
- **Không kết nối SQL:** kiểm tra instance, TCP/IP, cổng, SQL Authentication, quyền database và thông số JDBC. Không đưa mật khẩu database vào JSP.
- **No suitable driver found:** `DBContext` nạp tường minh `com.microsoft.sqlserver.jdbc.SQLServerDriver` vì Tomcat không tự quét driver trong `WEB-INF/lib`. Nếu đang chạy bản cũ, Stop Tomcat → Clean and Build → Run lại. WAR cần có `WEB-INF/lib/mssql-jdbc-12.8.1.jre11.jar`. Listener `JdbcDriverCleanup` hủy đăng ký driver của ứng dụng khi dừng/triển khai lại.
- **AI HTTP 403/404/429:** kiểm tra key, model, billing/hạn mức. Key rỗng sẽ trả lỗi rõ ràng, không tạo điểm hoặc câu hỏi giả.
- **Ảnh quá lớn / không đọc được:** giảm dung lượng, chụp rõ toàn bài; hệ thống không chấm 0 khi không đọc được.
- **Không có giao diện Bootstrap:** cần truy cập CDN jsDelivr; đây là lựa chọn CDN được yêu cầu.
- **NetBeans yêu cầu chọn server:** metadata server nằm ở cấu hình riêng máy, phải chọn Tomcat trong Properties → Run.

Xem [LEARNING-EXPERIENCE.md](docs/LEARNING-EXPERIENCE.md) cho bản cập nhật lộ trình Lịch sử 6, Tổng quan/Tiến độ, chat lưu database, sổ câu sai và tài khoản demo. Database cần migration 10 trước khi chạy WAR mới. `docs/COURSE-UPGRADE.md`, `docs/TEST-REPORT.md` và `docs/HTTP-CHECKS.txt` mô tả các bản trước.
