---
name: girigiri-dev
description: girigiri 프로젝트에서 엔티티/컨트롤러/리포지토리/서비스를 추가하거나 수정할 때 따라야 할 레이어 규칙, 패키지 구조, 네이밍, CSS 경계, 버튼/스타일 컨벤션, 커밋 규칙을 안내. 새 기능 구현, 코드 추가·수정 작업 시 항상 사용.
---

# girigiri 개발 컨벤션

전체 기획/엔티티/역할분담은 프로젝트 루트의 `CLAUDE.md`를 우선 참고한다. 이 스킬은 코드를 실제로 작성할 때 지켜야 할 컨벤션만 다룬다.

## 패키지 구조

```
src/main/java/net/dsa/girigiri/
├── controller/     화면(Thymeleaf)용 @Controller. REST가 필요하면 controller/api/ 하위에 배치
├── domain/
│   ├── dto/        화면·API 입출력용. Entity를 그대로 노출하지 않는다
│   └── entity/      @Entity, DB 테이블과 1:1
├── repository/     엔티티당 JpaRepository 1개
├── service/        비즈니스 로직. 구현체 분리가 필요해지면 service/impl/ 추가
├── security/       Spring Security 설정
├── exception/      커스텀 예외 + GlobalExceptionHandler
└── util/           공용 유틸리티
```

새 기능을 추가할 때는 이 구조를 그대로 따른다 — 임의로 새 최상위 패키지를 만들지 않는다.

## 레이어 규칙 (2026-09-03 추가) ★ 신규 코드 필수

패키지 구조만으로는 부족했다. **어느 레이어가 무엇을 호출할 수 있는지**를 명시한다.

```
Controller  →  Service  →  Repository
   (금지: Controller → Repository 직접 호출)
```

- **Controller는 Repository를 주입받지 않는다.** `private final XxxRepository`가 컨트롤러에 있으면 리뷰 반려.
- Controller가 하는 일은 3가지뿐: 요청 파라미터 받기 / Service 호출 / 뷰 이름·리다이렉트 반환.
- 다음은 전부 Service에 있어야 한다: 엔티티 조회·저장, `setStatus()` 같은 상태 변경, 입력값 검증, 날짜 파싱, 엔티티 빌드.
- 상태를 바꾸거나 2개 이상 테이블을 건드리는 Service 메서드에는 `@Transactional`을 **메서드 레벨**로 붙인다 (클래스 레벨 금지 — 범위를 눈으로 확인할 수 있어야 한다).

```java
// ❌ 반려
@PostMapping("/members/{id}/suspend")
public String suspend(@PathVariable Long id) {
    UserEntity user = userRepository.findById(id).orElseThrow(...);
    user.setStatus(UserEntity.STATUS_SUSPENDED);
    userRepository.save(user);
    return "redirect:/superadmin/members";
}

// ✅ 통과
@PostMapping("/members/{id}/suspend")
public String suspend(@PathVariable Long id) {
    memberAdminService.suspend(id);
    return "redirect:/superadmin/members";
}
```

**컨트롤러 크기 상한**: 엔드포인트 15개 또는 300줄을 넘으면 도메인 단위로 클래스를 분리한다
(예: `SuperAdminMemberController`, `SuperAdminNoticeController`).

> **적용 범위.** 이 규칙은 오늘 이후 새로 쓰는 코드에 적용한다.
> 기존 코드는 `refactor/service-layer` 브랜치에서 조장이 순차적으로 정리 중이며,
> 팀원이 개별적으로 소급 수정할 필요는 없다.
>
> | 대상 | 상태 |
> |---|---|
> | ProductController, StoreDetailController, MypageController, AuthController, HomeController | 정리 진행 중 (조장) |
> | StoreController (문창호), ReservationController (송채현) | 담당자와 일정 협의 후 진행 |
> | SuperAdminController | 9/18 이후 |
>
> 정리 중인 파일은 조장에게 확인 후 수정한다 — 충돌 방지 목적이다.

## 엔티티 작성 패턴

기존 8개 엔티티(`domain/entity/*.java`)와 동일한 패턴을 따른다:

