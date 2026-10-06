# Pixabay 샘플 이미지 (2026-10-06)

베이커리/카페/반찬/도시락/식당 등 상품·매장 사진이 필요한 화면(상품 등록, 샘플 데이터 등)에서
바로 쓸 수 있는 무료 이미지 134장. [Pixabay](https://pixabay.com)에서 API로 받았고,
[Pixabay Content License](https://pixabay.com/service/license-summary/)라 상업·비상업 모두
무료 사용 가능, 저작자 표시도 법적으로 필수는 아니다(이미지 자체를 재판매하거나 독립된 스톡
사진집으로 재배포하는 것만 금지).

## 폴더 구성

| 폴더 | 내용 | 장수 |
|---|---|---|
| `bakery/` | 빵, 크루아상, 베이글 등 베이커리 | 28 |
| `cafe/` | 카페 내부, 커피, 라떼아트 | 23 |
| `banchan/` | 한식 반찬, 김치 | 14 |
| `dosirak/` | 도시락 | 14 |
| `restaurant/` | 식당 내부, 식당 음식 | 17 |
| `dessert/` | 케이크, 도넛, 디저트 | 15 |
| `food-general/` | 샌드위치, 샐러드, 포장 음식 등 일반 음식 | 23 |

템플릿·샘플 데이터에서는 `/images/pixabay/<폴더>/<파일명>.jpg` 경로로 바로 참조하면 된다
(예: `/images/pixabay/bakery/bakery-1867459.jpg`).

`manifest.json`에 이미지별 Pixabay 원본 페이지 링크·태그가 들어있다 — 특정 이미지 출처가
필요하면 거기서 id로 찾으면 된다.

## 더 받고 싶으면

`fetch-more.py` 참고. [Pixabay API 키](https://pixabay.com/ko/api/docs/)를 무료로 발급받아
`PIXABAY_KEY` 환경변수로 넘기면 된다(키를 코드에 직접 적지 말 것 — 레포에 커밋되면 안 됨):

```bash
PIXABAY_KEY=발급받은키 python3 fetch-more.py 검색어 저장할폴더명
```
