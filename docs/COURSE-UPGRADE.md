# Nâng cấp khóa học — 06/10/2026

## Phạm vi đã triển khai

Giữ Java 17, Servlet/JSP/JSTL, JDBC SQL Server, Ant, Tomcat 10.1 và Bootstrap 5.3.3. Không thêm thư viện runtime hay thay thế hệ thống cũ.

1. `/explore`, `/courses`: trang giới thiệu, danh mục công khai, tìm tên, lọc môn/khối/giáo viên, phân trang, FAQ.
2. `/course?id=…`, `/preview?id=…`: mục tiêu, đối tượng, đầu vào, chương trình, giáo viên; chỉ bài được cho phép mới học thử công khai.
3. `/teacher/courses`: tạo bản nháp, tái sử dụng bài giảng cùng môn, sắp thứ tự/nhóm chương, bài bắt buộc/bổ sung, học thử, quản lý ghi danh và tiến độ.
4. `/student/dashboard`, `/student/courses`: khóa đã đăng ký, thống kê, trạng thái chờ duyệt, học tiếp, lượt luyện tập còn dở.
5. `/student/lesson`: video/tài liệu, bài đọc, đánh dấu đã học, ghi chú tối đa 8.000 ký tự, bookmark, bài trước/sau. Dữ liệu lưu SQL Server và còn sau logout.
6. `/student/profile`: họ tên, khối lớp, môn quan tâm, đổi mật khẩu. Không hỗ trợ tự đổi email, quên mật khẩu/email xác nhận trong đợt này.
7. Nộp ảnh tách khỏi AI: lưu ảnh + answer trước, điểm NULL; yêu cầu chấm ở trang xem lại. PENDING / PROCESSING / AI_GRADED / RETRY / MANUAL_REQUIRED; MCQ là RULE_GRADED. AI lỗi không tự ghi 0 và không yêu cầu tải lại ảnh đã lưu.
8. Audit log cho thay đổi khóa, phân công, ghi danh và nội dung; chưa có màn hình tra cứu audit riêng.

## Quy tắc mặc định của bản này

- Giáo viên tự tạo nháp và được phân công vào khóa mình tạo; chỉ ADMIN thêm/bỏ giáo viên và xuất bản. ADMIN cũng tạo được nháp nhưng phải phân công giáo viên trước khi xuất bản.
- Khóa miễn phí có hai chế độ DIRECT và APPROVAL, chọn khi tạo. Không có thanh toán.
- Ghi danh duy nhất theo khóa + học sinh, chống trùng cả khi gửi đồng thời. PENDING/REJECTED/REVOKED không được đọc nội dung riêng. Giáo viên phụ trách/ADMIN có thể cấp lại quyền.
- Khóa có ít nhất một bài bắt buộc và một giáo viên hoạt động mới được xuất bản.
- Chương trình chỉ thay đổi khi DRAFT và chưa từng có ghi danh. Đã có ghi danh thì không về DRAFT. Không có curriculum versioning: tạo khóa mới khi cần thay chương trình.
- Bài giảng gắn vào khóa được bảo vệ khỏi sửa/xóa trực tiếp, kể cả ADMIN. Muốn sửa trước xuất bản: bỏ khỏi các khóa nháp liên quan rồi chỉnh; muốn đổi chương trình đã có học sinh: tạo nội dung/khóa mới.
- Hoàn thành = đã đánh dấu tất cả bài bắt buộc. Mở trang/phát video không tự hoàn thành. Đây là tự xác nhận đã học, không phải chứng nhận thi đạt.
- ARCHIVED ngừng nhận ghi danh và ẩn khỏi danh mục; học sinh ACTIVE vẫn học được. Không xóa dữ liệu khi lưu trữ/thu hồi quyền.
- Ghi chú chỉ có giao diện/API cho chính học sinh; giáo viên xem số bài hoàn thành, không đọc ghi chú riêng.
- Nội dung legacy được giữ trong thư viện; bài giảng/câu hỏi mới mặc định private. ADMIN quyết định chia sẻ. Giáo viên không sửa nội dung đã chia sẻ để tránh thay đổi ngân hàng chung.
- Giáo viên không xem bài luyện tự do/ảnh của mọi học sinh; quyền này chỉ có chủ bài làm và ADMIN. Điểm luyện tự do chưa gắn với điểm khóa.

