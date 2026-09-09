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

@SpringBootTest
@AutoConfigureMockMvc
class StoreSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("비로그인 사용자가 브라우저(Accept: text/html)로 /store/dashboard 접근 시 /auth/loginForm 으로 302 리다이렉트된다")
	void unauthenticatedUserRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/store/dashboard").header("Accept", "text/html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost/auth/loginForm"));
	}

	@Test
	@WithMockUser(username = "test@example.com", roles = "USER")
	@DisplayName("인증된 사용자는 session.userId가 있을 때 /store/dashboard 정상 접근(200 OK)")
	void authenticatedUserCanAccessStore() throws Exception {
		mockMvc.perform(get("/store/dashboard").sessionAttr("userId", 1L))
				.andExpect(status().isOk());
	}
}
