# POS json 자동 수신 연동 명세 (가정)

> WBS 3.0 "POS json 자동 수신 연동 (가정)" · 담당 문창호 · 최종 갱신 2026-08-30
>
> **이 문서는 "가정"이다.** 실제 POS사(오케이포스·포스뱅크 등) 단말과 연동하지 않는다.
> "매장 POS가 아래 규격의 JSON을 기리기리로 보낸다"고 가정하고, 그 수신부만 구현했다.
> 시연은 `/store/pos/sim`(POS 시뮬레이터)으로 한다.

---

## 1. 배경 — 왜 이 방식인가

동네에서 **미리 만들어 파는 집**(빵집·김밥·초밥·반찬가게)을 가정한다.
사장님이 아침(또는 아침·점심·저녁)에 한 번씩 쫙 만들고, 그날 안에 소진하거나 폐기한다.

- 이런 매장은 **POS로 품목·재고를 관리**한다 (한 그릇씩 즉석조리하는 집과 다름).
- POS는 "지금 이 메뉴 몇 개 남았나"를 안다.
- 그 재고 스냅샷을 마감 무렵 기리기리가 받아서 →
  **"지금 크루아상 8개 남았는데 앱에 팔래요?"** 초안을 자동 생성한다.

POS엔 **유통기한·제조시각은 안 들어간다** (판매·재고 시스템이지 생산관리 시스템이 아님).
그래서 "오늘 재고에 잡힘 = 오늘 생산분"으로 보고, 유일한 마감 기준은 **매장 영업 마감 시각**이다.

---

## 2. 엔드포인트

| 메서드 | 경로 | 용도 | 빈도(가정) |
|---|---|---|---|
| `POST` | `/api/pos/catalog` | 매장 메뉴 카탈로그(품목·가격) 등록/갱신 | 연동 시 1회 + 메뉴/가격 바뀔 때 |
| `POST` | `/api/pos/stock` | 현재 재고 스냅샷 | 생산 직후 + 주기적(예: 30분) |

- 요청/응답 `Content-Type: application/json`
- 매장 식별: **요청 바디에 매장 id를 안 받는다.** 로그인 세션(`session.userId → store.owner_id`)으로 찾는다.
  → 지금 규격에선 "로그인한 점주 세션"이 있어야 호출된다. (실제 연동이라면 여기에 매장별 API 키/서명이 필요 — 5절 참고)

---

## 3. `POST /api/pos/catalog` — 메뉴 카탈로그

### 요청 바디

```json
[
  { "posSku": "BR001", "name": "크루아상",   "originalPrice": 3500, "imageUrl": null },
  { "posSku": "BR002", "name": "소금빵",     "originalPrice": 3000, "imageUrl": null },
  { "posSku": "BR005", "name": "밀크 식빵",  "originalPrice": 5500, "imageUrl": "https://..." }
]
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `posSku` | string | 권장 | POS 원본 식별자(바코드/상품코드). **upsert 기준.** 없으면 매번 새 메뉴로 들어감 |
| `name` | string | ✅ | 품목명 |
| `originalPrice` | int | ✅ | 정상가(원). 0 이하면 무시 |
| `imageUrl` | string | ✗ | 사진 URL. null이면 카테고리 기본 이미지 사용 |

### 동작

- `posSku` 기준 **upsert** → `menu_item` 테이블.
- 기존 메뉴면 `name` / `originalPrice` / `imageUrl`만 갱신.
  점주가 화면에서 조정한 **앱 판매 on/off · 할인율 · 앱 판매 수량 · 재고는 보존**한다.
- 새 메뉴면 `appSaleEnabled=true`(일단 다 켬) + 시드 재고를 채운다. 점주가 `/store/pos`에서 조정.
- 카탈로그에 안 들어온(=POS에서 없어진) 기존 메뉴는 `/store/pos`의 [연동 해제]로만 지워진다.

### 응답

```json
{ "applied": 8 }        // 200 OK — 반영된 건수
{ "error": "login_required" }   // 401
{ "error": "empty_payload" }    // 400
{ "error": "store_not_found" }  // 403/404
```

---

## 4. `POST /api/pos/stock` — 재고 스냅샷 (B안 핵심)

### 요청 바디

```json
[
  { "posSku": "BR001", "remaining": 8 },
  { "posSku": "BR002", "remaining": 12 },
  { "posSku": "BR005", "remaining": 0 }
]
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `posSku` | string | ✅ | 카탈로그에 이미 있는 SKU. 모르는 SKU는 조용히 무시(먼저 `/catalog`) |
| `remaining` | int | ✅ | 현재 남은 재고. 음수는 무시 |

### 동작

- `posSku` 기준으로 `menu_item.stock_quantity` 갱신, `store.pos_last_sync_at` 갱신.
- 이 값이 **"마감 무렵 자동 초안"의 수량 기준**이 된다.

### 응답

```json
{ "applied": 3 }
```

---

## 5. 재고 → "오늘의 구제" 초안 자동 생성 흐름

