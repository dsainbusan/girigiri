-- =====================================================================
-- 팀 전체 기능 테스트용 "대량 추가" 샘플 데이터 (2026-10-06)
-- =====================================================================
-- "지금 있는 상태에서 여러가지 테스트 해봐야 하니 모든 팀원 작업에 샘플 데이터를 50개 정도씩 넣어달라"는
-- 요청으로 작성. sql/sample-data.sql(+sample-coupon-data.sql, sample-region-data.sql)이 이미 들어있는
-- DB에 "추가 전용"으로 얹는다 — 기존 데이터를 지우지 않고 INSERT IGNORE라 여러 번 실행해도 중복 안 됨.
--
-- 전제: sql/sample-data.sql이 먼저 실행되어 있어야 한다(users 1~15, store 1~8, product 1~22,
--      reservation 1~27까지 존재). sample-region-data.sql의 id 범위(users 21+, store 11+,
--      product 101+, reservation 101+)와도 겹치지 않는다.
-- 실행: mysql -u root -p --default-character-set=utf8mb4 girigiri < sql/sample-data-bulk-2.sql
--
-- 담당 영역별로 늘린 것:
--   문창호(인증·매장운영·가계부·공지)   : reservation/payment/payment_cancel/receipt, settlement,
--                                        notification_setting, store_announcement, menu_item,
--                                        listing_template, coupon_policy, user_social_accounts
--   강노은(지도·탐색·알림·리뷰)         : review, review_summary, likes, notification
--   송채현(예약·결제·챗봇)              : reservation/payment 다양한 상태, payment_cancel
--   송보미(공통·슈퍼어드민)             : complaint, inquiry/inquiry_comment, notice, user_archive
--
-- reservation 28~57(30건), payment 4~33, payment_cancel 1~2, receipt 17~36, review 11~23,
-- likes 18~49(32건), notification 11~50(40건) — product/user는 sample-data.sql 카탈로그 재사용.
-- =====================================================================

-- ---------------------------------------------------------------------
-- reservation (30건 추가 — pending/confirmed/ready/picked/cancelled/noshowed 다양하게)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at, accepted_at, cancel_reason, cancelled_by) VALUES
(28, 5, 19, '돈까스 도시락', 7, 1, 3600, DATE_SUB(NOW(), INTERVAL 327 HOUR), 'PICK-1028', 'picked', DATE_SUB(NOW(), INTERVAL 327 HOUR), DATE_SUB(NOW(), INTERVAL 327 HOUR), DATE_SUB(NOW(), INTERVAL 327 HOUR), NULL, NULL),
(29, 1, 4, '단팥빵 5개입', 1, 2, 8400, DATE_SUB(NOW(), INTERVAL 199 HOUR), 'PICK-1029', 'picked', DATE_SUB(NOW(), INTERVAL 199 HOUR), DATE_SUB(NOW(), INTERVAL 199 HOUR), DATE_SUB(NOW(), INTERVAL 199 HOUR), NULL, NULL),
(30, 5, 20, '샐러드 도시락', 8, 1, 4800, DATE_SUB(NOW(), INTERVAL 941 HOUR), 'PICK-1030', 'picked', DATE_SUB(NOW(), INTERVAL 941 HOUR), DATE_SUB(NOW(), INTERVAL 941 HOUR), DATE_SUB(NOW(), INTERVAL 941 HOUR), NULL, NULL),
(31, 1, 13, '계란말이 + 진미채', 5, 1, 4000, DATE_ADD(NOW(), INTERVAL 5 HOUR), 'PICK-1031', 'pending', NOW(), NULL, NULL, NULL, NULL),
(32, 14, 7, '아메리카노 원두 마감', 3, 1, 6000, DATE_ADD(NOW(), INTERVAL 2 HOUR), 'PICK-1032', 'confirmed', NOW(), NULL, NOW(), NULL, NULL),
(33, 1, 10, '어제 구운 스콘 4개', 4, 1, 3600, DATE_SUB(NOW(), INTERVAL 207 HOUR), 'PICK-1033', 'picked', DATE_SUB(NOW(), INTERVAL 207 HOUR), DATE_SUB(NOW(), INTERVAL 207 HOUR), DATE_SUB(NOW(), INTERVAL 207 HOUR), NULL, NULL),
(34, 2, 12, '잡채 한 팩', 5, 1, 5400, DATE_SUB(NOW(), INTERVAL 147 HOUR), 'PICK-1034', 'picked', DATE_SUB(NOW(), INTERVAL 147 HOUR), DATE_SUB(NOW(), INTERVAL 147 HOUR), DATE_SUB(NOW(), INTERVAL 147 HOUR), NULL, NULL),
(35, 5, 13, '계란말이 + 진미채', 5, 1, 4000, DATE_SUB(NOW(), INTERVAL 665 HOUR), 'PICK-1035', 'picked', DATE_SUB(NOW(), INTERVAL 665 HOUR), DATE_SUB(NOW(), INTERVAL 665 HOUR), DATE_SUB(NOW(), INTERVAL 665 HOUR), NULL, NULL),
(36, 5, 13, '계란말이 + 진미채', 5, 1, 4000, DATE_SUB(NOW(), INTERVAL 433 HOUR), 'PICK-1036', 'picked', DATE_SUB(NOW(), INTERVAL 433 HOUR), DATE_SUB(NOW(), INTERVAL 433 HOUR), DATE_SUB(NOW(), INTERVAL 433 HOUR), NULL, NULL),
(37, 12, 21, '김밥 3줄 세트', 8, 1, 3000, DATE_SUB(NOW(), INTERVAL 543 HOUR), 'PICK-1037', 'picked', DATE_SUB(NOW(), INTERVAL 543 HOUR), DATE_SUB(NOW(), INTERVAL 543 HOUR), DATE_SUB(NOW(), INTERVAL 543 HOUR), NULL, NULL),
(38, 12, 19, '돈까스 도시락', 7, 2, 7200, DATE_SUB(NOW(), INTERVAL 742 HOUR), 'PICK-1038', 'picked', DATE_SUB(NOW(), INTERVAL 742 HOUR), DATE_SUB(NOW(), INTERVAL 742 HOUR), DATE_SUB(NOW(), INTERVAL 742 HOUR), NULL, NULL),
(39, 13, 5, '크로플 세트 (2개)', 3, 2, 9600, DATE_ADD(NOW(), INTERVAL 1 HOUR), 'PICK-1039', 'pending', NOW(), NULL, NULL, NULL, NULL),
(40, 2, 4, '단팥빵 5개입', 1, 1, 4200, DATE_SUB(NOW(), INTERVAL 865 HOUR), 'PICK-1040', 'noshowed', DATE_SUB(NOW(), INTERVAL 865 HOUR), NULL, DATE_SUB(NOW(), INTERVAL 865 HOUR), NULL, NULL),
(41, 1, 20, '샐러드 도시락', 8, 2, 9600, DATE_ADD(NOW(), INTERVAL 4 HOUR), 'PICK-1041', 'confirmed', NOW(), NULL, NOW(), NULL, NULL),
(42, 5, 17, '제육볶음 도시락', 7, 1, 4250, DATE_SUB(NOW(), INTERVAL 235 HOUR), 'PICK-1042', 'picked', DATE_SUB(NOW(), INTERVAL 235 HOUR), DATE_SUB(NOW(), INTERVAL 235 HOUR), DATE_SUB(NOW(), INTERVAL 235 HOUR), NULL, NULL),
(43, 1, 11, '오늘의 나물 반찬세트', 5, 1, 7200, DATE_SUB(NOW(), INTERVAL 891 HOUR), 'PICK-1043', 'cancelled', DATE_SUB(NOW(), INTERVAL 891 HOUR), NULL, DATE_SUB(NOW(), INTERVAL 891 HOUR), '단순 변심', 'USER'),
(44, 1, 15, '멸치볶음 + 콩자반', 6, 1, 3500, DATE_SUB(NOW(), INTERVAL 1026 HOUR), 'PICK-1044', 'picked', DATE_SUB(NOW(), INTERVAL 1026 HOUR), DATE_SUB(NOW(), INTERVAL 1026 HOUR), DATE_SUB(NOW(), INTERVAL 1026 HOUR), NULL, NULL),
(45, 15, 10, '어제 구운 스콘 4개', 4, 1, 3600, DATE_SUB(NOW(), INTERVAL 314 HOUR), 'PICK-1045', 'picked', DATE_SUB(NOW(), INTERVAL 314 HOUR), DATE_SUB(NOW(), INTERVAL 314 HOUR), DATE_SUB(NOW(), INTERVAL 314 HOUR), NULL, NULL),
(46, 15, 18, '오늘의 도시락 (랜덤)', 7, 1, 3500, DATE_SUB(NOW(), INTERVAL 664 HOUR), 'PICK-1046', 'picked', DATE_SUB(NOW(), INTERVAL 664 HOUR), DATE_SUB(NOW(), INTERVAL 664 HOUR), DATE_SUB(NOW(), INTERVAL 664 HOUR), NULL, NULL),
(47, 1, 8, '베이글 2개 세트', 4, 1, 3200, DATE_SUB(NOW(), INTERVAL 1162 HOUR), 'PICK-1047', 'picked', DATE_SUB(NOW(), INTERVAL 1162 HOUR), DATE_SUB(NOW(), INTERVAL 1162 HOUR), DATE_SUB(NOW(), INTERVAL 1162 HOUR), NULL, NULL),
(48, 2, 5, '크로플 세트 (2개)', 3, 2, 9600, DATE_SUB(NOW(), INTERVAL 1126 HOUR), 'PICK-1048', 'picked', DATE_SUB(NOW(), INTERVAL 1126 HOUR), DATE_SUB(NOW(), INTERVAL 1126 HOUR), DATE_SUB(NOW(), INTERVAL 1126 HOUR), NULL, NULL),
(49, 13, 7, '아메리카노 원두 마감', 3, 1, 6000, DATE_SUB(NOW(), INTERVAL 639 HOUR), 'PICK-1049', 'cancelled', DATE_SUB(NOW(), INTERVAL 639 HOUR), NULL, DATE_SUB(NOW(), INTERVAL 639 HOUR), '재고 소진으로 매장에서 취소', 'STORE'),
(50, 14, 22, '어제 만든 불고기 도시락', 8, 1, 3400, DATE_SUB(NOW(), INTERVAL 898 HOUR), 'PICK-1050', 'picked', DATE_SUB(NOW(), INTERVAL 898 HOUR), DATE_SUB(NOW(), INTERVAL 898 HOUR), DATE_SUB(NOW(), INTERVAL 898 HOUR), NULL, NULL),
(51, 13, 1, '식빵 마감세트', 1, 1, 3000, DATE_ADD(NOW(), INTERVAL 1 HOUR), 'PICK-1051', 'ready', NOW(), NULL, NOW(), NULL, NULL),
(52, 1, 21, '김밥 3줄 세트', 8, 1, 3000, DATE_SUB(NOW(), INTERVAL 139 HOUR), 'PICK-1052', 'picked', DATE_SUB(NOW(), INTERVAL 139 HOUR), DATE_SUB(NOW(), INTERVAL 139 HOUR), DATE_SUB(NOW(), INTERVAL 139 HOUR), NULL, NULL),
(53, 13, 3, '어제 만든 케이크', 1, 1, 6000, DATE_SUB(NOW(), INTERVAL 571 HOUR), 'PICK-1053', 'picked', DATE_SUB(NOW(), INTERVAL 571 HOUR), DATE_SUB(NOW(), INTERVAL 571 HOUR), DATE_SUB(NOW(), INTERVAL 571 HOUR), NULL, NULL),
(54, 13, 19, '돈까스 도시락', 7, 2, 7200, DATE_ADD(NOW(), INTERVAL 4 HOUR), 'PICK-1054', 'ready', NOW(), NULL, NOW(), NULL, NULL),
(55, 1, 7, '아메리카노 원두 마감', 3, 1, 6000, DATE_SUB(NOW(), INTERVAL 883 HOUR), 'PICK-1055', 'picked', DATE_SUB(NOW(), INTERVAL 883 HOUR), DATE_SUB(NOW(), INTERVAL 883 HOUR), DATE_SUB(NOW(), INTERVAL 883 HOUR), NULL, NULL),
(56, 14, 2, '크루아상 3개입', 1, 1, 4500, DATE_SUB(NOW(), INTERVAL 125 HOUR), 'PICK-1056', 'picked', DATE_SUB(NOW(), INTERVAL 125 HOUR), DATE_SUB(NOW(), INTERVAL 125 HOUR), DATE_SUB(NOW(), INTERVAL 125 HOUR), NULL, NULL),
(57, 2, 4, '단팥빵 5개입', 1, 1, 4200, DATE_ADD(NOW(), INTERVAL 4 HOUR), 'PICK-1057', 'confirmed', NOW(), NULL, NOW(), NULL, NULL);

