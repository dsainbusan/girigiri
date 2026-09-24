document.querySelectorAll('.reveal-words').forEach(el => {
  const text = el.textContent.trim();
  el.setAttribute('aria-label', text);         // 스크린리더는 원문 그대로 읽게
  el.textContent = '';
  text.split(/\s+/).forEach((word, i) => {
    const wrap = document.createElement('span');
    wrap.className = 'w';
    wrap.setAttribute('aria-hidden', 'true');
    const inner = document.createElement('span');
    inner.style.setProperty('--i', i);
    inner.textContent = word;                  // innerHTML 대신 써서 XSS 방지
    wrap.appendChild(inner);
    el.append(wrap, ' ');
  });
});

const io = new IntersectionObserver(entries => entries.forEach(e => {
  if (e.isIntersecting) { e.target.classList.add('is-in'); io.unobserve(e.target); }
}), { rootMargin: '0px 0px -18% 0px' });      // 화면 아래 18% 지점에서 시작
document.querySelectorAll('.reveal-words').forEach(el => io.observe(el));
