package net.dsa.girigiri;

import net.dsa.girigiri.domain.dto.SalesReportData;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.SalesReportService;
import net.dsa.girigiri.service.StoreAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SalesReportViewTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StoreAccessService storeAccessService;

	@MockitoBean
	private SalesReportService salesReportService;

	private StoreEntity mockStore;

	@BeforeEach
	void setUp() {
		mockStore = new StoreEntity();
		mockStore.setId(1L);
		mockStore.setStoreName("창호베이커리 중앙점");
		when(storeAccessService.findMyStore(anyLong())).thenReturn(Optional.of(mockStore));
	}

	@Test
	@WithMockUser(username = "test@example.com", roles = "USER")
	@DisplayName("A안(히어로 요약형) 매출 리포트 화면이 정상 렌더링되고 모든 핵심 컴포넌트가 포함되어 있다")
	void salesReportViewRendersAProposalComponents() throws Exception {
		SalesReportData.DayLine day1 = new SalesReportData.DayLine("09/18", true, 96000L, 12, 3, 60, 20);
		SalesReportData.ProductLine prod1 = new SalesReportData.ProductLine("단팥빵", "베이커리", 30, 25, 5, 50000L, 83);

		SalesReportData mockReport = new SalesReportData(
				"창호베이커리 중앙점",
				"2026-09-15 ~ 2026-09-21 (지난 주)",
				"lastweek",
				"2026-09-15",
				"2026-09-21",
				true,
				false,
				1240000L,
				92000L,
				280000L,
				27,
				107,
				88,
				19,
				82,
				5.5,
				List.of(day1),
				List.of(prod1)
		);

		when(salesReportService.isConfigured()).thenReturn(true);
		when(salesReportService.build(any(), any(), any(), any())).thenReturn(mockReport);

		MvcResult result = mockMvc.perform(get("/store/sales-report").sessionAttr("userId", 1L))
				.andExpect(status().isOk())
				.andReturn();

		String html = result.getResponse().getContentAsString();

		// 1. 페이지 및 히어로 영역 검증
		assertThat(html).contains("page--sales-report");
		assertThat(html).contains("dash-hero");
		assertThat(html).contains("창호베이커리 중앙점 · 2026-09-15 ~ 2026-09-21 (지난 주)");
		assertThat(html).contains("회수 매출");
		assertThat(html).contains("1,240,000원");
		// 전주 대비 배지 검증: 전주(280,000원) 대비 ▲ +27%
		assertThat(html).contains("sales-delta-badge");
		assertThat(html).contains("전주(<span>280,000</span>원) 대비");
		assertThat(html).contains("▲");
		assertThat(html).contains("+27%");
		// 2분할 스탯 바 검증 (판매 수량 88개 / 구제율 82%)
		assertThat(html).contains("dash-hero__stat-bar");
		assertThat(html).contains("판매 수량");
		assertThat(html).contains("88");
		assertThat(html).contains("구제율");
		assertThat(html).contains("82");

		// 2. 바디 시트 및 기간 선택 Pill 4개 검증
		assertThat(html).contains("dash-sheet");
		assertThat(html).contains("settle-period");
		assertThat(html).contains("오늘");
		assertThat(html).contains("이번 주");
		assertThat(html).contains("지난 주");
		assertThat(html).contains("직접 선택");

		// 3. 2분할 흰 카드 (판매 수량 / 할인 제공액 92,000원)
		assertThat(html).contains("sales-metrics-split");
		assertThat(html).contains("92,000");

		// 4. 구제 · 폐기 현황 4칸 카드 + CO2 문구 검증
		assertThat(html).contains("구제 · 폐기 현황");
		assertThat(html).contains("sales-rescue-grid");
		assertThat(html).contains("107"); // 등록
		assertThat(html).contains("19");  // 폐기
		assertThat(html).contains("CO₂ 절감은 카테고리별 배출계수 × 판매 수량 추정치예요.");
		assertThat(html).contains("5.5kg");

		// 5. 일별 판매 · 폐기 차트 검증
		assertThat(html).contains("일별 판매 · 폐기");
		assertThat(html).contains("dash-chart");
		assertThat(html).contains("bar-chart__col");
		assertThat(html).contains("bar-chart__hint");
		assertThat(html).contains("판매 <span>88</span>개 · 폐기 <span>19</span>개");

		// 6. 상품별 판매 가로 스크롤 Sticky 표 검증
		assertThat(html).contains("상품별 판매");
		assertThat(html).contains("report-table-wrap");
		assertThat(html).contains("is-sticky-col");
		assertThat(html).contains("단팥빵");
		assertThat(html).contains("50,000");

		// 7. 리포트 파일 Excel / PDF 다운로드 버튼 검증
		assertThat(html).contains("리포트 파일");
		assertThat(html).contains("report-download-btn");
		assertThat(html).contains("Excel");
		assertThat(html).contains("PDF");
	}

	@Test
	@WithMockUser(username = "test@example.com", roles = "USER")
	@DisplayName("하루 기간(오늘)일 때 불필요한 일별 차트 섹션 자체가 깔끔하게 생략된다")
	void salesReportSingleDayHidesChartAndShowsNotice() throws Exception {
		SalesReportData mockReport = new SalesReportData(
				"창호베이커리 중앙점",
				"2026-09-22 (오늘)",
				"today",
				"2026-09-22",
				"2026-09-22",
				false, // showGraph = false
				false,
				150000L,
				20000L,
				null,
				null,
				20,
				15,
				5,
				75,
				1.2,
				List.of(),
				List.of()
		);

		when(salesReportService.isConfigured()).thenReturn(true);
		when(salesReportService.build(any(), any(), any(), any())).thenReturn(mockReport);

		MvcResult result = mockMvc.perform(get("/store/sales-report?period=today").sessionAttr("userId", 1L))
				.andExpect(status().isOk())
				.andReturn();

		String html = result.getResponse().getContentAsString();
		assertThat(html).doesNotContain("일별 판매 · 폐기");
		assertThat(html).doesNotContain("dash-chart__plot");
	}

	@Test
	@WithMockUser(username = "test@example.com", roles = "USER")
	@DisplayName("매출 데이터가 없는 기간(empty)일 때 안내 카드 하나만 노출된다")
	void salesReportEmptyStateShowsOnlyEmptyCard() throws Exception {
		SalesReportData mockReport = new SalesReportData(
				"창호베이커리 중앙점",
				"2026-09-01 ~ 2026-09-07",
				"custom",
				"2026-09-01",
				"2026-09-07",
				true,
				true, // empty = true
				0L,
				0L,
				null,
				null,
				0,
				0,
				0,
				0,
				0.0,
				List.of(),
				List.of()
		);

		when(salesReportService.isConfigured()).thenReturn(true);
		when(salesReportService.build(any(), any(), any(), any())).thenReturn(mockReport);

		MvcResult result = mockMvc.perform(get("/store/sales-report?period=custom&from=2026-09-01&to=2026-09-07").sessionAttr("userId", 1L))
				.andExpect(status().isOk())
				.andReturn();

		String html = result.getResponse().getContentAsString();
		assertThat(html).contains("이 기간에 매출 데이터가 없어요.");
		assertThat(html).contains("다른 기간을 선택하거나 영업일이 있는 주간을 골라보세요.");
		// 2)~6) 섹션은 숨겨져야 함
		assertThat(html).doesNotContain("sales-metrics-split");
		assertThat(html).doesNotContain("구제 · 폐기 현황");
		assertThat(html).doesNotContain("상품별 판매");
	}
}
