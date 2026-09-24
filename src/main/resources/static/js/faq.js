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
