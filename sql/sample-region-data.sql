-- 지역별 매장 현황(슈퍼어드민 대시보드 "지역별 현황" · /superadmin/regions/{시도}/stores) 확인용
-- "추가 전용" 샘플 데이터 (2026-10-06). 기존 데이터를 지우지 않고 INSERT IGNORE 라 여러 번 실행해도 중복되지 않는다.
-- 전제: users 12~15, 서울 매장 1~8(sample-data.sql 기준)이 있어야 한다.
-- 실행: mysql -u root -p --default-character-set=utf8mb4 girigiri < sql/sample-region-data.sql
--
-- 지역별 결과(최근 7일, 마감 지난 상품 기준 — 표본 5건 미만은 "표본 부족", 소진율 20% 미만은 "점검 필요"):
--   경기 정상(21건 중 11 소진) · 부산 점검 필요(14건 중 2) · 대구 정상(11건 중 7) · 인천 표본 부족(3건)
--   대전 정상(5건 중 3) · 광주 표본 부족(2건) · 제주 정상(6건 중 5) · 강원 등록 0건(표본 부족)
--   충북은 승인 대기(PENDING) 매장뿐이라 "미진출", 나머지 시도(충남·세종·경북·경남·울산·전북·전남)도 미진출.
-- 서울은 기존 8개 매장 중 승인된 7곳이 같은 방식으로 집계된다(store.sido 가 비어 있던 것을 아래에서 채운다).

-- 0. 기존 서울 매장의 시도 값 채우기 (지역 집계는 store.sido 로 묶는다)
UPDATE store SET sido = '서울' WHERE sido IS NULL AND address LIKE '서울%';

-- 1. 지역 매장 사장님 계정
INSERT IGNORE INTO users (id, oauth_provider, oauth_id, role, status, nickname, email, password, region, profile_completed, terms_agreed, privacy_agreed, marketing_agreed, representative_badge, savings_goal_amount, latitude, longitude, created_at, updated_at) VALUES
(21, 'google', 'google_region_21', 'OWNER', 'ACTIVE', '수원 행궁빵집 사장님', 'owner21@example.com', NULL, '경기도', 1, 1, 1, 0, NULL, NULL, 37.2819, 127.016, NOW(), NOW()),
(22, 'google', 'google_region_22', 'OWNER', 'ACTIVE', '성남 모란반찬 사장님', 'owner22@example.com', NULL, '경기도', 1, 1, 1, 0, NULL, NULL, 37.432, 127.129, NOW(), NOW()),
(23, 'google', 'google_region_23', 'OWNER', 'ACTIVE', '고양 일산도시락 사장님', 'owner23@example.com', NULL, '경기도', 1, 1, 1, 0, NULL, NULL, 37.6584, 126.774, NOW(), NOW()),
(24, 'google', 'google_region_24', 'OWNER', 'ACTIVE', '해운대 바다카페 사장님', 'owner24@example.com', NULL, '부산광역시', 1, 1, 1, 0, NULL, NULL, 35.1587, 129.1604, NOW(), NOW()),
(25, 'google', 'google_region_25', 'OWNER', 'ACTIVE', '서면 단팥공방 사장님', 'owner25@example.com', NULL, '부산광역시', 1, 1, 1, 0, NULL, NULL, 35.1578, 129.0594, NOW(), NOW()),
(26, 'google', 'google_region_26', 'OWNER', 'ACTIVE', '광안리 든든도시락 사장님', 'owner26@example.com', NULL, '부산광역시', 1, 1, 1, 0, NULL, NULL, 35.1532, 129.1186, NOW(), NOW()),
(27, 'google', 'google_region_27', 'OWNER', 'ACTIVE', '동성로 할매반찬 사장님', 'owner27@example.com', NULL, '대구광역시', 1, 1, 1, 0, NULL, NULL, 35.8714, 128.5958, NOW(), NOW()),
(28, 'google', 'google_region_28', 'OWNER', 'ACTIVE', '수성 아침빵집 사장님', 'owner28@example.com', NULL, '대구광역시', 1, 1, 1, 0, NULL, NULL, 35.858, 128.63, NOW(), NOW()),
(29, 'google', 'google_region_29', 'OWNER', 'ACTIVE', '부평 한끼도시락 사장님', 'owner29@example.com', NULL, '인천광역시', 1, 1, 1, 0, NULL, NULL, 37.489, 126.724, NOW(), NOW()),
(30, 'google', 'google_region_30', 'OWNER', 'ACTIVE', '둔산 오후의카페 사장님', 'owner30@example.com', NULL, '대전광역시', 1, 1, 1, 0, NULL, NULL, 36.351, 127.378, NOW(), NOW()),
(31, 'google', 'google_region_31', 'OWNER', 'ACTIVE', '상무 정성반찬 사장님', 'owner31@example.com', NULL, '광주광역시', 1, 1, 1, 0, NULL, NULL, 35.153, 126.851, NOW(), NOW()),
(32, 'google', 'google_region_32', 'OWNER', 'ACTIVE', '제주 오름베이커리 사장님', 'owner32@example.com', NULL, '제주특별자치도', 1, 1, 1, 0, NULL, NULL, 33.489, 126.498, NOW(), NOW()),
(33, 'google', 'google_region_33', 'OWNER', 'ACTIVE', '춘천 닭갈비도시락 사장님', 'owner33@example.com', NULL, '강원특별자치도', 1, 1, 1, 0, NULL, NULL, 37.8813, 127.7298, NOW(), NOW()),
(34, 'google', 'google_region_34', 'OWNER', 'ACTIVE', '청주 아침카페 사장님', 'owner34@example.com', NULL, '충청북도', 1, 1, 1, 0, NULL, NULL, 36.635, 127.489, NOW(), NOW());

