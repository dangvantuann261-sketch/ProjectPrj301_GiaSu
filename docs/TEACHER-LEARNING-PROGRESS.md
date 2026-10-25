# Tiến độ lớp và lịch sử học tập

Giáo viên mở **Tiến độ học tập** (`/teacher/progress`). Trong cấu trúc hiện tại, lớp được theo dõi theo từng **khóa học đã được Admin phân công**, chưa có thực thể lớp/học kỳ riêng. Link cũ `/teacher/students` cũng mở màn hình này. Admin xem báo cáo tại `/admin/progress`.

Màn hình có bộ lọc khóa, tên/email học sinh, trạng thái ghi danh và tiến độ; sắp xếp theo tên, hoạt động gần nhất hoặc tỷ lệ hoàn thành. Mỗi dòng là một học sinh trong một khóa, phân trang 20 dòng. Các số tổng hợp tính trên toàn bộ kết quả của bộ lọc, không chỉ trang hiện tại.

- **Chưa bắt đầu:** chưa có bản ghi học tập trong khóa.
- **Đang học:** đã có hoạt động nhưng chưa hoàn thành mọi bài bắt buộc.
- **Đã hoàn thành:** khóa có ít nhất một bài bắt buộc và học sinh đã hoàn thành tất cả bài bắt buộc.
- **Tỷ lệ:** số bài bắt buộc hoàn thành / tổng bài bắt buộc. Bài mở rộng không làm tăng tỷ lệ; mở bài không tự đánh dấu hoàn thành.

Chọn **Xem lịch sử** để xem tiến độ từng bài, lần truy cập gần nhất, thời điểm hoàn thành và nhật ký truy cập/hoàn thành. Tất cả giờ hiển thị là giờ Việt Nam. Nhật ký mới chỉ bắt đầu từ khi cập nhật tính năng; dữ liệu cũ giữ nguyên tiến độ và thời điểm truy cập có sẵn. Không suy đoán thời điểm hoàn thành từng bài đã có từ trước. Các lần mở lại cùng bài trong 5 phút được gộp thành một sự kiện; thời điểm truy cập gần nhất vẫn cập nhật. Hoàn thành cùng bài nhiều lần chỉ ghi một sự kiện.

Giáo viên chỉ đọc được tiến độ của khóa hiện còn được phân công. Quyền được kiểm tra tại filter và trong từng truy vấn tổng hợp/danh sách/chi tiết/nhật ký. Dù học sinh học ở nhiều khóa, giáo viên không được đọc tiến độ ở khóa khác. Gỡ phân công có hiệu lực với phiên đăng nhập đang mở. Ghi chú, đánh dấu riêng và bài luyện tự do không xuất hiện trong báo cáo. Chưa đo thời lượng xem video và không dùng số lần truy cập để suy ra thời gian học.

## Cập nhật và kiểm thử

Migration `database/08-learning-progress.sql` bổ sung bảng `learning_activity`, ràng buộc sự kiện phải thuộc đúng khóa và index cho lịch sử. Không sửa dữ liệu tiến độ cũ. Chạy sau migration 04; bản ứng dụng hiện tại cũng cần migration 07 cho tính năng tự luận.

```powershell
# Tạo backup đã xác minh, phục hồi bản sao thử nghiệm, áp dụng migration 08.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/migrate-learning-progress.ps1 -ConfigPath 'C:/path/config.properties' -TestCopy
# Sau kiểm thử, chạy lại không có -TestCopy để cập nhật database chính.
```

Script xác minh số dòng và SHA-256 các bảng cũ, bao gồm ghi danh và tiến độ. Bản sao thử nghiệm tắt key AI và dùng uploads riêng.

Sau `ant clean dist test`, chạy Tomcat thử nghiệm với `.local/learning-progress-test-config.properties` trên cổng 18092, sau đó:

```powershell
& 'C:/Program Files/Java/jdk-17/bin/java.exe' '-Dtutor.config=.local/learning-progress-test-config.properties' -cp 'build/test/classes;build/web/WEB-INF/classes;lib/*;lib/compile-only/*' vn.suviet.LearningProgressTests 'http://127.0.0.1:18092/SuVietTutor'
```

Bài kiểm tra chỉ chạy trên database `_UpgradeTest_`: quyền giữa giáo viên, cùng học sinh khác khóa, gỡ phân công, không lộ ghi chú, tỷ lệ bài bắt buộc, ghi sự kiện một lần, múi giờ, lọc/sắp xếp và phân trang. Dữ liệu thử được giữ trong bản sao để kiểm tra.

Tham khảo cách tổ chức chỉ số tiến độ/hoàn thành: [Coursera EnrollmentReport](https://dev.coursera.com/docs/coursera-for-business-api-product/1/types/EnrollmentReport), [Coursera – xem tiến độ theo khóa](https://www.coursera.support/s/article/learner-000002219?language=en_US). Quy tắc hoàn thành của dự án là hoàn thành các bài bắt buộc, không sao chép quy tắc tính điểm của Coursera.
