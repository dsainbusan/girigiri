package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가됨 (2026-10-06) — 신고 상세(SuperAdminSupportController#complaintDetail) 템플릿 문법 오류를
 * 컴파일로는 못 잡으므로 실제 렌더링까지 확인한다(SuperAdminDashboardRenderTest와 동일 패턴).
 *
 * 수정됨 (2026-10-06, 신고 기반 리팩터링) — order_id(targetReservationId) 없는 신고의 "신고자 최근
 * 주문" 보조 목록은 제거됐다(order_id가 항상 채워지는 새 흐름에선 불필요). 이제 이 케이스는 접힌
 * 픽업 코드 검색(<details>)만 렌더링되는지 확인한다. sql/sample-data.sql의 complaint id=1
 * (targetStoreId 있음, targetReservationId 없음, reporterId=2)로 그 분기를 탄다.
 *
 * 추가됨 (2026-10-06, 신고 기반 리팩터링) — "신고 대상 주문" 카드(order_id 있음) 분기도 같이 확인한다.
 * sample-data.sql엔 payment 행이 하나도 없어서(결제 연동 이전 데이터) 환불 액션 자체(POST)는 이
 * 테스트에서 실행하지 않고 GET 렌더링만 확인한다 — reservation id=2가 picked 상태라 "환불 처리"
 * 버튼까지 보이는 분기를 탄다. @Transactional로 테스트가 끝나면 삽입한 신고는 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SuperAdminComplaintDetailRenderTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComplaintRepository complaintRepository;

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("targetReservationId 없는 신고(접힌 픽업 코드 검색 분기)가 정상 렌더링된다")
	void complaintDetailWithoutLinkedReservationRenders() throws Exception {
		mockMvc.perform(get("/superadmin/complaints/1")
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("targetReservationId 있는 신고(신고 대상 주문 카드 + 환불 버튼 분기)가 정상 렌더링된다")
	void complaintDetailWithLinkedReservationRenders() throws Exception {
		ComplaintEntity complaint = ComplaintEntity.builder()
				.targetName("다이스키 베이커리")
				.targetStoreId(1L)
				.targetReservationId(2L)   // sample-data.sql: picked 상태 예약
				.reason("상품 상태 불량")
				.content("테스트용 신고")
				.reporterName("테스트유저")
				.reporterId(2L)
				.build();
		Long id = complaintRepository.save(complaint).getId();

		mockMvc.perform(get("/superadmin/complaints/{id}", id)
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}
}
