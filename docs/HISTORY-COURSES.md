# Bộ dữ liệu cũ – đã thay thế ngày 08/10/2026

Website hiện tập trung Lịch sử lớp 6. Xem [hướng dẫn hiện hành](HISTORY-GRADE6.md). Nội dung bên dưới là hồ sơ của bộ demo cũ; không chạy runner 6–12 để cập nhật website.

# Bộ khóa học Lịch sử lớp 6–12

Bộ nội dung ngày 07/10/2026 gồm **7 khóa, 21 bài giảng, 105 câu trắc nghiệm**. Mỗi khóa có 3 bài theo thứ tự, mỗi bài có 5 câu hỏi với 4 lựa chọn, đáp án và giải thích. Nội dung được tự biên soạn ở mức nhận biết và thông hiểu, lựa chọn các chủ đề tương ứng khối lớp theo nguồn Kết nối tri thức. Đây là bộ học thử/kiểm thử, không phải chương trình đầy đủ cả năm hay bộ đề thi chính thức.

| Khối | Chủ đề |
|---|---|
| 6 | Sử liệu; Ai Cập và Lưỡng Hà; Văn Lang và Âu Lạc |
| 7 | Nhà Lý; kháng chiến Mông – Nguyên; khởi nghĩa Lam Sơn |
| 8 | Cách mạng Pháp; cách mạng công nghiệp; phong trào Tây Sơn |
| 9 | Chiến tranh thế giới thứ hai; Cách mạng tháng Tám; Điện Biên Phủ |
| 10 | Hiện thực và nhận thức lịch sử; văn minh phương Đông; văn minh phương Tây |
| 11 | Cách mạng tư sản; thực dân ở Đông Nam Á; bảo vệ Tổ quốc |
| 12 | Liên hợp quốc; ASEAN; Đổi mới ở Việt Nam |

## Học và làm bài

1. Mở `/SuVietTutor/courses` (VS Code: `http://localhost:8082/SuVietTutor/courses`).
2. Lọc khối lớp, mở khóa và xem chương trình. Bài đầu tiên được học thử không cần đăng nhập.
3. Đăng nhập học sinh, đăng ký khóa rồi mở bài học.
4. Đọc nội dung, xem video hoặc chọn **Mở video / tài liệu gốc**.
5. Chọn **Luyện tập tự do → Bắt đầu luyện tập** để làm 5 câu trắc nghiệm.

Khóa được xuất bản với chế độ ghi danh trực tiếp. Script chọn admin đang hoạt động làm tác giả và giáo viên đang hoạt động có ID nhỏ nhất để phụ trách; không tạo tài khoản mới. Nội dung được chia sẻ vào thư viện (`library_visible=1`) để dùng luồng luyện tập hiện có. Điểm luyện tập được lưu trong lịch sử tự học; ứng dụng hiện chưa tính điểm kiểm tra vào điều kiện hoàn thành khóa. Học sinh tự đánh dấu đã học từng bài để cập nhật tiến độ. Bộ bổ sung này chỉ có trắc nghiệm, không thêm câu tự luận.

## Các file và cách nhập

- `database/history-courses-content.json`: nội dung, câu hỏi, đáp án, nguồn và ngày kiểm tra nguồn.
- `database/05-seed-history-courses.sql`: nhập bổ sung trong một transaction, kiểm tra quan hệ dữ liệu và lưu dấu nhập.
- `scripts/seed-history-courses.ps1`: kiểm tra JSON, sao lưu, chạy SQL, xác minh và ngăn nhập trùng.

Chạy từ thư mục gốc project:

```powershell
# Chỉ kiểm tra file dữ liệu, không kết nối SQL Server.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-history-courses.ps1 -Mode Validate

# Thử INSERT rồi ROLLBACK. Không giữ bản ghi; SQL Server vẫn có thể tăng bộ đếm IDENTITY.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-history-courses.ps1 -Mode DryRun

# Sao lưu COPY_ONLY + CHECKSUM, RESTORE VERIFYONLY rồi nhập chính thức.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-history-courses.ps1 -Mode Apply

# Chỉ đọc và kiểm tra dữ liệu đã nhập.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-history-courses.ps1 -Mode Verify
```

Script tự tìm cùng file cấu hình ngoài project như script chạy Tomcat; có thể truyền `-ConfigPath 'C:\SuVietTutorData\config.properties'`. Nó chỉ đọc đích JDBC, dùng Windows Authentication và không in mật khẩu/API key. Tài khoản Windows cần quyền nhập dữ liệu; chế độ Apply cần thêm quyền sao lưu. Không thay đổi quyền tài khoản ứng dụng.

