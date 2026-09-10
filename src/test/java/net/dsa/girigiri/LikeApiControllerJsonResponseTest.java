package net.dsa.girigiri;

import net.dsa.girigiri.controller.api.LikeApiController;
import net.dsa.girigiri.service.LikeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검증(더미데이터) — "LikeApiController(REST)에서 예외가 나면 GlobalExceptionHandler가 뷰 이름
 * 문자열을 그대로 응답 바디로 내려버려서 프론트 fetch의 JSON 파싱이 깨진다" 버그 수정 확인.
 * GlobalExceptionHandler는 아예 등록하지 않은 standalone MockMvc로 컨트롤러만 띄운다 — 즉 여기서
 * 500이 JSON으로 나온다면 그건 GlobalExceptionHandler 없이도(=컨트롤러 자체 try/catch로) 잡혔다는
 * 뜻이라 수정이 제대로 됐는지 가장 정확히 보여준다. LikeService는 목(mock)이라 DB는 안 건드린다.
 */
class LikeApiControllerJsonResponseTest {

	@Test
	void 찜_토글_중_예외가_나면_HTML_뷰이름_대신_JSON_에러로_응답한다() throws Exception {
		LikeService likeService = mock(LikeService.class);
		// 더미데이터: storeId=999는 존재하지 않는 매장이라고 가정 -> 서비스 내부에서 예외 발생 상황 재현
		when(likeService.toggle(anyLong(), anyLong())).thenThrow(new RuntimeException("dummy failure"));

		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LikeApiController(likeService)).build();

		MockHttpSession session = new MockHttpSession();
		session.setAttribute("userId", 1L);

		mockMvc.perform(post("/api/likes/999/toggle").session(session))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.error").value("toggle_failed"))
				// 수정 전 버그였다면 이 자리에 "errorView/custom-error-page" 문자열이 그대로 찍혔다.
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("errorView"))));
	}

	@Test
	void 정상_토글은_liked_값을_JSON으로_돌려준다() throws Exception {
		LikeService likeService = mock(LikeService.class);
		when(likeService.toggle(anyLong(), anyLong())).thenReturn(true);

		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LikeApiController(likeService)).build();

		MockHttpSession session = new MockHttpSession();
		session.setAttribute("userId", 1L);

		mockMvc.perform(post("/api/likes/1/toggle").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true));
	}
}
