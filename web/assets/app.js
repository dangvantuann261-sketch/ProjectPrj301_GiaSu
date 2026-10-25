'use strict';
document.querySelectorAll('[data-essay]').forEach(panel => {
  const context = document.querySelector('meta[name="app-context"]').content;
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  const endpoint = context + '/api/essay-step';
  const form = panel.querySelector('[data-essay-form]');
  const start = panel.querySelector('[data-essay-start]');
  const reload = panel.querySelector('[data-essay-reload]');
  const status = panel.querySelector('[data-essay-status]');
  const messages = panel.querySelector('[data-essay-messages]');
  const input = form.querySelector('textarea');
  const resubmit = document.querySelector('[data-essay-resubmit]');
  let state, busy = false;
  function render(data) {
    state = data; messages.replaceChildren();
    data.messages.forEach(message => {
      const bubble = document.createElement('div'); bubble.className = 'chat-bubble ' + (message.role === 'user' ? 'from-user' : 'from-ai');
      const label = document.createElement('strong'); label.textContent = message.role === 'user' ? 'Em' : 'Gia sư';
      const body = document.createElement('p'); body.textContent = message.text; bubble.append(label, body); messages.appendChild(bubble);
    });
    start.hidden = data.phase !== 'NEW'; form.hidden = data.phase !== 'WAITING';
    if (resubmit) resubmit.hidden = data.phase !== 'RESUBMIT';
    status.textContent = data.phase === 'NEW' ? 'Em chủ động bắt đầu khi sẵn sàng.' : data.phase === 'RESUBMIT' ? 'Bắt buộc: tự làm lại toàn bài và nộp ảnh mới ở bên dưới.' : 'Bước ' + data.step + ': điền đáp số hoặc ý còn thiếu. Gia sư đang chờ em.';
    messages.scrollTop = messages.scrollHeight;
  }
  async function request(action) {
    if (busy) return; busy = true;
    panel.querySelectorAll('button').forEach(button => { button.disabled = true; });
    status.textContent = action ? 'AI đang kiểm tra bước này…' : 'Đang tải bước hiện tại…';
    const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 100000);
    try {
      const options = { signal: controller.signal };
      let url = endpoint + '?answerId=' + encodeURIComponent(panel.dataset.answerId);
      if (action) {
        options.method = 'POST'; options.headers = { 'X-CSRF-Token': csrf };
        options.body = new URLSearchParams({ answerId: panel.dataset.answerId, version: state.version, action, value: action === 'answer' ? input.value.trim() : '' });
        url = endpoint;
      }
      const response = await fetch(url, options); const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Chưa xử lý được. Em hãy tải lại bước hiện tại.');
      render(data); if (action) input.value = '';
    } catch (error) { status.textContent = error.name === 'AbortError' ? 'Yêu cầu quá lâu. Hãy tải lại bước hiện tại trước khi gửi tiếp.' : error.message; }
    finally { clearTimeout(timeout); busy = false; panel.querySelectorAll('button').forEach(button => { button.disabled = false; }); }
  }
  start.addEventListener('click', () => { if (state) request('start'); });
  reload.addEventListener('click', () => request());
  form.addEventListener('submit', event => { event.preventDefault(); if (state && input.value.trim()) request('answer'); });
  request();
});
document.querySelectorAll('form').forEach(form => {
  const choices = form.querySelectorAll('.answer-choice');
  if (!choices.length) return;
  function syncChoices() {
    choices.forEach(choice => {
      const input = choice.querySelector('input[type="radio"]');
      choice.classList.toggle('is-selected', Boolean(input && input.checked));
    });
  }
  form.addEventListener('change', syncChoices);
  form.addEventListener('reset', () => setTimeout(syncChoices, 0));
  window.addEventListener('pageshow', syncChoices);
  syncChoices();
});
document.querySelectorAll('form[data-confirm]').forEach(form => form.addEventListener('submit', event => { if (!confirm(form.dataset.confirm)) event.preventDefault(); }));
document.querySelectorAll('form[data-busy]').forEach(form => {
  const button = form.querySelector('button[type="submit"], button:not([type])');
  if (!button) return;
  const original = button.textContent;
  form.addEventListener('submit', event => { if (!event.defaultPrevented) { button.disabled = true; button.textContent = form.dataset.busy; form.setAttribute('aria-busy', 'true'); } });
  window.addEventListener('pageshow', () => { button.disabled = false; button.textContent = original; form.removeAttribute('aria-busy'); });
});
const current = location.pathname + location.search;
document.querySelectorAll('.side-nav a').forEach(link => { const url = new URL(link.href); if (current === url.pathname + url.search) { link.classList.add('active'); link.setAttribute('aria-current', 'page'); } });
document.querySelectorAll('[data-chat]').forEach(async panel => {
  const context = document.querySelector('meta[name="app-context"]').content;
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  const endpoint = context + '/api/chat';
  const messages = panel.querySelector('[data-messages]');
  const status = panel.querySelector('[data-chat-status]');
  const form = panel.querySelector('[data-chat-form]');
  const input = form.querySelector('textarea');
  const button = form.querySelector('button');
  const reset = panel.querySelector('[data-chat-reset]');
  let busy = false, resetNext = false;
  function append(role, text) {
    const bubble = document.createElement('div'); bubble.className = 'chat-bubble ' + (role === 'user' ? 'from-user' : 'from-ai');
    const label = document.createElement('strong'); label.textContent = role === 'user' ? 'Em' : '✦ Gia sư';
    const body = document.createElement('p'); body.textContent = text; bubble.append(label, body); messages.appendChild(bubble); messages.scrollTop = messages.scrollHeight;
    return bubble;
  }
  async function send(message) {
    if (busy) return; busy = true; button.disabled = true; reset.disabled = true; status.textContent = 'Gia sư đang suy nghĩ…';
    if (resetNext) messages.replaceChildren();
    const bubble = append('user', message);
    const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 100000);
    try {
      const body = new URLSearchParams({ message, answerId: panel.dataset.answerId, itemId: panel.dataset.itemId || '0', reset: resetNext ? '1' : '0' });
      const response = await fetch(endpoint, { method: 'POST', headers: { 'X-CSRF-Token': csrf }, body, signal: controller.signal });
      const data = await response.json(); if (!response.ok) throw new Error(data.error || 'Chưa gửi được tin nhắn.');
      append('model', data.reply); input.value = ''; resetNext = false; status.textContent = 'Đến lượt em. Hãy thử trả lời gợi ý ở trên.';
    } catch (error) { bubble.remove(); input.value = message; status.textContent = error.name === 'AbortError' ? 'Kết nối quá lâu. Em hãy thử gửi lại.' : error.message; }
    finally { clearTimeout(timeout); busy = false; button.disabled = false; reset.disabled = false; input.focus(); }
  }
  form.addEventListener('submit', event => { event.preventDefault(); const message = input.value.trim(); if (message) send(message); });
  reset.addEventListener('click', () => { if (busy) return; resetNext = true; messages.replaceChildren(); status.textContent = 'Nhập câu hỏi mới để bắt đầu lại.'; input.focus(); });
  try {
    const response = await fetch(endpoint + '?answerId=' + encodeURIComponent(panel.dataset.answerId) + '&itemId=' + encodeURIComponent(panel.dataset.itemId || '0')); const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Không tải được hội thoại.');
    data.messages.forEach(message => append(message.role, message.text));
    if (data.messages.length === 0 && panel.dataset.autostart === 'true') await send('Em chưa hiểu chỗ sai trong bài làm. Hãy chỉ cho em một gợi ý đầu tiên.');
  } catch (error) { status.textContent = error.message; }
});

