package net.dsa.girigiri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가됨 (2026-10-07) — storeDetail.html에 넣은 반려/매장 정지 버튼이 상태에 맞게만 보이는지 확인한다.
 * sample-data.sql 기준: store id=1 APPROVED(정지 버튼), id=2 PENDING(승인·반려 버튼, 정지 버튼 없음).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuperAdminStoreDetailActionRenderTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("승인된 매장 상세에는 매장 정지 버튼이 있고 반려 버튼은 없다")
	void approvedStoreShowsSuspend() throws Exception {
		mockMvc.perform(get("/superadmin/stores/{id}", 1L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/superadmin/stores/1/suspend")))
				.andExpect(content().string(not(containsString("/superadmin/stores/1/reject"))));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("대기 매장 상세에는 반려 버튼이 있고 매장 정지 버튼은 없다")
	void pendingStoreShowsReject() throws Exception {
		mockMvc.perform(get("/superadmin/stores/{id}", 2L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/superadmin/stores/2/reject")))
				.andExpect(content().string(not(containsString("/superadmin/stores/2/suspend"))));
	}
}
