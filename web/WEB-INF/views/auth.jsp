<%@ page pageEncoding="UTF-8" %>
<%@ include file="partials/header.jspf" %>

<div class="auth-grid"><section class="auth-story"><a class="brand" href="${ctx}/login"><span class="brand-mark">S</span>Sử Việt.</a><div><div class="eyebrow">LỊCH SỬ LỚP 6</div><h1>Mỗi câu chuyện cũ,<br>một khám phá mới.</h1><p>Từ những dấu tích đầu tiên đến buổi đầu dựng nước. Cùng học, đặt câu hỏi và tìm lời giải từng bước.</p></div><img class="history-art" src="${ctx}/assets/history.svg" alt="Minh họa trống đồng, núi và dòng thời gian"><span class="small-label">KHÁM PHÁ · SUY NGẪM · GHI NHỚ</span></section>
<section class="auth-form"><div class="eyebrow">CHÀO MỪNG ĐẾN SỬ VIỆT</div><h2>${register ? 'Bắt đầu hành trình học tập' : 'Rất vui được gặp em'}</h2><p class="text-secondary">${register ? 'Tạo tài khoản học sinh để học và luyện tập.' : 'Đăng nhập để tiếp tục bài học của mình.'}</p>
<form method="post" action="${ctx}/${register ? 'register' : 'login'}"><%@ include file="partials/csrf.jspf" %>
<c:if test="${register}"><label class="form-label" for="name">Họ và tên</label><input class="form-control mb-3" id="name" name="full_name" maxlength="100" autocomplete="name" required value="${fn:escapeXml(param.full_name)}"></c:if>
<label class="form-label" for="email">Email</label><input id="email" class="form-control mb-3" name="email" type="email" maxlength="254" autocomplete="username" required value="${fn:escapeXml(param.email)}">
<label class="form-label" for="password">Mật khẩu</label><input id="password" class="form-control mb-2" name="password" type="password" maxlength="128" autocomplete="${register ? 'new-password' : 'current-password'}" required>
<c:if test="${register}"><div class="form-text mb-3">Từ 10 đến 128 ký tự.</div></c:if>
<button class="btn btn-primary w-100 mt-3 py-3">${register ? 'Tạo tài khoản' : 'Đăng nhập'} <span aria-hidden="true">→</span></button></form>
<p class="mt-4 text-secondary">${register ? 'Đã có tài khoản?' : 'Chưa có tài khoản?'} <a href="${ctx}/${register ? 'login' : 'register'}">${register ? 'Đăng nhập' : 'Đăng ký học sinh'}</a></p><p class="auth-tip">✦ Gia sư AI đồng hành, giúp em tự tìm ra câu trả lời.</p></section></div>

<%@ include file="partials/footer.jspf" %>