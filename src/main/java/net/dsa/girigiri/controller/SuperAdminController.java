package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.CalendarDayDto;
import net.dsa.girigiri.domain.dto.DailySignupBarDto;
import net.dsa.girigiri.domain.dto.MemberActivityRowDto;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.InquiryCommentEntity;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.NoticeEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.InquiryCommentRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.NoticeRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.service.InquiryService;
import net.dsa.girigiri.service.NotificationService;
import net.dsa.girigiri.util.StoreHoursUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 슈퍼어드민(플랫폼 운영자) 화면 라우팅.
 * common/layout-admin 을 쓰는 wide 레이아웃 전용 — 나머지 컨트롤러(common/layout, 420px)와는 별도 트랙.
 *
 * TODO(송보미): 회원 관리(members)/매장 관리(stores)/공지사항(notices)은 실데이터 연동했다. 나머지
 *   (신고·문의/코드)는 해당 엔티티가 아직 없어 계속 데모 데이터 — Repository 생기는 대로 순서대로 교체할 것.
 *   문창호의 role 분리 작업이 끝나면 role=SUPERADMIN 기준 접근 제어를 여기(혹은 시큐리티 설정)에 추가할 것.
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

	// MypageController.withdraw()의 자진 탈퇴와 동일한 가드 — 미완료 예약이 있으면 탈퇴(삭제)를 막는다.
	private static final List<String> INCOMPLETE_RESERVATION_STATUSES = List.of("pending", "confirmed");

	// 글이 쌓일수록 목록이 길어지는 화면(공지사항/신고·문의)의 페이지당 행 수 — 데이터가 이 프로젝트
	// 규모에서 몇백 건을 넘길 일이 없어 DB 레벨 Pageable 대신 정렬된 List를 메모리에서 그냥 잘라 쓴다.
	private static final int PAGE_SIZE = 10;

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final NoticeRepository noticeRepository;
	private final ReservationRepository reservationRepository;
	private final InquiryRepository inquiryRepository;
	private final InquiryCommentRepository inquiryCommentRepository;
	private final ComplaintRepository complaintRepository;
	private final InquiryService inquiryService;
	private final NotificationService notificationService;

	@Value("${kakao.map.js-key}")
	private String kakaoMapJsKey;

	/**
	 * 추가됨 — 왜: "최근 처리 대기"를 예시 1건짜리에서 실제 대기 건수 요약으로 바꿔달라는 요청.
	 * 승인대기 매장 수(pendingStoreCount)는 SuperAdminNotificationAdvice가 이미 모든 슈퍼어드민 페이지에
	 * 공급 중이라 여기서 따로 계산 안 함 — 미답변 문의 수만 reports()와 동일한 로직으로 새로 계산한다.
	 * 변경됨 — 왜: "신고 접수"가 ComplaintEntity로 실데이터가 됐으니 "(데모)" 표시를 떼고 대기 건수도
	 * 실제로 센다.
	 * 변경됨 — 왜: 대시보드에 달력·그래프도 넣어달라는 요청 — storeView 대시보드의
	 * "최근 7일 판매/폐기 막대그래프"와 같은 패턴(막대 높이 %를 컨트롤러가 미리 계산)으로 "최근 7일
	 * 신규 가입" 막대그래프를 추가하고, 같은 가입일 집계를 이번 달 전체로 넓혀 미니 캘린더(가입자 있는
	 * 날에 점 표시)도 만들었다 — 둘 다 UserRepository 하나로 계산되는 실데이터다.
	 */
	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		Map<Long, Long> commentCounts = inquiryCommentRepository.findAll().stream()
				.collect(Collectors.groupingBy(InquiryCommentEntity::getInquiryId, Collectors.counting()));
		long pendingInquiryCount = inquiryRepository.findAll().stream()
				.filter(inquiry -> !commentCounts.containsKey(inquiry.getId()))
				.count();
		model.addAttribute("pendingInquiryCount", pendingInquiryCount);

		long pendingComplaintCount = complaintRepository.findAll().stream()
				.filter(c -> ComplaintEntity.STATUS_PENDING.equals(c.getStatus()))
				.count();
		model.addAttribute("pendingComplaintCount", pendingComplaintCount);

		Map<LocalDate, Long> signupsByDate = userRepository.findAll().stream()
				.filter(u -> u.getCreatedAt() != null)
				.collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate(), Collectors.counting()));
		model.addAttribute("weeklySignupBars", buildWeeklySignupBars(signupsByDate));
		model.addAttribute("calendarDays", buildSignupCalendar(signupsByDate));
		model.addAttribute("calendarMonthLabel", YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy년 M월")));

		return "superAdminView/dashboard";
	}

	private List<DailySignupBarDto> buildWeeklySignupBars(Map<LocalDate, Long> signupsByDate) {
		LocalDate today = LocalDate.now();

		int max = 0;
		for (int i = 0; i <= 6; i++) {
			max = Math.max(max, signupsByDate.getOrDefault(today.minusDays(i), 0L).intValue());
		}

		DateTimeFormatter dayLabelFormat = DateTimeFormatter.ofPattern("M/d");
		List<DailySignupBarDto> bars = new ArrayList<>();
		for (int i = 6; i >= 0; i--) {
			LocalDate date = today.minusDays(i);
			int count = signupsByDate.getOrDefault(date, 0L).intValue();
			int heightPercent = max == 0 ? 0 : (int) Math.round(count * 100.0 / max);
			bars.add(new DailySignupBarDto(date.format(dayLabelFormat), count, heightPercent, date.equals(today)));
		}
		return bars;
	}

	// 이번 달 1일이 시작하는 요일 앞으로 지난 달 날짜를 채워 월요일 시작 7칸 격자를 만들고, 항상 42칸
	// (6주)로 고정해 어떤 달이 와도 레이아웃이 흔들리지 않게 한다.
	private List<CalendarDayDto> buildSignupCalendar(Map<LocalDate, Long> signupsByDate) {
		LocalDate today = LocalDate.now();
		YearMonth month = YearMonth.from(today);
		LocalDate firstOfMonth = month.atDay(1);
		int leadingBlanks = firstOfMonth.getDayOfWeek().getValue() - 1;
		LocalDate cursor = firstOfMonth.minusDays(leadingBlanks);

		List<CalendarDayDto> days = new ArrayList<>();
		for (int i = 0; i < 42; i++) {
			boolean inMonth = YearMonth.from(cursor).equals(month);
			int count = signupsByDate.getOrDefault(cursor, 0L).intValue();
			days.add(new CalendarDayDto(cursor.getDayOfMonth(), inMonth, cursor.equals(today), count));
			cursor = cursor.plusDays(1);
		}
		return days;
	}

	@GetMapping("/members")
	public String members(@RequestParam(required = false) String q,
	                       @RequestParam(required = false) String filter,
	                       @RequestParam(defaultValue = "0") int page,
	                       Model model) {
		String normalizedFilter = normalizeMemberFilter(filter);
		List<UserEntity> users = findFilteredMembers(q, normalizedFilter);

		model.addAttribute("users", users);
		model.addAttribute("pagedUsers", paginate(users, page, PAGE_SIZE));
		model.addAttribute("totalPages", totalPages(users.size(), PAGE_SIZE));
		model.addAttribute("page", page);
		model.addAttribute("q", q == null ? "" : q.trim());
		model.addAttribute("filter", normalizedFilter);

		return "superAdminView/members";
	}

	/**
	 * 추가됨 — 왜: 참고 이미지의 "회원 목록 다운로드" 버튼. 현재 검색(q)/필터(filter) 조건을 그대로
	 * 반영해서 CSV로 내려준다 — 순수 텍스트 조립이라 Apache POI(엑셀 리포트용)까지는 필요 없다.
	 * UTF-8 BOM을 붙이는 이유: BOM 없이 내려주면 엑셀에서 한글이 깨져 보인다.
	 */
	@GetMapping("/members/export")
	public ResponseEntity<byte[]> exportMembers(@RequestParam(required = false) String q,
	                                             @RequestParam(required = false) String filter) {
		List<UserEntity> users = findFilteredMembers(q, normalizeMemberFilter(filter));

		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		StringBuilder csv = new StringBuilder("﻿");
		csv.append("닉네임,이메일,권한,상태,가입일,최근수정일\n");
		for (UserEntity u : users) {
			csv.append(csvField(u.getNickname())).append(',')
					.append(csvField(u.getEmail())).append(',')
					.append(csvField(u.getRole())).append(',')
					.append(csvField(u.getStatus())).append(',')
					.append(u.getCreatedAt() != null ? u.getCreatedAt().format(dateFormat) : "").append(',')
					.append(u.getUpdatedAt() != null ? u.getUpdatedAt().format(dateFormat) : "")
					.append('\n');
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=members.csv")
				.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.body(csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String csvField(String value) {
		String safe = value == null ? "" : value.replace("\"", "\"\"");
		return "\"" + safe + "\"";
	}

	// 변경됨 — 왜: 필터 탭을 "전체/일반 회원/점주 회원/정지 회원"으로 바꿔달라는 요청 — 역할 기준
	// 필터가 USER/ADMIN(운영자)에서 USER(일반 회원)/OWNER(점주 회원)로 바뀌었다. 운영자 계정은 수가
	// 적고 이 화면의 주 관리 대상(소비자·점주)이 아니라서 전용 탭은 뺐다 — "전체"에서는 여전히 보임.
	private String normalizeMemberFilter(String filter) {
		return "USER".equals(filter) || "OWNER".equals(filter) || "SUSPENDED".equals(filter) ? filter : null;
	}

	private List<UserEntity> findFilteredMembers(String q, String normalizedFilter) {
		Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
		String keyword = q == null ? "" : q.trim();

		List<UserEntity> users = keyword.isEmpty()
				? userRepository.findAll(sort)
				: userRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword, sort);

		if ("USER".equals(normalizedFilter)) {
			return users.stream().filter(u -> UserEntity.ROLE_USER.equals(u.getRole())).toList();
		}
		if ("OWNER".equals(normalizedFilter)) {
			return users.stream().filter(u -> UserEntity.ROLE_OWNER.equals(u.getRole())).toList();
		}
		if ("SUSPENDED".equals(normalizedFilter)) {
			return users.stream().filter(u -> UserEntity.STATUS_SUSPENDED.equals(u.getStatus())).toList();
		}
		return users;
	}

	/**
	 * 추가됨 — 왜: 표 왼쪽 체크박스로 여러 명을 골라 한 번에 정지/정지 해제하는 일괄 액션(디자인 참고
	 * 요청 — 장식용 체크박스는 이번 세션의 "비활성 버튼 금지" 방침과 안 맞아서 실제로 동작하게 만듦).
	 * q/filter를 리다이렉트에 실어 보내는 이유: 검색·필터 걸어둔 채로 일괄 처리해도 그 화면으로 돌아가게
	 * 하려고 — UriComponentsBuilder로 인코딩해야 q에 한글이 있어도 깨지지 않는다.
	 */
	@PostMapping("/members/bulk-suspend")
	public String bulkSuspendMembers(@RequestParam(required = false) List<Long> ids,
	                                  @RequestParam(required = false) String q,
	                                  @RequestParam(required = false) String filter,
	                                  @RequestParam(required = false) Integer page) {
		if (ids != null && !ids.isEmpty()) {
			List<UserEntity> targets = userRepository.findAllById(ids);
			targets.forEach(u -> u.setStatus(UserEntity.STATUS_SUSPENDED));
			userRepository.saveAll(targets);
		}
		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	@PostMapping("/members/bulk-unsuspend")
	public String bulkUnsuspendMembers(@RequestParam(required = false) List<Long> ids,
	                                    @RequestParam(required = false) String q,
	                                    @RequestParam(required = false) String filter,
	                                    @RequestParam(required = false) Integer page) {
		if (ids != null && !ids.isEmpty()) {
			List<UserEntity> targets = userRepository.findAllById(ids);
			targets.forEach(u -> u.setStatus(UserEntity.STATUS_ACTIVE));
			userRepository.saveAll(targets);
		}
		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	/**
	 * 변경됨 — 왜: "일반 회원 탭에서 정지시켰는데 전체 탭으로 튕긴다"는 피드백 — 개별 행 액션(정지/정지
	 * 해제/탈퇴)도 일괄 액션과 똑같이 q/filter/page를 그대로 들고 돌아가게 통일했다.
	 */
	private String buildMembersRedirectUri(String q, String filter, Integer page, boolean withdrawError) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/superadmin/members")
				.queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
				.queryParamIfPresent("filter", Optional.ofNullable(filter))
				.queryParamIfPresent("page", Optional.ofNullable(page).filter(p -> p > 0));
		if (withdrawError) {
			builder.queryParam("withdrawError");
		}
		return builder.build().encode().toUriString();
	}

	// 이미 정렬된 리스트를 페이지 단위로 자른다 — page는 0부터 시작, 범위를 벗어나면 빈 리스트.
	private <T> List<T> paginate(List<T> sorted, int page, int pageSize) {
		int from = Math.max(page, 0) * pageSize;
		if (from >= sorted.size()) {
			return List.of();
		}
		return sorted.subList(from, Math.min(from + pageSize, sorted.size()));
	}

	private int totalPages(int totalItems, int pageSize) {
		return Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
	}

	/**
	 * 추가됨 — 왜: 회원 목록에서 이름을 클릭하면 상세 정보(연락처/가입 경로/지역/가입일 등)를 볼 수 있게
	 * 해달라는 요청. OWNER는 본인 소유 매장이 있으면 같이 보여줘서 슈퍼어드민이 바로 매장 상세로 넘어갈
	 * 수 있게 한다(StoreRepository#findByOwnerId 재사용).
	 * 변경됨 — 왜: "신고자/문의자 상세에 이전에 문의한 거 정리된 리스트도 보여달라"는 요청 — 이 회원이
	 * 작성자인 문의(InquiryRepository#findByUserId)와 신고자인 신고(ComplaintRepository#findByReporterId)
	 * 를 하나로 합쳐 최신순으로 보여준다(MemberActivityRowDto).
	 * 변경됨 — 왜: "← 회원 목록"이 원래 보던 필터 탭으로 정확히 돌아가게 해달라는 요청 — 목록에서
	 * 상세로 넘어올 때 실려온 q/filter/page를 그대로 모델에 얹어서, 상세 화면이 "← 회원 목록" 링크에
	 * 다시 실어 돌려준다(history.back() 없이 URL로 왕복).
	 */
	@GetMapping("/members/{id}")
	public String memberDetail(@PathVariable Long id,
	                            @RequestParam(required = false) String q,
	                            @RequestParam(required = false) String filter,
	                            @RequestParam(required = false) Integer page,
	                            Model model) {
		UserEntity user = userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다: " + id));

		model.addAttribute("member", user);
		model.addAttribute("q", q);
		model.addAttribute("filter", filter);
		model.addAttribute("page", page);
		if (UserEntity.ROLE_OWNER.equals(user.getRole())) {
			storeRepository.findByOwnerId(user.getId()).ifPresent(store -> model.addAttribute("ownedStore", store));
		}

		Sort byNewest = Sort.by(Sort.Direction.DESC, "createdAt");
		List<MemberActivityRowDto> activity = new ArrayList<>();
		for (InquiryEntity i : inquiryRepository.findByUserId(id, byNewest)) {
			activity.add(new MemberActivityRowDto("문의", i.getTitle(), i.getCreatedAt(), "/superadmin/inquiries/" + i.getId()));
		}
		for (ComplaintEntity c : complaintRepository.findByReporterId(id, byNewest)) {
			activity.add(new MemberActivityRowDto("신고", c.getReason(), c.getCreatedAt(), "/superadmin/complaints/" + c.getId()));
		}
		activity.sort(Comparator.comparing(MemberActivityRowDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
		model.addAttribute("memberActivity", activity);

		return "superAdminView/memberDetail";
	}

	/**
	 * 추가됨 — 왜: 목록의 "관리" 칸에 수정 기능도 넣어달라는 요청. storeEditForm/storeEditSubmit과
	 * 같은 패턴 — 닉네임/이메일/권한/활동 지역만 고친다(OAuth 연동 필드·좌표는 관리자가 손댈 이유가
	 * 없어 뺌, 상태는 이미 정지/정지해제 버튼으로 따로 처리 중이라 여기서 안 건드림).
	 * 변경됨 — 왜: 활동 지역을 한때 GPS 버튼(운영자 기기의 현재 위치, 회원 본인 위치와 무관)으로 채우게
	 * 했었는데, "슈퍼어드민은 GPS 말고 드롭다운으로" 피드백으로 select 방식으로 바꾸면서 카카오 지오코더가
	 * 더 이상 필요 없어졌다 — kakaoMapJsKey 모델 속성도 함께 제거.
	 */
	@GetMapping("/members/{id}/edit")
	public String memberEditForm(@PathVariable Long id, Model model) {
		UserEntity user = userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다: " + id));
		model.addAttribute("member", user);
		return "superAdminView/memberEdit";
	}

	@PostMapping("/members/{id}/edit")
	public String memberEditSubmit(@PathVariable Long id,
	                                @RequestParam String nickname,
	                                @RequestParam(required = false) String email,
	                                @RequestParam String role,
	                                @RequestParam(required = false) String region) {
		UserEntity user = userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다: " + id));

		boolean validRole = List.of(UserEntity.ROLE_USER, UserEntity.ROLE_OWNER, UserEntity.ROLE_ADMIN).contains(role);
		if (nickname == null || nickname.isBlank() || !validRole) {
			return "redirect:/superadmin/members/" + id + "/edit?error";
		}

		user.setNickname(nickname.trim());
		user.setEmail(email == null || email.isBlank() ? null : email.trim());
		user.setRole(role);
		user.setRegion(region == null || region.isBlank() ? null : region.trim());
		userRepository.save(user);

		return "redirect:/superadmin/members/" + id;
	}

	@PostMapping("/members/{id}/suspend")
	public String suspendMember(@PathVariable Long id,
	                             @RequestParam(required = false) String q,
	                             @RequestParam(required = false) String filter,
	                             @RequestParam(required = false) Integer page) {
		UserEntity user = userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다: " + id));

		user.setStatus(UserEntity.STATUS_SUSPENDED);
		userRepository.save(user);

		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	@PostMapping("/members/{id}/unsuspend")
	public String unsuspendMember(@PathVariable Long id,
	                               @RequestParam(required = false) String q,
	                               @RequestParam(required = false) String filter,
	                               @RequestParam(required = false) Integer page) {
		UserEntity user = userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다: " + id));

		user.setStatus(UserEntity.STATUS_ACTIVE);
		userRepository.save(user);

		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	/**
	 * 추가됨 — 왜: MypageController#withdraw(자진 탈퇴)와 동일한 규칙으로 운영자가 강제 탈퇴시킨다 —
	 * 미완료 예약(본인 예약이거나, 본인이 점주인 매장에 걸린 예약)이 있으면 막고, 없으면 계정을
	 * 실제로 삭제한다(soft-delete 아님, 자진 탈퇴와 동일).
	 */
	@PostMapping("/members/{id}/withdraw")
	public String withdrawMember(@PathVariable Long id,
	                              @RequestParam(required = false) String q,
	                              @RequestParam(required = false) String filter,
	                              @RequestParam(required = false) Integer page) {
		if (!userRepository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다: " + id);
		}

		if (reservationRepository.existsByUserIdAndStatusIn(id, INCOMPLETE_RESERVATION_STATUSES)) {
			return "redirect:" + buildMembersRedirectUri(q, filter, page, true);
		}

		StoreEntity ownedStore = storeRepository.findByOwnerId(id).orElse(null);
		if (ownedStore != null
				&& reservationRepository.existsByStoreIdAndStatusIn(ownedStore.getId(), INCOMPLETE_RESERVATION_STATUSES)) {
			return "redirect:" + buildMembersRedirectUri(q, filter, page, true);
		}

		userRepository.deleteById(id);

		return "redirect:" + buildMembersRedirectUri(q, filter, page, false);
	}

	// 변경됨 — 왜: "승인대기/매장목록을 따로 나누지 말고 전체 하나로, 대기 매장은 필터로 보게 해달라"는
	// 요청 — REJECTED만 빼고 전부 한 리스트로 묶은 뒤, filter=PENDING일 때만 대기 매장으로 좁힌다.
	@GetMapping("/stores")
	public String stores(@RequestParam(required = false) String filter, Model model) {
		String normalizedFilter = "PENDING".equals(filter) ? "PENDING" : null;

		List<StoreEntity> all = storeRepository.findAll().stream()
				.filter(s -> !StoreEntity.STATUS_REJECTED.equals(s.getApprovalStatus()))
				.toList();
		List<StoreEntity> stores = "PENDING".equals(normalizedFilter)
				? all.stream().filter(s -> StoreEntity.STATUS_PENDING.equals(s.getApprovalStatus())).toList()
				: all;

		// 대기 매장은 "대기" 배지로 고정 표시하니 영업시간 기준 영업중/휴업 판정은 대기가 아닌 매장만 계산.
		Map<Long, Boolean> openStatusMap = new HashMap<>();
		for (StoreEntity store : stores) {
			if (StoreEntity.STATUS_PENDING.equals(store.getApprovalStatus())) {
				continue;
			}
			StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
			boolean isOpen = closingInfo.closeAt() == null || closingInfo.closeAt().isAfter(LocalDateTime.now());
			openStatusMap.put(store.getId(), isOpen);
		}

		model.addAttribute("stores", stores);
		model.addAttribute("openStatusMap", openStatusMap);
		model.addAttribute("filter", normalizedFilter);
		return "superAdminView/stores";
	}

	/**
	 * 추가됨 — 왜: "입점 승인 대기" 목록의 승인 버튼 처리. AuthController#ownerApply 주석에 이미
	 * "운영자의 심사/승인 후 role=OWNER로 전환됨"이라고 적혀 있던 대로, 매장 승인과 함께 신청자
	 * 계정(UserEntity)의 role도 USER → OWNER로 올려준다(그래야 점주 대시보드/상품 관리 접근이 열림 —
	 * StoreProductController가 role이 아니라 store.approvalStatus==APPROVED로만 체크하고 있긴 하지만,
	 * UserEntity.role도 실제로 쓰이는 곳들이 있어 둘 다 맞춰준다).
	 */
	@PostMapping("/stores/{id}/approve")
	public String approveStore(@PathVariable Long id) {
		StoreEntity store = storeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다: " + id));

		store.setApprovalStatus(StoreEntity.STATUS_APPROVED);
		storeRepository.save(store);

		userRepository.findById(store.getOwnerId()).ifPresent(owner -> {
			owner.setRole(UserEntity.ROLE_OWNER);
			userRepository.save(owner);
		});

		return "redirect:/superadmin/stores";
	}

	/**
	 * 추가됨 — 왜: 매장 관리 목록에 "삭제" 기능을 붙여달라는 요청. withdrawMember(회원 강제 탈퇴)와 같은
	 * 가드를 쓴다 — 미완료 예약이 걸려 있으면 막는다. FK가 연관관계로 매핑돼 있지 않아(ERD 확정 전까지
	 * plain Long id 컬럼만 쓰는 컨벤션) 매장을 지우면 그 매장의 ProductEntity들이 고아로 남으므로 같이
	 * 지운다. approveStore()가 승인 시 OWNER로 올려주는 것의 반대로, 삭제 시 소유자가 아직 OWNER면
	 * USER로 되돌린다(다른 매장을 또 만들 수도 있으니 강제 탈퇴는 아님).
	 */
	@PostMapping("/stores/{id}/delete")
	public String deleteStore(@PathVariable Long id) {
		StoreEntity store = storeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다: " + id));

		if (reservationRepository.existsByStoreIdAndStatusIn(id, INCOMPLETE_RESERVATION_STATUSES)) {
			return "redirect:/superadmin/stores?deleteError";
		}

		productRepository.deleteAll(productRepository.findByStoreId(id));

		userRepository.findById(store.getOwnerId())
				.filter(owner -> UserEntity.ROLE_OWNER.equals(owner.getRole()))
				.ifPresent(owner -> {
					owner.setRole(UserEntity.ROLE_USER);
					userRepository.save(owner);
				});

		storeRepository.deleteById(id);

		return "redirect:/superadmin/stores";
	}

	/**
	 * 추가됨 — 왜: 매장 목록에서 클릭해 들어오는 상세 화면. 운영자가 특정 매장 하나를 골라 긴급
	 * 연락처·최근 판매율(구제율)을 확인하는 용도라, StoreController.dashboard()(점주 본인용, "오늘"
	 * 기준)와 달리 등록/판매가 없는 날도 의미 있게 보이도록 최근 7일 창으로 구제율을 계산한다.
	 * 변경됨 — 왜: memberDetail.html의 "연결된 매장"과 대칭으로, 여기도 이 매장을 소유한 회원 계정
	 * ("연결된 계정")을 보여주고 클릭하면 회원 상세로 가게 해달라는 요청. owner_id가 없거나(이론상)
	 * 탈퇴 등으로 회원을 못 찾으면 그냥 섹션을 숨긴다.
	 * 변경됨 — 왜: "← 매장 목록"이 원래 보던 필터 탭(대기 등)으로 정확히 돌아가게 해달라는 요청 —
	 * memberDetail과 동일하게 목록에서 실려온 filter를 그대로 모델에 얹는다.
	 */
	@GetMapping("/stores/{id}")
	public String storeDetail(@PathVariable Long id,
	                           @RequestParam(required = false) String filter,
	                           Model model) {
		StoreEntity store = storeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다: " + id));

		model.addAttribute("filter", filter);
		if (store.getOwnerId() != null) {
			userRepository.findById(store.getOwnerId()).ifPresent(owner -> model.addAttribute("owner", owner));
		}

		StoreHoursUtil.ClosingInfo closingInfo = StoreHoursUtil.parse(store.getOperatingHours(), 60);
		boolean isOpen = closingInfo.closeAt() == null || closingInfo.closeAt().isAfter(LocalDateTime.now());

		LocalDateTime rangeStart = LocalDate.now().minusDays(6).atStartOfDay();
		List<ProductEntity> recentProducts = productRepository.findByStoreId(id).stream()
				.filter(p -> p.getRegisteredAt() != null && !p.getRegisteredAt().isBefore(rangeStart))
				.toList();

		int registeredCount7d = recentProducts.size();
		int soldCount7d = recentProducts.stream()
				.mapToInt(p -> p.getQuantity() - p.getRemainingQuantity())
				.sum();
		int totalQuantity7d = recentProducts.stream().mapToInt(ProductEntity::getQuantity).sum();
		int rescueRate7d = totalQuantity7d == 0 ? 0 : (int) Math.round(100.0 * soldCount7d / totalQuantity7d);
		int rescueGoalPercent = store.getRescueGoalPercent() != null ? store.getRescueGoalPercent() : 70;

		model.addAttribute("store", store);
		model.addAttribute("isOpen", isOpen);
		model.addAttribute("rescueRate7d", rescueRate7d);
		model.addAttribute("rescueGoalPercent", rescueGoalPercent);
		model.addAttribute("registeredCount7d", registeredCount7d);
		model.addAttribute("soldCount7d", soldCount7d);
		model.addAttribute("totalQuantity7d", totalQuantity7d);
		return "superAdminView/storeDetail";
	}

	/**
	 * 추가됨 — 왜: 점주 본인용 /store/edit는 상호명/사업자번호/주소를 승인 심사 근거라는 이유로
	 * 일부러 막아뒀지만(StoreController.editSubmit 주석 참고), 운영자는 그 제한을 받을 이유가
	 * 없어서(오히려 오탈자·정보 오류를 고쳐줘야 하는 쪽) 전체 필드를 여는 별도 화면을 둔다.
	 * approvalStatus는 여기서 안 건드린다 — "입점 승인 대기" 액션이 생기면 그쪽에서 따로 처리할 것.
	 */
	@GetMapping("/stores/{id}/edit")
	public String storeEditForm(@PathVariable Long id, Model model) {
		StoreEntity store = storeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다: " + id));

		model.addAttribute("store", store);
		model.addAttribute("kakaoMapJsKey", kakaoMapJsKey);
		return "superAdminView/storeEdit";
	}

	@PostMapping("/stores/{id}/edit")
	public String storeEditSubmit(@PathVariable Long id,
	                               @RequestParam String storeName,
	                               @RequestParam String category,
	                               @RequestParam String phone,
	                               @RequestParam String address,
	                               @RequestParam(required = false) String businessNumber,
	                               @RequestParam(required = false) String operatingHours,
	                               @RequestParam(required = false) Double latitude,
	                               @RequestParam(required = false) Double longitude) {
		StoreEntity store = storeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다: " + id));

		if (storeName == null || storeName.isBlank() || category == null || category.isBlank()
				|| phone == null || phone.isBlank() || address == null || address.isBlank()) {
			return "redirect:/superadmin/stores/" + id + "/edit?error";
		}

		store.setStoreName(storeName.trim());
		store.setCategory(category.trim());
		store.setPhone(phone.trim());
		store.setAddress(address.trim());
		store.setBusinessNumber(businessNumber != null && !businessNumber.isBlank() ? businessNumber.trim() : null);
		store.setOperatingHours(operatingHours != null && !operatingHours.isBlank() ? operatingHours.trim() : null);
		store.setLatitude(latitude);
		store.setLongitude(longitude);
		storeRepository.save(store);

		return "redirect:/superadmin/stores/" + id;
	}

	/**
	 * 추가됨 — 왜: "매장 문의"/"유저 문의" 섹션을 InquiryEntity(강노은/김태훈이 이미 만든 실제 문의
	 * 게시판 백엔드)로 연동한다. storeId가 있으면 매장 문의, 없으면 서비스 전체 문의로 나눠서 보여준다.
	 * "신고 접수" 섹션은 아직 백엔드가 없어 데모 데이터 그대로 둔다(별도 스코프).
	 *
	 * 변경됨 — 왜: 신고접수/매장문의/유저문의를 한 화면에 쭉 늘어놓지 말고 탭으로 나눠달라는 요청 —
	 * 다른 필터 탭들과 동일하게 쿼리파라미터(tab)로 서버가 골라서 그리는 방식(JS 없음). 세 섹션 데이터
	 * 전부 그대로 계산해서 모델에 담아두고, 템플릿에서 tab 값에 맞는 것만 th:if로 보여준다 — 세 번 따로
	 * 쿼리 분기할 만큼 무거운 데이터가 아니라서 계산 자체는 안 나눔.
	 *
	 * 변경됨 — 왜: "글이 많아지면" 목록이 한없이 길어지니 페이지네이션 해달라는 요청 — 탭마다(신고/매장
	 * 문의/유저문의) 정렬은 그대로 다 계산해두고, 화면에 보여줄 페이지 슬라이스만 자른다(paginate 헬퍼).
	 * 같은 page 파라미터를 세 탭이 공유하지만 항상 tab이 같이 붙어서 링크가 생성되므로(각 탭의
	 * totalPages 안에서만 링크를 만듦) 탭을 오갈 때 엉뚱한 페이지가 섞이지 않는다.
	 *
	 * 변경됨 — 왜: 표로 바꾸면서 "답변일"·"문의자" 컬럼이 추가됐다(reports.html 직접 수정 후 요청) —
	 * 신고는 ComplaintEntity.resolvedAt을 그대로 쓰고, 매장/유저 문의는 댓글이 없어 answeredAtByInquiryId
	 * (그 문의의 첫 답변 댓글 createdAt, 없으면 미표시)를 새로 계산한다.
	 *
	 * 변경됨 — 왜: "탭 옮길 때마다 표가 왔다갔다해서 통일성이 없다"는 피드백 — 세 탭의 컬럼을 완전히
	 * 똑같은 순서·폭·헤더(제목/매장명/작성자/접수일/답변일/상태)로 통일했다. 탭마다 안 맞는 칸은 "-"로
	 * 채운다(유저 문의엔 매장명이 없음). 이러려면 매장 문의 탭에도 작성자가 필요해져서
	 * userInquiryAuthorNames를 inquiryAuthorNames로 넓혀 store+user 문의 전체(all)에서 계산한다.
	 */
	@GetMapping("/reports")
	public String reports(@RequestParam(required = false) String tab,
	                       @RequestParam(defaultValue = "0") int page, Model model) {
		String normalizedTab = "store".equals(tab) || "user".equals(tab) ? tab : "report";
		model.addAttribute("tab", normalizedTab);
		model.addAttribute("page", page);

		List<ComplaintEntity> allComplaints = complaintRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
		model.addAttribute("complaints", paginate(allComplaints, page, PAGE_SIZE));
		model.addAttribute("complaintsTotalPages", totalPages(allComplaints.size(), PAGE_SIZE));

		List<InquiryEntity> all = inquiryRepository.findAll();

		Map<Long, Long> commentCounts = inquiryCommentRepository.findAll().stream()
				.collect(Collectors.groupingBy(InquiryCommentEntity::getInquiryId, Collectors.counting()));

		Map<Long, LocalDateTime> answeredAtByInquiryId = new HashMap<>();
		for (InquiryCommentEntity c : inquiryCommentRepository.findAll()) {
			answeredAtByInquiryId.merge(c.getInquiryId(), c.getCreatedAt(),
					(existing, candidate) -> existing.isBefore(candidate) ? existing : candidate);
		}

		Comparator<InquiryEntity> byNewest =
				Comparator.comparing(InquiryEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

		List<InquiryEntity> storeInquiries = all.stream()
				.filter(i -> i.getStoreId() != null)
				.sorted(byNewest)
				.toList();
		List<InquiryEntity> userInquiries = all.stream()
				.filter(i -> i.getStoreId() == null)
				.sorted(byNewest)
				.toList();

		Map<Long, String> storeNames = storeRepository.findAllById(
						storeInquiries.stream().map(InquiryEntity::getStoreId).distinct().toList()).stream()
				.collect(Collectors.toMap(StoreEntity::getId, StoreEntity::getStoreName));

		Map<Long, String> inquiryAuthorNames = userRepository.findAllById(
						all.stream().map(InquiryEntity::getUserId).distinct().toList()).stream()
				.collect(Collectors.toMap(UserEntity::getId, UserEntity::getNickname));

		model.addAttribute("storeInquiries", paginate(storeInquiries, page, PAGE_SIZE));
		model.addAttribute("storeInquiriesTotalPages", totalPages(storeInquiries.size(), PAGE_SIZE));
		model.addAttribute("userInquiries", paginate(userInquiries, page, PAGE_SIZE));
		model.addAttribute("userInquiriesTotalPages", totalPages(userInquiries.size(), PAGE_SIZE));
		model.addAttribute("commentCounts", commentCounts);
		model.addAttribute("storeNames", storeNames);
		model.addAttribute("answeredAtByInquiryId", answeredAtByInquiryId);
		model.addAttribute("inquiryAuthorNames", inquiryAuthorNames);
		return "superAdminView/reports";
	}

	/**
	 * 추가됨 — 왜: "답변" 버튼 처리. 슈퍼어드민 세션/식별자가 아직 없어(문창호 role 분리 작업 전) 답변
	 * 작성자를 특정할 수 없다 — 임시로 role=ADMIN인 첫 계정을 답변자로 쓴다. role 분리가 끝나면
	 * 세션의 실제 운영자 계정으로 교체할 것.
	 * 변경됨 — 왜: 답변 폼이 목록이 아니라 상세 페이지로 옮겨가서(문의 클릭→상세 요청), 답변 후엔
	 * 목록이 아니라 그 상세 페이지로 돌아가야 방금 단 답변이 바로 보인다.
	 */
	@PostMapping("/inquiries/{id}/reply")
	public String replyToInquiry(@PathVariable Long id, @RequestParam String content) {
		if (content != null && !content.isBlank()) {
			UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "운영자 계정을 찾을 수 없습니다."));
			inquiryService.addComment(admin.getId(), id, content.trim());
		}
		return "redirect:/superadmin/inquiries/" + id;
	}

	/**
	 * 추가됨 — 왜: 문의 목록에서 글을 클릭하면 제목/내용만 보이던 걸 벗어나 상세 페이지(작성자/사진/댓글)
	 * 로 이동하게 해달라는 요청. 컨슈머용 inquiryView/detail.html과 같은 InquiryService 메서드를
	 * 그대로 재사용한다 — 댓글의 canDelete 계산에 필요한 (userId, role)은 replyToInquiry와 동일한
	 * 스톱갭(role=ADMIN 첫 계정)을 쓴다.
	 */
	@GetMapping("/inquiries/{id}")
	public String inquiryDetail(@PathVariable Long id, Model model) {
		InquiryEntity inquiry = inquiryService.getInquiry(id);
		UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).orElse(null);
		Long adminId = admin != null ? admin.getId() : null;

		model.addAttribute("inquiry", inquiry);
		model.addAttribute("authorName", inquiryService.getAuthorName(inquiry.getUserId()));
		model.addAttribute("authorExists", userRepository.existsById(inquiry.getUserId()));
		model.addAttribute("storeName", inquiryService.getStoreName(inquiry.getStoreId()));
		model.addAttribute("comments", inquiryService.getComments(id, adminId, UserEntity.ROLE_ADMIN));
		return "superAdminView/inquiryDetail";
	}

	/**
	 * 추가됨 — 왜: "신고 접수" 탭도 매장 문의/유저 문의처럼 글을 클릭하면 상세를 보고 답변할 수 있게
	 * 해달라는 요청. 문의와 달리 신고는 댓글 스레드가 아니라 답변 하나만 남기면 끝(ComplaintEntity에
	 * adminReply 필드 하나) — 답변을 달면 그 자리에서 상태가 자동으로 처리완료(RESOLVED)로 바뀐다.
	 */
	@GetMapping("/complaints/{id}")
	public String complaintDetail(@PathVariable Long id, Model model) {
		ComplaintEntity complaint = complaintRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다: " + id));
		model.addAttribute("complaint", complaint);
		return "superAdminView/complaintDetail";
	}

	@PostMapping("/complaints/{id}/reply")
	public String replyToComplaint(@PathVariable Long id, @RequestParam String content) {
		ComplaintEntity complaint = complaintRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다: " + id));

		if (content != null && !content.isBlank()) {
			complaint.setAdminReply(content.trim());
			complaint.setStatus(ComplaintEntity.STATUS_RESOLVED);
			complaint.setResolvedAt(LocalDateTime.now());
			complaintRepository.save(complaint);
		}
		return "redirect:/superadmin/complaints/" + id;
	}

	@GetMapping("/notices")
	public String notices(@RequestParam(defaultValue = "0") int page, Model model) {
		List<NoticeEntity> allNotices = noticeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
		model.addAttribute("notices", paginate(allNotices, page, PAGE_SIZE));
		model.addAttribute("totalPages", totalPages(allNotices.size(), PAGE_SIZE));
		model.addAttribute("page", page);
		return "superAdminView/notices";
	}

	@GetMapping("/notices/new")
	public String noticeNewForm() {
		return "superAdminView/noticeNew";
	}

	/**
	 * 변경됨 — 왜: "게시 기간을 정하고 싶다"는 요청 — 시작일/종료일(둘 다 선택, 비우면 무제한)을 같이
	 * 받는다. 종료일이 시작일보다 빠르면 저장 자체를 막는다(사용자 실수 방지).
	 */
	@PostMapping("/notices/new")
	public String noticeCreate(@RequestParam String title, @RequestParam String content,
	                            @RequestParam(required = false) String publishStartAt,
	                            @RequestParam(required = false) String publishEndAt) {
		if (title == null || title.isBlank() || content == null || content.isBlank()) {
			return "redirect:/superadmin/notices/new?error";
		}

		LocalDate startAt = parseNoticeDate(publishStartAt);
		LocalDate endAt = parseNoticeDate(publishEndAt);
		if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
			return "redirect:/superadmin/notices/new?error=period";
		}

		NoticeEntity notice = NoticeEntity.builder()
				.title(title.trim())
				.content(content.trim())
				.published(true)
				.publishStartAt(startAt)
				.publishEndAt(endAt)
				.build();
		noticeRepository.save(notice);

		return "redirect:/superadmin/notices";
	}

	private LocalDate parseNoticeDate(String value) {
		return value == null || value.isBlank() ? null : LocalDate.parse(value);
	}

	@GetMapping("/notices/{id}")
	public String noticeDetail(@PathVariable Long id, Model model) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다: " + id));

		model.addAttribute("notice", notice);
		return "superAdminView/noticeDetail";
	}

	@GetMapping("/notices/{id}/edit")
	public String noticeEditForm(@PathVariable Long id, Model model) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다: " + id));

		model.addAttribute("notice", notice);
		return "superAdminView/noticeEdit";
	}

	@PostMapping("/notices/{id}/edit")
	public String noticeEditSubmit(@PathVariable Long id,
	                                @RequestParam String title,
	                                @RequestParam String content,
	                                @RequestParam(required = false) Boolean published,
	                                @RequestParam(required = false) String publishStartAt,
	                                @RequestParam(required = false) String publishEndAt) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다: " + id));

		if (title == null || title.isBlank() || content == null || content.isBlank()) {
			return "redirect:/superadmin/notices/" + id + "/edit?error";
		}

		LocalDate startAt = parseNoticeDate(publishStartAt);
		LocalDate endAt = parseNoticeDate(publishEndAt);
		if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
			return "redirect:/superadmin/notices/" + id + "/edit?error=period";
		}

		notice.setTitle(title.trim());
		notice.setContent(content.trim());
		notice.setPublished(Boolean.TRUE.equals(published));
		notice.setPublishStartAt(startAt);
		notice.setPublishEndAt(endAt);
		noticeRepository.save(notice);

		return "redirect:/superadmin/notices/" + id;
	}

	/**
	 * 추가됨 — 왜: "게시글 내리기"를 수정 화면까지 안 들어가고 상세에서 바로 할 수 있게 해달라는 요청 —
	 * members.html의 정지/정지해제 버튼과 같은 패턴(즉시 토글 + 상세로 리다이렉트).
	 */
	@PostMapping("/notices/{id}/unpublish")
	public String noticeUnpublish(@PathVariable Long id) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다: " + id));
		notice.setPublished(false);
		noticeRepository.save(notice);
		return "redirect:/superadmin/notices/" + id;
	}

	@PostMapping("/notices/{id}/publish")
	public String noticePublish(@PathVariable Long id) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다: " + id));
		notice.setPublished(true);
		noticeRepository.save(notice);
		return "redirect:/superadmin/notices/" + id;
	}

	@PostMapping("/notices/{id}/delete")
	public String noticeDelete(@PathVariable Long id) {
		if (!noticeRepository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다: " + id);
		}
		noticeRepository.deleteById(id);
		return "redirect:/superadmin/notices";
	}

	@GetMapping("/codes")
	public String codes() {
		return "superAdminView/codes";
	}

	/**
	 * 추가됨 — 왜: 알림 패널에서 알림 하나를 클릭했을 때. NotificationController#open(/user/alerts/{id})와
	 * 동일한 패턴 — 읽음 처리 후 linkUrl로 보낸다(없으면 대시보드로).
	 */
	@GetMapping("/notifications/{id}")
	public String openNotification(@PathVariable Long id) {
		UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).orElse(null);
		if (admin == null) {
			return "redirect:/superadmin/dashboard";
		}
		String linkUrl = notificationService.markRead(admin.getId(), id);
		return linkUrl != null && !linkUrl.isBlank() ? "redirect:" + linkUrl : "redirect:/superadmin/dashboard";
	}

	@PostMapping("/notifications/read-all")
	public String readAllNotifications(@RequestHeader(value = "Referer", required = false) String referer) {
		UserEntity admin = userRepository.findFirstByRole(UserEntity.ROLE_ADMIN).orElse(null);
		if (admin != null) {
			notificationService.markAllRead(admin.getId());
		}
		return "redirect:" + (referer != null && !referer.isBlank() ? referer : "/superadmin/dashboard");
	}
}
