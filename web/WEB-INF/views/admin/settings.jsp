<%@ page pageEncoding="UTF-8" %>
<%@ include file="../partials/header.jspf" %>

<div class="page-heading"><div><div class="eyebrow">QUẢN TRỊ HỆ THỐNG</div><h1>Cấu hình hệ thống</h1><p>Tham số vận hành và API AI tương thích Gemini. Áp dụng từ yêu cầu kế tiếp.</p></div></div>
<form method="post" data-busy="Đang lưu…"><%@ include file="../partials/csrf.jspf" %><div class="row g-4">
<c:forEach var="section" items="${['ai_', 'essay_ai_']}"><c:set var="ai" value="${section eq 'ai_' ? generalAi : essayAi}"/>
<section class="col-lg-6"><div class="surface h-100"><h2>${section eq 'ai_' ? 'API gia sư và soạn nội dung' : 'API tự luận từng bước'}</h2>
<label class="form-label" for="${section}url">Link API (kết thúc bằng /models/)</label><input id="${section}url" name="${section}url" type="url" class="form-control mb-3" maxlength="2000" value="${fn:escapeXml(ai.url)}" required>
<label class="form-label" for="${section}model">Model</label><input id="${section}model" name="${section}model" class="form-control mb-3" maxlength="100" value="${fn:escapeXml(ai.model)}" required>
<label class="form-label" for="${section}key">API key mới</label><input id="${section}key" name="${section}key" type="password" class="form-control mb-2" maxlength="500" autocomplete="new-password" spellcheck="false">
<p class="small text-secondary">${ai.hasKey ? 'Đã có key. Để trống để giữ nguyên.' : 'Chưa có key. Nhập key để sử dụng AI.'} Key đã lưu không hiển thị lại.</p>
<label class="d-block small mb-3"><input type="checkbox" name="${section}clear"> Dùng lại key ${section eq 'ai_' ? 'trong cấu hình máy chủ' : 'của API chung'} (khi không nhập key mới)</label>
<label class="form-label" for="${section}timeout">Thời gian chờ (giây)</label><input id="${section}timeout" name="${section}timeout" type="number" min="5" max="90" class="form-control" value="${ai.timeout}" required></div></section></c:forEach>
<section class="col-12"><div class="surface"><h2>Prompt AI</h2><label class="form-label" for="prompt">Hướng dẫn gia sư chung</label><textarea id="prompt" class="form-control mb-3" name="prompt" rows="8" maxlength="12000" required><c:out value="${prompt}"/></textarea>
<label class="form-label" for="essay_prompt">Hướng dẫn nhận xét và luyện tự luận</label><textarea id="essay_prompt" class="form-control mb-3" name="essay_prompt" rows="8" maxlength="12000" required><c:out value="${essayPrompt}"/></textarea>
<div class="alert alert-info">Mỗi lượt chỉ hiện một công thức/khung có chỗ trống. Học sinh tự điền, AI kiểm tra rồi mới sang bước tiếp. Kết thúc hướng dẫn, học sinh phải làm lại và nộp ảnh mới.</div>
<button class="btn btn-primary">Lưu cấu hình AI</button></div></section></div></form>

<%@ include file="../partials/footer.jspf" %>
