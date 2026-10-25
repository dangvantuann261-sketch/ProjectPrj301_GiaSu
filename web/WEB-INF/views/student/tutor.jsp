<%@ page pageEncoding="UTF-8" %>
<%@ include file="../partials/header.jspf" %>

<div class="page-heading"><div><div class="eyebrow">HỌC BẰNG CÁCH ĐẶT CÂU HỎI</div><h1>Cùng gia sư tìm lời giải <span class="spark">✦</span></h1><p>Hỏi về Lịch sử 6. Gia sư sẽ chờ em trả lời sau mỗi gợi ý và lưu hội thoại để em học tiếp.</p></div></div><c:if test="${not empty item}"><p class="surface">Đang trao đổi về: <strong><c:out value="${item.title}"/></strong> · <a href="${ctx}/student/lesson?id=${item.id}">Về bài học →</a></p></c:if><div class="tutor-wide"><%@ include file="../partials/chat.jspf" %></div>

<%@ include file="../partials/footer.jspf" %>