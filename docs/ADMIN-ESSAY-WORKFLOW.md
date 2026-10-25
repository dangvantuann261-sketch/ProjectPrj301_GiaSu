# Quản trị và tự luận từng bước

Admin dùng `/admin/*`: người dùng; môn/bài/chủ đề/bài giảng/câu hỏi; khóa học, phân công, ghi danh; dữ liệu bài làm; thống kê và báo cáo; cấu hình AI. Admin bị chặn khỏi `/teacher/*`, `/student/*` và API dành cho học sinh ngay tại filter. Giáo viên và học sinh không truy cập được `/admin/*`. Chức năng soạn câu hỏi với AI thuộc giáo viên.

## Cấu hình AI

Mở **Admin → Cấu hình AI** (`/admin/settings`). Có hai kết nối: gia sư/soạn nội dung và tự luận/đánh giá ảnh. Mỗi kết nối có link API tương thích Gemini (HTTPS, kết thúc `/models/`), model, thời gian chờ và API key. Chưa hỗ trợ giao thức OpenAI-compatible.

Nhập key mới để thay; để trống giữ key đang có. Ô dùng lại key sẽ bỏ override và dùng key cấu hình máy chủ (API chung) hoặc key API chung (tự luận). Khi chưa lưu cấu hình qua Admin, ứng dụng tiếp tục dùng `config.properties` hiện có. Thay đổi từ Admin áp dụng cho yêu cầu tiếp theo, không cần khởi động lại.

Key lưu trong SQL được mã hóa AES-256-GCM và không trả về HTML/JSON. Khóa mã hóa nằm ngoài WAR tại `${catalina.base}/conf/ai-settings.key`; có thể chỉ định đường dẫn bền vững bằng `-Dtutor.aiKeyFile=C:/.../ai-settings.key`. Hạn chế quyền đọc file cho tài khoản chạy máy chủ. **Sao lưu cả database và file khóa** khi di chuyển/khôi phục máy chủ. Mất file này thì phải nhập lại key API. Nhật ký chỉ ghi sự kiện thay đổi, không ghi key.

Prompt chung và prompt tự luận có thể sửa. Quy tắc bắt buộc được ghép vào yêu cầu sau prompt để yêu cầu AI chỉ đưa một công thức/khung có chỗ trống. Chất lượng công thức, nhận xét và đánh giá đúng/sai vẫn phụ thuộc model; cần kiểm tra với model thực trước khi sử dụng trong lớp.

## Quy trình học sinh

1. Nộp ảnh JPG/PNG hợp lệ. Lưu ảnh trước, chưa có điểm.
2. Chọn nhận đánh giá AI. Không đọc được ảnh thì cho nộp ảnh rõ hơn; API lỗi giữ ảnh và cho thử lại, không gán điểm 0.
3. Bài cần sửa: chọn bắt đầu hướng dẫn. Mỗi lượt chỉ hiện một công thức hoặc khung lập luận có `___` để học sinh tự điền. Với Lịch sử dùng khung sự kiện/nguyên nhân/kết quả.
4. Sai: máy chủ giữ nguyên bước và chỗ trống; đúng: chuyển đúng một bước. Tải lại trang hoặc đăng nhập phiên khác vẫn tiếp tục bước đã lưu. Chat chung không nhận bài tự luận để bỏ qua quy trình.
5. Hoàn tất các bước: chỉ cho làm lại trên giấy và nộp ảnh mới. Chặn gửi lại đúng cùng tệp ảnh. Hệ thống không thể xác minh chỉ từ sự khác nhau của tệp rằng học sinh đã thực sự tự làm lại.
6. Khi nộp lại, lưu ảnh/điểm/nhận xét/hội thoại cũ vào `essay_answer_revisions`; ảnh mới trở về trạng thái chờ đánh giá. Lịch sử và ảnh cũ chỉ chủ bài làm hoặc Admin xem được. Điểm tự luận là đề xuất của AI.

Trạng thái và phiên bản được kiểm tra trên máy chủ; khóa xử lý chặn các lần gửi trùng/đồng thời. Ảnh làm lại không tạo lượt luyện tập hoặc tăng số câu đã nộp.

## Cập nhật database

Sau migration 04, chạy `database/07-admin-essay-workflow.sql`. Không chạy lại các script tạo bảng/dữ liệu. Script PowerShell sau tạo và xác minh backup trước, kiểm tra số dòng và SHA-256 dữ liệu cũ:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/migrate-admin-essay.ps1 -ConfigPath 'C:/path/config.properties' -TestCopy
# Sau kiểm thử, bỏ -TestCopy để áp dụng cho database thật.
```

Script cần tài khoản Windows quản trị SQL Server; quyền của tài khoản ứng dụng không thay đổi. Bản sao thử nghiệm gỡ các override key/API đã lưu để tránh gọi dịch vụ thật. Bản sao dùng uploads riêng trong `.local/`.

## Kiểm thử

`ant clean dist test`: kiểm tra lõi và HTTP Gemini giả lập. `CourseIntegrationTests` và `CourseHttpTests`: hồi quy SQL/HTTP trên database `_UpgradeTest_`, AI tắt. `AdminEssayHttpTests`: quyền Admin/Teacher/Student, CSRF, lưu key mã hóa, thay đổi cấu hình tức thời, các bước đúng/sai, dữ liệu AI sai định dạng, gửi trùng, tiếp tục qua phiên đăng nhập, nộp lại và quyền xem ảnh cũ. Chỉ chạy bài kiểm tra mới với `ai.baseUrl=http://127.0.0.1:18091/v1beta/models/` trong file cấu hình thử nghiệm và Tomcat thử nghiệm dùng cùng file. Bài kiểm tra tự khởi động API giả lập trên cổng đó; không gọi Google.
