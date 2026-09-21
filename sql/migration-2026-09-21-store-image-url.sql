-- 2026-09-21 (송보미) — 홈/찜/가게상세 카드가 전부 "가게명 첫 글자 + 카테고리색" 아바타뿐이라
-- 실제 사진을 넣을 컬럼이 없었다. product.image_url과 같은 방식(null이면 화면에서 기존
-- 아바타로 대체)으로 store에도 컬럼을 추가한다.
-- 로컬은 ddl-auto=update로 자동 추가되지만, 운영/통합 DB엔 이 스크립트로 반영한다.

ALTER TABLE store
    ADD COLUMN image_url VARCHAR(255) NULL;
