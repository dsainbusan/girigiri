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

  // 강노은: 찜하기 버튼 (common/components.html의 storeCard 프래그먼트에서 쓰임).
  // 카드 전체가 <a> 링크라서 하트 클릭이 카드 이동으로 새는 걸 막고, fetch로 토글 후 색만 바꾼다.
  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-like-btn]");
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation();

    var storeId = btn.getAttribute("data-store-id");
    if (!storeId) return;

    fetch("/api/likes/" + storeId + "/toggle", { method: "POST" })
      .then(function (res) {
        // 401 또는 (로그인 세션이 없어 로그인 페이지로 302 리다이렉트된) HTML 응답 둘 다 "로그인 필요"로 처리.
        var contentType = res.headers.get("content-type") || "";
        if (res.status === 401 || res.redirected || contentType.indexOf("application/json") === -1) {
          return Promise.reject(new Error("login_required"));
        }
        return res.json();
      })
      .then(function (data) {
        btn.classList.toggle("is-liked", !!data.liked);
      })
      .catch(function () {
        if (confirm("찜하려면 로그인이 필요해요. 로그인 화면으로 이동할까요?")) {
          location.href = "/auth/loginForm";
        }
      });
  });
})();
