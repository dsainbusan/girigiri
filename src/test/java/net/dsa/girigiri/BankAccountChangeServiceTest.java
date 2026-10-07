package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.BankAccountChangeRequestEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.BankAccountChangeRequestRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.service.BankAccountChangeService;
import net.dsa.girigiri.util.FileStorageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — BankAccountChangeService 핵심 로직 단위 테스트.
 * "계좌 변경 승인/반려 반영 시점"(사용자 요구사항 "작업 방식" 항목)을 검증한다:
 *   - 신청 즉시 StoreEntity.accountStatus가 UNDER_REVIEW로 바뀌어 지급이 보류되는지
 *   - 승인 시 신청 내용이 StoreEntity에 그대로 반영되고 NORMAL로 돌아오는지
 *   - 반려 시 신청 전 상태(기존 정상 계좌 있었으면 NORMAL, 없었으면 UNREGISTERED)로 되돌아오는지
 */
@ExtendWith(MockitoExtension.class)
class BankAccountChangeServiceTest {

	@Mock
	private BankAccountChangeRequestRepository changeRequestRepository;
	@Mock
	private StoreRepository storeRepository;
	@Mock
	private FileStorageUtil fileStorageUtil;

	@InjectMocks
	private BankAccountChangeService service;

	private MockMultipartFile passbook() {
		return new MockMultipartFile("passbook", "passbook.jpg", "image/jpeg", new byte[]{1, 2, 3});
	}

	@Test
	void 필수값이_비어있으면_거부된다() {
		StoreEntity store = StoreEntity.builder().id(1L).build();

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> service.submit(store, 100L, "", "11112222", "홍길동", passbook()));
		assertEquals(400, e.getStatusCode().value());
	}

	@Test
	void 통장_사본이_없으면_거부된다() {
		StoreEntity store = StoreEntity.builder().id(1L).build();

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> service.submit(store, 100L, "국민", "11112222", "홍길동", null));
		assertEquals(400, e.getStatusCode().value());
	}

	@Test
	void 이미_대기중인_신청이_있으면_거부된다() {
		StoreEntity store = StoreEntity.builder().id(1L).build();
		when(changeRequestRepository.existsByStoreIdAndStatus(1L, BankAccountChangeRequestEntity.STATUS_PENDING)).thenReturn(true);

		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> service.submit(store, 100L, "국민", "11112222", "홍길동", passbook()));
		assertEquals(409, e.getStatusCode().value());
	}

	@Test
	void 신청하면_매장_계좌상태가_심사중으로_바뀐다() {
		StoreEntity store = StoreEntity.builder().id(1L).accountStatus(StoreEntity.ACCOUNT_STATUS_NORMAL).build();
		when(changeRequestRepository.existsByStoreIdAndStatus(1L, BankAccountChangeRequestEntity.STATUS_PENDING)).thenReturn(false);
		when(fileStorageUtil.storePrivate(any(), eq("passbooks"))).thenReturn("passbooks/test.jpg");

		service.submit(store, 100L, "국민", "11112222", "홍길동", passbook());

		assertEquals(StoreEntity.ACCOUNT_STATUS_UNDER_REVIEW, store.getAccountStatus());
		verify(changeRequestRepository).save(any(BankAccountChangeRequestEntity.class));
	}

	@Test
	void 승인하면_신청_내용이_매장에_반영되고_정상으로_돌아온다() {
		BankAccountChangeRequestEntity request = BankAccountChangeRequestEntity.builder()
				.id(10L).storeId(1L).status(BankAccountChangeRequestEntity.STATUS_PENDING)
				.newBankName("신한").newBankAccount("99998888").newAccountHolder("김사장")
				.newPassbookImageUrl("passbooks/new.jpg")
				.build();
		StoreEntity store = StoreEntity.builder().id(1L).accountStatus(StoreEntity.ACCOUNT_STATUS_UNDER_REVIEW).build();
		when(changeRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
		when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

		service.approve(10L, 999L);

		assertEquals("신한", store.getBankName());
		assertEquals("99998888", store.getBankAccount());
		assertEquals("김사장", store.getAccountHolder());
		assertEquals(StoreEntity.ACCOUNT_STATUS_NORMAL, store.getAccountStatus());
		assertEquals(BankAccountChangeRequestEntity.STATUS_APPROVED, request.getStatus());
		assertEquals(999L, request.getReviewedBy());
	}

	@Test
	void 반려하면_기존_정상_계좌가_있던_매장은_정상으로_되돌아간다() {
		BankAccountChangeRequestEntity request = BankAccountChangeRequestEntity.builder()
				.id(11L).storeId(1L).status(BankAccountChangeRequestEntity.STATUS_PENDING)
				.oldBankAccountMasked("110***4567")   // 기존에 정상 계좌가 있었다는 스냅샷
				.build();
		StoreEntity store = StoreEntity.builder().id(1L).accountStatus(StoreEntity.ACCOUNT_STATUS_UNDER_REVIEW).build();
		when(changeRequestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(request));
		when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

		service.reject(11L, 999L, "통장 사본 식별 불가");

		assertEquals(StoreEntity.ACCOUNT_STATUS_NORMAL, store.getAccountStatus());
		assertEquals(BankAccountChangeRequestEntity.STATUS_REJECTED, request.getStatus());
		assertEquals("통장 사본 식별 불가", request.getRejectReason());
	}

	@Test
	void 반려하면_최초_등록_시도였던_매장은_미등록으로_되돌아간다() {
		BankAccountChangeRequestEntity request = BankAccountChangeRequestEntity.builder()
				.id(12L).storeId(1L).status(BankAccountChangeRequestEntity.STATUS_PENDING)
				.oldBankAccountMasked(null)   // 기존 계좌가 아예 없었던(최초 등록) 경우
				.build();
		StoreEntity store = StoreEntity.builder().id(1L).accountStatus(StoreEntity.ACCOUNT_STATUS_UNDER_REVIEW).build();
		when(changeRequestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));
		when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

		service.reject(12L, 999L, null);

		assertEquals(StoreEntity.ACCOUNT_STATUS_UNREGISTERED, store.getAccountStatus());
	}

	@Test
	void 이미_처리된_신청을_다시_승인하려_하면_거부된다() {
		BankAccountChangeRequestEntity request = BankAccountChangeRequestEntity.builder()
				.id(13L).storeId(1L).status(BankAccountChangeRequestEntity.STATUS_APPROVED)
				.build();
		when(changeRequestRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(request));

		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.approve(13L, 999L));
		assertEquals(409, e.getStatusCode().value());
	}
}
