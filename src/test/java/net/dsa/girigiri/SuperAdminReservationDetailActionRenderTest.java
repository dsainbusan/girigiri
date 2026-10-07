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
 * 추가됨 (2026-10-06, 신고 없는 직접 취소/환불 회귀 확인용) — reservationDetail.html에 추가한
 * 취소/환불 액션 섹션이 네 가지 상태(취소 가능/환불 가능/이미 종결/이미 환불완료) 전부 정상
 * 렌더링되는지 확인한다. sample-data.sql 기준: id=1 confirmed(취소 가능), id=2 picked(환불 가능),
 * id=17 cancelled(둘 다 불가), id=27 refunded(환불 완료 배지).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuperAdminReservationDetailActionRenderTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("취소 가능한 주문(confirmed) 상세가 정상 렌더링된다")
	void cancellableOrderRenders() throws Exception {
		mockMvc.perform(get("/superadmin/reservations/{id}", 1L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("환불 가능한 주문(picked) 상세가 정상 렌더링된다")
	void refundableOrderRenders() throws Exception {
		mockMvc.perform(get("/superadmin/reservations/{id}", 2L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("이미 취소된 주문 상세(액션 없음)가 정상 렌더링된다")
	void alreadyCancelledOrderRenders() throws Exception {
		mockMvc.perform(get("/superadmin/reservations/{id}", 17L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("이미 환불 완료된 주문 상세(환불 완료 배지)가 정상 렌더링된다")
	void alreadyRefundedOrderRenders() throws Exception {
		mockMvc.perform(get("/superadmin/reservations/{id}", 27L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}
}
