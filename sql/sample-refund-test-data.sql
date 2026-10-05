-- 신고 기반 환불 테스트용 "추가 전용" 샘플 데이터 (2026-10-06).
-- sql/sample-data.sql 과 달리 기존 데이터를 지우지 않는다 — 이미 sample-data.sql 이 들어 있는 DB에
-- 신고 3~6번, 주문 27번, 결제 3건, 환불 1건만 덧붙인다. 여러 번 실행해도 중복으로 안 들어간다(INSERT IGNORE).
-- 전제: users 1·5·12·13, store 3·4·5, reservation 18·21·26 이 있어야 한다(sample-data.sql 기준).
-- 실행: mysql -u root -p --default-character-set=utf8mb4 girigiri < sql/sample-refund-test-data.sql

-- 27번: 이미 환불 완료된 주문(신고 5번과 연결)
INSERT IGNORE INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at, accepted_at, cancel_reason, cancelled_by) VALUES
(27, 13, 12, '잡채 한 팩', 5, 1, 5400, DATE_SUB(NOW(), INTERVAL 20 HOUR), 'PICK-1027', 'refunded', DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR), '상품 상태 불량 확인되어 전액 환불', 'ADMIN');

-- 결제: 26·21번은 PAID(환불 버튼 테스트용), 27번은 환불 완료. reservation_id UNIQUE 라 id 충돌 대신 이 키로 막는다.
INSERT IGNORE INTO payment (reservation_id, merchant_uid, imp_uid, amount, pay_method, pay_status, fail_reason, paid_at, requested_at, updated_at) VALUES
(26, 'SAMPLE-PAY-26', 'SAMPLE-IMP-26', 5400, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(21, 'SAMPLE-PAY-21', 'SAMPLE-IMP-21', 3200, 'kakaopay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(27, 'SAMPLE-PAY-27', 'SAMPLE-IMP-27', 5400, 'card', 'CANCELLED', '상품 상태 불량 확인되어 전액 환불', DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- 신고 (주문 연결 있음)
--  3: 픽업완료+PAID 대기 → [환불 처리] 버튼   4: 같은 조건(카카오페이)
--  5: 이미 환불 완료 → "환불 완료" 배지         6: 노쇼 주문 신고 → 환불 불가 안내
INSERT IGNORE INTO complaint (id, target_name, target_store_id, target_reservation_id, reason, content, reporter_name, reporter_id, status, admin_reply, created_at, resolved_at) VALUES
(3, '엄마손반찬', 5, 26, '상품 상태 불량', '받아온 잡채가 상해서 신맛이 났어요. 환불 부탁드립니다.', '구제왕나은', 1, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL),
(4, '커피와우 명동점', 4, 21, '수량 부족', '베이글이 2개라고 했는데 1개만 들어 있었어요.', '자취생박지민', 12, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), NULL),
(5, '엄마손반찬', 5, 27, '상품 상태 불량', '잡채에서 이물질이 나왔습니다.', '다이어터최유나', 13, 'RESOLVED', '확인 후 전액 환불 처리했어요. 불편을 드려 죄송합니다.', DATE_SUB(NOW(), INTERVAL 18 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(6, '브런치카페 온', 3, 18, '노쇼 오처리', '방문했는데 노쇼로 처리됐어요.', '노쇼왕문창호', 5, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- 환불 완료 기록 (order_id UNIQUE) + 상태 변경 이력. requested_by=4 는 운영자(admin@girigiri.com).
INSERT IGNORE INTO refund (order_id, report_id, amount, reason, status, pg_refund_tid, requested_by, created_at, updated_at) VALUES
(27, 5, 5400, '상품 상태 불량 확인되어 전액 환불', 'DONE', 'SAMPLE-PAY-27', 4, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR));

INSERT INTO reservation_status_history (reservation_id, from_status, to_status, changed_by, reason, created_at)
SELECT 27, 'picked', 'refunded', 4, '상품 상태 불량 확인되어 전액 환불', DATE_SUB(NOW(), INTERVAL 2 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM reservation_status_history WHERE reservation_id = 27);
