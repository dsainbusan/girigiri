package net.dsa.girigiri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가됨 (2026-10-06) — 신고 상세(SuperAdminSupportController#complaintDetail)에 "신고자의 최근
 * 주문" 목록을 추가하면서, 템플릿 문법 오류를 컴파일로는 못 잡으므로 실제 렌더링까지 확인한다
 * (SuperAdminDashboardRenderTest와 동일 패턴). sql/sample-data.sql의 complaint id=1(targetStoreId
 * 있음, targetReservationId 없음, reporterId=2)로 "신고자 최근 주문" 분기를 탄다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuperAdminComplaintDetailRenderTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("targetReservationId 없는 신고(신고자 최근 주문 목록 분기)가 정상 렌더링된다")
	void complaintDetailWithoutLinkedReservationRenders() throws Exception {
		mockMvc.perform(get("/superadmin/complaints/1")
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}
}
