package net.dsa.girigiri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가됨 (2026-10-06, 코드 리뷰 #4 회귀 확인용) — 다른 매장 소유 예약을 건드리려는 시도가
 * 이전엔 상태 오류용 커스텀 예외(AcceptNotAllowedException 등)를 거쳐 409 Conflict로 내려갔다
 * (의미상 권한 문제인데 상태 충돌로 표현됨). ResponseStatusException(FORBIDDEN)으로 바꾼 뒤
 * 403으로 정확히 내려가는지 확인한다.
 * sample-data.sql 기준: reservation id=1은 store_id=1(owner_id=3) 소유. user 6은 store 3의
 * 사장님이라 store 1과 무관 — session.userId=6으로 요청하면 전부 소유권 불일치여야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StoreOwnershipForbiddenTest {

	@Autowired
	private MockMvc mockMvc;

	private static final long OTHER_STORE_OWNER_USER_ID = 6L;

	@Test
	@WithMockUser
	@DisplayName("다른 매장 예약 취소 시도는 403")
	void storeCancelOtherStoreReservationIsForbidden() throws Exception {
		mockMvc.perform(post("/reservation/{id}/store-cancel", 1L)
						.sessionAttr("userId", OTHER_STORE_OWNER_USER_ID))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	@DisplayName("다른 매장 예약 수락 시도는 403")
	void acceptOtherStoreReservationIsForbidden() throws Exception {
		mockMvc.perform(post("/reservation/{id}/accept", 1L)
						.sessionAttr("userId", OTHER_STORE_OWNER_USER_ID))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	@DisplayName("다른 매장 예약 노쇼 처리 시도는 403")
	void noShowOtherStoreReservationIsForbidden() throws Exception {
		mockMvc.perform(post("/reservation/{id}/noshow", 1L)
						.sessionAttr("userId", OTHER_STORE_OWNER_USER_ID))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	@DisplayName("다른 매장 픽업 코드로 단건 픽업 시도는 403")
	void pickupOtherStoreReservationIsForbidden() throws Exception {
		mockMvc.perform(post("/reservation/pickup")
						.param("pickupCode", "PICK-1001")
						.sessionAttr("userId", OTHER_STORE_OWNER_USER_ID))
				.andExpect(status().isForbidden());
	}
}
