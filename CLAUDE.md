# 기리기리(끼리끼리) — 동네 가게 마감 임박 음식 긴급 구제 서비스

[日本語](./CLAUDE.ja.md)

## 📋 프로젝트 개요
- **프로젝트명**: 기리기리(끼리끼리)
- **조 정보**: 14기 A반 2조 (조이름: 다이스키)
- **조장**: 송보미 | **조원**: 강노은, 김태훈, 문창호, 송채현 (5명)
- **목표**: 동네 소상공인의 폐기 손실을 줄이고, 소비자에게는 합리적인 가격에 신선식품/베이커리 등을 구매할 기회 제공 → 음식물 쓰레기 문제와 소상공인 매출 문제 동시 해결

## 🎯 핵심 컨셉
- **사장님 입장**: 마감 시간이 다가올수록 팔리지 않은 음식은 전량 폐기 손실. 실시간으로 남은 음식과 할인 정보를 고객에게 알릴 방법이 없음.
- **소비자 입장**: 저렴하게 음식을 사고 싶어도 어느 가게가 언제 마감세일을 하는지 알기 어려움.
- **솔루션**: 실시간 지도 기반 마감세일 정보 공유 + 선결제 예약 + 문서 자동 생성으로 투명성 확보

## 🏗️ 기술 스택
| 구분 | 기술 |
|---|---|
| Frontend | Thymeleaf + JavaScript (모바일 고정폭 ~420px) |
| Backend | Java + Spring Boot |
| Database | MySQL |
| 인증 | Spring Security + OAuth2 (카카오/네이버) |
| 지도/위치 | 카카오맵 API |
| 실시간 알림 | WebSocket / STOMP |
| 결제 | PortOne (아임포트) 테스트 결제 |
| 문서 생성 | openhtmltopdf (PDF) + Apache POI (Excel) |
| QR코드 | QR 라이브러리 |

## 🔑 주요 기능

### 3.1 회원 및 인증 [신규]
- 소셜 로그인(카카오/네이버 OAuth2) + 일반 회원가입
- Role 기반 라우팅: USER(일반) / ADMIN(사장님)
- 어드민 dual mode: 실제 role은 고정, viewMode 세션으로 "관리자 모드 / 사용자 모드" 전환
- 권한 체크는 항상 실제 role 기준 (viewMode 아님)

### 3.2 사장님용 재고 등록
- 마감 전 남은 상품 사진 + 간단 정보 등록 (품목, 원가, 수량)
- 오늘 등록/판매 현황 한눈에 확인
- POS기에서 JSON 데이터 수신 (자동화 가정)

### 3.3 소비자용 실시간 탐색
- 내 주변 마감세일 중인 가게 지도로 확인
- 카테고리 필터링 (베이커리/반찬/도시락/카페 등)
- 관심 가게 찜하기 + 실시간 알림 (WebSocket/STOMP)

### 3.4 예약 및 픽업
- 원하는 상품 선결제 후 픽업 시간대 예약
- 픽업 QR코드 (또는 번호) 발급으로 현장 확인
- 노쇼 방지 메커니즘

### 3.5 문서 자동 생성 [신규 · 차별 포인트]
- 결제 영수증 PDF (유저용): 마이페이지에서 재다운로드 가능
- 판매·폐기 리포트 (사장님용): Excel + PDF
  - 품목별 등록/판매/폐기 수량
  - 매출·할인액
  - 구제율 (판매÷등록)
  - CO₂ 절감 환산
  - 일간 기본, 누적 시 주간 확장
- (옵션) 환경 기여도 인증서 PDF: SNS 공유용

### 3.6 신뢰 및 기록
- 이용 후 가게 리뷰 & 별점
- 누적 절약 금액, 구제한 음식 개수, CO₂ 절감량 등 개인 대시보드

## 📱 주요 화면 UI

모든 화면은 모바일 고정폭(약 420px, 중앙 정렬) 기준 설계

| 화면 | 설명 |
|---|---|
| 로그인/모드 선택 [신규] | 소셜 로그인, 어드민은 "관리자 모드 / 사용자 모드" 선택 |
| 홈 화면 | 지도 기반 주변 마감세일 가게 카드 나열 (할인율, 남은 시간 강조) |
| 상품 상세 | 원가/할인가 비교, 남은 수량, 픽업 시간, 예약 버튼 |
| 사장님 대시보드 | 등록 현황, 판매/폐기 통계 그래프, 리포트 다운로드(Excel/PDF) |
| 마이페이지 | 예약 내역, 픽업 QR, 영수증 PDF 재다운, 누적 절약·환경 기여도 |

