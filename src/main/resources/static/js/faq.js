/* faq.js — 구매자 / 가게 탭 전환 (방향키 지원) */
(function () {
  const tabs = [...document.querySelectorAll(".faq__tab")];
  if (!tabs.length) return;

  function select(tab) {
    tabs.forEach((t) => {
      const on = t === tab;
      t.setAttribute("aria-selected", on);
      t.tabIndex = on ? 0 : -1;
      document.getElementById(t.getAttribute("aria-controls")).hidden = !on;
    });
    tab.focus();
  }

  tabs.forEach((tab, i) => {
    tab.addEventListener("click", () => select(tab));
    tab.addEventListener("keydown", (e) => {
      if (e.key === "ArrowRight") select(tabs[(i + 1) % tabs.length]);
      if (e.key === "ArrowLeft") select(tabs[(i - 1 + tabs.length) % tabs.length]);
    });
  });

  // /home#faq-store 처럼 들어오면 가게 탭을 먼저 열기
  const fromHash = tabs.find((t) => "#" + t.getAttribute("aria-controls") === location.hash);
  if (fromHash) select(fromHash);
})();

/* 2026-09-29 추가 — "채팅 상담" 버튼(common/faq.html의 [data-open-chat])을 누르면 비회원용
   챗봇 패널을 연다. 실제 열기 로직은 common/chatWidget.html이 가지고 있고(패널 표시, 포커스
   이동 등) 여기서는 그 프래그먼트가 전역에 노출한 window.girigiriOpenChat()만 호출한다 — DOM
   구조를 다시 알 필요 없이 위젯을 완전히 블랙박스로 재사용하기 위함. faq.js는 chatWidget보다
   먼저 로드될 수도 있어서(head의 <script defer>) 클릭 시점에만 함수 존재를 확인한다. */
document.addEventListener("click", (e) => {
  if (e.target.closest("[data-open-chat]") && typeof window.girigiriOpenChat === "function") {
    window.girigiriOpenChat();
  }
});
