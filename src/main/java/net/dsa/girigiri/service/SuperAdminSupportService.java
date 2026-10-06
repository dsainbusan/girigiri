package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.SupportReportsDataDto;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.InquiryCommentEntity;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.InquiryCommentRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 슈퍼어드민 "신고·문의(Support)" 도메인 서비스 (2026-09-03, 레이어 규칙 2단계 — SuperAdminController 도메인 분리).
 *
 * SuperAdminController의 신고 접수/매장 문의/유저 문의 관련 Repository 직접 호출·상태 변경 로직을 옮겨온다.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminSupportService {

	private final ComplaintRepository complaintRepository;
	private final InquiryRepository inquiryRepository;
	private final InquiryCommentRepository inquiryCommentRepository;
	private final StoreRepository storeRepository;
	private final UserRepository userRepository;
	private final InquiryService inquiryService;
	private final LookupService lookupService;

	/**
	 * "매장 문의"/"유저 문의" 섹션을 InquiryEntity로 연동한다. storeId가 있으면 매장 문의, 없으면
	 * 서비스 전체 문의로 나눈다. 신고는 ComplaintEntity.resolvedAt을 그대로 쓰고, 매장/유저 문의는
	 * 댓글이 없어 answeredAtByInquiryId(그 문의의 첫 답변 댓글 createdAt, 없으면 미표시)를 계산한다.
	 * 페이지 슬라이스는 컨트롤러가 하므로 여기선 정렬까지 끝난 전체 목록을 돌려준다.
	 */
	@Transactional(readOnly = true)
	public SupportReportsDataDto getReportsData() {
		List<ComplaintEntity> allComplaints = complaintRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

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

		return new SupportReportsDataDto(allComplaints, storeInquiries, userInquiries,
				commentCounts, storeNames, answeredAtByInquiryId, inquiryAuthorNames);
	}

	/**
	 * "답변" 버튼 처리. 답변 작성자는 컨트롤러가 세션에서 꺼내 넘겨준 실제 로그인 관리자
	 * (adminId)다 — 2026-09-29, "role=ADMIN인 첫 계정"으로 대신하던 스톱갭 제거.
	 */
	@Transactional
	public void replyToInquiry(Long adminId, Long id, String content) {
		if (content != null && !content.isBlank()) {
			inquiryService.addComment(adminId, id, content.trim());
		}
	}

	@Transactional(readOnly = true)
	public boolean userExists(Long userId) {
		return userRepository.existsById(userId);
	}

	// 추가됨 (2026-10-06, 신고 기반 리팩터링) — "환불 완료(일시, 처리자)" 배지에 쓸 관리자 표시 이름.
	// RefundEntity.requestedBy는 userId만 들고 있어서(스냅샷 컬럼 없음) 매번 조회해서 보여준다.
	@Transactional(readOnly = true)
	public String adminDisplayName(Long userId) {
		return userRepository.findById(userId).map(UserEntity::getNickname).orElse("운영자");
	}

	/**
	 * "신고 접수" 탭도 매장 문의/유저 문의처럼 답변할 수 있게 한다. 문의와 달리 신고는 댓글 스레드가
	 * 아니라 답변 하나만 남기면 끝(ComplaintEntity에 adminReply 필드 하나) — 답변을 달면 그 자리에서
	 * 상태가 자동으로 처리완료(RESOLVED)로 바뀐다.
	 */
	@Transactional
	public void replyToComplaint(Long id, String content) {
		ComplaintEntity complaint = lookupService.getComplaint(id);

		if (content != null && !content.isBlank()) {
			complaint.resolve(content.trim());
			complaintRepository.save(complaint);
		}
	}
}
