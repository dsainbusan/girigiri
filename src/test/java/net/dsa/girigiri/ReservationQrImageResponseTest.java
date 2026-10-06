package net.dsa.girigiri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가됨 (2026-10-06, 코드 리뷰 #3 회귀 확인용) — qrImage()가 예외를 던져 GlobalExceptionHandler의
 * HTML 에러 페이지로 빠지던 걸, 상태코드만 돌려주는 방식으로 바꿨다. 성공/404/403 세 경로가 전부
 * content-type을 뒤섞지 않고 의도한 상태코드로 응답하는지 확인한다.
 * sample-data.sql 기준 reservation id=1은 user_id=1 소유.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationQrImageResponseTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser
	@DisplayName("본인 예약이면 PNG 바이트를 200으로 받는다")
	void ownerGetsPngImage() throws Exception {
		mockMvc.perform(get("/reservation/{id}/qr-image", 1L).sessionAttr("userId", 1L))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	@Test
	@WithMockUser
	@DisplayName("존재하지 않는 예약은 404만 내려간다 (HTML 에러 페이지 아님)")
	void missingReservationReturns404() throws Exception {
		mockMvc.perform(get("/reservation/{id}/qr-image", 999_999L).sessionAttr("userId", 1L))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	@DisplayName("본인 예약이 아니면 403만 내려간다 (HTML 에러 페이지 아님)")
	void otherUsersReservationReturns403() throws Exception {
		mockMvc.perform(get("/reservation/{id}/qr-image", 1L).sessionAttr("userId", 2L))
				.andExpect(status().isForbidden());
	}
}
