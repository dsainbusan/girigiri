package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.InquiryCommentRowDto;
import net.dsa.girigiri.domain.dto.InquiryRowDto;
import net.dsa.girigiri.domain.entity.InquiryCommentEntity;
import net.dsa.girigiri.domain.entity.InquiryEntity;
import net.dsa.girigiri.domain.entity.NotificationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.InquiryCommentRepository;
import net.dsa.girigiri.repository.InquiryRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import net.dsa.girigiri.util.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InquiryService {

	private final InquiryRepository inquiryRepository;
	private final InquiryCommentRepository inquiryCommentRepository;
	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final FileStorageUtil fileStorageUtil;
	private final NotificationService notificationService;

	// 강노은: 문의 사진 파일이 저장되는 하위 디렉터리 이름 (upload/inquiries/...)
	private static final String INQUIRY_IMAGE_SUBDIR = "inquiries";

	/**
	 * 작성자 본인 / 문의 대상 가게의 사장님 / 관리자만 열람 가능하다는 요구사항에 맞춰
	 * 전체 목록이 아니라 "이 사용자가 볼 수 있는 문의"만 걸러서 반환한다.
	 */
	public List<InquiryRowDto> getInquiriesForUser(Long userId, String role) {
		Long ownedStoreId = storeRepository.findByOwnerId(userId).map(StoreEntity::getId).orElse(null);
		boolean isAdmin = UserEntity.ROLE_ADMIN.equals(role);

		List<InquiryEntity> inquiries = inquiryRepository.findAll().stream()
				.filter(i -> isAdmin || userId.equals(i.getUserId()) || (ownedStoreId != null && ownedStoreId.equals(i.getStoreId())))
				.toList();

		return toRowDtos(inquiries, userId, role);
	}

	/**
	 * 고객센터 "내 문의내역" 탭 전용 — 문의 대상 가게 사장님 시야는 빼고, 내가 직접 쓴 문의만.
	 */
	public List<InquiryRowDto> getMyInquiries(Long userId, String role) {
		List<InquiryEntity> inquiries = inquiryRepository.findAll().stream()
				.filter(i -> userId.equals(i.getUserId()))
				.toList();

		return toRowDtos(inquiries, userId, role);
	}

	private List<InquiryRowDto> toRowDtos(List<InquiryEntity> inquiries, Long userId, String role) {
		List<InquiryEntity> sorted = inquiries.stream()
				.sorted(Comparator.comparing(InquiryEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		Map<Long, String> nicknameByUserId = nicknameMap(sorted.stream().map(InquiryEntity::getUserId).distinct().toList());
		Map<Long, String> storeNameById = storeRepository.findAllById(
						sorted.stream().map(InquiryEntity::getStoreId).filter(java.util.Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(StoreEntity::getId, StoreEntity::getStoreName));

		Map<Long, Long> commentCountByInquiryId = inquiryCommentRepository.findAll().stream()
				.collect(Collectors.groupingBy(InquiryCommentEntity::getInquiryId, Collectors.counting()));

		return sorted.stream()
				.map(i -> new InquiryRowDto(
						i.getId(),
						i.getTitle(),
						nicknameByUserId.getOrDefault(i.getUserId(), "익명"),
						i.getStoreId() == null ? null : storeNameById.get(i.getStoreId()),
						commentCountByInquiryId.getOrDefault(i.getId(), 0L).intValue(),
						relativeLabel(i.getCreatedAt()),
						canDelete(i.getUserId(), userId, role)
				))
				.toList();
	}

	public InquiryEntity getInquiry(Long id) {
		return inquiryRepository.findById(id)
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다: " + id));
	}

	/** 작성자 본인 / 문의 대상 가게 사장님 / 관리자만 true. */
	public boolean canView(InquiryEntity inquiry, Long userId, String role) {
		if (UserEntity.ROLE_ADMIN.equals(role)) {
			return true;
		}
		if (userId.equals(inquiry.getUserId())) {
			return true;
		}
		if (inquiry.getStoreId() == null) {
			return false;
		}
		return storeRepository.findByOwnerId(userId)
				.map(store -> store.getId().equals(inquiry.getStoreId()))
				.orElse(false);
	}

	/**
	 * 삭제 권한은 열람 권한보다 좁다 — 가게 사장님은 문의를 볼 순 있어도 지울 순 없다.
	 * 작성자 본인 / 관리자만 true.
	 */
	private boolean canDelete(Long authorUserId, Long userId, String role) {
		return UserEntity.ROLE_ADMIN.equals(role) || (userId != null && userId.equals(authorUserId));
	}

	public boolean canDeleteInquiry(InquiryEntity inquiry, Long userId, String role) {
		return canDelete(inquiry.getUserId(), userId, role);
	}

	public String getAuthorName(Long userId) {
		return userRepository.findById(userId).map(UserEntity::getNickname).filter(n -> n != null && !n.isBlank()).orElse("익명");
	}

	public String getStoreName(Long storeId) {
		if (storeId == null) {
			return null;
		}
		return storeRepository.findById(storeId).map(StoreEntity::getStoreName).orElse(null);
	}

	public List<InquiryCommentRowDto> getComments(Long inquiryId, Long currentUserId, String role) {
		List<InquiryCommentEntity> comments = inquiryCommentRepository.findAll().stream()
				.filter(c -> inquiryId.equals(c.getInquiryId()))
				.sorted(Comparator.comparing(InquiryCommentEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();

		Map<Long, String> nicknameByUserId = nicknameMap(comments.stream().map(InquiryCommentEntity::getUserId).distinct().toList());

		return comments.stream()
				.map(c -> new InquiryCommentRowDto(
						c.getId(),
						nicknameByUserId.getOrDefault(c.getUserId(), "익명"),
						c.getContent(),
						relativeLabel(c.getCreatedAt()),
						canDelete(c.getUserId(), currentUserId, role)
				))
				.toList();
	}

	// 변경됨 (강노은) — 왜: 문의에 사진 첨부 기능 추가(예: 상품 하자 사진 등). 문의는 수정 기능이 없어서
	// (등록만 가능) 리뷰처럼 "새 파일로 교체/제거" 같은 분기 없이 등록 시 한 번만 저장하면 된다.
	@Transactional
	public Long createInquiry(Long userId, Long storeId, String title, String content, MultipartFile imagePhoto) {
		InquiryEntity inquiry = InquiryEntity.builder()
				.userId(userId)
				.storeId(storeId)
				.title(title == null ? "" : title.trim())
				.content(content == null ? "" : content.trim())
				.imageUrl(fileStorageUtil.store(imagePhoto, INQUIRY_IMAGE_SUBDIR))
				.build();
		return inquiryRepository.save(inquiry).getId();
	}

	// 변경됨 (강노은) — 왜: "댓글 알림 발송" 요구사항. 문의 게시판은 내 코드라 다른 사람 파일에 훅을
	// 심을 필요 없이 여기서 바로 알림을 만든다(예약·상품처럼 남의 코드를 스캔하는 방식과는 다름).
	@Transactional
	public void addComment(Long userId, Long inquiryId, String content) {
		InquiryCommentEntity comment = inquiryCommentRepository.save(InquiryCommentEntity.builder()
				.inquiryId(inquiryId)
				.userId(userId)
				.content(content == null ? "" : content.trim())
				.build());
		notifyOtherParty(userId, inquiryId, comment.getId());
	}

	/**
	 * 댓글이 달리면 "댓글 단 사람 말고" 이 문의의 다른 당사자에게 알린다.
	 * 작성자가 아닌 사람(가게 사장님/운영자)이 댓글을 달면 → 작성자에게.
	 * 작성자 본인이 후속 댓글을 달면 → 그 문의 대상 가게 사장님에게(운영자용 일반 문의는 대상 없음).
	 */
	private void notifyOtherParty(Long commenterId, Long inquiryId, Long commentId) {
		InquiryEntity inquiry = inquiryRepository.findById(inquiryId).orElse(null);
		// 강노은: 리뷰 지적 사항 — commenterId/inquiry.getUserId()가 null이면 이 아래
		// notificationService.createNotification(null, ...) 이 SseEmitterRegistry의
		// ConcurrentHashMap.get(null)에서 NPE를 던진다(HashMap과 달리 null 키를 못 받음).
		// 지금은 컨트롤러가 항상 로그인된 userId만 넘겨서 실제로는 안 터지지만, 방어적으로 막아둔다.
		if (inquiry == null || commenterId == null || inquiry.getUserId() == null) {
			return;
		}
		String linkUrl = "/user/inquiries/" + inquiryId;
		String message = "\"" + shorten(inquiry.getTitle()) + "\"에 새 댓글이 달렸어요.";

		if (!commenterId.equals(inquiry.getUserId())) {
			notificationService.createNotification(inquiry.getUserId(), NotificationEntity.TYPE_INQUIRY_COMMENT,
					message, linkUrl, "inquiry_comment:" + commentId + ":author");
			return;
		}
		if (inquiry.getStoreId() == null) {
			return; // 운영자 대상 일반 문의라 알릴 "가게 사장님"이 없음
		}
		storeRepository.findById(inquiry.getStoreId())
				.map(StoreEntity::getOwnerId)
				.filter(ownerId -> ownerId != null && !ownerId.equals(commenterId))
				.ifPresent(ownerId -> notificationService.createNotification(ownerId, NotificationEntity.TYPE_INQUIRY_COMMENT,
						message, linkUrl, "inquiry_comment:" + commentId + ":owner"));
	}

	private String shorten(String title) {
		if (title == null) {
			return "";
		}
		return title.length() > 20 ? title.substring(0, 20) + "…" : title;
	}

	public InquiryCommentEntity getComment(Long commentId) {
		return inquiryCommentRepository.findById(commentId)
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다: " + commentId));
	}

	/** FK 제약이 없어(Long id 컬럼만 씀 — SKILL.md 컨벤션) 문의 삭제 시 댓글을 직접 같이 지워야 고아 row가 안 남는다. */
	@Transactional
	public void deleteInquiry(Long userId, String role, Long inquiryId) {
		InquiryEntity inquiry = getInquiry(inquiryId);
		if (!canDeleteInquiry(inquiry, userId, role)) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "이 문의를 삭제할 권한이 없습니다.");
		}
		List<InquiryCommentEntity> comments = inquiryCommentRepository.findAll().stream()
				.filter(c -> inquiryId.equals(c.getInquiryId()))
				.toList();
		inquiryCommentRepository.deleteAll(comments);
		fileStorageUtil.deleteIfOwned(inquiry.getImageUrl(), INQUIRY_IMAGE_SUBDIR);
		inquiryRepository.delete(inquiry);
	}

	@Transactional
	public void deleteComment(Long userId, String role, Long commentId) {
		InquiryCommentEntity comment = getComment(commentId);
		if (!canDelete(comment.getUserId(), userId, role)) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "이 댓글을 삭제할 권한이 없습니다.");
		}
		inquiryCommentRepository.delete(comment);
	}

	private Map<Long, String> nicknameMap(List<Long> userIds) {
		return userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(UserEntity::getId, u -> u.getNickname() == null || u.getNickname().isBlank() ? "익명" : u.getNickname()));
	}

	private String relativeLabel(LocalDateTime createdAt) {
		if (createdAt == null) {
			return "";
		}
		long days = ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now());
		if (days <= 0) {
			return "오늘";
		}
		if (days == 1) {
			return "어제";
		}
		return days + "일 전";
	}
}
