# Phân quyền quản trị và nội dung giảng dạy

Tài liệu này mô tả ma trận quyền đang được **thực thi ở server** (filter → servlet → service → truy vấn), không chỉ ẩn nút giao diện. Áp dụng từ migration `11-content-governance.sql`.

## 1. Ai được làm gì

| Năng lực | ADMIN | TEACHER | Học sinh |
|---|---|---|---|
| Tài khoản giáo viên/học sinh: thêm, sửa, khóa, mở khóa | ✅ | ❌ | ❌ |
| Cấu hình hệ thống, API key, model AI | ✅ | ❌ | ❌ |
| Danh mục môn học, bài học, chủ đề | ✅ | ❌ (403) | ❌ |
| Tạo chương trình, sửa thông tin chương trình | ✅ | ✅ (chương trình mình phụ trách) | ❌ |
| Phân công/bỏ giáo viên | ✅ | ❌ | ❌ |
| Sắp xếp chương/bài, thêm/bớt bài giảng trong chương trình | ❌ | ✅ | ❌ |
| Soạn/sửa/xóa bài giảng, câu hỏi, đáp án, hướng dẫn chấm | ❌ | ✅ (chỉ bản do mình tạo) | ❌ |
| Chọn câu hỏi luyện tập cho từng bài | ❌ | ✅ | ❌ |
| Chia sẻ nội dung cho giáo viên khác sao chép | ❌ | ✅ (nội dung của mình) | ❌ |
| Sao chép nội dung được chia sẻ thành bản riêng | ❌ | ✅ | ❌ |
| Xuất bản/lưu trữ chương trình | ✅ | ✅ (chương trình phụ trách) | ❌ |
| Ẩn/gỡ nội dung vi phạm kèm lý do + nhật ký | ✅ | ❌ (403) | ❌ |
| Xem thống kê, tiến độ toàn hệ thống | ✅ | ❌ | ❌ |
| Xem tiến độ học sinh thuộc chương trình phụ trách | — | ✅ | chỉ dữ liệu của mình |
| Luyện tập, xem bài làm của mình | ❌ | ❌ | ✅ |

## 2. Quy tắc nội dung được thực thi

- **Không còn bypass cho ADMIN.** `ContentAccessService.edit` chỉ cho tác giả sửa bài giảng/câu hỏi; admin sửa nội dung trả 403 kể cả khi gọi thẳng URL.
- **Chia sẻ khác xuất bản.** `library_visible` = cho giáo viên khác sao chép; `courses.status=PUBLISHED` = mở cho học sinh. Không dùng chung một cờ.
- **Sao chép giữ nguồn.** Bản sao lưu `copied_from_id`, thuộc sở hữu giáo viên sao chép, không sửa được bản gốc.
- **Luyện tập có phạm vi tường minh.** Bảng `course_item_questions` gắn câu hỏi vào từng mục học; `PracticeDao` chỉ chọn câu đã gắn, không lấy toàn bộ chủ đề. Migration 11 sao lưu hành vi cũ bằng cách gắn câu hỏi hiện có cho mục học hiện có.
- **Nội dung đã dùng được quản lý theo phiên bản.** Khi giáo viên sửa bài giảng/câu hỏi đang được chương trình dùng, hệ thống tạo bản ghi mới (`version+1`, `root_id`), đánh dấu bản cũ `superseded=1`, giữ nguyên bản cũ và lịch sử bài làm. Câu hỏi: liên kết luyện tập trỏ sang bản mới (bài làm đã nộp dùng snapshot). Bài giảng: chỉ chương trình nháp chưa có ghi danh tự trỏ sang bản mới; lớp đã học giữ bản cũ.
- **Ẩn nội dung không tự hiện lại.** Cờ `hidden`, `hidden_reason`, `hidden_by`, `hidden_at` nằm trên bản ghi; khi tạo phiên bản mới, trạng thái ẩn được sao chép sang. Mọi truy vấn cho học sinh (`CourseDao.item/items`, `PracticeDao`, `LearningServlet`) lọc `hidden=0`.
- **Nhật ký kiểm duyệt.** `audit_logs.detail` lưu lý do; action `CONTENT_HIDE_<bảng>` / `CONTENT_UNHIDE_<bảng>`. Lý do ẩn tối thiểu 10 ký tự.
- **URL trực tiếp cũng bị chặn.** `/preview` chỉ mở bài có `is_preview=1` hoặc người quản lý chương trình; giáo viên ngoài chương trình nhận 403 khi POST trạng thái/chương trình.

## 3. Cập nhật database

```powershell
# Sao lưu + áp dụng, chỉ thêm cột/bảng, giữ nguyên dữ liệu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/migrate-content-governance.ps1 `
  -ConfigPath 'C:\SuVietTutorData\config.properties'
```

Migration `11-content-governance.sql` thêm:

- `lectures`/`questions`: `hidden`, `hidden_reason`, `hidden_by`, `hidden_at`, `copied_from_id`, `version`, `root_id`, `superseded`.
- `audit_logs.detail`.
- `course_item_questions(course_item_id, question_id, created_by, created_at)` + index.
- Seed liên kết câu hỏi cho mọi mục học hiện có theo quy tắc cũ, và `root_id=id` cho nội dung cũ.

Script kiểm tra số dòng của `lectures`, `questions`, `course_items`, `attempts`, `answers` trước/sau và dừng nếu thay đổi.

## 4. Kiểm thử

```powershell
# Lõi + Gemini (không cần DB)
java -jar <ant-launcher.jar> -Dtutor.java.home="C:\Program Files\Java\jdk-17" test

# Tích hợp + HTTP trên database _UpgradeTest_ (không đụng dữ liệu chính)
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-courses.ps1 `
  -TestConfigPath .local/governance-test-config.properties `
  -BaseUrl http://127.0.0.1:18086/SuVietTutor
```

`CourseIntegrationTests` (46 checks) phủ: admin không sửa nội dung, giáo viên chéo, sao chép, ẩn kèm lý do + audit, phiên bản bài giảng/câu hỏi, phạm vi câu hỏi luyện tập, giáo viên phụ trách xuất bản. `CourseHttpTests` (77 checks) phủ URL trực tiếp, POST trái quyền, kiểm duyệt qua HTTP, preview riêng tư, dữ liệu học sinh.

## 5. Giới hạn đã biết

- `enrollment_mode=APPROVAL` hiện không chặn truy cập: chương trình đã xuất bản tự mở ghi danh khi học sinh xem bài (hành vi có từ migration 10). Đây là quyết định sản phẩm, không phải lỗi phân quyền mới.
- Giáo viên vẫn có thể gắn nội dung vào bất kỳ chủ đề nào trong danh mục dùng chung; nội dung chỉ đến học sinh qua chương trình được phân công.
- Việc chọn câu hỏi luyện tập chỉ mở khi chương trình còn nháp và chưa có ghi danh (cùng quy tắc khóa chương trình hiện hữu).