## 👥 역할 분담 (페이지 기준)
| 담당자 | 축 | 주요 기능 |
|---|---|---|
| 문창호 | 인증 + 매장 운영 | 회원가입/로그인, 소셜로그인(OAuth2 카카오/네이버), role 라우팅·모드 분기, POS json 연동, 할인율 자동계산, 픽업 예약 관리(QR 스캔/코드 확인)·노쇼 방지, 판매·폐기 리포트(Excel/PDF) |
| 송보미 (조장) | 공통 UI / DB | 공통 레이아웃(모바일 고정폭 컨테이너, 헤더/네비), DB 스키마 구현·엔티티·공통 CRUD |
| 김태훈 | 사장님용 (재고·대시보드) | 상품(재고) 등록 화면, 오늘 판매/등록 현황 대시보드, 판매/폐기 통계 그래프, 공지사항 게시판 관리 |
| 강노은 | 일반 유저용 (지도·상품) | 카카오맵 API 연동/지도 시각화, 메인 화면(주변 마감세일 탐색 카드 리스트), 카테고리 필터, 상품 상세 화면, 관심 가게 찜하기 |
| 송채현 | 예약·결제·문서 | 예약 로직(백엔드), 선결제(PortOne 테스트 연동), 픽업 코드(QR) 발급, 결제 영수증 PDF 생성 |
| 담당 미정 | 실시간 알림 | 실시간 마감세일 알림(WebSocket/SSE) — 담당자 배정 필요 |

**⚠️ 크리티컬 패스:**
1. 인증(회원가입/로그인·소셜로그인·role 분기) — 문창호
2. 지도 연동 — 강노은 / 실시간 알림 — 담당 미정
→ 이 셋이 병목이므로 우선 착수, 특히 실시간 알림은 담당자부터 확정 필요

## 🎨 설계 원칙
- **모바일 우선**: 고정폭 420px 기준 (PWA 미적용 — 반응형이 아닌, 모바일 사이즈로 고정된 일반 웹 페이지)
- **Dual Mode 분기**:
  - 로그인 시 role 확인 → USER 또는 ADMIN 화면으로 진입
  - ADMIN은 헤더 토글로 "관리자 모드 / 사용자 모드" 전환 (viewMode 세션 저장)
  - 권한 체크는 항상 실제 role 기준
- **실시간성**: WebSocket 사용 (찜한 가게 마감세일 시작 시)
- **트랜잭션 안전성**: 결제 검증 (프론트 → 백엔드 재검증)
- **문서 자동화**: PDF/Excel 생성으로 기록 & 정산 투명성

## 📊 주요 엔티티 (예상)

```
User (일반 사용자)
├─ loginId, password (또는 OAuth2 ID)
├─ role (USER / ADMIN)
├─ nickname, location(위도/경도)
└─ createdAt, updatedAt

Store (매장/사장님 계정)
├─ loginId, password
├─ storeName, category, address
├─ location(위도/경도), operatingHours
├─ role = ADMIN
└─ createdAt, updatedAt

Product (등록한 음식 상품)
├─ storeId (FK)
├─ name, originalPrice, discountedPrice
├─ quantity, remainingQuantity
├─ imageUrl, description
├─ status (active / sold / expired)
└─ registeredAt

Reservation (예약)
├─ userId, productId, storeId
├─ reservedQuantity, totalPrice
├─ pickupTime, pickupCode (QR)
├─ status (pending / confirmed / picked / cancelled / noshowed)
└─ reservedAt, pickedAt

Review (리뷰)
├─ userId, storeId
├─ rating, content
└─ createdAt

Like (찜하기)
├─ userId, storeId
├─ createdAt

Receipt (영수증 PDF 기록)
├─ reservationId
├─ pdfUrl, generatedAt

Report (매장 리포트)
├─ storeId, reportDate
├─ registeredCount, soldCount, expiredCount
├─ totalSales, totalDiscount, savedCO2
├─ excelUrl, pdfUrl
└─ generatedAt
```

## ⚙️ 개발 체크리스트 (일정)

### 2.0 공통 기반 (인증 · 레이아웃)
- [ ] 회원가입 / 로그인 — 문창호 (08/25 ~ 08/27)
- [ ] 소셜 로그인 (OAuth2 카카오/네이버) — 문창호 (08/27 ~ 08/30)
- [ ] role 라우팅 + 어드민/유저 모드 분기 화면 — 문창호 (08/29 ~ 08/31)
- [ ] 공통 레이아웃 (모바일 고정폭 컨테이너, 헤더/네비) — 송보미 (08/25 ~ 08/28)
- [ ] DB 스키마 구현 / 엔티티 / 공통 CRUD — 송보미 (08/25 ~ 08/31)

