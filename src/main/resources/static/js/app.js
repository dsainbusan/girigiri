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

  // 강노은: 범용 탭 전환. [data-tabs] 컨테이너 안의 버튼(data-tab="이름")을 누르면, 같은
  // 부모 아래 있는 [data-tab-panel="이름"] 패널만 보이고 나머지는 숨는다. 페이지 이동 없이
  // 클릭만으로 전환 — 원래 supportView/home.html(고객센터)에만 있던 걸 가게 상세 페이지
  // (정보/상품/리뷰 탭)에도 재사용하면서 공용으로 뺐다.
  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-tab]");
    if (!btn) return;
    var tabBar = btn.closest("[data-tabs]");
    if (!tabBar || !tabBar.parentElement) return;

    tabBar.querySelectorAll("[data-tab]").forEach(function (b) { b.classList.remove("is-active"); });
    btn.classList.add("is-active");

    var name = btn.getAttribute("data-tab");
    tabBar.parentElement.querySelectorAll("[data-tab-panel]").forEach(function (panel) {
      panel.hidden = panel.getAttribute("data-tab-panel") !== name;
    });
  });

  // 강노은: "접힌 걸 눌러서 펼치기" 공통 패턴 (가게 상세의 "리뷰 작성"/"내 리뷰 수정하기",
  // 내 리뷰 관리의 리뷰별 인라인 수정 폼 등 — 여러 개 있을 수 있는 패널이라 id로 대상을 지정한다).
  // data-toggle-target="엘리먼트id" 버튼을 누르면 해당 id의 hidden을 벗기고 화면 중앙으로 스크롤한다.
  // 폼이 펼쳐진 동안 "펼치기" 버튼 자체는 할 일이 끝났으니 같이 숨긴다(안 그러면 폼이 열려있는데
  // "리뷰 작성" 버튼이 그대로 남아있어서 안 눌린 것처럼 보인다).
  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-toggle-target]");
    if (!btn) return;
    var target = document.getElementById(btn.getAttribute("data-toggle-target"));
    if (!target) return;
    target.hidden = false;
    btn.hidden = true;
    target.scrollIntoView({ behavior: "smooth", block: "center" });
  });

  // 강노은: 위에서 펼친 패널을 다시 접는 "닫기" 버튼. 작성/수정하다가 귀찮아지거나 마음이 바뀔 수도
  // 있으니, 제출하지 않고도 패널을 닫을 방법이 필요해서 추가. data-close-panel 버튼을 누르면 가장
  // 가까운 [data-reopen-btn] 조상(=위에서 펼쳐진 패널 자신)을 찾아 다시 숨기고, 그 패널을 열었던
  // 버튼(data-reopen-btn이 가리키는 id)을 되살린다.
  document.addEventListener("click", function (e) {
    var closeBtn = e.target.closest("[data-close-panel]");
    if (!closeBtn) return;
    var panel = closeBtn.closest("[data-reopen-btn]");
    if (!panel) return;
    panel.hidden = true;
    var openBtn = document.getElementById(panel.getAttribute("data-reopen-btn"));
    if (openBtn) openBtn.hidden = false;
  });

  // 강노은: 리뷰 등록/수정 완료 안내 같은 1회성 알림(flash)은 계속 떠있을 필요가 없어서,
  // data-auto-dismiss="ms" 만큼 지나면 살짝 페이드되며 사라지게 한다.
  document.querySelectorAll("[data-auto-dismiss]").forEach(function (el) {
    var delay = parseInt(el.getAttribute("data-auto-dismiss"), 10) || 3000;
    setTimeout(function () {
      el.style.transition = "opacity .3s ease";
      el.style.opacity = "0";
      setTimeout(function () { el.hidden = true; }, 300);
    }, delay);
  });

  // 강노은: 리뷰 사진 dropzone — 클릭 선택 + 드래그 앤 드롭 둘 다 지원.
  // storeView/detail.html(새 리뷰 작성 · 내 리뷰 수정)과 reviewView/my.html(내 리뷰 관리 인라인
  // 수정)에서 [data-photo-dropzone] 하나씩을 이 함수로 초기화한다. 상태는 2가지:
  //   idle(사진 없음/선택 전) ↔ preview(사진 있음 — 기존 사진이거나 새로 고른 파일)
  // "제거"를 누르면 별도 안내 문구 없이 곧장 idle(참고 이미지의 첫 화면)로 돌아간다 — 그것만으로도
  // "사진이 없어졌다"가 idle 화면 자체로 충분히 보인다. removeFlag는 그대로 true로 세팅해서
  // 저장 시 서버가 기존 사진을 지우게 한다.
  function initReviewPhotoDropzone(root) {
    var input = root.querySelector("[data-photo-input]");
    var removeFlag = root.querySelector("[data-photo-remove-flag]");
    var idle = root.querySelector("[data-photo-idle]");
    var preview = root.querySelector("[data-photo-preview]");
    var previewImg = root.querySelector("[data-photo-preview-img]");
    var filenameEl = root.querySelector("[data-photo-filename]");
    var removeBtn = root.querySelector("[data-photo-remove]");
    if (!input || !idle || !preview) return;

    function setState(state) {
      idle.hidden = state !== "idle";
      preview.hidden = state !== "preview";
    }

    function showPreview(file) {
      previewImg.src = URL.createObjectURL(file);
      if (filenameEl) filenameEl.textContent = file.name;
      if (removeFlag) removeFlag.value = "false";
      setState("preview");
    }

    input.addEventListener("change", function () {
      if (input.files && input.files[0]) showPreview(input.files[0]);
    });

    if (removeBtn) {
      removeBtn.addEventListener("click", function (e) {
        // ✕ 버튼 클릭이 root까지 버블링되면 아래 root 클릭 핸들러가 파일 선택창을 다시 열어버려서 막는다.
        e.preventDefault();
        e.stopPropagation();
        input.value = "";
        if (removeFlag) removeFlag.value = "true";
        setState("idle");
      });
    }

    // div+role="button"이라 label의 네이티브 클릭 forwarding이 없다 — 직접 열어준다.
    root.addEventListener("click", function (e) {
      if (e.target.closest("[data-photo-remove]")) return;
      input.click();
    });

    root.addEventListener("keydown", function (e) {
      if (e.target !== root) return; // ✕ 버튼 등 자식 요소의 Enter/Space는 그냥 두기
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        input.click();
      }
    });

    // dragenter/dragleave가 dropzone 안의 자식 요소를 넘나들 때마다 반복 발생해서
    // 카운터로 세지 않으면 is-dragover가 깜빡인다.
    var dragCounter = 0;
    root.addEventListener("dragenter", function (e) {
      e.preventDefault();
      dragCounter++;
      root.classList.add("is-dragover");
    });
    root.addEventListener("dragover", function (e) {
      e.preventDefault();
      if (e.dataTransfer) e.dataTransfer.dropEffect = "copy"; // 커서를 "놓을 수 있음" 모양으로
    });
    root.addEventListener("dragleave", function (e) {
      e.preventDefault();
      dragCounter = Math.max(0, dragCounter - 1);
      if (dragCounter === 0) root.classList.remove("is-dragover");
    });
    root.addEventListener("drop", function (e) {
      e.preventDefault();
      dragCounter = 0;
      root.classList.remove("is-dragover");
      var files = e.dataTransfer && e.dataTransfer.files;
      if (files && files[0]) {
        input.files = files;
        showPreview(files[0]);
      }
    });
  }

  document.querySelectorAll("[data-photo-dropzone]").forEach(initReviewPhotoDropzone);

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