## Các phần chưa triển khai (không phải đã hoàn tất toàn bộ nền tảng)

- Bài tập chính thức trong khóa, hạn nộp, rubric, điểm giáo viên xác nhận, chấm tay và nộp lại nhiều phiên bản.
- Q&A, thông báo, cộng đồng, báo cáo/kiểm duyệt, lịch gia sư, thanh toán.
- Chat AI bền vững theo bài học. Chat cũ vẫn lưu session, mất khi logout/hết phiên; liên kết từ bài học tới chat chung chưa mang ngữ cảnh khóa.
- Video completion tracking, chứng chỉ, cập nhật curriculum có version, upload tài liệu khóa.
- Hạn mức AI toàn tài khoản/hệ thống, queue nền, connection pool, rate limit đăng nhập đa phiên/IP, phân trang cho danh sách quản lý quy mô lớn.

Không gọi Gemini thật trong đợt kiểm thử: kiểm tra nhánh không cấu hình AI và tính bền vững của bài nộp. Khi triển khai thật cần kiểm chứng model/key, chi phí và chất lượng đầu ra.

## Migration và bảo toàn dữ liệu

`database/04-courses.sql` là migration thêm bảng/cột, có transaction, schema_migrations version=4 và chạy lặp an toàn. Không xóa accounts/content/history. Điểm đổi từ NOT NULL sang nullable nhưng giữ nguyên DECIMAL(5,2). Các bảng mới có FK, unique constraints và index phục vụ các truy vấn chính.

`scripts/migrate-courses.ps1` dùng Windows Integrated Security của quản trị SQL, không nâng quyền tài khoản ứng dụng. Script:

1. COPY_ONLY backup có CHECKSUM và RESTORE VERIFYONLY.
2. Sao chép thư mục upload sang `.local/backups/<timestamp>/uploads` nếu tồn tại.
3. Với `-TestCopy`: RESTORE sang DB có tên chứa `_UpgradeTest_<timestamp>`, không ghi đè database khác.
4. So sánh số dòng và SHA-256 JSON của tất cả cột cũ trong 10 bảng trước/sau migration.
5. Kiểm tra FK không bị disable/untrusted.

Chạy script khi Tomcat đã dừng để dữ liệu không thay đổi trong lúc chụp fingerprint. Script yêu cầu sysadmin Windows; nếu không có, nhờ DBA backup rồi chạy SQL trong SSMS. Script không tự rollback DB đã commit nếu bước kiểm chứng hậu migration lỗi: dừng triển khai, kiểm tra backup và trạng thái trước khi tiếp tục.

Không chạy lại `00`–`02` trên dữ liệu hiện hữu. Không chuyển test-config thành cấu hình production. `.local/test-config.properties` chứa thông tin truy cập DB và bị gitignore; không chia sẻ `.local`.

## Chạy và kiểm thử lặp lại

Đường dẫn gốc trên máy hiện tại:
`C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\outputs\ProjectPrj_GiaSu`

```powershell
$root = 'C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\outputs\ProjectPrj_GiaSu'
$config = 'C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\SuVietTutorData\config.properties'
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

# Backup + restore database kiểm thử + migration (không sửa DB gốc)
& "$root\scripts\migrate-courses.ps1" -ConfigPath $config -TestCopy

# Terminal riêng, giữ mở; không dùng cùng TomcatBase với production
& "$root\scripts\run-vscode.ps1" -ConfigPath "$root\.local\test-config.properties" -Port 18086 -TomcatBase "$root\.local\tomcat-test"

# Terminal khác: suite có guard chỉ cho _UpgradeTest_, AI phải để trống
& "$root\scripts\test-courses.ps1" -TestConfigPath "$root\.local\test-config.properties"

# Sau khi test đạt, dừng production, chạy migration cho DB thật rồi khởi động
& "$root\scripts\migrate-courses.ps1" -ConfigPath $config
& "$root\scripts\run-vscode.ps1" -ConfigPath $config -Port 8082
```

Build standalone dùng JDK 17 tường minh và Ant có sẵn; `ant test` chỉ chạy 18 core checks. Các suite DB/HTTP chỉ chạy khi yêu cầu riêng, tạo dữ liệu tổng hợp trong database kiểm thử và giữ lại để kiểm tra, không dọn dữ liệu production.

### Kết quả đã quan sát