### 3.0 매장 주인용 (Admin) 개발
- [ ] 상품(재고) 등록 화면 (사진/품목/원가/수량) — 김태훈 (09/01 ~ 09/05)
- [ ] POS json 자동 수신 연동 (가정) — 문창호 (09/04 ~ 09/08)
- [ ] 오늘 판매/등록 현황 대시보드 — 김태훈 (09/08 ~ 09/12)
- [ ] 판매/폐기 절감 통계 그래프 — 김태훈 (09/11 ~ 09/14)
- [ ] 일/주간 판매·폐기 리포트 생성 (Excel + PDF 다운로드) — 문창호 (일정 미정)
- [ ] 할인율 자동 계산 로직 — 문창호 (09/01 ~ 09/04)
- [ ] 픽업 예약 관리 (QR 스캔 / 코드 확인) — 문창호 (09/05 ~ 09/10)
- [ ] 노쇼 방지 — 문창호 (09/10 ~ 09/12)
- [ ] 공지사항 게시판 관리 (등록/수정/삭제) — 김태훈 (09/12 ~ 09/14)

### 4.0 일반 유저용 (User) 개발 ※ 3.0과 병렬
- [ ] 카카오맵 API 연동 / 지도 시각화 — 강노은 (09/01 ~ 09/06)
- [ ] 메인 화면 - 주변 마감세일 탐색 (카드 리스트) — 강노은 (09/05 ~ 09/09)
- [ ] 카테고리 필터 (베이커리/반찬/도시락/카페) — 강노은 (09/09 ~ 09/11)
- [ ] 상품 상세 화면 (원가/할인가/수량/픽업시간) — 강노은 (09/01 ~ 09/06)
- [ ] 관심 가게 찜하기 — 강노은 (09/11 ~ 09/13)
- [ ] 실시간 마감세일 알림 (WebSocket / SSE) — 담당 미정 (09/12 ~ 09/16)

### 5.0 예약 · 결제 · 부가 기능
- [ ] 예약 로직 (백엔드) — 송채현 (09/15 ~ 09/18)
- [ ] 선결제 (PortOne 테스트 연동) — 송채현 (09/15 ~ 09/19)
- [ ] 픽업 코드(QR) 발급 — 송채현 (09/18 ~ 09/21)
- [ ] 결제 영수증 PDF 생성 (QR 포함, 결제 검증 후 생성) — 송채현 (일정 미정)

## 🔐 인증 & 권한 설계 [신규]

```javascript
// 세션에 저장
{
  userId: "...",
  role: "ADMIN", // 실제 권한 (불변)
  viewMode: "ADMIN_MODE", // 현재 보기 모드 (변경 가능)
  storeId: "..." // ADMIN인 경우만 필수
}

// 권한 체크는 항상 role 기준
if (session.role === "ADMIN") {
  // 사장님 기능 허용
}

// 화면 분기는 viewMode 기준
if (session.viewMode === "ADMIN_MODE") {
  // 사장님 대시보드 표시
} else {
  // 일반 사용자 홈 표시
}
```

## 🌍 차별점
| 항목 | 본 서비스 |
|---|---|
| 모바일 밀착형 UI | ✅ 고정폭 420px 최적화 |
| 환경 기여도 시각화 | ✅ 누적 절약·CO₂ 절감 대시보드 |
| 픽업 QR 시스템 | ✅ 노쇼 방지 & 현장 확인 |
| 문서 자동 생성 | ✅ 영수증 PDF / 매장 리포트(Excel/PDF) / 환경 인증서 |
| 실시간 알림 | ✅ WebSocket 기반 즉각 공지 |

## 📚 참고 서비스
- Too Good To Go (유럽): 폐기 임박 음식 판매 플랫폼 (핵심 구조 참고)
- 라스트오더 (국내): 유사 서비스 → 차별점 명확화 필요

## 🚀 로컬 개발 환경 설정 (예정)

```bash
# 1. 프로젝트 클론
git clone [repo-url]

# 2. MySQL 데이터베이스 생성
mysql -u root -p < schema.sql

# 3. application.properties 설정
# spring.datasource.url, username, password
# spring.security.oauth2.client.registration.kakao.client-id
# spring.security.oauth2.client.registration.naver.client-id
# portone.api.key, portone.merchant.id
# kakao.map.api.key

# 4. 프로젝트 실행
./gradlew bootRun
```

## 📞 주요 연락처 / 공지
- 조장 관리 채널: [Slack / Discord 링크]
- 기술 문제 공유: [GitHub Issues / Discussions]
- 정기 회의: [요일/시간]
