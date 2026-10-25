<%@ page pageEncoding="UTF-8" %>
<%@ include file="../partials/header.jspf" %>
<c:if test="${landing}">
<section class="landing-hero">
  <div class="landing-copy">
    <span class="hero-kicker">LỊCH SỬ LỚP 6 · KẾT NỐI TRI THỨC</span>
    <h1>Hiểu quá khứ.<br><span class="hero-accent">Vững bước hôm nay.</span></h1>
    <p class="landing-lead">Lộ trình Lịch sử lớp 6 được tổ chức theo chương và bài: xem bài giảng, nắm ý chính rồi luyện tập ngay sau đó. Tiến độ và điểm luyện tập được lưu theo tài khoản; gia sư AI chỉ gợi ý để em tự tìm ra câu trả lời.</p>
    <div class="hero-actions">
      <c:choose>
        <c:when test="${not empty featured}"><a class="btn btn-primary btn-lg" href="${ctx}/course?id=${featured.id}">Xem lộ trình học <span aria-hidden="true">→</span></a></c:when>
        <c:otherwise><a class="btn btn-primary btn-lg" href="${ctx}/courses">Xem chương trình <span aria-hidden="true">→</span></a></c:otherwise>
      </c:choose>
      <a class="btn btn-outline-primary btn-lg" href="${ctx}/${empty sessionScope.user ? 'register' : 'home'}">${empty sessionScope.user ? 'Tạo tài khoản học sinh' : 'Vào không gian của tôi'}</a>
    </div>
    <dl class="hero-stats">
      <div><dt><c:out value="${stats.programs}"/></dt><dd>chương trình đang mở</dd></div>
      <div><dt><c:out value="${stats.lessons}"/></dt><dd>bài học có luyện tập</dd></div>
      <div><dt><c:out value="${stats.questions}"/></dt><dd>câu hỏi trong ngân hàng</dd></div>
    </dl>
  </div>
  <div class="landing-visual">
    <div class="visual-halo" aria-hidden="true"></div>
    <c:choose>
      <c:when test="${not empty featured}">
        <article class="preview-card">
          <div class="preview-head"><span class="pill"><c:out value="${featured.subject_name}"/> · Lớp ${featured.grade}</span><span class="live-dot">ĐANG MỞ</span></div>
          <h2><c:out value="${featured.title}"/></h2>
          <p class="preview-summary"><c:out value="${featured.summary}"/></p>
          <ul class="preview-points">
            <li>${featured.item_count} bài học theo chương</li>
            <li>Luyện tập theo bài và ôn tập chương</li>
            <li>Tiến độ lưu theo tài khoản học sinh</li>
          </ul>
          <a class="btn btn-primary w-100" href="${ctx}/course?id=${featured.id}">Xem lộ trình <span aria-hidden="true">→</span></a>
        </article>
      </c:when>
      <c:otherwise>
        <article class="preview-card">
          <div class="preview-head"><span class="pill">SẮP MỞ</span></div>
          <h2>Chương trình đang được chuẩn bị</h2>
          <p class="preview-summary">Giáo viên sẽ mở lớp học trong thời gian tới. Em có thể tạo tài khoản để bắt đầu ngay khi có bài mới.</p>
        </article>
      </c:otherwise>
    </c:choose>
    <div class="visual-badge"><strong>AI</strong><span>Gia sư đồng hành<br>theo từng bài học</span></div>
  </div>
