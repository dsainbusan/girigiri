-- 2026-10-06 — 슈퍼어드민 주문 상세에서 신고 없이 바로 환불할 수 있게 하면서 refund.report_id의
-- NOT NULL 제약을 푼다. docs/schema.sql로 만든 DB는 이 컬럼이 NOT NULL이라 신고 없는 직접환불
-- 저장 시 "Column 'report_id' cannot be null"로 실패한다(ddl-auto=update는 NOT NULL을 풀어주지
-- 않는다 — migration-2026-10-06-coupon-nullable.sql과 동일한 이유). 이미 NULL 허용이면 아무 일도
-- 하지 않는다 — 여러 번 실행해도 안전.
ALTER TABLE refund MODIFY report_id BIGINT NULL;
