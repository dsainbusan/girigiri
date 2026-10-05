package net.dsa.girigiri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가됨 (2026-10-05) — 통계 대시보드에 일별 현황 달력 + Excel/PDF 리포트를 추가하면서,
 * 템플릿 문법 오류(Thymeleaf SpringEL 파싱 실패 등)를 컴파일로는 못 잡으므로 실제 렌더링까지
 * 확인한다(SuperAdminRegionSecurityTest와 동일 로그인 패턴).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuperAdminDashboardRenderTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("날짜 선택 없이 /superadmin/dashboard는 정상 렌더링된다")
	void dashboardWithoutDateRenders() throws Exception {
		mockMvc.perform(get("/superadmin/dashboard").sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("어제 날짜를 선택하면 일별 현황 패널까지 정상 렌더링된다")
	void dashboardWithYesterdaySelectedRenders() throws Exception {
		String yesterday = LocalDate.now().minusDays(1).toString();
		mockMvc.perform(get("/superadmin/dashboard").param("date", yesterday)
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("일별 현황 Excel 다운로드가 정상 응답한다")
	void dailyExcelDownloadWorks() throws Exception {
		String yesterday = LocalDate.now().minusDays(1).toString();
		mockMvc.perform(get("/superadmin/dashboard/daily/excel").param("date", yesterday)
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("일별 현황 PDF 다운로드가 정상 응답한다")
	void dailyPdfDownloadWorks() throws Exception {
		String yesterday = LocalDate.now().minusDays(1).toString();
		mockMvc.perform(get("/superadmin/dashboard/daily/pdf").param("date", yesterday)
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("미래 날짜로 Excel 다운로드를 요청하면 400을 받는다")
	void dailyExcelRejectsFutureDate() throws Exception {
		String tomorrow = LocalDate.now().plusDays(1).toString();
		mockMvc.perform(get("/superadmin/dashboard/daily/excel").param("date", tomorrow)
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isBadRequest());
	}

	/**
	 * 추가됨 (2026-10-05) — 달력 클릭 시 풀페이지 새로고침 대신 fetch로 받는 부분 렌더링
	 * 엔드포인트(dashboard.html :: dailyReportBody). 풀페이지 렌더링과 같은 모델 데이터를 쓰므로
	 * 숫자가 어긋나지 않는지는 SuperAdminDashboardServiceTest에서, 여기서는 템플릿 조각이
	 * 단독으로도 깨지지 않고 200을 내려주는지만 확인한다.
	 */
	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("일별 현황 프래그먼트(날짜 선택 없음)가 정상 렌더링된다")
	void dailyReportFragmentWithoutDateRenders() throws Exception {
		mockMvc.perform(get("/superadmin/dashboard/daily")
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("일별 현황 프래그먼트(어제 날짜 선택)가 정상 렌더링된다")
	void dailyReportFragmentWithDateRenders() throws Exception {
		String yesterday = LocalDate.now().minusDays(1).toString();
		mockMvc.perform(get("/superadmin/dashboard/daily").param("date", yesterday)
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin@girigiri.com", roles = "ADMIN")
	@DisplayName("일별 현황 프래그먼트(월 이동)가 정상 렌더링된다")
	void dailyReportFragmentWithMonthRenders() throws Exception {
		mockMvc.perform(get("/superadmin/dashboard/daily").param("month", "2026-09")
						.sessionAttr("userId", 4L).sessionAttr("role", "ADMIN"))
				.andExpect(status().isOk());
	}
}