const navToggle = document.querySelector('[data-nav-toggle]');
navToggle?.addEventListener('click', () => {
  const open = document.body.classList.toggle('navigation-open');
  navToggle.setAttribute('aria-expanded', String(open));
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape') { document.body.classList.remove('navigation-open'); navToggle?.setAttribute('aria-expanded', 'false'); }
});
// Keep the roadmap selected while reading one of its lessons.
if (/\/(course|student\/lesson)$/.test(location.pathname)) {
  document.querySelectorAll('.side-nav a').forEach(link => {
    if (new URL(link.href).pathname.endsWith('/courses')) { link.classList.add('active'); link.setAttribute('aria-current', 'page'); }
  });
}
const lessonSearch = document.querySelector('[data-lesson-search]');
lessonSearch?.addEventListener('input', () => {
  const normalize = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase();
  const term = normalize(lessonSearch.value.trim()); let count = 0;
  document.querySelectorAll('.chapter-block').forEach(chapter => {
    let visible = 0;
    chapter.querySelectorAll('[data-lesson-row]').forEach(row => {
      row.hidden = !normalize(row.textContent + chapter.querySelector('summary').textContent).includes(term);
      if (!row.hidden) visible++;
    });
    chapter.hidden = visible === 0;
    if (term && visible) chapter.open = true;
    count += visible;
  });
  document.querySelector('[data-search-status]').textContent = count ? `Tìm thấy ${count} bài học.` : 'Chưa tìm thấy bài. Em hãy thử tên khác hoặc xóa từ khóa.';
});
const chart = document.querySelector('[data-score-chart]');
if (chart) {
  const scores = [...document.querySelectorAll('[data-score-table] [data-score]')].map(row => Number(row.dataset.score));
  const draw = () => {
    const w = chart.clientWidth, h = 260, ratio = window.devicePixelRatio || 1;
    chart.width = w * ratio; chart.height = h * ratio;
    const c = chart.getContext('2d'); c.scale(ratio, ratio); c.font = '12px sans-serif';
    for (let n = 0; n <= 10; n += 2) {
      const y = 225 - n * 19; c.strokeStyle = '#dce7e1'; c.beginPath(); c.moveTo(35, y); c.lineTo(w - 20, y); c.stroke();
      c.fillStyle = '#52665c'; c.fillText(String(n), 10, y + 4);
    }
    const x = i => scores.length === 1 ? w / 2 : 40 + i * (w - 65) / (scores.length - 1);
    c.strokeStyle = '#176b57'; c.lineWidth = 3; c.beginPath();
    scores.forEach((score, i) => i ? c.lineTo(x(i), 225 - score * 19) : c.moveTo(x(i), 225 - score * 19)); c.stroke();
    scores.forEach((score, i) => { c.fillStyle = '#176b57'; c.beginPath(); c.arc(x(i), 225 - score * 19, 4, 0, Math.PI * 2); c.fill(); });
    c.fillStyle = '#52665c'; c.fillText('Cũ nhất', 35, 251); c.fillText('Gần nhất', w - 72, 251);
  };
  new ResizeObserver(draw).observe(chart);
}
const notificationPanel = document.querySelector('[data-notification-list]');
if (notificationPanel) {
  const context = document.querySelector('meta[name="app-context"]').content;
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  const refresh = async () => {
    if (document.hidden) return;
    try {
      const response = await fetch(context + '/student/notifications'); if (!response.ok) return;
      const data = await response.json(); notificationPanel.replaceChildren();
      if (!data.notifications.length) { const p = document.createElement('p'); p.textContent = 'Thông báo sẽ hiện tại đây khi bài tự luận được AI chấm xong.'; notificationPanel.append(p); }
      data.notifications.forEach(n => {
        const row = document.createElement('div'); row.className = 'resume-row';
        const link = document.createElement('a'); link.href = context + '/student/review?id=' + n.id; link.textContent = `Bài tự luận đã được AI nhận xét · ${n.score}/10 →`;
        const read = document.createElement('button'); read.type = 'button'; read.className = 'btn btn-sm btn-outline-secondary'; read.textContent = 'Đã đọc';
        read.addEventListener('click', async () => {
          read.disabled = true;
          try { const result = await fetch(context + '/student/notifications', { method: 'POST', headers: { 'X-CSRF-Token': csrf }, body: new URLSearchParams({ id: n.id }) }); if (result.ok) await refresh(); }
          finally { read.disabled = false; }
        }); row.append(link, read); notificationPanel.append(row);
      });
    } catch (_) { /* Leave the last successfully loaded notifications visible. */ }
  };
  refresh(); setInterval(refresh, 30000);
}
document.querySelectorAll('[data-password-toggle]').forEach(button => {
  const input = document.getElementById(button.getAttribute('aria-controls'));
  if (!input) return;
  button.addEventListener('click', () => {
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.textContent = show ? 'Ẩn' : 'Hiện';
    button.setAttribute('aria-pressed', String(show));
  });
});
