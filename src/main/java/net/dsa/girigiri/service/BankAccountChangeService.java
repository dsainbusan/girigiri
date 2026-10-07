package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.BankAccountChangeRequestRowDto;
import net.dsa.girigiri.domain.entity.BankAccountChangeRequestEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.BankAccountChangeRequestRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.BankAccountMaskUtil;
import net.dsa.girigiri.util.FileStorageUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — 승인된 매장의 정산 계좌 "등록/변경 신청 → 슈퍼어드민 승인" 흐름.
 *
 * 입점 신청(최초 등록)의 계좌는 AuthService#ownerApply가 StoreEntity에 바로 저장하고(통째로 매장
 * 승인 심사에 같이 포함됨, 별도 승인 단계 없음) — 승인된 뒤 계좌를 "바꾸고" 싶을 때만 이 서비스를
 * 거친다. 기존 계좌가 아예 없던 매장(미등록)이 처음 등록하는 것도 같은 흐름으로 처리한다(oldXxx
 * 스냅샷이 비어있을 뿐, 로직은 동일) — "등록"과 "변경"을 굳이 나누지 않는다.
 *
 * 신청 직후 StoreEntity.accountStatus를 UNDER_REVIEW로 바꿔서 승인 전까지 그 매장 정산은 지급
 * 보류된다(SuperAdminSettlementService 참고). 승인되면 StoreEntity에 실제 반영 + NORMAL로,
 * 반려되면 신청 전 상태로 되돌린다(기존 정상 계좌가 있었으면 NORMAL, 처음 등록 시도였으면
 * UNREGISTERED).
 */
@Service
@RequiredArgsConstructor
public class BankAccountChangeService {

	private final BankAccountChangeRequestRepository changeRequestRepository;
	private final StoreRepository storeRepository;
	private final FileStorageUtil fileStorageUtil;