</section>
<c:if test="${not empty chapters}">
<section id="lo-trinh" class="curriculum-section">
  <div class="section-head">
    <div>
      <span class="eyebrow">LỘ TRÌNH CỤ THỂ</span>
      <h2>Nội dung chương trình</h2>
      <p><strong>${fn:length(chapters)} chương · ${featured.item_count} bài học</strong> — chọn một chương để xem các bài bên trong.</p>
    </div>
  </div>
  <ol class="chapter-list">
    <c:forEach var="chapter" items="${chapters}" varStatus="loop">
      <li class="chapter-item">
        <details class="chapter-fold">
          <summary class="chapter-row">
            <span class="chapter-index" aria-hidden="true">${loop.count lt 10 ? '0' : ''}${loop.count}</span>
            <span class="chapter-body">
              <span class="chapter-title"><c:out value="${chapter.title}"/></span>
              <span class="chapter-meta"><span>${fn:length(chapter.items)} bài học</span><c:if test="${chapter.question_count gt 0}"><span>${chapter.question_count} câu trắc nghiệm</span></c:if></span>
            </span>
            <span class="chapter-toggle" aria-hidden="true"></span>
          </summary>
          <ol class="lesson-list">
            <c:forEach var="lesson" items="${chapter.items}" varStatus="ls">
              <li class="lesson-row"><span class="lesson-index" aria-hidden="true">${ls.count lt 10 ? '0' : ''}${ls.count}</span><a class="lesson-title" href="${ctx}/preview?id=${lesson.id}"><c:out value="${lesson.title}"/></a></li>
            </c:forEach>
          </ol>
        </details>
      </li>
    </c:forEach>
  </ol>
</section>
</c:if>
<section class="landing-steps" aria-label="Ba bước bắt đầu">
  <article><span>01</span><h3>Mở đúng chương</h3><p>Vào chương đang học và xem danh sách bài theo đúng thứ tự.</p></article>
  <article><span>02</span><h3>Học và ghi nhớ</h3><p>Đọc bài giảng, xem video và đánh dấu bài đã hoàn thành.</p></article>
  <article><span>03</span><h3>Luyện tập ngay</h3><p>Trả lời câu hỏi theo bài, ôn cả chương và xem lại điểm cao nhất.</p></article>
</section>
</c:if>
<c:if test="${not landing}">
<c:set var="catalogPath" value="/courses"/>
<section id="catalog" class="catalog-section">
  <div class="section-head">
    <div><span class="eyebrow">KHO HỌC LIỆU</span><h2>Chương trình học</h2><p>Lộ trình Lịch sử lớp 6 theo 5 chương của bộ sách Kết nối tri thức.</p></div>
    <span class="result-count"><strong><c:out value="${total}"/></strong> chương trình</span>
  </div>
  <form class="catalog-filters surface" method="get" action="${ctx}${catalogPath}">
    <div class="filter-field"><label for="q">Tìm chương trình</label><input class="form-control" id="q" name="q" maxlength="100" placeholder="Tên chương trình…" value="${fn:escapeXml(param.q)}"></div>
    <div class="filter-field"><label for="subject">Môn học</label><select id="subject" name="subject" class="form-select"><option value="0">Tất cả môn</option><c:forEach var="s" items="${subjects}"><option value="${s.id}" ${param.subject eq s.id ? 'selected' : ''}><c:out value="${s.label}"/></option></c:forEach></select></div>
    <div class="filter-field"><label for="grade">Khối lớp</label><select id="grade" name="grade" class="form-select"><option value="6">Lớp 6</option></select></div>
    <button class="btn btn-primary">Tìm kiếm</button>
  </form>
  <div class="course-grid">
    <c:forEach var="c" items="${rows}" varStatus="loop">
      <article class="course-card">
        <div class="course-cover">
          <span class="pill">Lớp ${c.grade}</span>
          <div class="cover-footer">
            <span class="cover-subject"><c:out value="${c.subject_name}"/></span>
            <span class="cover-index" aria-hidden="true">${loop.count lt 10 ? '0' : ''}${loop.count}</span>
          </div>
        </div>
        <div class="course-card-body">
          <span class="eyebrow">CHƯƠNG TRÌNH · LỚP ${c.grade}</span>
          <h3><a href="${ctx}/course?id=${c.id}"><c:out value="${c.title}"/></a></h3>
          <p class="course-description clamp-text"><c:out value="${c.summary}"/></p>
          <div class="course-meta"><span><b>${c.item_count}</b> bài giảng</span><span><b>${c.duration_minutes}</b> phút dự kiến</span></div>
          <a class="course-cta" href="${ctx}/course?id=${c.id}">Xem chương trình <span aria-hidden="true">→</span></a>
        </div>
      </article>
    </c:forEach>
  </div>
  <c:if test="${empty rows}">
    <div class="surface empty-state">
      <div class="empty-icon" aria-hidden="true">▤</div>
      <h3>Chưa có chương trình phù hợp</h3>
      <p>Thử thay đổi bộ lọc. Chương trình mới sẽ xuất hiện tại đây sau khi được xuất bản.</p>
      <a class="btn btn-outline-primary" href="${ctx}${catalogPath}">Xóa bộ lọc</a>
      <c:if test="${not empty sessionScope.user and sessionScope.user.role ne 'STUDENT'}">
        <a class="btn btn-primary" href="${ctx}/${sessionScope.user.role eq 'ADMIN' ? 'admin' : 'teacher'}/courses">Quản lý chương trình</a>
      </c:if>
    </div>
  </c:if>
  <nav class="course-pagination" aria-label="Phân trang">
    <span class="small text-secondary align-self-center">Trang ${page}/${pages}</span>
    <c:forEach var="n" begin="${pageStart}" end="${pageEnd}">
      <c:url var="pageUrl" value="${catalogPath}"><c:param name="page" value="${n}"/><c:if test="${not empty param.q}"><c:param name="q" value="${param.q}"/></c:if><c:if test="${not empty param.subject}"><c:param name="subject" value="${param.subject}"/></c:if><c:if test="${not empty param.grade}"><c:param name="grade" value="${param.grade}"/></c:if></c:url>
      <a class="${n eq page ? 'selected' : ''}" href="${fn:escapeXml(pageUrl)}" aria-label="Trang ${n}">${n}</a>
    </c:forEach>
  </nav>
