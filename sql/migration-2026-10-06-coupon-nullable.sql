-- 2026-10-06 — 지역/매장 지정 쿠폰을 위해 쿠폰 테이블의 NOT NULL 제약을 푼다.
-- 코드가 없는 캠페인(매장/지역 지정)과 정액(AMOUNT) 쿠폰은 code / discount_rate 가 NULL 이다.
-- docs/schema.sql 로 만든 DB는 이 컬럼들이 NOT NULL 이라 발행 시 "Column 'code' cannot be null" 로 실패한다
-- (ddl-auto=update 는 NOT NULL 을 풀어주지 않는다). 이미 NULL 허용이면 아무 일도 하지 않는다 — 여러 번 실행해도 안전.
-- coupon_region 테이블은 앱을 한 번 실행하면 ddl-auto 가 만들어 준다.
ALTER TABLE coupon_campaign MODIFY code VARCHAR(30) NULL;
ALTER TABLE coupon_campaign MODIFY discount_rate INT NULL;
ALTER TABLE coupon MODIFY discount_rate INT NULL;
