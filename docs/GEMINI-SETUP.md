# Kết nối Gemini API

Project đã tích hợp Gemini ở phía Java server: chat gia sư, gợi ý chấm ảnh và soạn câu hỏi. Không cần cài thêm SDK, không đặt API key trong JavaScript/JSP và không gửi key vào cuộc trò chuyện hỗ trợ.

## 1. Tạo key hợp lệ

Mở <https://aistudio.google.com/apikey>, đăng nhập Google, chọn **Create API key**, chọn/tạo project phù hợp. Nếu key cũ bị thu hồi hoặc bị Google chặn, tạo key mới. Kiểm tra giới hạn key cho **Generative Language API**, model, quota và billing trong Google AI Studio; không mặc định rằng mọi model đều miễn phí.

## 2. Sửa cấu hình bên ngoài project

Ứng dụng trên máy hiện tại dùng file:

```text
C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\SuVietTutorData\config.properties
```

- Thay giá trị sau `ai.apiKey=` bằng key vừa tạo, trên cùng một dòng, không thêm dấu nháy.
- Cấu hình hiện tại trên máy dùng `ai.model=gemini-3.6-flash` theo lựa chọn của người dùng. Google đã trả model này trong danh sách model hỗ trợ `generateContent`. Mẫu cấu hình trong project vẫn dùng `gemini-3.8-flash`; cấu hình ngoài WAR quyết định model thực tế, quyền sử dụng/hạn mức tùy tài khoản.
- Giữ `ai.timeoutSeconds=45`.
- Không sửa các dòng `db.*` hoặc `uploads.dir` khi chỉ cấu hình AI.
- Không dùng cấu hình test ở thư mục `.local`: bộ kiểm thử khóa học cố ý tắt AI.

File được đọc một lần khi lớp cấu hình được nạp. Lưu file rồi **khởi động lại Tomcat**; chỉ refresh trình duyệt không đủ. Khi chuyển sang máy khác, chỉ định đúng file bằng `-ConfigPath`.

## 3. Chạy lại website

Dừng phiên Tomcat 8082 cũ (Ctrl+C trong terminal đang chạy) trước khi chạy lệnh sau trong PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\outputs\ProjectPrj_GiaSu\scripts\run-vscode.ps1' -ConfigPath 'C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\SuVietTutorData\config.properties' -Port 8082
```

Đăng nhập tại <http://127.0.0.1:8082/SuVietTutor/login>, mở chat gia sư và gửi một câu hỏi ngắn. Đây là yêu cầu API thật, có thể dùng quota/tính phí. Kiểm tra chấm ảnh bằng ảnh bài làm thử, không dùng thông tin cá nhân nhạy cảm. Nộp ảnh vẫn được lưu trước khi yêu cầu AI; lỗi API không làm mất ảnh hoặc tự ghi điểm 0.

## 4. Đọc lỗi

| Lỗi | Cách xử lý |
|---|---|
| Chưa cấu hình API key | Điền `ai.apiKey` trong đúng file ngoài WAR, khởi động lại |
| `API_KEY_INVALID` (thường HTTP 400) | Google không chấp nhận key; tạo key hợp lệ, kiểm tra đã sao chép đầy đủ, cập nhật và khởi động lại |
| HTTP 401/403 | Kiểm tra key bị chặn/thu hồi, giới hạn API, quyền project/model trong AI Studio |
| HTTP 404 | Kiểm tra `ai.model`, model đã ngừng hoạt động hoặc chưa có quyền truy cập |
| HTTP 429 | Kiểm tra quota/billing, giảm tần suất, chờ trước khi thử lại; không bấm gửi liên tục |
| HTTP 5xx hoặc timeout | Google/network tạm lỗi; thử lại sau, không tự động tạo hàng loạt request |

Ứng dụng không hiển thị raw response Google hoặc key trong thông báo lỗi. Bộ `GeminiClientTests` dùng HTTP server giả lập cục bộ và key giả, không gọi Google hoặc đọc database; được chạy cùng core tests khi thực hiện Ant `test`.

## Chẩn đoán trên máy — 06/10/2026

Lần đầu, yêu cầu chỉ đọc danh sách model (`GET /v1beta/models`) với key cũ trả **HTTP 400**, `INVALID_ARGUMENT`, `API_KEY_INVALID`. Người dùng sau đó thay key trong file cấu hình ngoài WAR và chọn `gemini-3.6-flash`.

Kiểm tra sau khi lưu key mới:
- Google chấp nhận yêu cầu đọc danh sách model; có `models/gemini-3.6-flash` hỗ trợ `generateContent`.
- Một yêu cầu tổng hợp ngắn qua `GeminiClient` gặp lỗi 5xx. Thử lại một lần bằng REST nhận **HTTP 503**. Không thử liên tục, không tự đổi model hoặc bật billing. Việc đọc danh sách model thành công chưa chứng minh sinh nội dung hoạt động; cần thử lại khi dịch vụ khả dụng.
- Không gửi dữ liệu học sinh/ảnh bài làm; không tạo hoặc sửa dữ liệu database trong lần kiểm tra này. Không sao chép API key vào repository hoặc log.
- Ant `dist test` chạy lại đạt **18 core checks + 40 Gemini HTTP contract checks** (giả lập, không gọi Google). Trước đó `clean dist test` cũng đạt.
- Đã sao lưu WAR đang chạy và khởi động lại Tomcat 8082 bằng cấu hình ngoài WAR của người dùng, triển khai bản xử lý lỗi mới. Phiên đăng nhập/chat cũ không được giữ khi restart.

WAR trước lần restart này được giữ tại:
`C:\Users\Tuan\Documents\Codex\2026-10-05\files-mentioned-by-the-user-codex\outputs\ProjectPrj_GiaSu\.local\backups\gemini-20261006-112232\SuVietTutor.war`.

Tài liệu chính thức:
- <https://ai.google.dev/gemini-api/docs/api-key>
- <https://ai.google.dev/gemini-api/docs/models>
- <https://ai.google.dev/gemini-api/docs/troubleshooting>