</section>
</c:if>
<c:if test="${landing and empty sessionScope.user}">
<section class="audience-grid" aria-label="Dành cho học sinh và giáo viên">
  <article class="audience-card">
    <span class="eyebrow">DÀNH CHO HỌC SINH</span>
    <h3>Học theo nhịp của riêng em</h3>
    <ul>
      <li>Mở đúng chương, đúng bài đang học</li>
      <li>Tiến độ và bài cần ôn được lưu lại</li>
      <li>Luyện tập theo bài và ôn tập chương</li>
    </ul>
    <a class="btn btn-outline-primary" href="${ctx}/register">Tạo tài khoản học sinh</a>
  </article>
  <article class="audience-card audience-card--dark">
    <span class="eyebrow">DÀNH CHO GIÁO VIÊN</span>
    <h3>Quản lý lớp học gọn hơn</h3>
    <ul>
      <li>Quản lý chương trình được phân công</li>
      <li>Theo dõi tiến độ từng học sinh</li>
      <li>Soạn câu hỏi luyện tập với sự hỗ trợ của AI</li>
    </ul>
    <a class="btn btn-outline-primary" href="${ctx}/login">Đăng nhập để quản lý lớp</a>
  </article>
</section>
</c:if>
<section class="surface faq-section">
  <div class="eyebrow">GIẢI ĐÁP NHANH</div>
  <h2>Trước khi bắt đầu</h2>
  <details><summary>Tôi bắt đầu học như thế nào?</summary><p>Chọn chương và bài học để đọc lý thuyết. Đăng nhập bằng tài khoản học sinh để lưu tiến độ và luyện tập trực tiếp.</p></details>
  <details><summary>Tiến độ được tính như thế nào?</summary><p>Tiến độ dựa trên số bài bắt buộc bạn đã đánh dấu hoàn thành. Mở trang hay phát video không tự động hoàn thành bài.</p></details>
  <details><summary>Điểm AI có phải điểm chính thức?</summary><p>Không. AI có thể đọc hoặc nhận xét sai. Điểm AI chỉ mang tính gợi ý; hãy đối chiếu kiến thức và hỏi giáo viên khi cần.</p></details>
</section>
<%@ include file="../partials/footer.jspf" %>