- Clean build: 36 Java source + 3 Java test, WAR tạo thành công.
- 18 core checks.
- 29 SQL integration checks: unique enrollment dưới 6 request đồng thời; ownership, khóa curriculum, hoàn thành không tính optional; lưu ghi chú, ảnh còn khi AI lỗi.
- 70 HTTP checks: login 3 vai trò, guest/public, CRUD khóa, publish, enrollment/approve, CSRF, IDOR, private library, JSP, profile, note escape, logout/login, ảnh multipart thật/giả, AI unavailable, đổi password vô hiệu phiên khác.
- Restore từ backup trên SQL Server thật thành công; migration chạy lặp thành công; FK hợp lệ.
- Chrome headless đã render trang công khai desktop và đo mobile 390px: `innerWidth = clientWidth = scrollWidth = 390`.
- Không có kiểm thử tải, pentest chuyên sâu, mọi trình duyệt, mọi màn hình mobile hay Gemini thật. Không tái sử dụng con số 198 HTTP checks trong báo cáo cũ.
- Suite cuối cùng đã chạy lại qua `scripts/test-courses.ps1`: 18 + 29 + 70 kiểm tra đạt. Log lưu tại `docs/COURSE-TEST-RESULTS.txt`.
- Sau triển khai thật, `/`, `/explore`, `/courses`, `/login`, `/register` và 3 CSS/JS đã kiểm tra HTTP 200. WAR đang chạy có SHA-256 `B1DD29B406D9BDCDF0678537500A63F7DB75C7F8F55CA9A90FE75855882A996D`, khớp WAR build.
- Kiểm tra đọc DB thật sau migration: version 4, 4 tài khoản, 4 lượt luyện tập, 0 khóa học; tài khoản ứng dụng không có quyền ALTER database. Các dữ liệu kiểm thử chỉ ở database `_UpgradeTest_`.

## Hướng dẫn sử dụng lần đầu

1. Đăng nhập giáo viên, tạo/sử dụng nội dung riêng; vào **Khóa phụ trách** tạo nháp, chọn môn và kiểu ghi danh.
2. Thêm bài giảng cùng môn; chọn học thử/bắt buộc và thứ tự.
3. ADMIN kiểm tra, phân công giáo viên (nếu chưa có), đổi trạng thái **Xuất bản**.
4. Học sinh vào **Khám phá khóa học**, xem chương trình/học thử và đăng ký. Giáo viên duyệt nếu APPROVAL.
5. Học sinh vào **Khóa học của tôi**, học tiếp, ghi chú, lưu bài cần ôn và đánh dấu đã học.

Không tự sinh khóa mẫu vào DB thật. Danh mục trống sau nâng cấp là bình thường cho tới khi có khóa được xuất bản.

## Rollback vận hành

### Bản sao lưu tại máy trong lần triển khai này

- Mã nguồn trước thay đổi: `C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\outputs\ProjectPrj_GiaSu-before-courses-20261006-102423.zip`.
- Database thật trước migration: `D:\Setup\MSSQL16.MSSQLSERVER01\MSSQL\Backup\SuVietTutor-before-courses-20261006-105918.bak` (COPY_ONLY, CHECKSUM, VERIFYONLY đạt).
- WAR cũ: `C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\outputs\ProjectPrj_GiaSu\.local\backups\deployment-before-courses\SuVietTutor.war`.
- Upload backup được script đặt dưới `.local/backups/20261006-105918/` nếu thư mục nguồn tồn tại.
- Migration trên DB thật đã hoàn tất; SHA-256 các cột cũ và số dòng của cả 10 bảng trước/sau giống nhau. Không tạo tài khoản hoặc khóa mẫu trên DB thật.

Giữ source ZIP, WAR cũ, SQL backup và upload backup. Ưu tiên dừng rollout rồi sửa tiến về phía trước vì migration chỉ bổ sung. Nếu cần quay lại trọn phiên bản: dừng Tomcat; DBA restore bản SQL đã kiểm chứng vào DB riêng, đối chiếu và chuyển cấu hình; khôi phục upload tương ứng; triển khai WAR/source cũ. Không chạy DROP hàng loạt hoặc tự động ghi đè DB đang chứa dữ liệu sau nâng cấp. Restore về thời điểm cũ sẽ mất mọi thay đổi sau backup nếu không có kế hoạch hợp nhất/log backup.