Database cần có schema migration `04-courses.sql`, một admin và một giáo viên đang hoạt động. Không chạy lại `00`–`02` trên database hiện hữu. File SQL `05` được runner truyền hai tham số `@seed_json`, `@commit`; dùng runner thay vì chạy riêng file SQL không có tham số trong SSMS.

Khóa đánh dấu `seed.history_6_12.v1` trong `app_settings` lưu danh sách ID khóa. Toàn bộ dữ liệu và dấu đánh dấu được commit cùng nhau. Khi đã có dấu nhập, chạy Apply chỉ xác minh, không thêm hoặc ghi đè nội dung. Sửa JSON sau khi đã nhập không tự cập nhật database; cần một đợt cập nhật có chủ đích. Không xóa dấu nhập để cố chạy lại. Bản sao lưu được ghi vào thư mục backup mặc định của SQL Server, đường dẫn in trong kết quả Apply.

## Nguồn học liệu

Nguồn liên kết thuộc VietJack và các kênh YouTube tương ứng, không được tải hoặc đóng gói lại vào project. Đã kiểm tra HTTP của 21 trang nguồn và metadata oEmbed của 15 video vào 07/10/2026. Đây là kiểm tra tồn tại/khớp tiêu đề, không phải xác nhận phát video trên mọi thiết bị; việc phát nhúng còn phụ thuộc nhà cung cấp. Sáu bài dùng trang tài liệu làm đường dẫn nguồn. Tên slug của một số URL lớp 10 là tên cũ, nhưng tiêu đề trang hiện tại khớp nội dung bài.