	/**
	 * 점주가 계좌 등록/변경을 신청한다. 통장 사본은 필수 — 입점 신청과 동일한 기준(서버 단 검증,
	 * 프론트 required만 믿지 않음).
	 */
	@Transactional
	public BankAccountChangeRequestEntity submit(StoreEntity store, Long requestedByUserId,
	                                              String newBankName, String newBankAccount,
	                                              String newAccountHolder, MultipartFile passbook) {
		if (newBankName == null || newBankName.isBlank()
				|| newBankAccount == null || newBankAccount.isBlank()
				|| newAccountHolder == null || newAccountHolder.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "은행, 계좌번호, 예금주를 모두 입력해 주세요.");
		}
		if (passbook == null || passbook.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "통장 사본을 첨부해 주세요.");
		}
		if (changeRequestRepository.existsByStoreIdAndStatus(store.getId(), BankAccountChangeRequestEntity.STATUS_PENDING)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리 대기 중인 계좌 변경 신청이 있어요. 운영자 처리를 기다려 주세요.");
		}

		String passbookUrl = fileStorageUtil.storePrivate(passbook, "passbooks");

		BankAccountChangeRequestEntity request = BankAccountChangeRequestEntity.builder()
				.storeId(store.getId())
				.requestedBy(requestedByUserId)
				.oldBankName(store.getBankName())
				.oldBankAccountMasked(BankAccountMaskUtil.mask(store.getBankAccount()))
				.oldAccountHolder(store.getAccountHolder())
				.newBankName(newBankName.trim())
				.newBankAccount(newBankAccount.trim())
				.newBankAccountMasked(BankAccountMaskUtil.mask(newBankAccount.trim()))
				.newAccountHolder(newAccountHolder.trim())
				.newPassbookImageUrl(passbookUrl)
				.build();
		changeRequestRepository.save(request);

		store.setAccountStatus(StoreEntity.ACCOUNT_STATUS_UNDER_REVIEW);
		storeRepository.save(store);

		return request;
	}

	/** 승인 — 신청 내용을 StoreEntity에 실제 반영하고 계좌 상태를 NORMAL로 되돌린다. */
	@Transactional
	public void approve(Long requestId, Long adminId) {
		BankAccountChangeRequestEntity request = changeRequestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new EntityNotFoundException("계좌 변경 신청을 찾을 수 없습니다. id=" + requestId));
		if (!BankAccountChangeRequestEntity.STATUS_PENDING.equals(request.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 신청이에요.");
		}
		StoreEntity store = storeRepository.findById(request.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + request.getStoreId()));

		store.setBankName(request.getNewBankName());
		store.setBankAccount(request.getNewBankAccount());
		store.setAccountHolder(request.getNewAccountHolder());
		store.setPassbookImageUrl(request.getNewPassbookImageUrl());
		store.setAccountStatus(StoreEntity.ACCOUNT_STATUS_NORMAL);
		storeRepository.save(store);

		request.approve(adminId);
		changeRequestRepository.save(request);
	}

	/** 반려 — 신청 전 계좌 상태로 되돌린다(원래 정상 계좌가 있었으면 NORMAL, 처음 등록 시도였으면 UNREGISTERED). */
	@Transactional
	public void reject(Long requestId, Long adminId, String reason) {
		BankAccountChangeRequestEntity request = changeRequestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new EntityNotFoundException("계좌 변경 신청을 찾을 수 없습니다. id=" + requestId));
		if (!BankAccountChangeRequestEntity.STATUS_PENDING.equals(request.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 신청이에요.");
		}
		StoreEntity store = storeRepository.findById(request.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + request.getStoreId()));

		store.setAccountStatus(request.getOldBankAccountMasked() != null
				? StoreEntity.ACCOUNT_STATUS_NORMAL
				: StoreEntity.ACCOUNT_STATUS_UNREGISTERED);
		storeRepository.save(store);

		request.reject(adminId, reason != null && !reason.isBlank() ? reason.trim() : "운영자 반려");
		changeRequestRepository.save(request);
	}

	@Transactional(readOnly = true)
	public List<BankAccountChangeRequestEntity> getPendingRequests() {
		return changeRequestRepository.findByStatusOrderByRequestedAtAsc(BankAccountChangeRequestEntity.STATUS_PENDING);
	}

	/** 슈퍼어드민 "계좌 변경 심사" 화면용 — 지급 대기(settlements.html)와 동일하게 매장명을 얹은 행. */
	@Transactional(readOnly = true)
	public List<BankAccountChangeRequestRowDto> getPendingRequestRows() {
		return toRows(getPendingRequests());
	}

	@Transactional(readOnly = true)
	public List<BankAccountChangeRequestRowDto> getRecentRequestRows() {
		return toRows(changeRequestRepository.findAll().stream()
				.sorted((a, b) -> b.getRequestedAt().compareTo(a.getRequestedAt()))
				.limit(100)
				.toList());
	}

	private List<BankAccountChangeRequestRowDto> toRows(List<BankAccountChangeRequestEntity> requests) {
		Map<Long, StoreEntity> storesById = storeRepository.findAllById(
				requests.stream().map(BankAccountChangeRequestEntity::getStoreId).distinct().toList())
				.stream().collect(Collectors.toMap(StoreEntity::getId, s -> s));
		return requests.stream()
				.map(r -> new BankAccountChangeRequestRowDto(
						r.getId(),
						r.getStoreId(),
						storesById.containsKey(r.getStoreId()) ? storesById.get(r.getStoreId()).getStoreName() : "알 수 없음",
						r.getRequestedAt(),
						r.getStatus(),
						statusLabel(r.getStatus()),
						r.getNewBankName(),
						r.getNewBankAccountMasked(),
						r.getNewAccountHolder(),
						r.getNewPassbookImageUrl() != null,
						r.getRejectReason()))
				.toList();
	}

	private String statusLabel(String status) {
		return switch (status) {
			case BankAccountChangeRequestEntity.STATUS_PENDING -> "대기중";
			case BankAccountChangeRequestEntity.STATUS_APPROVED -> "승인됨";
			case BankAccountChangeRequestEntity.STATUS_REJECTED -> "반려됨";
			default -> status;
		};
	}

	@Transactional(readOnly = true)
	public List<BankAccountChangeRequestEntity> getHistoryForStore(Long storeId) {
		return changeRequestRepository.findByStoreIdOrderByRequestedAtDesc(storeId);
	}

	// 추가됨 (2026-10-07) — PassbookFileController가 변경 신청 건의 통장 사본을 스트리밍할 때 쓴다.
	// 컨트롤러는 Repository를 직접 주입받지 않는다는 레이어 규칙이라 서비스에 조회 메서드를 둔다.
	@Transactional(readOnly = true)
	public BankAccountChangeRequestEntity getRequestOrThrow(Long requestId) {
		return changeRequestRepository.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("계좌 변경 신청을 찾을 수 없습니다. id=" + requestId));
	}
}
