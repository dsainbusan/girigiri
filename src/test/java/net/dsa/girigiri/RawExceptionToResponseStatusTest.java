package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.ReservationService;
import net.dsa.girigiri.service.StoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가됨 (2026-10-06, 코드 리뷰 #2 회귀 확인용) — 커스텀 예외 12종으로 표현 안 되는 raw
 * IllegalStateException/IllegalArgumentException을 ResponseStatusException으로 바꾼 두 곳이
 * 의도한 상태코드(400)로 응답하는지 확인한다. 둘 다 전에는 핸들러 없는 예외라
 * GlobalExceptionHandler의 catch-all(Exception)로 떨어져 500 + "알 수 없는 오류가
 * 발생했습니다"만 보여줬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RawExceptionToResponseStatusTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StoreService storeService;

	@Test
	@WithMockUser
	@DisplayName("마이페이지 예약 목록에 알 수 없는 탭을 넘기면 400이 내려간다")
	void unknownTabReturns400() throws Exception {
		mockMvc.perform(get("/reservation/my").param("tab", "garbage")
						.sessionAttr("userId", 1L))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("영업시간 형식이 잘못되면 400(ResponseStatusException)으로 거부된다")
	void invalidOperatingHoursThrowsBadRequest() {
		StoreEntity store = StoreEntity.builder().build();

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> storeService.updateStoreInfo(store, "베이커리", "02-1234-5678", "이상한 형식",
						37.5, 127.0, null, null, null));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
	}
}
