# Học theo chương và bài

Luồng học hiện tại là **Chương trình → Chương → Bài học → Luyện tập bài / Ôn tập chương**, tham khảo cách tổ chức nội dung tại https://olm.vn/bg/lich-su-dia-ly-6/.

- Học sinh mở bài trực tiếp trong chương trình đã xuất bản. Không đăng ký, không chờ duyệt. Tài khoản học sinh cần đăng nhập để lưu tiến độ và làm bài.
- Mỗi chương hiển thị bài học, tiến độ lý thuyết, số bài đã luyện, nút ôn tập chương. Có thể thu gọn từng chương.
- Luyện tập bài: tối đa 5 câu. Ôn tập chương: tối đa 10 câu trắc nghiệm, chọn từ các chủ đề của bài giảng trong đúng chương đó. Câu hỏi phải còn hiệu lực; không phụ thuộc cờ chia sẻ thư viện.
- Bấm luyện tập lần nữa sẽ tiếp tục lượt cùng phạm vi/loại còn dở. Hoàn tất rồi có thể tạo lượt mới.
- Lưu số lượt làm, số lượt hoàn tất và điểm cao nhất của lượt hoàn tất đã chấm đủ. Điểm 0 được hiển thị. Lượt làm dở không làm tăng điểm cao nhất.
- Tiến độ lý thuyết = số bài bắt buộc đánh dấu đã học / tổng bài bắt buộc. Mở bài không tự đánh dấu hoàn thành; luyện tập được theo dõi riêng.
- Giáo viên xem tiến độ từng chương, từng bài, số lượt luyện tập và điểm cao nhất tại **Tiến độ học tập** → chọn học sinh. Báo cáo vẫn giới hạn theo chương trình được phân công; ghi chú cá nhân được giữ riêng.
- Thư viện tự học không còn trong điều hướng. URL cũ `/student/learn` chuyển sang chương trình học; `/student/topic` chuyển về bài tương ứng nếu có. Endpoint `/student/enroll` trả HTTP 410. Quản lý chương trình không còn duyệt ghi danh.

## Cập nhật database hiện có

Sao lưu database và ảnh bài làm, rồi mở SSMS, chọn database ứng dụng và chạy `database/09-chapter-learning.sql` bằng tài khoản quản trị. Database phải có migrations 04, 07 và 08 trước đó. Với cài mới, chạy schema/seed và các migrations đó trước 09.

Migration bổ sung liên kết bài/chương vào `attempts` và nới ràng buộc tối đa 10 câu chỉ cho ôn tập chương. Chạy lại an toàn. Không xóa hay gán ngược lịch sử cũ vào chương/bài vì không đủ thông tin để xác định chính xác. Lịch sử cũ vẫn xem được trong tài khoản học sinh.

Các bảng `courses`, `course_items`, `enrollments`, `learning_records` được giữ để bảo toàn dữ liệu: `section_title` xác định chương trong từng chương trình; `enrollments` chỉ dùng làm mã hồ sơ tiến độ, tự tạo khi học sinh mở bài hoặc bắt đầu luyện tập. Trạng thái chờ/từ chối/thu hồi cũ không chặn chương trình đang xuất bản. Bản nháp vẫn riêng tư; chương trình lưu trữ chỉ mở cho học sinh đã có hồ sơ ACTIVE.

Sau migration, build `dist/SuVietTutor.war` rồi triển khai và khởi động lại Tomcat. Chỉ triển khai WAR sau khi database đã có migration 09.

## Kiểm thử

`ant dist test` chạy 30 kiểm tra lõi và 40 kiểm tra hợp đồng Gemini bằng máy chủ giả lập cục bộ.

Bộ kiểm thử mới `vn.suviet.ChapterLearningTests` chỉ chấp nhận database có `_UpgradeTest_` trong tên; dùng tài khoản ứng dụng như bình thường, sau khi quản trị viên đã áp dụng migration 09 trên bản sao đó:

```powershell
& 'C:/Program Files/Java/jdk-17/bin/java.exe' '-Dtutor.config=.local/learning-progress-test-config.properties' -cp 'build/test/classes;build/web/WEB-INF/classes;lib/*;lib/compile-only/*' vn.suviet.ChapterLearningTests 'http://127.0.0.1:18093/SuVietTutor'
```

Bộ kiểm thử tạo dữ liệu thử mới, kiểm tra học trực tiếp, phạm vi câu hỏi chương/bài, tiếp tục lượt chưa xong, điểm và tiến độ riêng từng học sinh, quyền giáo viên, các trang JSP và các URL cũ. Không dùng nó trên database chính. Các bài kiểm thử khóa học cũ có giả định đăng ký/chờ duyệt không còn đại diện cho luồng học mới.
