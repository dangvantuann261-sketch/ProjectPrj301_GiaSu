<%@ page pageEncoding="UTF-8" %>
<%@ include file="../partials/header.jspf" %>

<a href="${ctx}/student/history">← Lịch sử luyện tập</a><div class="page-heading mt-4"><div><div class="eyebrow">${question.kind eq 'MCQ' ? 'LUYỆN TRẮC NGHIỆM' : 'LUYỆN TỰ LUẬN'}</div><h1><c:out value="${attempt.topic_title}"/></h1></div><span class="pill">Câu ${attempt.current_pos+1} / ${attempt.total}</span></div>
<progress class="practice-progress" value="${attempt.current_pos}" max="${attempt.total}" aria-label="Tiến độ làm bài"></progress>
<section class="surface question-surface"><span class="eyebrow">CÂU ${attempt.current_pos+1}</span><h2 class="question-prompt"><c:out value="${question.prompt}"/></h2><form method="post" ${question.kind eq 'ESSAY' ? 'enctype="multipart/form-data"' : ''} data-busy="Đang lưu bài làm…"><%@ include file="../partials/csrf.jspf" %><input type="hidden" name="id" value="${attempt.id}"><input type="hidden" name="position" value="${attempt.current_pos}">
<c:choose><c:when test="${question.kind eq 'MCQ'}"><fieldset><legend class="visually-hidden">Chọn một đáp án</legend>
<label class="answer-choice"><input type="radio" name="chosen" value="A" required><span class="answer-letter">A</span><span><c:out value="${question.optionA}"/></span></label>
<label class="answer-choice"><input type="radio" name="chosen" value="B"><span class="answer-letter">B</span><span><c:out value="${question.optionB}"/></span></label>
<label class="answer-choice"><input type="radio" name="chosen" value="C"><span class="answer-letter">C</span><span><c:out value="${question.optionC}"/></span></label>
<label class="answer-choice"><input type="radio" name="chosen" value="D"><span class="answer-letter">D</span><span><c:out value="${question.optionD}"/></span></label></fieldset></c:when>
<c:otherwise><div class="upload-zone"><span class="upload-symbol">↑</span><h3>Ảnh bài làm viết tay của em</h3><p>Chụp đủ sáng, thấy rõ toàn bộ bài. JPG hoặc PNG, tối đa 5 MB.</p><label for="image" class="form-label">Chọn ảnh bài làm</label><input id="image" type="file" name="image" class="form-control" accept="image/jpeg,image/png" required><p class="small text-secondary mt-3">Ảnh được lưu trước. Sau khi nộp, em có thể chọn gửi ảnh tới Gemini để nhận gợi ý. AI lỗi không làm mất bài và không tự ghi điểm 0.</p></div></c:otherwise></c:choose><button class="btn btn-primary mt-4">${question.kind eq 'MCQ' ? 'Kiểm tra câu trả lời' : 'Lưu và nộp ảnh'} →</button></form></section>

<%@ include file="../partials/footer.jspf" %>