-- ---------------------------------------------------------------------
-- payment (위 reservation 30건과 1:1)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO payment (id, reservation_id, merchant_uid, imp_uid, amount, pay_method, pay_status, fail_reason, paid_at, requested_at, updated_at) VALUES
(4, 28, 'BULK-PAY-28', 'BULK-IMP-28', 3600, 'tosspay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 327 HOUR), DATE_SUB(NOW(), INTERVAL 327 HOUR), DATE_SUB(NOW(), INTERVAL 327 HOUR)),
(5, 29, 'BULK-PAY-29', 'BULK-IMP-29', 8400, 'naverpay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 199 HOUR), DATE_SUB(NOW(), INTERVAL 199 HOUR), DATE_SUB(NOW(), INTERVAL 199 HOUR)),
(6, 30, 'BULK-PAY-30', 'BULK-IMP-30', 4800, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 941 HOUR), DATE_SUB(NOW(), INTERVAL 941 HOUR), DATE_SUB(NOW(), INTERVAL 941 HOUR)),
(7, 31, 'BULK-PAY-31', NULL, 4000, 'card', 'READY', NULL, NULL, NOW(), NOW()),
(8, 32, 'BULK-PAY-32', 'BULK-IMP-32', 6000, 'naverpay', 'PAID', NULL, NOW(), NOW(), NOW()),
(9, 33, 'BULK-PAY-33', 'BULK-IMP-33', 3600, 'tosspay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 207 HOUR), DATE_SUB(NOW(), INTERVAL 207 HOUR), DATE_SUB(NOW(), INTERVAL 207 HOUR)),
(10, 34, 'BULK-PAY-34', 'BULK-IMP-34', 5400, 'kakaopay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 147 HOUR), DATE_SUB(NOW(), INTERVAL 147 HOUR), DATE_SUB(NOW(), INTERVAL 147 HOUR)),
(11, 35, 'BULK-PAY-35', 'BULK-IMP-35', 4000, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 665 HOUR), DATE_SUB(NOW(), INTERVAL 665 HOUR), DATE_SUB(NOW(), INTERVAL 665 HOUR)),
(12, 36, 'BULK-PAY-36', 'BULK-IMP-36', 4000, 'naverpay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 433 HOUR), DATE_SUB(NOW(), INTERVAL 433 HOUR), DATE_SUB(NOW(), INTERVAL 433 HOUR)),
(13, 37, 'BULK-PAY-37', 'BULK-IMP-37', 3000, 'kakaopay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 543 HOUR), DATE_SUB(NOW(), INTERVAL 543 HOUR), DATE_SUB(NOW(), INTERVAL 543 HOUR)),
(14, 38, 'BULK-PAY-38', 'BULK-IMP-38', 7200, 'kakaopay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 742 HOUR), DATE_SUB(NOW(), INTERVAL 742 HOUR), DATE_SUB(NOW(), INTERVAL 742 HOUR)),
(15, 39, 'BULK-PAY-39', NULL, 9600, 'card', 'READY', NULL, NULL, NOW(), NOW()),
(16, 40, 'BULK-PAY-40', 'BULK-IMP-40', 4200, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 865 HOUR), DATE_SUB(NOW(), INTERVAL 865 HOUR), DATE_SUB(NOW(), INTERVAL 865 HOUR)),
(17, 41, 'BULK-PAY-41', 'BULK-IMP-41', 9600, 'naverpay', 'PAID', NULL, NOW(), NOW(), NOW()),
(18, 42, 'BULK-PAY-42', 'BULK-IMP-42', 4250, 'naverpay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 235 HOUR), DATE_SUB(NOW(), INTERVAL 235 HOUR), DATE_SUB(NOW(), INTERVAL 235 HOUR)),
(19, 43, 'BULK-PAY-43', 'BULK-IMP-43', 7200, 'card', 'CANCELLED', '단순 변심', DATE_SUB(NOW(), INTERVAL 891 HOUR), DATE_SUB(NOW(), INTERVAL 891 HOUR), DATE_SUB(NOW(), INTERVAL 890 HOUR)),
(20, 44, 'BULK-PAY-44', 'BULK-IMP-44', 3500, 'kakaopay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 1026 HOUR), DATE_SUB(NOW(), INTERVAL 1026 HOUR), DATE_SUB(NOW(), INTERVAL 1026 HOUR)),
(21, 45, 'BULK-PAY-45', 'BULK-IMP-45', 3600, 'naverpay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 314 HOUR), DATE_SUB(NOW(), INTERVAL 314 HOUR), DATE_SUB(NOW(), INTERVAL 314 HOUR)),
(22, 46, 'BULK-PAY-46', 'BULK-IMP-46', 3500, 'tosspay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 664 HOUR), DATE_SUB(NOW(), INTERVAL 664 HOUR), DATE_SUB(NOW(), INTERVAL 664 HOUR)),
(23, 47, 'BULK-PAY-47', 'BULK-IMP-47', 3200, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 1162 HOUR), DATE_SUB(NOW(), INTERVAL 1162 HOUR), DATE_SUB(NOW(), INTERVAL 1162 HOUR)),
(24, 48, 'BULK-PAY-48', 'BULK-IMP-48', 9600, 'kakaopay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 1126 HOUR), DATE_SUB(NOW(), INTERVAL 1126 HOUR), DATE_SUB(NOW(), INTERVAL 1126 HOUR)),
(25, 49, 'BULK-PAY-49', 'BULK-IMP-49', 6000, 'card', 'CANCELLED', '재고 소진으로 매장에서 취소', DATE_SUB(NOW(), INTERVAL 639 HOUR), DATE_SUB(NOW(), INTERVAL 639 HOUR), DATE_SUB(NOW(), INTERVAL 638 HOUR)),
(26, 50, 'BULK-PAY-50', 'BULK-IMP-50', 3400, 'tosspay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 898 HOUR), DATE_SUB(NOW(), INTERVAL 898 HOUR), DATE_SUB(NOW(), INTERVAL 898 HOUR)),
(27, 51, 'BULK-PAY-51', 'BULK-IMP-51', 3000, 'card', 'PAID', NULL, NOW(), NOW(), NOW()),
(28, 52, 'BULK-PAY-52', 'BULK-IMP-52', 3000, 'card', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 139 HOUR), DATE_SUB(NOW(), INTERVAL 139 HOUR), DATE_SUB(NOW(), INTERVAL 139 HOUR)),
(29, 53, 'BULK-PAY-53', 'BULK-IMP-53', 6000, 'tosspay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 571 HOUR), DATE_SUB(NOW(), INTERVAL 571 HOUR), DATE_SUB(NOW(), INTERVAL 571 HOUR)),
(30, 54, 'BULK-PAY-54', 'BULK-IMP-54', 7200, 'kakaopay', 'PAID', NULL, NOW(), NOW(), NOW()),
(31, 55, 'BULK-PAY-55', 'BULK-IMP-55', 6000, 'naverpay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 883 HOUR), DATE_SUB(NOW(), INTERVAL 883 HOUR), DATE_SUB(NOW(), INTERVAL 883 HOUR)),
(32, 56, 'BULK-PAY-56', 'BULK-IMP-56', 4500, 'tosspay', 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 125 HOUR), DATE_SUB(NOW(), INTERVAL 125 HOUR), DATE_SUB(NOW(), INTERVAL 125 HOUR)),
(33, 57, 'BULK-PAY-57', 'BULK-IMP-57', 4200, 'naverpay', 'PAID', NULL, NOW(), NOW(), NOW());

-- ---------------------------------------------------------------------
-- payment_cancel (cancelled로 끝난 결제 2건의 취소 시도 로그 — 기존 17·19번은 payment 자체가
-- 없던 데이터라 같이 보강)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO payment_cancel (id, payment_id, amount, reason, succeeded, requested_at) VALUES
(1, 19, 7200, '단순 변심', 1, DATE_SUB(NOW(), INTERVAL 890 HOUR)),
(2, 25, 6000, '재고 소진으로 매장에서 취소', 1, DATE_SUB(NOW(), INTERVAL 638 HOUR));

-- ---------------------------------------------------------------------
-- receipt (picked 20건)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO receipt (id, reservation_id, pdf_url, generated_at) VALUES
(17, 28, '/receipts/reservation-28.pdf', DATE_SUB(NOW(), INTERVAL 327 HOUR)),
(18, 29, '/receipts/reservation-29.pdf', DATE_SUB(NOW(), INTERVAL 199 HOUR)),
(19, 30, '/receipts/reservation-30.pdf', DATE_SUB(NOW(), INTERVAL 941 HOUR)),
(20, 33, '/receipts/reservation-33.pdf', DATE_SUB(NOW(), INTERVAL 207 HOUR)),
(21, 34, '/receipts/reservation-34.pdf', DATE_SUB(NOW(), INTERVAL 147 HOUR)),
(22, 35, '/receipts/reservation-35.pdf', DATE_SUB(NOW(), INTERVAL 665 HOUR)),
(23, 36, '/receipts/reservation-36.pdf', DATE_SUB(NOW(), INTERVAL 433 HOUR)),
(24, 37, '/receipts/reservation-37.pdf', DATE_SUB(NOW(), INTERVAL 543 HOUR)),
(25, 38, '/receipts/reservation-38.pdf', DATE_SUB(NOW(), INTERVAL 742 HOUR)),
(26, 42, '/receipts/reservation-42.pdf', DATE_SUB(NOW(), INTERVAL 235 HOUR)),
(27, 44, '/receipts/reservation-44.pdf', DATE_SUB(NOW(), INTERVAL 1026 HOUR)),
(28, 45, '/receipts/reservation-45.pdf', DATE_SUB(NOW(), INTERVAL 314 HOUR)),
(29, 46, '/receipts/reservation-46.pdf', DATE_SUB(NOW(), INTERVAL 664 HOUR)),
(30, 47, '/receipts/reservation-47.pdf', DATE_SUB(NOW(), INTERVAL 1162 HOUR)),
(31, 48, '/receipts/reservation-48.pdf', DATE_SUB(NOW(), INTERVAL 1126 HOUR)),
(32, 50, '/receipts/reservation-50.pdf', DATE_SUB(NOW(), INTERVAL 898 HOUR)),
(33, 52, '/receipts/reservation-52.pdf', DATE_SUB(NOW(), INTERVAL 139 HOUR)),
(34, 53, '/receipts/reservation-53.pdf', DATE_SUB(NOW(), INTERVAL 571 HOUR)),
(35, 55, '/receipts/reservation-55.pdf', DATE_SUB(NOW(), INTERVAL 883 HOUR)),
(36, 56, '/receipts/reservation-56.pdf', DATE_SUB(NOW(), INTERVAL 125 HOUR));

-- ---------------------------------------------------------------------
-- review (reservation_id 연결, 일부만 사장님 답글)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO review (id, user_id, store_id, reservation_id, rating, content, image_url, edited, created_at, reply_content, reply_created_at, reply_edited) VALUES
(11, 5, 7, 28, 5, '포장도 깔끔하고 맛도 좋았습니다.', NULL, 0, DATE_SUB(NOW(), INTERVAL 326 HOUR), '소중한 후기 감사해요. 더 신경 써서 준비할게요!', DATE_SUB(NOW(), INTERVAL 316 HOUR), 0),
(12, 1, 4, 33, 4, '픽업 시간 맞춰 가니 바로 받을 수 있어서 편했어요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 206 HOUR), '소중한 후기 감사해요. 더 신경 써서 준비할게요!', DATE_SUB(NOW(), INTERVAL 195 HOUR), 0),
(13, 2, 5, 34, 3, '괜찮았지만 생각보다 양이 적었어요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 146 HOUR), '소중한 후기 감사해요. 더 신경 써서 준비할게요!', DATE_SUB(NOW(), INTERVAL 132 HOUR), 0),
(14, 5, 5, 35, 5, '마감 임박인데도 신선해서 놀랐어요. 또 올게요!', NULL, 0, DATE_SUB(NOW(), INTERVAL 664 HOUR), NULL, NULL, 0),
(15, 5, 5, 36, 2, '유통기한이 생각보다 너무 임박해 있었어요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 432 HOUR), NULL, NULL, 0),
(16, 12, 8, 37, 4, '친절하게 안내해주셔서 좋았습니다.', NULL, 0, DATE_SUB(NOW(), INTERVAL 542 HOUR), '불편을 드려 죄송해요. 다음엔 더 꼼꼼히 챙기겠습니다.', DATE_SUB(NOW(), INTERVAL 534 HOUR), 0),
(17, 1, 6, 44, 4, '가격 대비 양이 많아서 만족스러웠어요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 1025 HOUR), NULL, NULL, 0),
(18, 15, 7, 46, 4, '픽업 시간 맞춰 가니 바로 받을 수 있어서 편했어요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 663 HOUR), NULL, NULL, 0),
(19, 1, 4, 47, 2, '유통기한이 생각보다 너무 임박해 있었어요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 1161 HOUR), '맛있게 드셔주셔서 감사합니다! 다음에도 좋은 상품으로 찾아뵐게요 :)', DATE_SUB(NOW(), INTERVAL 1144 HOUR), 0),
(20, 2, 3, 48, 5, '맛도 좋고 환경에도 도움되는 느낌이라 더 좋아요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 1125 HOUR), NULL, NULL, 0),
(21, 14, 8, 50, 3, '괜찮았지만 생각보다 양이 적었어요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 897 HOUR), '맛있게 드셔주셔서 감사합니다! 다음에도 좋은 상품으로 찾아뵐게요 :)', DATE_SUB(NOW(), INTERVAL 887 HOUR), 0),
(22, 13, 1, 53, 5, '포장도 깔끔하고 맛도 좋았습니다.', NULL, 0, DATE_SUB(NOW(), INTERVAL 570 HOUR), NULL, NULL, 0),
(23, 1, 3, 55, 2, '유통기한이 생각보다 너무 임박해 있었어요.', NULL, 0, DATE_SUB(NOW(), INTERVAL 882 HOUR), NULL, NULL, 0);

-- ---------------------------------------------------------------------
-- review_summary (매장별 AI 요약 캐시 — 강노은 담당 화면용 초기값)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO review_summary (id, store_id, summary, review_count_at_summary, generated_at) VALUES
(1, 1, '신선한 빵과 합리적인 마감 할인에 대한 호평이 많아요. 유통기한이 임박했다는 지적도 간혹 있어요.', 5, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 3, '커피와 디저트 모두 마감 시간에도 품질이 좋다는 평이 많습니다.', 4, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(3, 4, '베이글과 디저트 양이 다소 적다는 의견이 있지만 신선도는 만족스럽다는 평가예요.', 3, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(4, 5, '집밥 느낌 나는 반찬이라는 호평이 많고, 간이 세다는 의견도 일부 있어요.', 5, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(5, 6, '김치·불고기 등 메인 반찬 만족도가 높습니다.', 2, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(6, 7, '도시락 양이 많고 가격 대비 만족스럽다는 평이 대부분이에요.', 3, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(7, 8, '샐러드·김밥 모두 신선하다는 평가가 많습니다.', 3, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ---------------------------------------------------------------------
-- likes (32건 추가, 중복 없는 (user_id, store_id) 조합)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO likes (id, user_id, store_id, created_at) VALUES
(18, 15, 3, DATE_SUB(NOW(), INTERVAL 51 HOUR)),
(19, 5, 1, DATE_SUB(NOW(), INTERVAL 227 HOUR)),
(20, 13, 1, DATE_SUB(NOW(), INTERVAL 48 HOUR)),
(21, 5, 4, DATE_SUB(NOW(), INTERVAL 571 HOUR)),
(22, 15, 5, DATE_SUB(NOW(), INTERVAL 880 HOUR)),
(23, 2, 4, DATE_SUB(NOW(), INTERVAL 137 HOUR)),
(24, 2, 6, DATE_SUB(NOW(), INTERVAL 297 HOUR)),
(25, 13, 7, DATE_SUB(NOW(), INTERVAL 430 HOUR)),
(26, 1, 4, DATE_SUB(NOW(), INTERVAL 148 HOUR)),
(27, 5, 8, DATE_SUB(NOW(), INTERVAL 554 HOUR)),
(28, 2, 8, DATE_SUB(NOW(), INTERVAL 121 HOUR)),
(29, 12, 3, DATE_SUB(NOW(), INTERVAL 585 HOUR)),
(30, 14, 1, DATE_SUB(NOW(), INTERVAL 316 HOUR)),
(31, 14, 3, DATE_SUB(NOW(), INTERVAL 574 HOUR)),
(32, 13, 6, DATE_SUB(NOW(), INTERVAL 836 HOUR)),
(33, 5, 7, DATE_SUB(NOW(), INTERVAL 699 HOUR)),
(34, 14, 4, DATE_SUB(NOW(), INTERVAL 186 HOUR)),
(35, 14, 7, DATE_SUB(NOW(), INTERVAL 106 HOUR)),
(36, 2, 5, DATE_SUB(NOW(), INTERVAL 596 HOUR)),
(37, 12, 4, DATE_SUB(NOW(), INTERVAL 585 HOUR)),
(38, 14, 5, DATE_SUB(NOW(), INTERVAL 655 HOUR)),
(39, 12, 8, DATE_SUB(NOW(), INTERVAL 193 HOUR)),
(40, 5, 5, DATE_SUB(NOW(), INTERVAL 382 HOUR)),
(41, 1, 8, DATE_SUB(NOW(), INTERVAL 100 HOUR)),
(42, 12, 6, DATE_SUB(NOW(), INTERVAL 561 HOUR)),
(43, 1, 6, DATE_SUB(NOW(), INTERVAL 730 HOUR)),
(44, 1, 5, DATE_SUB(NOW(), INTERVAL 65 HOUR)),
(45, 15, 7, DATE_SUB(NOW(), INTERVAL 578 HOUR)),
(46, 5, 6, DATE_SUB(NOW(), INTERVAL 62 HOUR)),
(47, 2, 3, DATE_SUB(NOW(), INTERVAL 634 HOUR)),
(48, 15, 6, DATE_SUB(NOW(), INTERVAL 211 HOUR)),
(49, 13, 5, DATE_SUB(NOW(), INTERVAL 509 HOUR));

-- ---------------------------------------------------------------------
-- notification (40건 추가)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO notification (id, user_id, type, message, link_url, source_key, is_read, created_at) VALUES
(11, 2, 'LIKE_STORE_OPEN', '찜한 "엄마손반찬"에서 마감세일이 시작됐어요', '/user/stores/5', 'like_store_open:2:5', 1, DATE_SUB(NOW(), INTERVAL 188 HOUR)),
(12, 2, 'RESERVATION_CONFIRMED', '"크루아상 3개입" 예약 확정', '/reservation/my', 'reservation_confirmed:12', 0, DATE_SUB(NOW(), INTERVAL 454 HOUR)),
(13, 14, 'RESERVATION_NOSHOW', '"어제 만든 케이크" 픽업 시간 경과 (노쇼 처리)', '/reservation/my', 'reservation_noshow:13', 0, DATE_SUB(NOW(), INTERVAL 857 HOUR)),
(14, 15, 'RESERVATION_PICKUP_SOON', '"어제 만든 불고기" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:14', 1, DATE_SUB(NOW(), INTERVAL 171 HOUR)),
(15, 12, 'RESERVATION_PICKUP_SOON', '"크루아상 3개입" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:15', 1, DATE_SUB(NOW(), INTERVAL 886 HOUR)),
(16, 2, 'LIKE_STORE_OPEN', '찜한 "커피와우 명동점"에서 마감세일이 시작됐어요', '/user/stores/4', 'like_store_open:2:4', 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(17, 15, 'LIKE_STORE_OPEN', '찜한 "든든도시락"에서 마감세일이 시작됐어요', '/user/stores/7', 'like_store_open:15:7', 1, DATE_SUB(NOW(), INTERVAL 293 HOUR)),
(18, 14, 'RESERVATION_NOSHOW', '"크로플 세트 (2개)" 픽업 시간 경과 (노쇼 처리)', '/reservation/my', 'reservation_noshow:18', 1, DATE_SUB(NOW(), INTERVAL 678 HOUR)),
(19, 2, 'REVIEW_REPLY', '내 리뷰에 사장님이 답글을 남겼어요', '/user/reviews/my', 'review_reply:19', 0, DATE_SUB(NOW(), INTERVAL 223 HOUR)),
(20, 14, 'RESERVATION_NOSHOW', '"크루아상 3개입" 픽업 시간 경과 (노쇼 처리)', '/reservation/my', 'reservation_noshow:20', 1, DATE_SUB(NOW(), INTERVAL 63 HOUR)),
(21, 1, 'RESERVATION_NOSHOW', '"크루아상 3개입" 픽업 시간 경과 (노쇼 처리)', '/reservation/my', 'reservation_noshow:21', 0, DATE_SUB(NOW(), INTERVAL 489 HOUR)),
(22, 13, 'RESERVATION_CONFIRMED', '"어제 만든 케이크" 예약 확정', '/reservation/my', 'reservation_confirmed:22', 0, DATE_SUB(NOW(), INTERVAL 872 HOUR)),
(23, 13, 'RESERVATION_CONFIRMED', '"계란말이 + 진미채" 예약 확정', '/reservation/my', 'reservation_confirmed:23', 0, DATE_SUB(NOW(), INTERVAL 692 HOUR)),
(24, 1, 'RESERVATION_NOSHOW', '"샐러드 도시락" 픽업 시간 경과 (노쇼 처리)', '/reservation/my', 'reservation_noshow:24', 0, DATE_SUB(NOW(), INTERVAL 253 HOUR)),
(25, 1, 'LIKE_STORE_OPEN', '찜한 "커피와우 명동점"에서 마감세일이 시작됐어요', '/user/stores/4', 'like_store_open:1:4', 1, DATE_SUB(NOW(), INTERVAL 674 HOUR)),
(26, 2, 'WELCOME_COUPON', '가입을 환영해요! 첫 예약 쿠폰이 발급됐어요', '/mypage', 'welcome_coupon:2', 1, DATE_SUB(NOW(), INTERVAL 734 HOUR)),
(27, 2, 'REVIEW_REPLY', '내 리뷰에 사장님이 답글을 남겼어요', '/user/reviews/my', 'review_reply:27', 0, DATE_SUB(NOW(), INTERVAL 406 HOUR)),
(28, 14, 'REVIEW_REPLY', '내 리뷰에 사장님이 답글을 남겼어요', '/user/reviews/my', 'review_reply:28', 1, DATE_SUB(NOW(), INTERVAL 469 HOUR)),
(29, 1, 'LIKE_STORE_OPEN', '찜한 "다이스키 베이커리"에서 마감세일이 시작됐어요', '/user/stores/1', 'like_store_open:1:1', 0, DATE_SUB(NOW(), INTERVAL 637 HOUR)),
(30, 13, 'RESERVATION_PICKUP_SOON', '"크로플 세트 (2개)" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:30', 1, DATE_SUB(NOW(), INTERVAL 519 HOUR)),
(31, 5, 'RESERVATION_CONFIRMED', '"잡채 한 팩" 예약 확정', '/reservation/my', 'reservation_confirmed:31', 0, DATE_SUB(NOW(), INTERVAL 901 HOUR)),
(32, 5, 'RESERVATION_PICKUP_SOON', '"샐러드 도시락" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:32', 1, DATE_SUB(NOW(), INTERVAL 449 HOUR)),
(33, 15, 'WELCOME_COUPON', '가입을 환영해요! 첫 예약 쿠폰이 발급됐어요', '/mypage', 'welcome_coupon:15', 0, DATE_SUB(NOW(), INTERVAL 542 HOUR)),
(34, 14, 'RESERVATION_NOSHOW', '"크로플 세트 (2개)" 픽업 시간 경과 (노쇼 처리)', '/reservation/my', 'reservation_noshow:34', 0, DATE_SUB(NOW(), INTERVAL 307 HOUR)),
(35, 5, 'RESERVATION_CONFIRMED', '"오늘의 도시락 (랜덤)" 예약 확정', '/reservation/my', 'reservation_confirmed:35', 0, DATE_SUB(NOW(), INTERVAL 912 HOUR)),
(36, 2, 'REVIEW_REPLY', '내 리뷰에 사장님이 답글을 남겼어요', '/user/reviews/my', 'review_reply:36', 0, DATE_SUB(NOW(), INTERVAL 289 HOUR)),
(37, 2, 'WELCOME_COUPON', '가입을 환영해요! 첫 예약 쿠폰이 발급됐어요', '/mypage', 'welcome_coupon:2', 1, DATE_SUB(NOW(), INTERVAL 650 HOUR)),
(38, 13, 'LIKE_STORE_OPEN', '찜한 "다이스키 베이커리"에서 마감세일이 시작됐어요', '/user/stores/1', 'like_store_open:13:1', 0, DATE_SUB(NOW(), INTERVAL 258 HOUR)),
(39, 14, 'LIKE_STORE_OPEN', '찜한 "다이스키 베이커리"에서 마감세일이 시작됐어요', '/user/stores/1', 'like_store_open:14:1', 1, DATE_SUB(NOW(), INTERVAL 850 HOUR)),
(40, 1, 'REVIEW_REPLY', '내 리뷰에 사장님이 답글을 남겼어요', '/user/reviews/my', 'review_reply:40', 0, DATE_SUB(NOW(), INTERVAL 790 HOUR)),
(41, 5, 'RESERVATION_PICKUP_SOON', '"오늘의 도시락 (랜덤)" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:41', 1, DATE_SUB(NOW(), INTERVAL 760 HOUR)),
(42, 14, 'LIKE_STORE_OPEN', '찜한 "다이스키 베이커리"에서 마감세일이 시작됐어요', '/user/stores/1', 'like_store_open:14:1', 0, DATE_SUB(NOW(), INTERVAL 575 HOUR)),
(43, 1, 'WELCOME_COUPON', '가입을 환영해요! 첫 예약 쿠폰이 발급됐어요', '/mypage', 'welcome_coupon:1', 0, DATE_SUB(NOW(), INTERVAL 926 HOUR)),
(44, 13, 'RESERVATION_CONFIRMED', '"돈까스 도시락" 예약 확정', '/reservation/my', 'reservation_confirmed:44', 1, DATE_SUB(NOW(), INTERVAL 855 HOUR)),
(45, 13, 'RESERVATION_PICKUP_SOON', '"크루아상 3개입" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:45', 0, DATE_SUB(NOW(), INTERVAL 441 HOUR)),
(46, 5, 'REVIEW_REPLY', '내 리뷰에 사장님이 답글을 남겼어요', '/user/reviews/my', 'review_reply:46', 0, DATE_SUB(NOW(), INTERVAL 921 HOUR)),
(47, 2, 'WELCOME_COUPON', '가입을 환영해요! 첫 예약 쿠폰이 발급됐어요', '/mypage', 'welcome_coupon:2', 0, DATE_SUB(NOW(), INTERVAL 256 HOUR)),
(48, 5, 'RESERVATION_NOSHOW', '"샐러드 도시락" 픽업 시간 경과 (노쇼 처리)', '/reservation/my', 'reservation_noshow:48', 1, DATE_SUB(NOW(), INTERVAL 906 HOUR)),
(49, 14, 'RESERVATION_PICKUP_SOON', '"오늘의 샌드위치" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:49', 0, DATE_SUB(NOW(), INTERVAL 948 HOUR)),
(50, 15, 'RESERVATION_PICKUP_SOON', '"식빵 마감세트" 픽업 시간이 다가와요', '/reservation/my', 'reservation_pickup_soon:50', 1, DATE_SUB(NOW(), INTERVAL 903 HOUR));

-- ---------------------------------------------------------------------
-- user_badge (12건 추가)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO user_badge (id, user_id, badge_code, earned_at, notified) VALUES
(9, 2, 'RESCUE_1', DATE_SUB(NOW(), INTERVAL 400 HOUR), 1),
(10, 2, 'RESCUE_5', DATE_SUB(NOW(), INTERVAL 60 HOUR), 1),
(11, 5, 'RESCUE_1', DATE_SUB(NOW(), INTERVAL 900 HOUR), 1),
(12, 5, 'RESCUE_5', DATE_SUB(NOW(), INTERVAL 300 HOUR), 1),
(13, 1, 'RESCUE_20', DATE_SUB(NOW(), INTERVAL 100 HOUR), 1),
(14, 13, 'RESCUE_5', DATE_SUB(NOW(), INTERVAL 500 HOUR), 1),
(15, 13, 'SAVE_50K', DATE_SUB(NOW(), INTERVAL 200 HOUR), 1),
(16, 14, 'RESCUE_5', DATE_SUB(NOW(), INTERVAL 700 HOUR), 1),
(17, 15, 'RESCUE_5', DATE_SUB(NOW(), INTERVAL 600 HOUR), 1),
(18, 12, 'RESCUE_5', DATE_SUB(NOW(), INTERVAL 400 HOUR), 1),
(19, 1, 'SAVE_50K', DATE_SUB(NOW(), INTERVAL 50 HOUR), 1),
(20, 5, 'SAVE_10K', DATE_SUB(NOW(), INTERVAL 150 HOUR), 1);

-- ---------------------------------------------------------------------
-- inquiry / inquiry_comment (12건 + 6건 추가)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO inquiry (id, user_id, store_id, title, content, created_at) VALUES
(5, 3, 1, '정산 지급일이 지났는데 아직 안 들어왔어요', '이번 주 정산 지급 예정일이 지났는데 입금이 안 된 것 같아요. 확인 부탁드립니다.', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(6, 6, 3, '메뉴판 사진이 안 올라가요', '메뉴 등록 화면에서 이미지 업로드가 계속 실패해요.', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(7, 7, 4, '쿠폰 발행 수량을 늘릴 수 있나요', '매장 지정 쿠폰 발행 수량 상한을 늘려줄 수 있는지 문의합니다.', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(8, 8, 5, '영업시간 수정이 반영 안 돼요', '영업시간을 수정했는데 화면에 예전 시간이 그대로 보여요.', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(9, 2, NULL, '비밀번호를 잊어버렸어요', '비밀번호 재설정 메일이 안 와요.', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(10, 12, NULL, '쿠폰이 왜 자동 적용 안 되나요', '가지고 있는 쿠폰이 체크아웃에서 자동으로 안 보여요.', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(11, 9, 6, 'POS 연동 재고가 안 맞아요', 'POS에서 올려준 재고 수량과 앱에 보이는 수량이 달라요.', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(12, 10, 7, '리뷰 답글 작성이 안 돼요', '리뷰 답글을 쓰려고 하면 오류가 나요.', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(13, 13, NULL, '절약 목표를 수정하고 싶어요', '절약 목표 금액을 바꾸는 방법을 모르겠어요.', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(14, 11, 8, '자동 할인율 기준이 궁금해요', '마감시간 기준 자동 할인율이 몇 %인지 알려주세요.', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(15, 14, NULL, '알림이 너무 많이 와요', '알림 설정을 끄는 방법이 있나요.', DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(16, 15, NULL, '탈퇴하고 싶어요', '회원 탈퇴 절차를 알려주세요.', DATE_SUB(NOW(), INTERVAL 10 HOUR));

INSERT IGNORE INTO inquiry_comment (id, inquiry_id, user_id, content, created_at) VALUES
(3, 5, 4, '확인해보니 영업일 계산 오류였습니다. 오늘 중 입금 처리하겠습니다.', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(4, 6, 4, '이미지 용량을 5MB 이하로 줄여서 다시 시도해주세요.', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(5, 8, 4, '캐시 문제였습니다. 새로고침 후 확인해주세요.', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(6, 9, 4, 'POS 동기화 주기를 확인했습니다. 다음 동기화부터 반영됩니다.', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(7, 11, 4, '마감 30분 전 20%, 10분 전 30%, 마감 시 50% 자동 적용돼요.', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8, 16, 4, '마이페이지 > 회원정보 수정에서 탈퇴를 진행할 수 있어요.', DATE_SUB(NOW(), INTERVAL 9 HOUR));

-- ---------------------------------------------------------------------
-- notice (5건 추가)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO notice (id, title, content, published, created_at, updated_at) VALUES
(3, '결제 시스템 점검 안내', '10/10(토) 02:00~04:00 결제 시스템 점검이 진행됩니다. 해당 시간엔 예약/결제가 제한됩니다.', 1, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(4, '신규 매장 입점 안내', '경기·부산·대구 지역에 신규 매장이 입점했습니다! 지도에서 확인해보세요.', 1, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(5, '절약 랭킹 이벤트 안내', '이번 달 절약 랭킹 TOP 10에게 쿠폰을 드려요!', 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(6, '초안 공지 (미발행)', '내부 검토 중인 공지사항 초안입니다.', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(7, '개인정보 처리방침 개정 안내', '2026-10-15부터 개정된 개인정보 처리방침이 적용됩니다.', 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ---------------------------------------------------------------------
-- complaint (12건 추가, order_id 없는 일반 신고 — 신고 목록 페이지네이션 테스트용)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO complaint (id, target_name, target_store_id, reason, content, reporter_name, reporter_id, status, admin_reply, created_at, resolved_at) VALUES
(7, '브런치카페 온', 3, '매장 응대', '픽업 갔는데 직원이 불친절했어요.', '야근러강태양', 14, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 15 DAY), NULL),
(8, '커피와우 명동점', 4, '수량 부족', '베이글 2개 세트인데 1개만 왔어요.', '대학생윤서아', 15, 'RESOLVED', '확인 후 매장에 재발방지 안내했습니다.', DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(9, '정성반찬가게', 6, '포장 불량', '반찬 포장이 새서 가방에 국물이 묻었어요.', '자취생박지민', 12, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), NULL),
(10, '든든도시락', 7, '위생 문제', '도시락에서 머리카락이 나왔어요.', '다이어터최유나', 13, 'RESOLVED', '매장에 위생 점검 요청했고 재발방지 서약 받았습니다.', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
(11, '매일도시락 용산점', 8, '노쇼 과다 청구', '제시간에 갔는데 노쇼 처리됐어요.', '구제왕나은', 1, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 11 DAY), NULL),
(12, '다이스키 베이커리', 1, '리뷰 삭제 요청', '제가 쓴 리뷰가 다른 사람 걸로 잘못 노출돼요.', '알뜰소비자김태훈', 2, 'RESOLVED', '확인해보니 캐시 문제였습니다. 수정 완료했습니다.', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(13, '엄마손반찬', 5, '매장 응대', '전화 문의에 응답이 없어요.', '노쇼왕문창호', 5, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NULL),
(14, '브런치카페 온', 3, '가격 불일치', '앱에 표시된 가격과 실제 결제 금액이 달랐어요.', '자취생박지민', 12, 'RESOLVED', '표시 오류 확인 후 차액 환불 처리했습니다.', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(15, '커피와우 명동점', 4, '상품 상태 불량', '스콘이 눅눅했어요.', '야근러강태양', 14, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL),
(16, '정성반찬가게', 6, '매장 응대', '픽업 시간을 어겼는데 사과가 없었어요.', '대학생윤서아', 15, 'RESOLVED', '매장에 안내 완료, 보상 쿠폰 지급했습니다.', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(17, '든든도시락', 7, '수량 부족', '2개 주문했는데 1개만 왔어요.', '다이어터최유나', 13, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), NULL),
(18, '매일도시락 용산점', 8, '위생 문제', '포장 용기가 깨져 있었어요.', '구제왕나은', 1, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- ---------------------------------------------------------------------
-- notification_setting (15건, 회원당 1개)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO notification_setting (id, user_id, push_enabled, like_alert_enabled, updated_at) VALUES
(1, 1, 1, 1, NOW()), (2, 2, 1, 1, NOW()), (3, 3, 1, 1, NOW()), (4, 4, 1, 1, NOW()),
(5, 5, 0, 0, NOW()), (6, 6, 1, 1, NOW()), (7, 7, 1, 1, NOW()), (8, 8, 1, 1, NOW()),
(9, 9, 1, 1, NOW()), (10, 10, 1, 1, NOW()), (11, 11, 1, 1, NOW()), (12, 12, 1, 1, NOW()),
(13, 13, 1, 1, NOW()), (14, 14, 0, 1, NOW()), (15, 15, 1, 0, NOW());

-- ---------------------------------------------------------------------
-- store_announcement (승인 매장 7곳 × 1~2개, 매장당 노출 1개 유지)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO store_announcement (id, store_id, title, content, is_exposed, created_at, updated_at) VALUES
(1, 1, '추석 연휴 임시 휴무 안내', '추석 연휴(9/24~9/27) 휴무입니다. 이후 정상 영업합니다.', 1, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY)),
(2, 1, '예전 공지 (비노출)', '지난 여름 휴무 공지였습니다.', 0, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY)),
(3, 3, '원두 메뉴 일부 변경 안내', '시즌 원두가 변경되어 맛이 조금 달라질 수 있어요.', 1, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
(4, 4, '주차 공간 안내', '매장 전용 주차공간이 없어 인근 공영주차장을 이용해주세요.', 1, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
(5, 5, '명절 반찬 예약 안내', '명절 특선 반찬은 앱에서 미리 예약 부탁드려요.', 1, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(6, 6, '영업시간 변경 안내', '평일 영업 종료 시간이 20:30으로 변경되었습니다.', 1, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(7, 7, '포장 용기 변경 안내', '친환경 종이 용기로 교체했습니다.', 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(8, 8, '신메뉴 출시 안내', '불고기 도시락이 새로 추가됐어요!', 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ---------------------------------------------------------------------
-- menu_item (POS 동기화 원본 — 승인 매장 7곳 × 3개 = 21건)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO menu_item (id, store_id, pos_sku, name, original_price, image_url, stock_quantity, app_sale_enabled, discount_rate, app_sale_quantity, created_at, updated_at) VALUES
(1, 1, 'POS-1-01', '식빵', 6000, '/images/product1.jpg', 6, 1, NULL, 4, NOW(), NOW()),
(2, 1, 'POS-1-02', '크루아상', 9000, '/images/product2.jpg', 0, 0, NULL, NULL, NOW(), NOW()),
(3, 1, 'POS-1-03', '단팥빵', 7000, '/images/product4.jpg', 8, 1, 30, 5, NOW(), NOW()),
(4, 3, 'POS-3-01', '크로플', 8000, '/images/product5.jpg', 3, 1, NULL, 2, NOW(), NOW()),
(5, 3, 'POS-3-02', '샌드위치', 7500, '/images/product6.jpg', 5, 1, 50, 4, NOW(), NOW()),
(6, 3, 'POS-3-03', '원두', 12000, '/images/product7.jpg', 0, 0, NULL, NULL, NOW(), NOW()),
(7, 4, 'POS-4-01', '베이글', 6500, '/images/product8.jpg', 4, 1, NULL, 3, NOW(), NOW()),
(8, 4, 'POS-4-02', '디저트 모음', 11000, '/images/product9.jpg', 2, 1, 50, 1, NOW(), NOW()),
(9, 4, 'POS-4-03', '스콘', 9000, '/images/product10.jpg', 0, 0, NULL, NULL, NOW(), NOW()),
(10, 5, 'POS-5-01', '나물 반찬세트', 12000, '/images/product11.jpg', 4, 1, NULL, 2, NOW(), NOW()),
(11, 5, 'POS-5-02', '잡채', 9000, '/images/product12.jpg', 6, 1, 40, 5, NOW(), NOW()),
(12, 5, 'POS-5-03', '계란말이', 8000, '/images/product13.jpg', 0, 0, NULL, NULL, NOW(), NOW()),
(13, 6, 'POS-6-01', '김치', 10000, '/images/product14.jpg', 5, 1, NULL, 3, NOW(), NOW()),
(14, 6, 'POS-6-02', '멸치볶음 세트', 7000, '/images/product15.jpg', 7, 1, 50, 6, NOW(), NOW()),
(15, 6, 'POS-6-03', '불고기', 14000, '/images/product16.jpg', 0, 0, NULL, NULL, NOW(), NOW()),
(16, 7, 'POS-7-01', '제육볶음 도시락', 8500, '/images/product17.jpg', 6, 1, NULL, 4, NOW(), NOW()),
(17, 7, 'POS-7-02', '랜덤 도시락', 7000, '/images/product18.jpg', 9, 1, 50, 6, NOW(), NOW()),
(18, 7, 'POS-7-03', '돈까스 도시락', 9000, '/images/product19.jpg', 0, 0, NULL, NULL, NOW(), NOW()),
(19, 8, 'POS-8-01', '샐러드 도시락', 8000, '/images/product20.jpg', 4, 1, NULL, 2, NOW(), NOW()),
(20, 8, 'POS-8-02', '김밥', 6000, '/images/product21.jpg', 5, 1, 50, 3, NOW(), NOW()),
(21, 8, 'POS-8-03', '불고기 도시락', 8500, '/images/product22.jpg', 0, 0, NULL, NULL, NOW(), NOW());

-- ---------------------------------------------------------------------
-- listing_template (자동 등록 템플릿 — 승인 매장 7곳 × 2개 = 14건)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO listing_template (id, store_id, name, original_price, image_url, description, default_quantity, weekdays, prompt_time, active, created_at, updated_at) VALUES
(1, 1, '식빵 마감세트', 6000, '/images/product1.jpg', '오늘 구운 식빵 마감 할인', 10, '1,2,3,4,5', '20:30:00', 1, NOW(), NOW()),
(2, 1, '단팥빵 5개입', 7000, '/images/product4.jpg', '팥이 꽉 찬 단팥빵 5개 세트', 8, '1,2,3,4,5,6', '20:30:00', 1, NOW(), NOW()),
(3, 3, '크로플 세트', 8000, '/images/product5.jpg', '바삭한 크로플 2개 + 시럽', 6, '1,2,3,4,5,6,7', '20:00:00', 1, NOW(), NOW()),
(4, 3, '오늘의 샌드위치', 7500, '/images/product6.jpg', '마감 임박 수제 샌드위치', 4, '1,2,3,4,5', '20:00:00', 1, NOW(), NOW()),
(5, 4, '베이글 2개 세트', 6500, '/images/product8.jpg', '플레인/에브리싱 베이글 2개', 7, '1,2,3,4,5,6,7', '21:00:00', 1, NOW(), NOW()),
(6, 4, '디저트 3종 모음', 11000, '/images/product9.jpg', '마감 임박 디저트 3종', 3, '1,2,3,4,5', '21:00:00', 0, NOW(), NOW()),
(7, 5, '나물 반찬세트', 12000, '/images/product11.jpg', '3가지 제철 나물 반찬 세트', 6, '1,2,3,4,5,6', '19:00:00', 1, NOW(), NOW()),
(8, 5, '잡채 한 팩', 9000, '/images/product12.jpg', '당일 조리 잡채 500g', 5, '1,2,3,4,5,6', '19:00:00', 1, NOW(), NOW()),
(9, 6, '김치찌개용 김치', 10000, '/images/product14.jpg', '숙성 배추김치 1kg', 5, '1,2,3,4,5,6', '19:30:00', 1, NOW(), NOW()),
(10, 6, '멸치볶음 + 콩자반', 7000, '/images/product15.jpg', '밑반찬 2종 세트', 6, '1,2,3,4,5,6', '19:30:00', 1, NOW(), NOW()),
(11, 7, '제육볶음 도시락', 8500, '/images/product17.jpg', '오늘의 제육볶음 도시락', 8, '1,2,3,4,5,6,7', '20:00:00', 1, NOW(), NOW()),
(12, 7, '오늘의 도시락 (랜덤)', 7000, '/images/product18.jpg', '남은 반찬으로 구성한 랜덤 도시락', 10, '1,2,3,4,5,6,7', '20:00:00', 1, NOW(), NOW()),
(13, 8, '샐러드 도시락', 8000, '/images/product20.jpg', '건강한 샐러드+닭가슴살 도시락', 5, '1,2,3,4,5,6,7', '20:30:00', 1, NOW(), NOW()),
(14, 8, '김밥 3줄 세트', 6000, '/images/product21.jpg', '당일 마감 김밥 3줄', 6, '1,2,3,4,5,6,7', '20:30:00', 0, NOW(), NOW());

-- ---------------------------------------------------------------------
-- coupon_policy (슈퍼어드민 설정 1행)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO coupon_policy (id, welcome_discount_rate, compensation_discount_rate, updated_at) VALUES
(1, 10, 15, NOW());

-- ---------------------------------------------------------------------
-- user_social_accounts (회원 15명의 소셜 연동 스냅샷 — users 테이블의 oauth_provider/oauth_id 반영)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO user_social_accounts (id, user_id, provider, provider_id, connected_email, connected_at) VALUES
(1, 1, 'google', 'google_1001', 'noeun@example.com', DATE_SUB(NOW(), INTERVAL 60 DAY)),
(2, 2, 'kakao', 'kakao_1001', NULL, DATE_SUB(NOW(), INTERVAL 59 DAY)),
(3, 3, 'google', 'google_1002', 'songchaehyeon@example.com', DATE_SUB(NOW(), INTERVAL 58 DAY)),
(4, 4, 'email', 'admin@girigiri.com', 'admin@girigiri.com', DATE_SUB(NOW(), INTERVAL 57 DAY)),
(5, 5, 'kakao', 'kakao_1002', NULL, DATE_SUB(NOW(), INTERVAL 56 DAY)),
(6, 6, 'kakao', 'kakao_1003', 'brunch.on@example.com', DATE_SUB(NOW(), INTERVAL 55 DAY)),
(7, 7, 'google', 'google_1003', 'coffeewow@example.com', DATE_SUB(NOW(), INTERVAL 54 DAY)),
(8, 8, 'kakao', 'kakao_1004', 'ommaban@example.com', DATE_SUB(NOW(), INTERVAL 53 DAY)),
(9, 9, 'google', 'google_1004', 'jungsungban@example.com', DATE_SUB(NOW(), INTERVAL 52 DAY)),
(10, 10, 'kakao', 'kakao_1005', 'dundunlunch@example.com', DATE_SUB(NOW(), INTERVAL 51 DAY)),
(11, 11, 'google', 'google_1005', 'maeillunch@example.com', DATE_SUB(NOW(), INTERVAL 50 DAY)),
(12, 12, 'kakao', 'kakao_1006', NULL, DATE_SUB(NOW(), INTERVAL 49 DAY)),
(13, 13, 'google', 'google_1006', 'yunachoi@example.com', DATE_SUB(NOW(), INTERVAL 48 DAY)),
(14, 14, 'kakao', 'kakao_1007', NULL, DATE_SUB(NOW(), INTERVAL 47 DAY)),
(15, 15, 'google', 'google_1007', 'seoayoon@example.com', DATE_SUB(NOW(), INTERVAL 46 DAY));

-- ---------------------------------------------------------------------
-- user_archive (탈퇴 회원 2건 — 활성 회원 id와 겹치지 않는 가상 id 사용)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO user_archive (id, original_user_id, oauth_provider, email, phone, joined_at, withdrawn_at, archived_at) VALUES
(1, 201, 'kakao', 'withdrawn1@example.com', NULL, DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY)),
(2, 202, 'google', 'withdrawn2@example.com', NULL, DATE_SUB(NOW(), INTERVAL 150 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY));

-- ---------------------------------------------------------------------
-- settlement (승인 매장 7곳 × 최근 3주 = 21건 — 문창호 WBS 2.0 정산 화면용)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO settlement (id, store_id, period_start, period_end, gross, refund, net_amount, commission_rate, commission, week_amount, carried_in, payout, status, merged_into_id, confirmed_at, scheduled_payout_date, paid_at, transfer_memo, created_at) VALUES
(1, 1, DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 317177, 12171, 305006, 5, 15250, 289756, 0, 289756, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(2, 1, DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 316890, 0, 316890, 5, 15844, 301046, 0, 301046, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(3, 1, DATE_SUB(CURDATE(), INTERVAL 21 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), 176815, 11387, 165428, 5, 8271, 157157, 0, 157157, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(4, 3, DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 177608, 4542, 173066, 5, 8653, 164413, 0, 164413, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(5, 3, DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 239069, 0, 239069, 5, 11953, 227116, 0, 227116, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(6, 3, DATE_SUB(CURDATE(), INTERVAL 21 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), 362430, 0, 362430, 5, 18122, 344308, 0, 344308, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(7, 4, DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 392180, 9490, 382690, 5, 19134, 363556, 0, 363556, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(8, 4, DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 162572, 0, 162572, 5, 8129, 154443, 0, 154443, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(9, 4, DATE_SUB(CURDATE(), INTERVAL 21 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), 357023, 0, 357023, 5, 17851, 339172, 0, 339172, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(10, 5, DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 98692, 0, 98692, 5, 4935, 93757, 0, 93757, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(11, 5, DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 394372, 3492, 390880, 5, 19544, 371336, 0, 371336, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(12, 5, DATE_SUB(CURDATE(), INTERVAL 21 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), 251071, 0, 251071, 5, 12554, 238517, 0, 238517, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(13, 6, DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 352170, 0, 352170, 5, 17608, 334562, 0, 334562, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(14, 6, DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 342025, 0, 342025, 5, 17101, 324924, 0, 324924, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(15, 6, DATE_SUB(CURDATE(), INTERVAL 21 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), 319772, 0, 319772, 5, 15989, 303783, 0, 303783, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(16, 7, DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 293269, 0, 293269, 5, 14663, 278606, 0, 278606, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(17, 7, DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 213166, 0, 213166, 5, 10658, 202508, 0, 202508, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(18, 7, DATE_SUB(CURDATE(), INTERVAL 21 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), 348899, 0, 348899, 5, 17445, 331454, 0, 331454, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(19, 8, DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 116817, 0, 116817, 5, 5841, 110976, 0, 110976, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(20, 8, DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 289924, 0, 289924, 5, 14496, 275428, 0, 275428, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(21, 8, DATE_SUB(CURDATE(), INTERVAL 21 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), 282644, 0, 282644, 5, 14132, 268512, 0, 268512, 'PAID', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 'SAMPLE 이체 메모', DATE_SUB(NOW(), INTERVAL 20 DAY));
