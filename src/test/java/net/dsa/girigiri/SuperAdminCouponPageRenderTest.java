package net.dsa.girigiri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쿠폰 캠페인 관리(/superadmin/coupons) 화면 개편(2026-10-06) 렌더링 확인 — 템플릿 표현식 오류는 컴파일로는
 * 못 잡고, 렌더링 도중 터지면 응답이 중간에 끊겨 화면이 멈춘 것처럼 보이기 때문에 실제로 그려본다.
 * (SuperAdminComplaintDetailRenderTest와 동일 패턴. 캠페인이 0건이어도 빈 상태 화면이 그려져야 한다.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuperAdminCouponPageRenderTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("쿠폰 캠페인 관리 화면이 정상 렌더링되고 지역 지정 쿠폰 발행 진입점이 있다")
	void couponPageRenders() throws Exception {
		mockMvc.perform(get("/superadmin/coupons")
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("지역 지정 쿠폰 발행")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("자동 발급 쿠폰 할인율")));
	}
}
