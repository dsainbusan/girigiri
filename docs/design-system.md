# 기리기리 · ギリギリ 디자인 시스템

> UI를 만들거나 고치기 전에 이 문서를 먼저 읽는다. 기준 코드: `src/main/resources/static/css/` 15개 파일 (dev@893a518과 동일).
> 규칙이 코드와 다르면 **코드(tokens.css)가 우선**이고, 이 문서를 고친다.

---

## 0. 작업 전 체크리스트 (Claude Code용)

1. 지금 고치는 화면이 **어느 시스템**인지 먼저 정한다 → [1. 세 개의 시스템](#1-세-개의-시스템)
2. 색·간격·모서리·폰트·그림자는 `var(--…)`만 쓴다. 새 hex/px 생값을 만들지 않는다. 필요한 값이 없으면 `tokens.css`에 토큰을 **추가**하고 이 문서 표에도 추가한다.
3. 공통 파일(`tokens.css`, `base.css`, `layout.css`, `components.css`)은 오너(송보미) 영역이다. 페이지 전용 스타일은 `static/css/<도메인>.css`에 둔다.
4. 텍스트 색을 새로 정하면 `--c-surface`(#FFF)와 `--c-bg`(#F6F0E3) 두 배경에서 대비를 잰다 (본문 4.5:1, 18px+ 또는 14px+ bold는 3:1).
5. 새 컴포넌트를 만들기 전에 [4. 공통 컴포넌트](#4-공통-컴포넌트-componentscss)와 [5. 도메인 CSS](#5-도메인-전용-css)에 비슷한 게 있는지 찾는다. 이미 3곳 이상에서 같은 모양이면 `components.css`로 승격을 제안한다.
6. 끝나면 [8. 알려진 부채](#8-알려진-부채--정리-대상) 목록에서 해결한 항목을 지운다.

---

## 1. 세 개의 시스템

이 저장소에는 **서로 섞으면 안 되는** 디자인 시스템이 3개 있다. 클래스명(`.btn`, `.badge`, `.tabs`, `.stat-card`, `.pagination`…)이 겹치므로 한 화면에 두 시스템 CSS를 같이 링크하면 캐스케이드가 꼬인다.

| 시스템 | 레이아웃 템플릿 | 로드하는 CSS | 폰트 | 폭 |
|---|---|---|---|---|
| **앱** (손님·점주) | `common/layout.html` | `tokens` → `base` → `layout` → `components` → 화면별 도메인 CSS | Noto Sans KR (Google Fonts 400/500/600/700/900) | 420px 고정 (`--app-max`) |
| **마케팅 홈** (`/`) | `marketingView/home.html` | `tokens` → `marketing` | Noto Sans KR | 데스크톱 반응형, 최대 1160px |
| **슈퍼어드민** | `common/layout-admin.html` | `layout-admin` (자체 토큰) | Pretendard (jsDelivr) | 최대 1360px |

- 마케팅은 `tokens.css`의 **브랜드 색만** 빌려 쓴다. `--fs-*`(모바일 11~36px)와 `--app-max`는 쓰지 않는다.
- 슈퍼어드민은 `--blue-*`/`--gray-*`/`--red-*`/`--green-*`/`--amber-*` 자체 토큰을 쓰는 독립 시스템이다. 이 문서의 앱 규칙(크림/네이비)을 어드민에 적용하지 않는다. → [7. 슈퍼어드민](#7-슈퍼어드민-layout-admincss)

---

## 2. 브랜드

### 이름
- 한국어 화면: **기리기리** / 일본어 화면: **ギリギリ**. 한 화면·한 문장에 둘을 섞지 않는다.
- 로마자가 꼭 필요하면 **GIRIGIRI**(대문자). "기리 기리"처럼 띄우지 않는다.
- 두 언어를 함께 보여 줄 때(소개 자료 등): "기리기리 ギリギリ" 순서.

### 로고
- 마크(다크용): 둥근 시계(마감 시간을 가리키는 바늘). 파일 `static/images/brand/girigiri-mark.png` (178×148, 어두운 바탕 #121212 + 밝은 회색 #D9D9D9 선, **바탕 포함**) — **아직 파일 없음, 추가 예정**.
  - 준비되면 원본 그대로 `<img alt="기리기리">`(일본어 화면은 `alt="ギリギリ"`)로 쓰고 다시 그리거나 색을 바꾸지 않는다. 모서리는 `--r-md`.
  - 바탕이 어두우므로 어두운 면(`--c-dark`, 다크 존)이나 앱 아이콘에 쓴다.
- 마크(라이트용, 2026-09-24 갱신): `static/images/brand/logo.svg` — 크림 배경(#F6F0E3) + 네이비 외곽선, 507B. 밝은 면(마케팅 홈 nav/footer, 로그인 화면, 어드민 gnb, favicon 세트)에 이미 적용 완료.
- 워드마크: 마크 옆 Noto Sans KR 900, 자간 0.
- `.mkt-logo__badge`(marketing.css)는 더 이상 텍스트("ギリ") 임시 배지가 아니라 위 `logo.svg`를 담는 `<img>` 컨테이너다. 다크용 `girigiri-mark.png`가 추가되면 어두운 면 전용 자리에 한해 교체 검토.

### 말투
- 손님에게 "~해요"체, 짧고 구체적으로: "예약하기", "마감 38분 전", "아직 받은 알림이 없어요".
- 버튼은 동사로 끝낸다("예약하기", "예약 취소"). 확인 모달 제목은 질문형("예약을 취소할까요?").
- 숫자는 단위와 함께: "3,900원", "도보 6분", "20:00 마감".
- UI 아이콘은 이모지 대신 SVG 스프라이트(`common/layout.html`의 `.icon-sprite`, 25개)를 쓴다. 뱃지 도감·공유 카드의 이모지(twemoji)는 콘텐츠라 예외.

---

## 3. 토큰 (`tokens.css`)

`var(--이름)`으로만 쓴다. 전체 값은 `tokens.css`가 원본이다.

### 색 — 브랜드
| 토큰 | 값 | 쓰는 곳 |
|---|---|---|
| `--c-primary` | #0F2B8C | 네이비. 주 버튼, 활성 탭, 판매가, 포커스 링, 링크 |
| `--c-primary-strong` | #001A5C | 짙은 네이비. hover·강조, 다크 존 배경 |
| `--c-primary-weak` | #F6F0E3 | 크림 틴트 배경 (= `--c-bg`) |
| `--c-primary-border` | #C7D2EA | 옅은 네이비 테두리 (선택 칩·배너·고스트 버튼) |
| `--c-primary-on` | #F6F0E3 | 네이비 위 글자 (흰색 대신 크림) |
| `--c-primary-active` | #193DB3 | 2026-09-24 추가 — `--c-primary`보다 밝은 활성 톤. layout.css 하단 탭바 FAB의 is-active 전용 |
| `--c-primary-mid` | #8CA3D6 | 도넛 "예약됨(픽업 대기)". 글자 색으로 쓰지 않는다 |
| `--c-idle` | #D9D2C4 | 도넛 "아직 판정 안 남". 글자 색으로 쓰지 않는다 |

### 색 — 기능 (의미가 고정됨)
| 토큰 | 값 | 의미 / 규칙 |
|---|---|---|
| `--c-accent` / `-weak` / `-border` / `-strong` | #F97316 / #FFF7ED / #FDBA74 / #C2410C | **주황 = 할인·마감임박 전용.** 실패·경고에 빌려 쓰지 않는다. 주황 채움 위 글자는 `--c-text`. 옅은 배경 위 글자는 `-strong`. `-border`는 2026-09-24 추가(reservation.css `.scan-status.status-warn` 테두리) |
| `--c-danger` / `-weak` / `-border` / `-strong` | #EF4444 / #FEF2F2 / #FECACA / #B91C1C | 폐기·긴급·실패·취소. 옅은 배경 위 글자는 `-strong`. **작은 글자(본문 크기)에 순정 `--c-danger`를 흰/크림 배경에 직접 쓰면 대비 미달(≈3.3~3.8:1)이라 항상 `-strong`을 쓴다** |
| `--c-info` / `-weak` / `-border` / `-strong` | #0EA5E9 / #EFF6FF / #A5F3FC / #0369A1 | 정보. 옅은 배경 위 글자는 `-strong`. `-border`는 2026-09-24 추가(components.css `.banner--info` 테두리) |
| `--c-badge-rep` / `-border` / `-on` / `-accent` | #FEF3C7 / #FDE68A / #92400E / #F59E0B | 대표 뱃지(게이미피케이션) 전용 호박색. `-accent`는 2026-09-24 추가(ledger.css 도감/달성 강조 테두리·배경, 9곳) |
| `--c-live-weak` / `-on` / `-border` | rgba 그린 / #86EFAC / rgba | "점주 모드" 라이브 배지 — **딥네이비 히어로 위 전용** |

### 색 — 중립 (웜 그레이)
| 토큰 | 값 | 대비 (흰색 / 크림) | 규칙 |
|---|---|---|---|
| `--c-text` | #1C1C1C | 18.1 / 15.9 | 기본 글자 |
| `--c-text-sub` | #5C5545 | 7.4 / 6.5 | 보조 글자 |
| `--c-text-mut-strong` | #6B6354 | 5.9 / 5.2 | `badge--muted` 글자, 작은 흐린 글자가 필요할 때 |
| `--c-text-mut` | #7D735E | 4.7 / 4.1 | 플레이스홀더·비활성. **크림 바탕의 작은 본문엔 금지** |
| `--c-line` | #CBBFA6 | — | 경계선·입력 테두리 |
| `--c-line-weak` | #F1ECE1 | — | 약한 구분선, 카드 테두리 |
| `--c-bg` | #F6F0E3 | — | 앱 본문 배경 (크림) |
| `--c-surface` | #FFFFFF | — | 카드·표면, 앱 바깥 브라우저 배경 |
| `--c-dark` | #1C1C1C | — | 다크 상단바(사장님/운영자) |

- 차가운 회청색(slate/gray: #9CA3AF, #D1D5DB, #1F2937 등)은 쓰지 않는다. 예외: `--c-google-on`(구글 버튼 규정).

### 색 — 소셜 로그인 (브랜드 규정)
`--c-kakao` #FEE500 + `--c-kakao-on` #191600 · `--c-line-brand` #06C755 (흰 글자) · `--c-google-on` #1F2937 (흰 바탕 + `--c-line` 테두리) · `--c-naver` #03C75A (토큰만 있음 — 현재 로그인 제공자는 구글/카카오/라인).

### 도넛·차트 색 순서
판매 `--c-primary` → 예약됨 `--c-primary-mid` → 폐기 `--c-danger` → 미정 `--c-idle`. "네이비 계열 = 좋은 결과". 옅은 네이비·주황·빨강은 **채움·점·아이콘에만** 쓰고 글씨 색으로 쓰지 않는다 (store.css 통계 탭 규칙).

### 간격 · 모서리 · 그림자 · 레이아웃
| 그룹 | 토큰 |
|---|---|
| 간격 (4px 단위) | `--s-1` 4 · `--s-2` 8 · `--s-3` 12 · `--s-4` 16 · `--s-5` 20 · `--s-6` 24 · `--s-8` 32 |
| 모서리 | `--r-sm` 8 · `--r-md` 12 · `--r-lg` 16 · `--r-xl` 18 · `--r-pill` 999 |
| 그림자 | `--shadow-card` (떠 있어야 할 카드) · `--shadow-nav` (하단 탭바, 챗봇 FAB) · `--shadow-modal` (모달 박스, 2026-09-24 추가 — components.css `.modal-box`가 순정 검정 대신 이 슬레이트 톤을 쓴다) |
| 레이아웃 | `--app-max` 420 · `--topbar-h` 56 · `--bottomnav-h` 64 · `--hero-h-sm` 120 · `--hero-h-lg` 150 |
| 오버레이 | `--overlay-scrim` rgba(0,0,0,.45) · `--z-modal` 200 |

### 타이포그래피
- `--font-base`: "Noto Sans KR", system-ui, -apple-system, "Malgun Gothic", sans-serif.
- 크기: `--fs-xs` 11 · `--fs-sm` 13 · `--fs-md` 15 · `--fs-lg` 18 · `--fs-xl` 22 · `--fs-2xl` 28 · `--fs-3xl` 36.
- 굵기: `--fw-regular` 400 · `--fw-medium` 500 · `--fw-semibold` 600 · `--fw-bold` 700 · `--fw-black` 900.
- 기본(base.css): body = `--fs-md` / **`--fw-semibold`(600)** / line-height 1.5. h1 = `--fs-xl` black, h2 = `--fs-lg` bold, h3 = `--fs-md` bold.
- **위계는 굵기 단계로.** 카드 하나에 black(900)은 하나(보통 판매가 `.price__sale`). 배지는 bold까지, 가게명은 semibold.
- 한글 자간을 좁히지 않는다(0). 예외: 마케팅 큰 제목(-0.02~-0.03em)은 기존 유지.

### z-index 사용 현황 (토큰화 전 참고)
`1~2` 카드 내부 · `10` 툴팁 · `20` 상단바 · `25` 하단 CTA · `30` 하단 탭바 · `40` 챗봇 FAB · `50` 챗봇 패널·마케팅 nav · `100` 공용 모달 · `200` `--z-modal` · `1000` 가계부 모달·점주 바텀시트. 새 레이어는 이 순서 안에 끼운다.

---

## 4. 공통 컴포넌트 (`components.css`)

BEM(`.block__element--modifier`), 상태는 `.is-*`. 오너: 송보미.

| 컴포넌트 | 클래스 | 변형 / 상태 | 규칙 |
|---|---|---|---|
| 아이콘 | `.icon` (18px, fill currentColor), `.icon--dim` | — | `<svg class="icon"><use href="#i-bell"/></svg>` |
| 버튼 | `.btn` | `--block` `--accent` `--danger` `--dark` `--outline` `--ghost` `--sm` · 소셜 `--kakao` `--google` `--line` `--naver` | 네이비 + 크림 글자, `--r-md`, 15×20px, black. `--accent`/`--danger` 글자는 `--c-text`. 화면(섹션)당 채움 버튼 하나 |
| 카드 | `.card` | `--flat` `--shadow` · `.is-disabled`(reservation) | 흰 바탕, `--c-line-weak` 테두리, `--r-lg` |
| 칩 | `.chip`, `.chip-row` | `.is-active` | 카테고리 필터, 가로 스크롤 |
| 배지 | `.badge` | `--discount`/`--accent`(주황) `--success`(네이비) `--info` `--danger` · `--muted`(reservation) | 배경 `-weak` + 글자 `-strong`. 실패는 `--danger`, 할인만 주황 |
| 가격 | `.price`, `__orig`, `__sale` | — | 판매가가 카드의 유일한 black |
| 가게 카드 | `.store-card` + `__thumb __body __top __name __meta __like __left` | `__like.is-liked`, `__left.is-urgent`, `.is-blocked`(home) | 임박 빨강은 백엔드가 `urgent`로 판정했을 때만. 찜 버튼 히트영역 42px |
| 배너 | `.banner`, `__title`, `__desc` | `--info` · `--danger`(reservation) | 크림 틴트 + 네이비 테두리 |
| 지표 카드 | `.stat-grid`, `.stat-card` + `__label __value __delta` | — | 2열 |
| 진행 막대 | `.progress`, `__bar` | `__bar.is-near`/`.is-achieved`(ledger), `.progress--xs`(ledger) | |
| 섹션 제목 | `.section-title`, `__count` | — | fs-lg black, 개수는 주황 |
| 입력 | `.field`, `.field__label`, `.input` | — | 포커스 시 테두리 `--c-primary`. 라벨 필수 |
| 검색 트리거 | `.search` | — | 크림 바탕 + `--c-line` 테두리 |
| 목록 행 | `.list-row` + `__ico __body __arrow` | — | 설정·내역 목록 |
| 역할 카드 | `.mode-card`, `__ico` | `--accent` | 가입 역할 선택. 입력은 `.sr-only` |
| 빈 상태 | `.support-empty`, `__ico`, `__text` | — | |
| 썸네일 대체 | `.thumb-placeholder` | — | 4:3 |
| 별점 입력 | `.star-input` | — | 라디오 + ★, 채움 주황 |
| 사진 업로드 | `.review-upload` + `__input __badge __text __choose __preview(-img) __filename __remove`, `.review-upload-hint` | `.is-dragover` | 동작은 app.js |
| 탭 | `.tabs`, `.tab` | `.is-active` | 동작은 app.js `[data-tabs]` |
| 모달 | `.modal-overlay`, `.modal-box` + `__icon(--neutral) __title __desc __actions __field` | — | 네이티브 `confirm()/alert()/prompt()` 대신 공용 `#girigiri-modal` |

### 레이아웃 (`layout.css`)
| 클래스 | 규칙 |
|---|---|
| `.app` | 420px 가운데, 흰 바탕, 데스크톱에서 1px 테두리 |
| `.app__main` (`--has-actionbar`, `--plain`) | 크림 바탕, 하단 탭바/액션바 높이만큼 여백 |
| `.page`, `.page--tight` | 좌우 여백 20 / 16px |
| `.topbar` + `__back __title __spacer __action __cta __mode-form __mode-btn __mode-icon`, `.topbar--dark` | 56px sticky. `--dark`는 사장님/운영자 |
| `.bottomnav` + `__item __ico`, `__item--fab` + `__fab-circle __fab-ico __fab-label` | 64px fixed. 가운데 FAB는 점주 QR 스캔 |
| `.bottom-cta` + `__price` | 하단 고정 결제/예약 바. 하단 탭바와 한 화면에 같이 쓰지 않는다 |

### 기본 (`base.css`)
- 리셋, `[hidden]{display:none!important}` (숨김은 `hidden` 속성으로), 전역 `:focus-visible` 2px `--c-primary` offset 2px, `.sr-only`.
- 유틸: `.u-mut .u-sub .u-bold .u-black .u-center .u-primary .u-accent .u-danger .u-row .u-grow .u-mt-2 .u-mt-4`. 새 유틸은 최소한으로.

---

## 5. 도메인 전용 CSS

해당 화면에서만 `<link>`한다. 다른 도메인 파일의 클래스를 가져다 쓰지 않는다(필요하면 `components.css` 승격).

| 파일 | 담당 | 화면 | 주요 클래스 |
|---|---|---|---|
| `auth.css` | — | 로그인/가입 | `.oauth-btn(__ico)` 소셜 버튼 아이콘 위치, `.oauth-or` 구분선, `.oauth-email` |
| `home.css` | — | 홈 목록 | `.store-card.is-blocked` (신뢰도 정지 매장 흐리게) |
| `chat.css` | 송채현 | 마이페이지 챗봇 | `.chat-fab`, `.chat-panel(__header __title __messages __input-row __input __send-btn …)`, `.chat-bubble--bot/--user`, `.chat-quick-reply`, `.chat-typing`, `.chat-escalate-link` |
| `mypage.css` | 문창호 | 마이페이지 메뉴 | `.list-menu-item(__icon __title __body __desc __value __arrow)`, `--danger` |
| `settings.css` | 문창호 | 환경설정 | `.settings-section(__title)`, `.settings-card`, `.settings-toggle(__track)`, `.list-menu-item--toggle/--radio`, `.settings-check` |
| `search.css` | 강노은 | 검색 결과 | `.range-slider(__track __fill)` 듀얼 슬라이더, `.range-inputs(__unit)` |
| `reservation.css` | — | 예약/체크아웃/QR 픽업 | `.btn:disabled`, `.card.is-disabled`, `.badge--muted`, `.banner--danger`, `.qty-stepper(…)`, `.tab-bar/.tab-item`, `.empty-state`, `.scan-status(.status-loading/-ok/-warn)`, `#cart-list`, `#batch-result-list` |
| `ledger.css` | 문창호 | 절약 가계부 | `.ledger-hero(…)`, `.ledger-tier-chip`, `.seg-tabs(--sub --underline)/.seg-tab/.seg-panel`, `.ledger-bar-chart(…)`, `.ledger-history-*`, `.ledger-badge-*`(도감·카드·필터), `.ledger-modal-*`(바텀시트), `.ledger-rank-*`, `.ledger-share-card`(SNS 캡처), `.ledger-eco-banner`, `.ledger-explore-nudge` |
| `store.css` | — | 점주 대시보드·상품·정산·매출 리포트 (2,179줄) | `.dash-hero`, `.dash-stats/.dash-metric/.dash-chart/.dash-legend/.dash-subhead`, `.stat-toggle-tabs(--underline)`, `.donut/.tally`, `.bar-chart`, `.todo-banner`, `.pos-strip/.pos-sim`, `.stock-item/.stock-sheet/.stock-dialog`, `.quick-edit-*/.qe-*`, `.settle-*`(정산서), `.sales-*`(매출 리포트), `.report-*`, `.pagination`(앱 버전) |
| `marketing.css` | — | 소개 홈 `/` | `.mkt-nav/.mkt-logo(__badge)`, `.mkt-btn(--primary --ghost --sm --block)`, `.mkt-hero/.mkt-eyebrow/.mkt-mock/.mkt-chip`, `.mkt-marquee`(제휴사), `.mkt-section(--tint)`, `.mkt-stats/.mkt-steps`, `.mkt-owner`(점주 CTA), `.mkt-footer` |

### 반복되는 패턴 (새로 만들 때 이걸 재사용)
- **세그먼트 토글**: 앱 전반은 `.seg-tabs`(ledger)와 `.stat-toggle-tabs`(store)가 같은 역할. 상위 토글은 알약, 하위 토글은 `--underline`(밑줄)로 구분한다.
- **바텀시트 모달**: `.ledger-modal-overlay/.ledger-modal-sheet`, store의 `.stock-sheet`/`.quick-edit-panel`. 모바일은 아래에서 올라오고, 600px 이상은 가운데 모달. 애니메이션 `cubic-bezier(0.16, 1, 0.3, 1)` 0.2~0.25s.
- **클릭 가능한 카드 표시**: 모바일이라 hover만으로는 부족하다 → 오른쪽 `›` 화살표(`.ledger-hero__chevron`, `.list-row__arrow`)를 함께 붙인다.
- **hover 효과**: 흐리게(opacity) 하지 말고 흰 배경 + 그림자로 살짝 뜨게.

---

## 6. 다크 존 · 알약 (추가 예정 — 아직 코드에 없음)

Subscrr 레퍼런스에서 가져온 추가 규칙. **기존 색 값은 그대로** 두고 토큰·modifier만 얹는다. 적용할 때 아래 코드를 `tokens.css`/`components.css` 맨 아래에 추가한다.

- 다크 존(`.zone`)은 **점주 대시보드 히어로, 마감 특가 배너, 랜딩·온보딩에만.** 소비자 앱 본문은 라이트 그대로. 화면당 하나.
- 존 안의 강조는 주황 하나(`--zone-accent`). 네이비(`--c-primary`)는 다크 배경에서 ~1.4:1이라 글자·선으로 쓰지 않는다.
- 새 화면의 버튼·입력은 `--pill` modifier(알약), 섹션 카드는 `.card--section`(24px 모서리).

```css
/* tokens.css 끝에 추가 */
:root {
  --zone-bg:      var(--c-primary-strong);   /* 딥 네이비 */
  --zone-bg-alt:  var(--c-dark);
  --zone-ink:     var(--c-bg);               /* 크림, zone-bg 대비 ~14:1 */
  --zone-ink-sub: var(--c-idle);             /* ~10.7:1 */
  --zone-accent:  var(--c-accent);           /* ~5.8:1 */
  --zone-line:    rgba(246, 240, 227, 0.12); /* 크림 12% */
  --r-2xl:        24px;
  --shadow-glow:  0 6px 20px -10px rgba(15, 43, 140, 0.45);
}

/* components.css 끝에 추가 */
.btn--pill { border-radius: var(--r-pill); transition: filter .15s ease, transform .4s cubic-bezier(0.16, 1, 0.3, 1), box-shadow .35s cubic-bezier(0.16, 1, 0.3, 1); }
.btn--pill:hover { transform: translateY(-2px); box-shadow: var(--shadow-glow); }
.btn--pill:active { transform: translateY(0); }
.input--pill, .search--pill { border-radius: var(--r-pill); }
.card--section { border-radius: var(--r-2xl); border: 1px solid var(--c-line); padding: var(--s-6); }
.zone { background: var(--zone-bg); color: var(--zone-ink); padding: var(--s-8) var(--s-5); border-radius: var(--r-2xl); }
.zone--alt { background: var(--zone-bg-alt); }
.zone--flush { border-radius: 0; }
.zone__eyebrow { font-size: var(--fs-xs); font-weight: var(--fw-bold); color: var(--zone-ink-sub); }
.zone__title { margin-top: var(--s-2); font-size: 40px; line-height: 1.15; font-weight: var(--fw-black); color: var(--zone-ink); }
.zone__title em { font-style: normal; color: var(--zone-accent); }
.zone__desc { margin-top: var(--s-3); color: var(--zone-ink-sub); }
.zone :focus-visible { outline-color: var(--c-primary-mid); }
.zone .btn { background: var(--zone-accent); color: var(--c-text); }
@media (prefers-reduced-motion: reduce) { .btn--pill, .btn--pill:hover { transition: none; transform: none; } }
```

---

## 7. 슈퍼어드민 (`layout-admin.css`)

- 참고 디자인(admin-console.html)을 이식한 **독립 시스템**. 앱 토큰(`--c-*`, `--s-*`, `--r-*`)을 쓰지 않는다.
- 토큰: `--blue-600/500/100/50`, `--red-500/400/100`, `--green-600/100`, `--amber-600/100`, `--gray-900…50`, `--white`, `--page-max` 1360, `--radius-sm/md/pill`, `--shadow-pop/drawer`, `--font-sans`(Pretendard), `--overlay-scrim`, `--z-modal`.
- 컴포넌트: `.topbar/.util-btn/.avatar`, `.gnb`, `.page-title`, `.tabs/.tab`, `.btn(--primary --outline --outline-danger --outline-neutral --ghost --sm --block)`, `.link-action`, `.chip-btn(--danger --neutral)`, `.toolbar`, `.field/.field-group/.select/.search`, `.data-table`(+ `.store-table/.member-table/.report-table`), `.info-list/.info-row`, `.stepper`, `.badge(--success --danger --neutral --open --warning)`, `.stat-grid(--2 --3)/.stat-card(--link)`, `.admin-bar-chart`, `.admin-calendar`, `.drawer/.noti`, `.pagination`, `.reply-box/.reply-view`, `.detail-header`, `.form-narrow/.form-error/.form-success`.
- 상태 색: 승인됨 = blue, 영업중 = green, 노쇼 = amber, 취소·정지 = red, 수정·중립 = gray. 배지와 행 액션(`.chip-btn`)은 56×24로 같은 치수.
- 아이콘은 선(stroke 1.6~1.7) 스타일 9개.

---

## 8. 알려진 부채 · 정리 대상

코드를 건드릴 때 같이 고칠 수 있으면 고치고, 고치면 이 목록에서 지운다.

### 누락 / 오류
- [x] `home.html`이 `css/map.css`를 링크한다는 항목은 오독이었다 — 실제로는 `<!--/* TODO(강노은): ... static/css/map.css로 분리 */-->` **주석**이고 진짜 `<link>`는 `home.css` 하나뿐이라 404는 나지 않는다 (2026-09-24 확인, 항목 정정).
- [x] `ledger.css` `.ledger-share-card` 주석 — "Noto Sans KR을 로드하지 않는다"를 "로드는 하지만 html2canvas 캡처 안정성 때문에 Malgun Gothic으로 고정한다"로 정정 완료 (2026-09-24).
- [x] `components.css` `.review-upload` 주석의 "--c-primary가 이미 초록 계열" → "당시엔 그린, 2026-09-17 리스킨 이후 지금은 네이비"로 정정 완료 (2026-09-24). (`layout-admin.css`/`store.css`/정산서 헤더는 슈퍼어드민·점주 도메인이라 이번 범위 밖 — 미정리 상태 그대로.)

### 대비 미달 (WCAG AA)
- [x] `reservation.css` `.scan-status.status-warn`: `--c-accent` on `-weak` ≈2.7:1 → `--c-accent-strong`로 교체 완료 (2026-09-24).
- [x] `reservation.css` `.banner--danger`: `--c-danger` on `-weak` ≈3.4:1 → `--c-danger-strong`로 교체 완료 (2026-09-24).
- [x] `components.css` `.store-card__left.is-urgent`(11px, 흰 바탕 ≈3.8:1), `ledger.css` `.ledger-history-card__rate`(10px, ≈3.8:1), `.ledger-hero__delta.is-down`(13px bold — large-text 기준 미달, 크림 바탕 ≈3.3:1) 전부 `--c-danger-strong`로 교체 완료 (2026-09-24).
- [x] `marketing.css` `.mkt-footer__meta`/`.mkt-mock__badge` — 2026-09-24 정리 완료 (아래 표 및 커밋 참고).

### 토큰 대신 하드코딩된 값
| 파일 | 상태 (2026-09-24) |
|---|---|
| `components.css` | `.banner--info` 테두리 #A5F3FC → 새 토큰 `--c-info-border`로 교체. `.modal-overlay` 배경 rgba(0,0,0,.5) → `--overlay-scrim`(.45, 시각차 미미)로 통일, box-shadow rgba(0,0,0,.25) → 새 토큰 `--shadow-modal`(다른 그림자들과 같은 슬레이트 톤)로 교체. z-index:100은 옆 주석("하단 액션바(25)/탭바(30)보다 위")과 [3. z-index 표](#3-토큰-tokenscss)에 이미 문서화된 별도 층(100 vs `--z-modal` 200)이라 손대지 않음(합치면 스태킹이 바뀜). `#fff`×3(naver/line 버튼, store-card__thumb 배지)은 [소셜 로그인 규정](#색--소셜-로그인-브랜드-규정)이 "line 흰 글자"를 명시하고 있고 thumb는 배경색이 매장마다 동적이라 순정 흰색이 맞음 — 확인 결과 실제 부채 아님. |
| `layout.css` | `.topbar--dark .topbar__mode-btn`의 `#FFFFFF`(대문자) 2곳을 파일 내 다른 곳과 같은 `#fff` 표기로 통일(값 자체는 이미 맞았음 — 대소문자만 불일치). `--c-surface`로 바꾸지 않은 이유: 그건 "표면/배경" 의미 토큰이라 다크 상단바 위 텍스트 색으로 쓰는 건 의미가 안 맞는다고 판단(marketing.css `.mkt-owner`와 같은 논리). FAB 활성 #193DB3 → 새 토큰 `--c-primary-active`로 교체. |
| `ledger.css` | 정확히 같은 값(#FEF3C7/#FDE68A/#92400E→`--c-badge-rep*`, #F97316→`--c-accent`, #FEF2F2/#FECACA→`--c-danger-weak/-border`, #F59E0B×9→새 토큰 `--c-badge-rep-accent`) 전부 교체 완료. 옛 네이비 그림자 rgba(1,42,140,*) 2곳도 현재 `--c-primary` rgb(15,43,140) 기준으로 보정. 남겨둔 것: 환경 배너 그린 3색·탐험 넛지 보라 3색(각각 "왜 그린/보라를 브랜드색 대신 쓰는지" 설명 주석과 실측 대비율까지 이미 있는 **의도된 예외** — 진짜 부채 아님), 호박 #B45309/#FBBF24/#FFFBEB/#78350F(자리 1~2곳뿐인 사소한 장식용 변형이라 토큰 승격 보류), 공유 카드 카테고리 그라데이션 10색(`--ledger-share-from/-to` 커스텀 프로퍼티로 이미 카테고리별로 잘 스코프돼 있어서 추가 추상화가 오히려 안 좋다고 판단). |
| `marketing.css` | ✅ 2026-09-24 정리 완료 (아래 "이전 정리 기록" 참고). |
| `reservation.css` | 테두리 #FDBA74 → 새 토큰 `--c-accent-border`로 교체. |
| `store.css` | 점주 대시보드 전용 도메인이라 이번 "유저 화면" 정리 범위 밖 — 미정리 상태 그대로 남음. |

- 2026-09-24에 새로 추가된 토큰: `--c-info-border`, `--c-accent-border`, `--c-primary-active`, `--c-badge-rep-accent`, `--shadow-modal` (전부 `tokens.css`에 추가, 위 표의 근거).
- `.dash-hero` 그라데이션(store.css) → [6. 다크 존](#6-다크-존--알약-추가-예정--아직-코드에-없음)의 `--zone-bg` 단색으로 대체 후보 (미정리, 점주 도메인).

### 중복 (승격 후보)
- [ ] `.btn:disabled`가 `reservation.css`에만 있다 → 모든 화면의 비활성 버튼에 필요 → `components.css`로.
- [ ] `.badge--muted`, `.banner--danger`(reservation) → 다른 화면에서도 쓰면 `components.css`로.
- [ ] 탭 3종: `.tabs/.tab`(공통) · `.tab-bar/.tab-item`(reservation) · `.seg-tabs`(ledger) · `.stat-toggle-tabs`(store) → 밑줄 탭 1종 + 알약 세그먼트 1종으로 정리.
- [ ] 빈 상태 3종: `.support-empty`(공통) · `.empty-state`(reservation) · `.ledger-empty` → `.support-empty`로 통일.
- [ ] 토글 스위치: `.settings-toggle`(settings)과 `alertView/settings.html` 인라인 스타일이 복제 → 세 번째가 생기면 공통화.
- [ ] 목록 행: `.list-row`(공통) vs `.list-menu-item`(mypage) — 용도 구분을 이 문서에 적거나 하나로.