-- 2. 지역 매장 (승인 13곳 + 승인 대기 1곳)
INSERT IGNORE INTO store (id, store_name, category, address, latitude, longitude, operating_hours, phone, business_number, role, owner_id, approval_status, reliability_suspension_count, reliability_banned, image_url, created_at, updated_at, sido) VALUES
(11, '수원 행궁빵집', '베이커리', '경기도 수원시 팔달구 행궁로 25', 37.2819, 127.016, '08:00 ~ 21:00', '011-000-0011', '911-11-00011', 'OWNER', 21, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-11/600/400', NOW(), NOW(), '경기'),
(12, '성남 모란반찬', '반찬', '경기도 성남시 중원구 성남대로 1100', 37.432, 127.129, '10:00 ~ 20:00', '012-000-0012', '912-11-00012', 'OWNER', 22, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-12/600/400', NOW(), NOW(), '경기'),
(13, '고양 일산도시락', '도시락', '경기도 고양시 일산동구 중앙로 1200', 37.6584, 126.774, '10:00 ~ 21:00', '013-000-0013', '913-11-00013', 'OWNER', 23, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-13/600/400', NOW(), NOW(), '경기'),
(14, '해운대 바다카페', '카페', '부산광역시 해운대구 해운대해변로 264', 35.1587, 129.1604, '09:00 ~ 22:00', '014-000-0014', '914-11-00014', 'OWNER', 24, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-14/600/400', NOW(), NOW(), '부산'),
(15, '서면 단팥공방', '베이커리', '부산광역시 부산진구 서면로 68', 35.1578, 129.0594, '08:30 ~ 21:00', '015-000-0015', '915-11-00015', 'OWNER', 25, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-15/600/400', NOW(), NOW(), '부산'),
(16, '광안리 든든도시락', '도시락', '부산광역시 수영구 광안해변로 219', 35.1532, 129.1186, '10:00 ~ 21:30', '016-000-0016', '916-11-00016', 'OWNER', 26, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-16/600/400', NOW(), NOW(), '부산'),
(17, '동성로 할매반찬', '반찬', '대구광역시 중구 동성로 30', 35.8714, 128.5958, '09:30 ~ 20:30', '017-000-0017', '917-11-00017', 'OWNER', 27, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-17/600/400', NOW(), NOW(), '대구'),
(18, '수성 아침빵집', '베이커리', '대구광역시 수성구 달구벌대로 2500', 35.858, 128.63, '07:30 ~ 21:00', '018-000-0018', '918-11-00018', 'OWNER', 28, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-18/600/400', NOW(), NOW(), '대구'),
(19, '부평 한끼도시락', '도시락', '인천광역시 부평구 부평대로 100', 37.489, 126.724, '10:00 ~ 21:00', '019-000-0019', '919-11-00019', 'OWNER', 29, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-19/600/400', NOW(), NOW(), '인천'),
(20, '둔산 오후의카페', '카페', '대전광역시 서구 둔산로 100', 36.351, 127.378, '09:00 ~ 22:00', '020-000-0020', '920-11-00020', 'OWNER', 30, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-20/600/400', NOW(), NOW(), '대전'),
(21, '상무 정성반찬', '반찬', '광주광역시 서구 상무중앙로 110', 35.153, 126.851, '10:00 ~ 20:00', '021-000-0021', '921-11-00021', 'OWNER', 31, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-21/600/400', NOW(), NOW(), '광주'),
(22, '제주 오름베이커리', '베이커리', '제주특별자치도 제주시 연동 300', 33.489, 126.498, '08:00 ~ 20:00', '022-000-0022', '922-11-00022', 'OWNER', 32, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-22/600/400', NOW(), NOW(), '제주'),
(23, '춘천 닭갈비도시락', '도시락', '강원특별자치도 춘천시 중앙로 50', 37.8813, 127.7298, '10:00 ~ 20:30', '023-000-0023', '923-11-00023', 'OWNER', 33, 'APPROVED', 0, 0, 'https://picsum.photos/seed/girigiri-store-23/600/400', NOW(), NOW(), '강원'),
(24, '청주 아침카페', '카페', '충청북도 청주시 상당구 상당로 80', 36.635, 127.489, '08:00 ~ 21:00', '024-000-0024', '924-11-00024', 'OWNER', 34, 'PENDING', 0, 0, 'https://picsum.photos/seed/girigiri-store-24/600/400', NOW(), NOW(), '충북');

-- 3. 상품: 최근 1~6일 전에 등록된 마감 지난 상품 (sold=판매, expired=폐기)
INSERT IGNORE INTO product (id, store_id, name, original_price, discounted_price, quantity, remaining_quantity, image_url, description, status, registered_at) VALUES
(101, 11, '식빵 마감세트', 6000, 3000, 5, 0, '/images/product1.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(102, 11, '크루아상 3개입', 9000, 4500, 5, 0, '/images/product1.jpg', '크루아상 3개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(103, 11, '소보로빵 4개', 7000, 3500, 5, 0, '/images/product1.jpg', '소보로빵 4개 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(104, 11, '단팥빵 5개입', 8400, 4200, 5, 0, '/images/product1.jpg', '단팥빵 5개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(105, 11, '식빵 마감세트', 6000, 3000, 5, 0, '/images/product1.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(106, 11, '크루아상 3개입', 9000, 4500, 5, 0, '/images/product1.jpg', '크루아상 3개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(107, 11, '소보로빵 4개', 7000, 3500, 5, 2, '/images/product1.jpg', '소보로빵 4개 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(108, 11, '단팥빵 5개입', 8400, 4200, 5, 2, '/images/product1.jpg', '단팥빵 5개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(109, 12, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/product1.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(110, 12, '잡채 한 팩', 9000, 5400, 5, 0, '/images/product1.jpg', '잡채 한 팩 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(111, 12, '멸치볶음 + 콩자반', 5000, 3000, 5, 0, '/images/product1.jpg', '멸치볶음 + 콩자반 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(112, 12, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/product1.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(113, 12, '잡채 한 팩', 9000, 5400, 5, 2, '/images/product1.jpg', '잡채 한 팩 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(114, 12, '멸치볶음 + 콩자반', 5000, 3000, 5, 2, '/images/product1.jpg', '멸치볶음 + 콩자반 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(115, 12, '오늘의 나물 반찬세트', 9000, 5400, 5, 2, '/images/product1.jpg', '오늘의 나물 반찬세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(116, 13, '제육볶음 도시락', 6500, 4000, 5, 0, '/images/product1.jpg', '제육볶음 도시락 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(117, 13, '오늘의 도시락', 7000, 3500, 5, 2, '/images/product1.jpg', '오늘의 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(118, 13, '김밥 3줄 세트', 6000, 3000, 5, 2, '/images/product1.jpg', '김밥 3줄 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(119, 13, '제육볶음 도시락', 6500, 4000, 5, 2, '/images/product1.jpg', '제육볶음 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(120, 13, '오늘의 도시락', 7000, 3500, 5, 2, '/images/product1.jpg', '오늘의 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(121, 13, '김밥 3줄 세트', 6000, 3000, 5, 2, '/images/product1.jpg', '김밥 3줄 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(122, 14, '케이크 조각 세트', 12000, 6000, 5, 0, '/images/product1.jpg', '케이크 조각 세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(123, 14, '샌드위치 세트', 7500, 3750, 5, 0, '/images/product1.jpg', '샌드위치 세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(124, 14, '쿠키 모음', 6000, 3000, 5, 2, '/images/product1.jpg', '쿠키 모음 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(125, 14, '케이크 조각 세트', 12000, 6000, 5, 2, '/images/product1.jpg', '케이크 조각 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(126, 14, '샌드위치 세트', 7500, 3750, 5, 2, '/images/product1.jpg', '샌드위치 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(127, 14, '쿠키 모음', 6000, 3000, 5, 2, '/images/product1.jpg', '쿠키 모음 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(128, 15, '식빵 마감세트', 6000, 3000, 5, 2, '/images/product1.jpg', '식빵 마감세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(129, 15, '크루아상 3개입', 9000, 4500, 5, 2, '/images/product1.jpg', '크루아상 3개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(130, 15, '소보로빵 4개', 7000, 3500, 5, 2, '/images/product1.jpg', '소보로빵 4개 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(131, 15, '단팥빵 5개입', 8400, 4200, 5, 2, '/images/product1.jpg', '단팥빵 5개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(132, 15, '식빵 마감세트', 6000, 3000, 5, 2, '/images/product1.jpg', '식빵 마감세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(133, 16, '제육볶음 도시락', 6500, 4000, 5, 2, '/images/product1.jpg', '제육볶음 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(134, 16, '오늘의 도시락', 7000, 3500, 5, 2, '/images/product1.jpg', '오늘의 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(135, 16, '김밥 3줄 세트', 6000, 3000, 5, 2, '/images/product1.jpg', '김밥 3줄 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(136, 17, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/product1.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(137, 17, '잡채 한 팩', 9000, 5400, 5, 0, '/images/product1.jpg', '잡채 한 팩 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(138, 17, '멸치볶음 + 콩자반', 5000, 3000, 5, 0, '/images/product1.jpg', '멸치볶음 + 콩자반 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(139, 17, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/product1.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(140, 17, '잡채 한 팩', 9000, 5400, 5, 2, '/images/product1.jpg', '잡채 한 팩 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(141, 17, '멸치볶음 + 콩자반', 5000, 3000, 5, 2, '/images/product1.jpg', '멸치볶음 + 콩자반 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(142, 18, '식빵 마감세트', 6000, 3000, 5, 0, '/images/product1.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(143, 18, '크루아상 3개입', 9000, 4500, 5, 0, '/images/product1.jpg', '크루아상 3개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(144, 18, '소보로빵 4개', 7000, 3500, 5, 0, '/images/product1.jpg', '소보로빵 4개 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(145, 18, '단팥빵 5개입', 8400, 4200, 5, 2, '/images/product1.jpg', '단팥빵 5개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(146, 18, '식빵 마감세트', 6000, 3000, 5, 2, '/images/product1.jpg', '식빵 마감세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(147, 19, '제육볶음 도시락', 6500, 4000, 5, 0, '/images/product1.jpg', '제육볶음 도시락 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(148, 19, '오늘의 도시락', 7000, 3500, 5, 2, '/images/product1.jpg', '오늘의 도시락 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(149, 19, '김밥 3줄 세트', 6000, 3000, 5, 2, '/images/product1.jpg', '김밥 3줄 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(150, 20, '케이크 조각 세트', 12000, 6000, 5, 0, '/images/product1.jpg', '케이크 조각 세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(151, 20, '샌드위치 세트', 7500, 3750, 5, 0, '/images/product1.jpg', '샌드위치 세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(152, 20, '쿠키 모음', 6000, 3000, 5, 0, '/images/product1.jpg', '쿠키 모음 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(153, 20, '케이크 조각 세트', 12000, 6000, 5, 2, '/images/product1.jpg', '케이크 조각 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(154, 20, '샌드위치 세트', 7500, 3750, 5, 2, '/images/product1.jpg', '샌드위치 세트 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(155, 21, '오늘의 나물 반찬세트', 9000, 5400, 5, 0, '/images/product1.jpg', '오늘의 나물 반찬세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(156, 21, '잡채 한 팩', 9000, 5400, 5, 0, '/images/product1.jpg', '잡채 한 팩 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(157, 22, '식빵 마감세트', 6000, 3000, 5, 0, '/images/product1.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(158, 22, '크루아상 3개입', 9000, 4500, 5, 0, '/images/product1.jpg', '크루아상 3개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(159, 22, '소보로빵 4개', 7000, 3500, 5, 0, '/images/product1.jpg', '소보로빵 4개 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(160, 22, '단팥빵 5개입', 8400, 4200, 5, 0, '/images/product1.jpg', '단팥빵 5개입 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(161, 22, '식빵 마감세트', 6000, 3000, 5, 0, '/images/product1.jpg', '식빵 마감세트 마감 할인', 'sold', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(162, 22, '크루아상 3개입', 9000, 4500, 5, 2, '/images/product1.jpg', '크루아상 3개입 마감 할인', 'expired', DATE_SUB(NOW(), INTERVAL 6 DAY));

-- 4. 판매된 상품의 픽업완료 예약 (대시보드 지역표는 picked 예약이 있는 상품을 '판매'로 센다)
INSERT IGNORE INTO reservation (id, user_id, product_id, product_name, store_id, reserved_quantity, total_price, pickup_time, pickup_code, status, reserved_at, picked_at, accepted_at, cancel_reason, cancelled_by) VALUES
(101, 13, 101, '식빵 마감세트', 11, 1, 3000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-101', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(102, 14, 102, '크루아상 3개입', 11, 1, 4500, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-102', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(103, 15, 103, '소보로빵 4개', 11, 1, 3500, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-103', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(104, 1, 104, '단팥빵 5개입', 11, 1, 4200, DATE_SUB(NOW(), INTERVAL 4 DAY), 'REG-104', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(105, 12, 105, '식빵 마감세트', 11, 1, 3000, DATE_SUB(NOW(), INTERVAL 5 DAY), 'REG-105', 'picked', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL),
(106, 13, 106, '크루아상 3개입', 11, 1, 4500, DATE_SUB(NOW(), INTERVAL 6 DAY), 'REG-106', 'picked', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL),
(107, 14, 109, '오늘의 나물 반찬세트', 12, 1, 5400, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-107', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(108, 15, 110, '잡채 한 팩', 12, 1, 5400, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-108', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(109, 1, 111, '멸치볶음 + 콩자반', 12, 1, 3000, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-109', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(110, 12, 112, '오늘의 나물 반찬세트', 12, 1, 5400, DATE_SUB(NOW(), INTERVAL 4 DAY), 'REG-110', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(111, 13, 116, '제육볶음 도시락', 13, 1, 4000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-111', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(112, 14, 122, '케이크 조각 세트', 14, 1, 6000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-112', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(113, 15, 123, '샌드위치 세트', 14, 1, 3750, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-113', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(114, 1, 136, '오늘의 나물 반찬세트', 17, 1, 5400, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-114', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(115, 12, 137, '잡채 한 팩', 17, 1, 5400, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-115', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(116, 13, 138, '멸치볶음 + 콩자반', 17, 1, 3000, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-116', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(117, 14, 139, '오늘의 나물 반찬세트', 17, 1, 5400, DATE_SUB(NOW(), INTERVAL 4 DAY), 'REG-117', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(118, 15, 142, '식빵 마감세트', 18, 1, 3000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-118', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(119, 1, 143, '크루아상 3개입', 18, 1, 4500, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-119', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(120, 12, 144, '소보로빵 4개', 18, 1, 3500, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-120', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(121, 13, 147, '제육볶음 도시락', 19, 1, 4000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-121', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(122, 14, 150, '케이크 조각 세트', 20, 1, 6000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-122', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(123, 15, 151, '샌드위치 세트', 20, 1, 3750, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-123', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(124, 1, 152, '쿠키 모음', 20, 1, 3000, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-124', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(125, 12, 155, '오늘의 나물 반찬세트', 21, 1, 5400, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-125', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(126, 13, 156, '잡채 한 팩', 21, 1, 5400, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-126', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(127, 14, 157, '식빵 마감세트', 22, 1, 3000, DATE_SUB(NOW(), INTERVAL 1 DAY), 'REG-127', 'picked', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL),
(128, 15, 158, '크루아상 3개입', 22, 1, 4500, DATE_SUB(NOW(), INTERVAL 2 DAY), 'REG-128', 'picked', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL),
(129, 1, 159, '소보로빵 4개', 22, 1, 3500, DATE_SUB(NOW(), INTERVAL 3 DAY), 'REG-129', 'picked', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL),
(130, 12, 160, '단팥빵 5개입', 22, 1, 4200, DATE_SUB(NOW(), INTERVAL 4 DAY), 'REG-130', 'picked', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL),
(131, 13, 161, '식빵 마감세트', 22, 1, 3000, DATE_SUB(NOW(), INTERVAL 5 DAY), 'REG-131', 'picked', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL);