```
POS ──/api/pos/stock──▶ menu_item.stock_quantity
                              │
        매일 store.pos_draft_prompt_time 시각 (점주가 /store/pos에서 설정)
                              │
              ListingDraftScheduler.scanPosStockSnapshots (60초 주기)
                              │
         메뉴별로 (appSaleEnabled && stock > 0) 이면 초안 생성:
           · 수량   = min(stock_quantity, app_sale_quantity ?? stock_quantity)
           · 할인율 = discount_rate ?? 자동(마감 3h+ −20% / 1~3h −30% / 1h이내 −50%)
                     (점주 지정값이 자동값보다 낮으면 자동값으로 올림)
           · 정가   = menu_item.original_price (점주 수정 불가 — POS 값 고정)
                              │
              product(status='draft', menu_item_id=…) 생성
                              │
      점주가 /store/products "발행 대기"에서 [바로 올리기] → status='active'
                              │
                   손님 홈/검색에 노출, 예약·결제 가능
```

### 시각·마감 규칙

- 초안이 뜨고 발행 가능한 구간 = **`pos_draft_prompt_time` ~ 매장 마감 10분 전**.
- 마감 10분 전(`StoreHoursUtil.PUBLISH_CUTOFF_MINUTES`)부터 [바로 올리기]는 닫힌다(손님이 픽업하러 올 시간이 없어서).
- 마감/자정이 지나 발행 안 된 초안은 스케줄러가 `skipped` 처리 → "발행 대기"에서 사라짐, 다음날 재고로 새 초안.
- `pos_draft_prompt_time`을 마감 10분 이내로 잡으면 초안 자체가 안 생긴다. **마감 1~1.5시간 전 권장.**

---

## 6. 시연 방법 — POS 시뮬레이터

실제 POS 단말이 없어서 `/store/pos/sim` 화면이 "매장 POS"인 척한다.

1. `/store/pos` → POS사 선택 + 매장 코드(아무 값) → **연동하기**
   → 매장 카테고리에 맞는 샘플 카탈로그가 `menu_item`에 들어옴
2. 메뉴별 **앱 판매 on** + (선택) 할인율 · 앱 판매 수량, **재고 확인 시각** 저장
3. `/store/pos/sim` →
   - 🌅 **아침 생산** — 재고 채우기
   - ⏩ **하루 장사 빨리감기** — 재고 팔린 만큼 감소
   - 📤 **마감 임박 → 앱에 물어보기** — 지금 재고로 초안 생성
4. `/store/products` "발행 대기"에서 확인 → **바로 올리기**

> 시뮬레이터의 "아침 생산 / 빨리감기 / 재고 저장" 버튼은 **실제로 `POST /api/pos/stock`을 호출한다.**
> 재고 숫자는 그 화면(=매장 POS)이 계산해서 이 규격의 JSON으로 보내고, 화면 하단 "전송 로그"에
> 실제 요청/응답이 찍힌다. ("마감 임박 → 앱에 물어보기"만 앱 내부 동작 = 스케줄러 대신 즉시 초안 생성.)
>
> curl/Postman으로 직접 쏴도 동일하게 동작한다(로그인 세션 쿠키 포함):
> ```
> curl -X POST http://localhost:8080/api/pos/stock \
>   -H 'Content-Type: application/json' -b cookie.txt \
>   --data-binary '[{"posSku":"BR001","remaining":8}]'
> ```

---

## 7. 실제 POS 연동으로 가려면 (미구현 — 참고)

이 프로젝트 범위 밖. 실제 연동 시 추가로 필요한 것:

- **매장별 인증** — API 키 발급 / HMAC 서명 / OAuth2 client-credentials. 지금은 로그인 세션에 의존.
- `/api/pos/**`를 인증 예외(또는 토큰 필터)로 — 지금은 `.authenticated()`라 브라우저 세션 없이는 못 부름.
- POS사별 카탈로그/재고 포맷 매핑 어댑터 (오케이포스·포스뱅크·유니온포스 규격 상이).
- 재고 push 실패/지연 대비 — 폴링 폴백, 마지막 동기화 시각 경고.
- 판매 이벤트 웹훅(`/api/pos/sales`) — 발행된 상품이 매장에서 직접 팔렸을 때 재고 차감.

---

## 관련 파일

| 파일 | 역할 |
|---|---|
| `controller/api/PosApiController` | `/api/pos/catalog`, `/api/pos/stock` 수신 |
| `controller/StorePosController` | `/store/pos/**` 점주 화면 + 시뮬레이터 |
| `service/PosCatalogService` | 카탈로그 upsert, 재고 반영, 초안 생성, 시뮬레이터 로직 |
| `service/ListingDraftScheduler` | `pos_draft_prompt_time`에 초안 자동 생성 + 생애주기 정리 |
| `domain/entity/MenuItemEntity` | `menu_item` — posSku·가격·재고·앱판매설정 |
| `domain/entity/StoreEntity` | `pos_provider` / `pos_store_code` / `pos_connected_at` / `pos_last_sync_at` / `pos_draft_prompt_time` |
| `domain/dto/PosMenuItemDto`, `PosStockDto` | 수신 포맷 |
| `templates/storeView/posConnect.html`, `posSimulator.html` | 화면 |
| `docs/schema.sql` | `menu_item` / `store` POS 컬럼 DDL |
