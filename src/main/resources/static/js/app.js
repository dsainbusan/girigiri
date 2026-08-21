/* app.js — 공통 최소 스크립트 (필요할 때만 확장) */
(function () {
  "use strict";

  // 뒤로가기 버튼
  document.querySelectorAll("[data-back]").forEach(function (el) {
    el.addEventListener("click", function () { history.back(); });
  });

  // 카테고리 칩: 클릭 시 활성 표시 (실제 필터는 서버 요청/쿼리스트링으로)
  document.querySelectorAll("[data-chip-row]").forEach(function (row) {
    row.addEventListener("click", function (e) {
      var chip = e.target.closest(".chip");
      if (!chip) return;
      row.querySelectorAll(".chip").forEach(function (c) { c.classList.remove("is-active"); });
      chip.classList.add("is-active");
    });
  });
})();
