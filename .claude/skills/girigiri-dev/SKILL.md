---
name: girigiri-dev
description: girigiri 프로젝트에서 엔티티/컨트롤러/리포지토리/서비스를 추가하거나 수정할 때 따라야 할 패키지 구조, 네이밍, 커밋 컨벤션을 안내. 새 기능 구현, 코드 추가 작업 시 사용.
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

## 공통 레이아웃 사용법

- 모든 화면은 `<div class="app-container">`로 감싼다. ADMIN 화면은 `class="app-container mode-admin"`로 액센트 색상을 바꾼다 (`static/css/common.css`의 `.mode-admin` 참고).
- 공통 헤더는 `fragments/layout.html`의 `header(title, badge)` 프래그먼트를 쓴다. **Thymeleaf는 이름 지정 파라미터 호출 시 시그니처에 선언된 파라미터를 전부 명시해야 하므로, badge를 안 쓰는 화면도 반드시 `badge=''`를 넘긴다** — 하나라도 빠뜨리면 `Cannot resolve fragment` 예외로 그 화면이 500 에러가 난다. 새 프래그먼트 파라미터를 추가할 때도 동일하게 기존 호출부를 전부 갱신해야 한다.
- 하단은 일반 화면이면 `nav(active)` 프래그먼트(홈/마이페이지 네비), 상세/체크아웃형 화면이면 `.app-actionbar`(버튼 1개짜리 고정바) 중 하나를 쓴다.
- 버튼/카드/배지/가격표시 등 재사용 컴포넌트는 `common.css`에 이미 정의되어 있다 (`.btn`, `.btn-primary`, `.btn-outline`, `.card`, `.badge`, `.stat-grid`, `.price-original`/`.price-discounted` 등) — 새로 만들기 전에 먼저 확인한다.

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

## 담당자 - 폴더 매핑 (CLAUDE.md 역할분담 요약)

| 담당자 | 주로 건드리는 영역 |
|---|---|
| 송보미 (조장) | security/, 로그인/role/viewMode 세션 로직, 예약 서비스 코어 |
| 송채현 | domain/entity(ERD), 재고 등록, 판매/폐기 리포트(Excel/PDF) |
| 강노은 | 예약/노쇼 처리, 결제(PortOne)/QR/영수증 PDF, 게시판 |
| 김태훈 | productView/, mypageView/, 리뷰, 환경 기여도 시각화 |
| 문창호 | mapView/, 카카오맵, 실시간 알림(WebSocket) |