```java
@Entity
@Table(name = "테이블명")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class XxxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ... 필드

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- FK는 아직 `@ManyToOne` 등 연관관계 매핑을 하지 않고 plain `Long` id 컬럼으로 둔다 (송채현의 ERD 확정 전까지). ERD가 확정되면 이 스킬도 함께 업데이트한다.
- MySQL 예약어(`user`, `like` 등)와 겹치는 이름은 테이블명을 복수형/변형으로 회피한다 (`users`, `likes`).
- 타임스탬프 필드명은 CLAUDE.md 엔티티 스펙의 필드명을 그대로 따른다 (예: Product는 `registeredAt`, Reservation은 `reservedAt`/`pickedAt`, Receipt/Report는 `generatedAt` — 일괄 `createdAt`으로 통일하지 않는다).

## 템플릿 폴더 네이밍

`templates/` 하위는 컨트롤러 도메인과 1:1 대응하는 `xxxView/` 접미사 폴더를 쓴다 (예: `mapView/`, `productView/`, `storeView/`, `mypageView/`, `authView/`, `errorView/`). 새 화면 도메인을 추가할 때 이 규칙을 따른다.

## 공통 레이아웃 사용법 (2026-08-20 전면 개편)

기존 `fragments/layout.html` + `common.css` 자체 제작 시스템을 폐기하고, 팀 공용 스타터킷(BEM + 디자인 토큰) 기반으로 전면 교체했다. 새 화면은 아래 컨벤션을 따른다.

- **풀페이지 래핑 방식**이다 (예전처럼 페이지가 직접 header/nav를 조립하는 부분 include 방식이 아니다). 페이지는 자기 `<html><head>`를 만들지 않고, `<html>` 태그 자체를 `common/layout :: layout(...)`으로 교체한다:
  ```html
  <html th:replace="~{common/layout :: layout('제목', ~{:: #content}, 'home', false, '', null)}">
  <body>
  <div id="content" th:fragment="content" class="page"> ...본문... </div>
  </body>
  </html>
  ```
- `layout(title, content, active, dark, badge, actionbar)` — **6개 인자 전부 명시**한다 (위치 인자라 생략해도 에러는 안 나지만, 예전 `badge=''` 누락으로 500 에러 났던 전례가 있어 팀 컨벤션으로 항상 다 채운다).
  - `title`: 상단바 제목 & `<title>`
  - `content`: 본문 프래그먼트 (`~{:: #content}`)
  - `active`: 하단 탭 활성값 (`'home' | 'like' | 'ledger' | 'alert' | 'my' | ''`)
  - `dark`: 상단바 다크 여부 (사장님/운영자 모드는 `true`)
  - `badge`: 상단바 제목 옆 텍스트 뱃지 (예: `'관리자 모드'`), 안 쓰면 `''`
  - `actionbar`: 상세/체크아웃형 화면의 하단 고정 버튼바 프래그먼트 (`~{:: #actionbar}`), 안 쓰면 `null` — 페이지에 `<div id="actionbar" th:fragment="actionbar" class="bottom-cta">...</div>`로 정의 (`productView/detail.html` 참고)
- 하단 탭바는 `common/footer.html :: bottomNav(active)`가 `layout`을 통해 자동으로 붙는다 (`active`가 빈 값이 아닐 때만) — 직접 호출할 일은 거의 없다.
- 재사용 컴포넌트는 `common/components.html`에 프래그먼트로 정의되어 있다 (`btn(label,variant,href)`, `chip(label,active)`, `badge(text,variant)`, `price(orig,sale)`, `storeCard(store)`, `savingBanner(title,desc)`, `statCard(label,value,delta,deltaColor)`) — 새로 만들기 전에 먼저 확인한다.
- **클래스 네이밍은 BEM**(`블록__요소--변형`)이다: `.btn--outline`, `.badge--discount`, `.store-card__name`, `.bottomnav__item.is-active` 등. 예전의 플랫 네이밍(`.btn-primary`, `.badge-discount`)은 전부 폐기됐다.
- **색·간격·모서리·타이포는 `tokens.css`의 CSS 변수만 쓴다** (`--c-primary`, `--s-4`, `--r-md`, `--fs-lg` 등) — `#15803D`, `16px` 같은 하드코딩 금지. 톤을 바꾸려면 `tokens.css` 한 곳만 고치면 전체 반영된다.
- CSS는 4개 파일로 분리되어 있다: `tokens.css`(토큰) → `base.css`(리셋) → `layout.css`(앱 껍데기/상단바/하단바) → `components.css`(버튼·카드·칩·뱃지 등). 페이지 전용 스타일이 필요하면 이 4개를 건드리지 말고 `static/css/<도메인>.css`(예: `store.css`, `ledger.css`)를 새로 만든다.
- 컴포넌트 생김새가 헷갈리면 `/styleguide`(`StyleguideController`, 로그인 불필요)에서 확인하고 복붙한다.
- 공통 파일(`templates/common/*.html`, `static/css/{tokens,base,layout,components}.css`) 오너는 송보미(조장)다 — 새 컴포넌트/토큰 추가는 오너에게 요청하거나 PR 리뷰를 거친다.

## CSS 경계 규칙 (2026-09-03 추가) ★ 슈퍼어드민 도입으로 신규

슈퍼어드민은 유저·매장과 **완전히 다른 디자인 시스템**을 쓴다. 두 시스템은 토큰 이름부터 다르므로 절대 섞지 않는다.

| | 유저 · 매장 | 슈퍼어드민 |
|---|---|---|
| 대상 폴더 | `templates/` (superAdminView 제외) | `templates/superAdminView/**` |
| 레이아웃 | `common/layout :: layout(...)` | `common/layout-admin :: layoutAdmin(...)` |
| CSS | `tokens.css` + `base.css` + `layout.css` + `components.css` | `layout-admin.css` |
| 토큰 | `--c-primary`, `--s-4`, `--r-md`, `--fs-lg` | `--gray-500`, `--blue-600`, `--radius-md` |
| 컴포넌트 | `.btn--outline`, `.store-card`, `.chip` | `.info-row`, `.field-group`, `.link-action`, `.badge` |
| 간격 | `.stack` / 토큰 기반 | `.mt-24`, `.mb-16` |

- 유저·매장 화면에서 `field-group`, `info-row` 등 어드민 클래스를 쓰지 않는다. 반대도 마찬가지다.
- `layout-admin.css`는 자기 변수를 자기 안에서 정의한다. `tokens.css` 변수를 여기서 참조하지 않는다.
- 어드민 전용 버튼이 필요하면 `components.css`를 고치지 말고 `layout-admin.css`에 추가한다.

## 버튼 규칙 (2026-09-03 추가)

### 가로폭 — 한 가지 방법만 쓴다

인라인 `style="flex:1"`(20건), `style="width:100%"`(10건)이 혼재해 버튼 크기가 어긋나는 문제가 있었다.

- 버튼 하나가 단독으로 가로를 채울 때 → `btn--block`
- `u-row` 안에 버튼 2개 이상을 나란히 둘 때 → 각 버튼에 `u-grow`, `btn--block`은 쓰지 않는다
- 인라인 `style`로 폭을 지정하지 않는다

### 세로 간격

`style="margin-top: var(--s-3)"`을 태그마다 적지 않는다. 부모에 `.stack`을 붙이면 자식 간격이 자동으로 잡힌다.

```html
<div class="stack">
  <input class="field">
  <input class="field">
  <button class="btn btn--block">저장</button>
</div>
```

### 라벨 문구

`~로 가기`, `~에서 확인하기` 같은 서술형을 쓰지 않는다. 아래 표에 없는 문구가 필요하면 조장에게 요청해 표에 추가한다.

| 상황 | 문구 | variant |
|---|---|---|
| 저장 / 제출 | `저장` | 기본 |
| 신규 등록 | `등록` | 기본 |
| 목록으로 복귀 | `목록` | `outline` |
| 직전 화면 취소 | `취소` | `outline` |
| 삭제 | `삭제` | `danger` |
| 내 정보 | `마이페이지` | 상단 네비에서만 |

### 프래그먼트 우선

버튼은 `common/components.html`의 `btn` 프래그먼트를 쓴다. 클래스를 손으로 적지 않는다.

```html
<div th:replace="~{common/components :: btn('저장', '', @{/store/settings})}"></div>
```

## Dual-mode 세션 규칙 (중요)

세션 구조:
```javascript
{
  userId: "...",
  role: "ADMIN",        // 실제 권한, 불변
  viewMode: "ADMIN_MODE", // 현재 보기 모드, 변경 가능
  storeId: "..."         // ADMIN인 경우만
}
```

- **권한 체크(서버 로직, `@PreAuthorize`, 서비스 레이어 검증 등)는 항상 `role` 기준.**
- **화면 분기(어떤 뷰/메뉴를 보여줄지)만 `viewMode` 기준.**
- `viewMode`를 권한 체크에 사용하는 코드는 리뷰에서 반려 대상이다.

## .env / 시크릿 규칙

- 실제 API 키·시크릿·비밀번호는 어떤 파일에도 커밋하지 않는다. `.env`는 `.gitignore`에 등록되어 있다.
- `.env.example` 파일은 두지 않기로 했다 (팀 결정). 필요한 `.env` 키 목록과 예시 값은 `README.md`의 "로컬 실행" 섹션에 문서화되어 있다.
- 새 외부 연동(OAuth2, PortOne, 카카오맵 등)을 추가할 때는 `README.md`의 로컬 실행 섹션에 새 키 이름을 추가해 문서화하고, 실제 값은 각자 로컬 `.env`에만 채운다.

## 커밋 메시지 컨벤션

Conventional Commits 스타일을 따른다: `feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:` 등 접두사 + 간결한 요약. 스캐폴딩 커밋 시퀀스가 예시다:

```
chore: add .gitignore and .gitattributes
docs: add CLAUDE.md project spec
chore: bootstrap Spring Boot 3.5.16 / Java 21 gradle project
feat: add core JPA entity skeletons and repositories
feat: add security stub with form-login placeholder
```

`main` 브랜치에는 직접 커밋하지 않는다 — 기능 브랜치(`dev` 또는 `feature/*`)에서 작업 후 PR로 병합한다.

## 일정 고정선 (2026-09-03 확정)

| 날짜 | 내용 |
|---|---|
| **9/18** | 기능 동결. 이후 신규 기능 추가 금지, 버그 수정·정리만 |
| **9/30** | 코드 프리즈 (이상적 마감) |
| 10/6 | 1차 제출 (문서 6종) |
| 10/7 | 발표 |
| 10/16 | 2차 제출 (시연 영상) |

9/18 이후 새 기능 브랜치를 만들지 않는다. 9/18~9/30은 QA, 버그 수정, 인라인 스타일 정리, 레이어 규칙 소급 정리에 쓴다.

## 커밋 전 셀프 체크

새 코드를 올리기 전에 아래를 확인한다. AI에게 코드를 요청할 때도 이 목록을 함께 전달한다.

- [ ] Controller에 `Repository`를 주입하지 않았는가
- [ ] 상태 변경 Service 메서드에 `@Transactional`을 붙였는가
- [ ] 컨트롤러가 300줄 / 엔드포인트 15개를 넘지 않는가
- [ ] `style="..."`에 `16px`, `#15803D` 같은 값을 직접 쓰지 않았는가 (토큰만 사용)
- [ ] 버튼을 `btn` 프래그먼트로 만들었는가, 라벨이 표에 있는 문구인가
- [ ] `superAdminView/`와 나머지의 CSS 클래스를 섞지 않았는가
- [ ] 커밋 메시지가 `feat:` / `fix:` / `refactor:` 형식인가

## 담당자 - 폴더 매핑 (CLAUDE.md 역할분담 요약, 2026-08-21 갱신)

| 담당자 | 주로 건드리는 영역 |
|---|---|
| 문창호 | security/, authView/(로그인·소셜로그인), role/viewMode 세션 로직, POS json 연동, 할인율 자동계산, 픽업 예약 관리(QR)·노쇼 방지, 판매·폐기 리포트(Excel/PDF), 마이페이지/절약 가계부 |
| 송보미 (조장) | 공통 레이아웃(templates/common/*.html, static/css/{tokens,base,layout,components}.css), domain/entity, repository 공통 CRUD, 슈퍼어드민 |
| 김태훈 | storeView/ (재고 등록, 판매/등록 현황 대시보드, 통계 그래프), 공지사항 게시판 관리 |
| 강노은 | HomeController/home.html 등 지도·메인 화면, mapView/, 카카오맵 연동, productView/(상품 상세), 카테고리 필터, 찜하기, 리뷰, 실시간 알림(WebSocket/SSE) |
| 송채현 | 예약 서비스 코어, 결제(PortOne), QR 발급, 영수증 PDF, 고객 지원 챗봇 |

> 최신 담당/일정은 WBS 기준으로 CLAUDE.md 쪽이 우선한다 — 이 표는 요약이라 세부 일정 변경 시q CLAUDE.md를 먼저 확인할 것.
