# Lộ trình Lịch sử 6 và không gian học sinh

Bản cập nhật ngày 23/10/2026 tập trung vào Lịch sử 6. Phần Địa lý và các tính năng thanh toán, cộng đồng, gia sư thật được hoãn theo phạm vi mới.

## Các luồng đã triển khai

- `/courses`: khi chỉ có một chương trình đã xuất bản, chuyển thẳng tới lộ trình. Năm chương có tiến độ, trạng thái từng bài, điểm luyện tập cao nhất và ô tìm bài học không phân biệt dấu. Tìm theo giáo viên đã bỏ.
- `/explore`: trang giới thiệu công khai và luôn hiển thị, không chuyển thẳng như `/courses`. Hero lấy số liệu thật từ database (chương trình đang mở, bài học, câu hỏi); bên dưới là lộ trình dạng accordion — mở một chương để xem danh sách bài và mở bài ở chế độ học thử `/preview`. Không hiển thị tiến độ của học sinh ở trang công khai.
- `/student/dashboard`: tiếp tục bài học, bài luyện đang dở, sổ câu sai, thông báo tự luận và ô hỏi AI nhanh. Không còn ba thẻ số liệu trùng trang Tiến độ.
- `/student/progress`: số bài hoàn thành theo chương, điểm trung bình trắc nghiệm, biểu đồ 20 lượt gần nhất, chủ đề cần ôn và bài đã đánh dấu.
- `/student/history`: từng lượt làm, thời gian Việt Nam, điểm và liên kết xem lại. Trang kết quả có điểm tổng, số câu đúng/sai và hỏi AI theo câu.
- Sidebar học sinh có đúng năm mục. Hồ sơ, đổi mật khẩu, đăng xuất nằm trong menu tên học sinh. Điện thoại dùng nút Menu; Escape đóng menu.
- Bài học có video, nội dung, đánh dấu đã học, bài trước/sau, luyện trắc nghiệm/tự luận, ghi chú riêng, đánh dấu bài và thẻ ghi nhớ. Lộ trình có dòng thời gian.
- Gia sư nhận `itemId` từ bài học hoặc `answerId` từ câu đã làm, kiểm tra quyền ở server. Hội thoại lưu trong database và tải lại sau khi đổi phiên đăng nhập. Bắt đầu lại lưu bản hội thoại trước vào bảng lưu trữ.
- Câu trắc nghiệm sai không hiển thị đáp án/giải thích đúng trong HTML; gia sư dùng gợi ý từng bước. Tự luận tiếp tục dùng tải ảnh, chấm AI, luyện từng bước và nộp ảnh làm lại đã có.
- Sổ câu sai dùng câu trả lời gần nhất theo câu hỏi và chương trình. Làm lại đúng loại câu khỏi sổ, giữ nguyên lượt cũ. Mỗi lượt ôn tối đa năm câu, bấm lại sẽ tiếp tục lượt đang dở.
- Khi tự luận có kết quả `AI_GRADED`, Tổng quan hiển thị thông báo của riêng học sinh; cập nhật mỗi 30 giây khi tab đang hiển thị. Đánh dấu đã đọc được lưu theo lần nộp ảnh.

## Phân quyền quản trị và giáo viên

- **ADMIN — quản trị nền tảng:** tài khoản (tạo, sửa, khóa, xóa), danh mục môn học → bài học → chủ đề và cấu hình hệ thống (prompt gia sư, API AI). Trang giám sát gồm duyệt chương trình, dữ liệu bài làm, tiến độ, báo cáo; nhóm "Kho dùng chung" giữ quyền chia sẻ bài giảng/câu hỏi cho giáo viên.
- **TEACHER — toàn bộ nội dung học:** chương trình phụ trách, bài giảng, ngân hàng câu hỏi, soạn câu hỏi với AI, học viên và tiến độ trong khóa mình phụ trách.
- Giáo viên không tạo/sửa môn học, bài học, chủ đề: `/teacher/catalog?entity=subjects|lessons|topics` trả 403. Ngược lại, giáo viên được gắn bài giảng/câu hỏi vào chủ đề do ADMIN tạo (chủ đề là danh mục dùng chung), trong khi chỉ sửa được nội dung do chính mình tạo.
- Sidebar chia nhóm theo vai trò; ADMIN không thấy lối tắt giáo viên và ngược lại. Kiểm tra quyền vẫn nằm ở `AppFilter` và service, không chỉ ẩn menu.
- Nút **Hiện/Ẩn** cạnh ô mật khẩu trong form tài khoản ADMIN chỉ đổi `type` của input tại trình duyệt, không gửi thêm dữ liệu lên server.


## Cách tính số liệu

