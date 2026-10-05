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
 * 추가됨 (2026-10-06, 신고 기반 리팩터링) — myReservations.html에 th:if/th:unless로 추가한
 * reportBlockedMessage 분기(ReservationListItemDto, ReservationService#toListItemDto)가 템플릿
 * 문법 오류 없이 렌더링되는지 확인한다. user id=2(sample-data.sql, reservation id=2 picked 소유자)의
 * 픽업완료 탭을 확인하면 [신고하기] 버튼 분기와 "이미 신고 접수됨"/시간초과 분기 중 하나를 반드시 탄다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MyReservationsReportButtonRenderTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(username = "user2@girigiri.com")
	@DisplayName("픽업완료 탭(신고 가능 여부 분기 포함)이 정상 렌더링된다")
	void pickedTabRenders() throws Exception {
		mockMvc.perform(get("/reservation/my").param("tab", "picked")
						.sessionAttr("userId", 2L))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "user1@girigiri.com")
	@DisplayName("진행중 탭이 정상 렌더링된다")
	void progressTabRenders() throws Exception {
		mockMvc.perform(get("/reservation/my").param("tab", "progress")
						.sessionAttr("userId", 1L))
				.andExpect(status().isOk());
	}
}
