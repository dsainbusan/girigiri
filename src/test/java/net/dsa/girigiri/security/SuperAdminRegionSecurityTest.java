package net.dsa.girigiri.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지역→매장 드릴다운(SuperAdminRegionController) 권한 체크 — 2026-10-01 신규.
 * SuperAdminAccessInterceptor가 /superadmin/** 전체에 role=ADMIN을 강제하는지 확인한다
 * (StoreSecurityTest와 동일 패턴).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuperAdminRegionSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("비로그인 사용자는 /superadmin/regions/서울/stores 접근 시 로그인 화면으로 리다이렉트된다")
	void unauthenticatedUserRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/superadmin/regions/서울/stores").header("Accept", "text/html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost/auth/loginForm"));
	}

	@Test
	@WithMockUser(username = "user@example.com", roles = "USER")
	@DisplayName("role=USER로 로그인한 사용자는 /superadmin/regions/서울/stores에서 403을 받는다")
	void nonAdminUserForbidden() throws Exception {
		mockMvc.perform(get("/superadmin/regions/서울/stores")
						.sessionAttr("userId", 1L)
						.sessionAttr("role", "USER"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("role=ADMIN은 /superadmin/regions/서울/stores에 정상 접근(200 OK)한다")
	void adminCanAccess() throws Exception {
		mockMvc.perform(get("/superadmin/regions/서울/stores")
						.sessionAttr("userId", 4L)
						.sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}
}