Tiến độ lý thuyết chỉ tăng khi học sinh đánh dấu đã học; xem video hoặc mở bài không tự hoàn thành. Điểm chương là trung bình các câu trắc nghiệm thuộc lượt đã hoàn tất, bao gồm ôn lại. Biểu đồ chỉ dùng lượt trắc nghiệm hoàn tất đủ điểm, bao gồm điểm 0. Chủ đề cần ôn có trung bình dưới 6/10 theo câu trả lời gần nhất cho từng câu hỏi; giao diện hiển thị số câu đã làm để tránh hiểu đây là đánh giá toàn bộ năng lực.

## Dữ liệu và tài khoản demo

Lộ trình giữ nguyên 20 bài, 20 video và 100 câu trắc nghiệm. Nội dung bổ sung gồm **40 câu tự luận** (hai câu mỗi bài), 20 thẻ ghi nhớ và 10 mốc lịch sử, lưu trong `src/conf/history-study-tools.json`. Câu tự luận được chèn vào ngân hàng câu hỏi đang dùng bởi giáo viên và học sinh.

- Email: `demo.history6@suviet.local`
- Mật khẩu: `DemoSuViet6!2026`
- Tên: **Học sinh minh họa – Lịch sử 6**.
- Có sáu lượt trắc nghiệm đã hoàn tất với điểm 4, 6, 2, 8, 6, 10; sáu bài hoàn thành; bài đang đọc, ghi chú và đánh dấu. Các câu trả lời có nhãn dữ liệu minh họa. Đây là dữ liệu demo được lưu thật, không phải kết quả học sinh thật hoặc nhận xét AI giả.

## Cập nhật database và chạy

Yêu cầu migrations 04, 07, 08, 09 đã có. Migration 10 chỉ thêm bảng hội thoại, lưu trữ hội thoại, trạng thái đọc thông báo và cờ `attempts.review_mode`. Không sửa điểm hoặc lịch sử hiện có.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/migrate-learning-experience.ps1 -ConfigPath "<config.properties>"
```

Script tạo backup COPY_ONLY có CHECKSUM, chạy RESTORE VERIFYONLY và so sánh dấu vân tay dữ liệu trước/sau. Sau khi biên dịch `ant dist test`, thêm nội dung/demo một lần bằng:

```powershell
& 'C:/Program Files/Java/jdk-17/bin/java.exe' '-Dtutor.config=<config.properties>' -cp 'build/test/classes;build/web/WEB-INF/classes;lib/*;C:/Program Files/Apache Software Foundation/Tomcat 10.1/lib/servlet-api.jar' vn.suviet.HistoryExperienceSeed --apply
```

Seed chạy trong transaction, có marker chống chạy trùng và từ chối ghi đè tài khoản trùng email. Không tự chạy khi ứng dụng khởi động. Thực hiện backup trước khi dùng seed. Sau đó dùng `scripts/run-vscode.ps1 -Port 8082 -SkipBuild` để chạy WAR mới.

## Kiểm thử

- `ant test`: 30 core checks và 40 Gemini HTTP contract checks dùng máy chủ giả lập cục bộ.
- `LearningExperienceTests`: 76 kiểm tra tích hợp trên database riêng `_UpgradeTest_`, bao gồm JSP, ba vai trò, nội dung do giáo viên tạo, dữ liệu 20 bài, quyền xem bài làm, chat xuyên phiên, lease chống yêu cầu đồng thời, reset không mất hội thoại, ôn câu sai và thông báo riêng tư.
- Lỗi so sánh `BigDecimal 10.00` với `10` trong JSP đã sửa thành `ge 10`; kiểm thử xác nhận lượt 4/5 câu đúng và điểm trung bình 8.
- Chưa xác nhận bố cục bằng ảnh chụp trình duyệt trong phiên này vì công cụ trình duyệt không cung cấp browser/tab. Menu và CSS điện thoại đã triển khai; cần kiểm tra trực quan trên điện thoại trước buổi báo cáo.

Kết quả chạy bản chính được ghi tại `docs/LEARNING-EXPERIENCE-CHECKS.txt`. AI thực tế phụ thuộc cấu hình key/model và hạn mức hiện có; lỗi nhà cung cấp không tạo câu trả lời hoặc điểm giả.

## Phân quyền quản trị và nội dung

Từ migration 11, quyền quản trị tách khỏi quyền soạn nội dung: ADMIN chỉ quản lý tài khoản, danh mục, phân công, cấu hình, báo cáo và ẩn/gỡ nội dung vi phạm kèm lý do; giáo viên sở hữu bài giảng/câu hỏi và xuất bản chương trình được phân công. Chi tiết ma trận quyền, quy tắc chia sẻ/sao chép, phiên bản nội dung, phạm vi câu hỏi luyện tập và cách chạy kiểm thử nằm trong [ROLE-GOVERNANCE.md](ROLE-GOVERNANCE.md).

