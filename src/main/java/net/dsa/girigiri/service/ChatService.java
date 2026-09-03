package net.dsa.girigiri.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.ChatRequestDto;
import net.dsa.girigiri.domain.dto.ChatResponseDto;
import net.dsa.girigiri.domain.dto.ReservationCancelStatusDto;
import net.dsa.girigiri.util.GeminiClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 고객 지원 챗봇 서비스.
 * 담당: 송채현 (WBS 6.5 고객 지원 챗봇)
 *
 * 로그인한 사용자의 viewMode(OWNER_MODE/USER_MODE)에 따라 서로 다른 시스템 프롬프트를 골라
 * Gemini API에 보낸다 — viewMode=OWNER_MODE면 사장님 전용 안내를, 그 외(USER_MODE)에는 손님용
 * 안내를 준다.
 *
 * 변경됨 (2026-08-26) — 왜: 원래는 ClaudeClient(Claude API)를 썼는데, Claude API는 신규 계정에
 * 자동 무료 크레딧이 없어 카드 등록 + 최소 결제가 필요했다. 개발/테스트 단계에서 비용 없이 쓸 수
 * 있는 GeminiClient(Gemini API, 무료 티어)로 교체했다 — CLAUDE.md 기획서(WBS 6.5)엔 "Claude API
 * 연동"이라고 적혀 있으니 팀에 공유하고 문서 업데이트 여부를 논의할 것.
 *
 * 변경됨 (2026-09-01) — 왜: CLAUDE.md의 dual-mode 세션 구조(role 고정 + viewMode 가변)상 원칙은
 * "화면 분기는 viewMode 기준"인데, 작성 시점(2026-08-25)엔 viewMode가 아직 세션에 연결돼 있지
 * 않아서 우선 role 기준으로 분기해뒀었다. 이후 문창호님 파트(AuthSessionInitializer, 로그인 시
 * viewMode 초기화 / AuthController.toggleMode(), 헤더의 유저·점주 모드 전환 토글)가 완료된 걸
 * 확인해서, 원래 계획대로 role -> viewMode로 분기 기준을 바꿨다. 이제 사장님이 헤더에서 "유저
 * 모드로" 전환해서 보는 중이면(role은 여전히 OWNER, viewMode만 USER_MODE) 챗봇도 손님용 프롬프트를
 * 준다 — role 그대로 썼다면 이 경우에도 계속 사장님용 안내가 나가서 실제로 보고 있는 화면과
 * 안 맞았을 상황. (요구사항정의서 REQ-F-120 설명 참고)
 *
 * FAQ/시스템 프롬프트 내용(REQ-F-121)은 우선 예약·결제·픽업 등 채채님 담당 영역 위주로 채워뒀다.
 * 로그인/지도·찜하기/마이페이지/절약가계부/리뷰 등 다른 팀원 담당 영역은 자리만 만들어뒀으니,
 * 각 담당자(문창호/강노은/김태훈)에게 자주 나오는 질문·답변을 받는 대로 아래 프롬프트에 채워
 * 넣을 것 — 그전까지는 그 영역 질문엔 "정확히 확인이 어렵다"고 답하도록 프롬프트에 명시해뒀다.
 *
 * 추가됨 (강노은, 2026-09-03) — 채현님 요청으로 지도 탐색/찜하기/리뷰 FAQ를 채워 넣었다
 * ([서비스 이용 안내]의 "매장 탐색"/"찜하기"/"리뷰" 항목). 마이페이지/절약가계부는 문창호님
 * 담당이라 그대로 비워뒀다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

	private final GeminiClient geminiClient;
	// 추가됨 (2026-08-31, 챗봇 기능 연동/function calling) — 왜: "지금 이 예약 취소할 수 있어요?" 같은
	// 질문에 실제 DB 데이터로 답하려면 예약 조회가 필요해서 주입했다. 챗봇이 이 서비스로 직접
	// 취소를 처리하지는 않는다 — 조회 전용 메서드(getCancelEligibilityForUser)만 사용한다.
	private final ReservationService reservationService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	// 추가됨 (2026-08-27) — 왜: 프론트(mypage.html)엔 입력창 maxlength=500을 걸어뒀지만, 그건
	// 브라우저 UI 제한이라 API를 직접 호출하면 얼마든지 우회할 수 있다. 너무 긴 메시지는 구글
	// API 비용/지연을 늘리고 악용 소지도 있어서, 서버에서도 한 번 더 길이를 막아준다.
	private static final int MAX_MESSAGE_LENGTH = 500;

	private static final String CUSTOMER_SYSTEM_PROMPT = """
			당신은 동네 가게의 마감 임박 음식을 손님이 미리 예약·결제하고 매장에서 픽업하는 서비스
			'기리기리(끼리끼리)'의 고객 지원 챗봇입니다. 친절하고 간결한 한국어 존댓말로 답하세요.

			[답변 규칙]
			- 아래 [서비스 이용 안내]에 없는 내용은 지어내지 말고, "그 부분은 정확히 확인이 어려워요.
			  마이페이지의 1:1 문의 게시판으로 문의해주시면 확인해드릴게요."라고 답하세요.
			- 실제 결제 취소/환불 처리, 계정 정지 해제처럼 시스템을 직접 조작해야 하는 요청은 챗봇이
			  처리할 수 없으니, 화면의 해당 버튼을 이용하거나 1:1 문의 게시판을 이용하도록 안내만
			  하세요. 직접 처리해준 것처럼 답하면 안 됩니다.
			- 서비스와 무관한 질문(일반 상식, 다른 회사 서비스 등)에는 정중히 답변을 거절하세요.
			- 채팅 화면은 마크다운을 지원하지 않으니, 별표(**)나 #, - 같은 마크다운 문법은 절대
			  쓰지 말고 순수 텍스트로만 답하세요. 강조하고 싶으면 그냥 문장으로 풀어서 쓰세요.
			- 예약 취소가 지금 가능한지, 취소까지 얼마나 남았는지를 물어보면 아래 안내 문구로
			  대충 답하지 말고, 제공된 예약 조회 함수를 호출해서 실제 데이터를 확인한 뒤 그 결과
			  그대로 답하세요. 이 함수는 조회만 할 뿐 실제로 예약을 취소하지는 않으니, 취소가
			  가능하다고 확인되면 마이페이지에서 취소하는 방법을 안내해주세요.

			[서비스 이용 안내]
			- 회원가입/로그인: 구글/카카오/라인 소셜 로그인 또는 이메일(비밀번호) 가입을 지원해요. 소셜 로그인은 처음
			  로그인하면 자동으로 계정이 만들어져요. 이메일 가입은 사이트에서 직접 비밀번호를 설정해서 가입하는 방식이에요.
			- 매장 탐색: 홈 화면 지도에서 내 주변 마감세일 중인 가게를 확인할 수 있어요. 위치
			  권한을 허용하면 자동으로 거리순으로 정렬되고, 카테고리(베이커리/반찬/도시락/카페)로
			  필터링도 가능해요. 검색 화면에서는 할인율순·가격순·마감임박순·거리순 중 원하는
			  정렬을 직접 골라서 찾을 수도 있어요.
			- 찜하기: 가게 카드나 가게 상세 화면의 하트(♥) 아이콘을 누르면 바로 찜 목록에
			  추가/해제돼요. 마이페이지 '찜한 가게'에서 모아볼 수 있고, 알림 설정에서 찜 알림을
			  켜두면 찜한 가게가 마감세일을 시작할 때 알림도 받을 수 있어요.
			- 예약: 상품 상세 화면에서 수량과 픽업 시간대를 고르고 예약하기를 누르면 결제 화면으로
			  이동해요.
			- 결제: PortOne을 통한 카드 결제를 지원해요(현재 테스트 결제 단계). 결제가 완료되면
			  즉시 예약이 확정되고, 픽업용 QR코드(픽업코드)가 발급돼요.
			- 픽업: 매장 방문 시 QR코드를 사장님께 보여주면 픽업 완료로 처리돼요. 픽업 마감시간까지
			  방문하지 않으면 노쇼로 처리돼요. 마감 임박 음식이라 노쇼 처리된 뒤엔 재판매가 불가능해서,
			  결제하신 금액은 환불되지 않고 상품 재고도 복구되지 않아요.
			- 취소/환불: 결제 완료 후 30분 이내이면서 매장 마감 30분 전까지만 마이페이지에서 직접
			  취소할 수 있어요. 그 시간이 지나면 취소가 제한돼요.
			- 영수증: 마이페이지에서 결제 영수증 PDF를 다시 확인하거나 다운로드할 수 있어요.
			- 리뷰: 그 가게에서 예약 후 픽업까지 완료한 손님만 리뷰를 작성할 수 있어요. 가게당
			  리뷰는 1개만 쓸 수 있고, 별점과 사진을 포함해 작성할 수 있으며 이미 쓴 리뷰는
			  나중에 다시 수정할 수도 있어요.
			- 마이페이지: 이번 달 절약 금액, 예약 내역, 찜한 가게, 알림 설정 등을 확인할 수 있어요.
			  다만 월별 절약 그래프, 카테고리별 분석, 절약 목표 설정 같은 상세 가계부 기능은
			  아직 준비 중이에요.
			""";

	private static final String OWNER_SYSTEM_PROMPT = """
			당신은 동네 가게의 마감 임박 음식을 손님에게 예약·픽업으로 판매하는 서비스
			'기리기리(끼리끼리)'의 사장님(점주) 전용 지원 챗봇입니다. 친절하고 간결한 한국어
			존댓말로 답하세요.

			[답변 규칙]
			- 아래 [사장님 기능 안내]에 없는 내용은 지어내지 말고, "그 부분은 정확히 확인이
			  어려워요. 1:1 문의 게시판으로 문의해주시면 확인해드릴게요."라고 답하세요.
			- 실제 예약 승인/거절, 정산 처리처럼 시스템을 직접 조작해야 하는 요청은 챗봇이 처리할
			  수 없으니, 대시보드의 해당 버튼을 이용하거나 1:1 문의 게시판을 이용하도록 안내만
			  하세요.
			- 채팅 화면은 마크다운을 지원하지 않으니, 별표(**)나 #, - 같은 마크다운 문법은 절대
			  쓰지 말고 순수 텍스트로만 답하세요. 강조하고 싶으면 그냥 문장으로 풀어서 쓰세요.

			[사장님 기능 안내]
			- 상품(재고) 등록: 대시보드에서 마감 임박 상품의 사진, 품목, 원가/할인가, 수량을
			  등록할 수 있어요. 수동 등록 외에도 POS 연동을 해두면 마감 무렵 남은 재고로 "오늘의 구제"
			  초안이 자동 생성돼서 점주는 바로 올리기만 하면 되고, 자주 파는 품목은 템플릿으로
			  등록해두면 매일 초안이 자동 생성돼요. 할인가는 마감까지 남은 시간 기준으로 자동
			  계산되고(3시간 이상 전 20%, 1~3시간 전 30%, 1시간 이내 50%), 점주는 그보다 더
			  깎는 것만 가능해요.
			- 판매금액/상품정보 수정: 이미 등록한 상품도 대시보드에서 원가, 할인가, 수량 등을
			  다시 수정할 수 있어요.
			- 예약 확인: 손님이 예약을 넣으면 '들어온 예약' 목록에서 확인 후 수락하거나 거절할 수
			  있어요.
			- 픽업 확인: 손님이 보여주는 QR코드를 스캔하거나 코드를 입력하면 픽업 완료로
			  처리돼요.
			- 노쇼 처리: 픽업 마감시간이 지나도 손님이 오지 않으면 자동으로 노쇼 처리되고, 이
			  경우 환불되지 않아요.
			- 매출/리포트: 대시보드에서 오늘 매출, 판매·등록 현황, 픽업 예약, 폐기 절감(구제율) 같은 핵심 지표와
			  이번 달 정산 예정액을 확인할 수 있고, 오늘 또는 최근 7일 기준 판매·폐기 통계와
			  절감 효과(구한 음식 개수·회수 매출·CO2 절감량)도 볼 수 있고,
			  일간/주간 리포트를 Excel·PDF로 다운로드할 수 있어요.
			- 공지사항: 플랫폼 전체 공지사항은 운영자가 관리해요. 사장님이 직접 매장(가게)
			  공지사항을 등록하는 기능은 아직 준비 중이에요.
			""";

	public ChatResponseDto sendMessage(Long userId, String viewMode, ChatRequestDto request) {
		if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
			return ChatResponseDto.failed("메시지를 입력해주세요.");
		}
		if (request.getMessage().length() > MAX_MESSAGE_LENGTH) {
			return ChatResponseDto.failed("메시지가 너무 길어요. " + MAX_MESSAGE_LENGTH + "자 이내로 입력해주세요.");
		}

		boolean isOwnerMode = "OWNER_MODE".equals(viewMode);
		String systemPrompt = isOwnerMode ? OWNER_SYSTEM_PROMPT : CUSTOMER_SYSTEM_PROMPT;

		// 예약 조회 tool은 손님 화면 전용이다(WBS "챗봇 기능 연동" — 본인 예약만 조회). 사장님이
		// 헤더에서 유저 모드로 전환해서 보는 중이면(viewMode=USER_MODE) 본인 명의 예약을 물어볼 수도
		// 있으니 이때도 tool을 실어준다. 사장님 모드로 보는 중일 때만 tool 없이(null) 예전과 동일하게
		// 동작한다.
		GeminiClient.ReservationToolExecutor toolExecutor =
				isOwnerMode ? null : () -> buildReservationStatusJson(userId);

		GeminiClient.ChatResult result = geminiClient.sendMessage(
				systemPrompt, request.getHistory(), request.getMessage(), toolExecutor);

		if (!result.success()) {
			log.warn("> [ChatService] Gemini API 응답 실패 - viewMode={}, 사유={}", viewMode, result.failReason());
			return ChatResponseDto.failed(result.failReason());
		}

		return ChatResponseDto.success(result.reply());
	}

	/**
	 * 예약 조회 tool의 실제 실행부. userId는 반드시 컨트롤러가 세션에서 꺼낸 실제 로그인 사용자
	 * id여야 한다(GeminiClient.ReservationToolExecutor 참고) — 모델이 함수 호출에 다른 값을 실어
	 * 보내더라도 여기서는 그 값을 쓰지 않고 항상 이 메서드 호출 시점에 넘어온 userId만 쓴다.
	 */
	private String buildReservationStatusJson(Long userId) {
		List<ReservationCancelStatusDto> statuses = reservationService.getCancelEligibilityForUser(userId);
		try {
			ObjectNode node = objectMapper.createObjectNode();
			node.set("reservations", objectMapper.valueToTree(statuses));
			return objectMapper.writeValueAsString(node);
		} catch (Exception e) {
			log.warn("> [ChatService] 예약 조회 tool 결과 직렬화 실패 - userId={}", userId, e);
			return "{\"reservations\":[],\"error\":\"조회 중 오류가 발생했어요.\"}";
		}
	}
}