| Khối | Bài giảng | Tài liệu | Video |
|---|---|---|---|
| 6 | Đọc dấu tích quá khứ: hiện vật, chữ viết và lời kể | [VietJack](https://vietjack.com/lich-su-6-ket-noi/bai-2-cac-nha-su-hoc-dua-vao-dau-de-biet-va-phuc-hung-lai-lich-su.jsp) | [YouTube](https://www.youtube.com/watch?v=0yY6nrAILik) |
| 6 | Ai Cập và Lưỡng Hà: những nền văn minh bên sông | [VietJack](https://vietjack.com/lich-su-6-ket-noi/bai-7-ai-cap-va-luong-ha-co-dai.jsp) | [YouTube](https://www.youtube.com/watch?v=ZTAphd0MH_E) |
| 6 | Văn Lang và Âu Lạc: buổi đầu dựng nước | [VietJack](https://vietjack.com/lich-su-6-ket-noi/bai-14-nha-nuoc-van-lang-au-lac.jsp) | [YouTube](https://www.youtube.com/watch?v=ksnsGMiIu6g) |
| 7 | Nhà Lý: dời đô và xây dựng Đại Việt | [VietJack](https://vietjack.com/lich-su-7-kn/bai-11-nha-ly-xay-dung-va-phat-trien-dat-nuoc-1009-1225.jsp) | [YouTube](https://www.youtube.com/watch?v=bc3P0rn_hsM) |
| 7 | Nhà Trần và ba lần kháng chiến chống Mông – Nguyên | [VietJack](https://vietjack.com/lich-su-7-kn/bai-14-ba-lan-khang-chien-chong-quan-xam-luoc-mong-nguyen.jsp) | [YouTube](https://www.youtube.com/watch?v=9Qz4xUrwZKk) |
| 7 | Khởi nghĩa Lam Sơn: từ gian khó đến thắng lợi | [VietJack](https://vietjack.com/lich-su-7-kn/bai-16-khoi-nghia-lam-son-1418-1427.jsp) | [YouTube](https://www.youtube.com/watch?v=7GW3_Ab5l0Q) |
| 8 | Cách mạng Pháp năm 1789 và những thay đổi xã hội | [VietJack](https://vietjack.com/lich-su-8-kn/bai-2-cach-mang-tu-san-phap-cuoi-the-ki-xviii.jsp) | [YouTube](https://www.youtube.com/watch?v=-XC9yBGlNdk) |
| 8 | Cách mạng công nghiệp: từ thủ công đến máy móc | [VietJack](https://vietjack.com/lich-su-8-kn/bai-3-cach-mang-cong-nghiep.jsp) | [YouTube](https://www.youtube.com/watch?v=p8tAAIAyyP4) |
| 8 | Phong trào Tây Sơn và sự nghiệp của Quang Trung | [VietJack](https://vietjack.com/lich-su-8-kn/bai-8-phong-trao-tay-son.jsp) | [YouTube](https://www.youtube.com/watch?v=jIV7dC8Ciuk) |
| 9 | Chiến tranh thế giới thứ hai, 1939–1945 | [VietJack](https://vietjack.com/lich-su-9-kn/bai-4-chien-tranh-the-gioi-thu-hai.jsp) | [YouTube](https://www.youtube.com/watch?v=zTOb0F51GWQ) |
| 9 | Cách mạng tháng Tám và nước Việt Nam Dân chủ Cộng hòa | [VietJack](https://vietjack.com/lich-su-9-kn/bai-8-cach-mang-thang-tam-nam-1945.jsp) | [YouTube](https://www.youtube.com/watch?v=6C4vX8w7FEo) |
| 9 | Điện Biên Phủ và kết thúc cuộc kháng chiến chống Pháp | [VietJack](https://vietjack.com/lich-su-9-kn/bai-15-viet-nam-khang-chien-chong-thuc-dan-phap.jsp) | [YouTube](https://www.youtube.com/watch?v=UshyGA4JG2Q) |
| 10 | Hiện thực lịch sử và cách con người nhận thức quá khứ | [VietJack](https://vietjack.com/lich-su-10-kn/bai-1-hien-thuc-lich-su-va-nhan-thuc-lich-su.jsp) | Dùng tài liệu nguồn |
| 10 | Văn minh phương Đông: thành tựu và sự lan tỏa | [VietJack](https://vietjack.com/lich-su-10-kn/bai-4-su-hoc-voi-mot-so-linh-vuc-nganh-nghe-hien-dai.jsp) | Dùng tài liệu nguồn |
| 10 | Văn minh phương Tây: Hy Lạp, La Mã và Phục hưng | [VietJack](https://vietjack.com/lich-su-10-kn/bai-5-khai-niem-van-minh-mot-so-nen-van-minh-phuong-dong-thoi-ki-co-trung-dai.jsp) | Dùng tài liệu nguồn |
| 11 | Cách mạng tư sản: mục tiêu và hình thức | [VietJack](https://vietjack.com/lich-su-11-kn/bai-1-mot-so-van-de-chung-ve-cach-mang-tu-san.jsp) | [YouTube](https://www.youtube.com/watch?v=ssZBimOs2r0) |
| 11 | Đông Nam Á trước làn sóng xâm lược thực dân | [VietJack](https://vietjack.com/lich-su-11-kn/bai-5-qua-trinh-xam-luoc-va-cai-tri-cua-chu-nghia-thuc-dan-o-dong-nam-a.jsp) | Dùng tài liệu nguồn |
| 11 | Chiến tranh bảo vệ Tổ quốc trong lịch sử Việt Nam | [VietJack](https://vietjack.com/lich-su-11-kn/bai-7-khai-quat-ve-chien-tranh-bao-ve-to-quoc-trong-lich-su-viet-nam.jsp) | Dùng tài liệu nguồn |
| 12 | Liên hợp quốc: ra đời, mục tiêu và nguyên tắc | [VietJack](https://vietjack.com/lich-su-12-kn/bai-1-lien-hop-quoc.jsp) | [YouTube](https://www.youtube.com/watch?v=HKNKn-Uc63Q) |
| 12 | ASEAN: từ năm nước sáng lập đến hợp tác khu vực | [VietJack](https://vietjack.com/lich-su-12-kn/bai-4-su-ra-doi-va-phat-trien-cua-hiep-hoi-ca-quoc-gia.jsp) | [YouTube](https://www.youtube.com/watch?v=ImO4j3Ww4fs) |
| 12 | Đổi mới ở Việt Nam: bối cảnh và định hướng | [VietJack](https://vietjack.com/lich-su-12-kn/bai-10-khai-quat-ve-cong-cuoc-doi-moi-tu-nam-1986-den-nay.jsp) | Dùng tài liệu nguồn |

## Kết quả kiểm tra ngày 07/10/2026

- Kiểm tra JSON: đủ lớp 6–12, 21 bài, 105 câu, mỗi câu có 4 lựa chọn khác nhau và đáp án hợp lệ.
- DryRun trên SQL Server: ràng buộc, liên kết chủ đề/môn/khóa, số bài và câu hỏi đều đạt; đã rollback dữ liệu thử.
- Apply: sao lưu được kiểm tra bằng RESTORE VERIFYONLY; nhập thành công 7 khóa với trạng thái PUBLISHED.
- Chạy lại Apply: chỉ xác minh, không tạo thêm bản ghi.
- HTTP trên cổng 8082: danh mục có đủ 7 khóa; 7 trang học thử, 21 trang chủ đề sau đăng nhập học sinh trả HTTP 200 và hiển thị đúng nội dung; mỗi chủ đề có 5 câu; 15 iframe YouTube và 6 liên kết tài liệu xuất hiện đúng nguồn.
- Kiểm tra HTTP chỉ đăng nhập và đọc trang, không ghi danh, tạo lượt làm bài hoặc thay đổi tiến độ. Chưa kiểm tra phát video trực tiếp trong trình duyệt hay nộp bài trắc nghiệm trong lần bổ sung dữ liệu này.
