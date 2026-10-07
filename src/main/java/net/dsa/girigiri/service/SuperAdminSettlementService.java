package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.SettlementRowDto;
import net.dsa.girigiri.domain.entity.SettlementEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.SettlementRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.FileStorageUtil;
import net.dsa.girigiri.util.SettlementTransferExcelGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 추가됨 (2026-09-29, 담당: 송보미) — 슈퍼어드민 "정산" 화면(/superadmin/settlements) 도메인 서비스.
 *
 * SettlementBatchService(문창호, WBS 2.0)의 클래스 주석에 이미 "지급 처리 화면은 슈퍼어드민(송보미)
 * 영역"이라고 남겨진 대로, 계산·확정 로직(SettlementBatchService/SettlementService)은 전혀 건드리지
 * 않고 그 결과(SettlementRepository)를 화면에 보여주고 markPaid()를 호출하는 얇은 조회/조립 계층만
 * 새로 만든다. 코드 감사에서 "슈퍼어드민 정산 화면이 없다"(결제·취소·환불은 보이는데 정산 지급을
 * 확인·대사할 방법이 없음)로 지적된 항목.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminSettlementService {

	private static final DateTimeFormatter PERIOD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final SettlementRepository settlementRepository;
	private final StoreRepository storeRepository;
	private final SettlementBatchService settlementBatchService;
	// 추가됨 (2026-10-07) — "지급 완료" 처리 시 이체 확인증(스크린샷) 업로드용. 리뷰 사진 업로드와
	// 같은 공용 유틸(이미지 전용, 매직바이트 검증) 그대로 재사용한다.
	private final FileStorageUtil fileStorageUtil;

	@Transactional(readOnly = true)
	public List<SettlementRowDto> getPendingSettlements() {
		return toRows(settlementRepository.findByStatusOrderByScheduledPayoutDateAsc(SettlementEntity.STATUS_PENDING));
	}

	/** "최근 정산 이력" — 상태 무관 최근 100건(SettlementRepository#findTop100ByOrderByConfirmedAtDesc). */
	@Transactional(readOnly = true)
	public List<SettlementRowDto> getRecentSettlements() {
		return toRows(settlementRepository.findTop100ByOrderByConfirmedAtDesc());
	}

	private List<SettlementRowDto> toRows(List<SettlementEntity> settlements) {
		Map<Long, StoreEntity> storesById = storesByIdFor(settlements);
		return settlements.stream()
				.map(s -> {
					StoreEntity store = storesById.get(s.getStoreId());
					return new SettlementRowDto(
							s.getId(),
							s.getStoreId(),
							store != null ? store.getStoreName() : "알 수 없음",
							periodLabel(s),
							s.getPayout(),
							s.getStatus(),
							statusLabel(s.getStatus()),
							s.getScheduledPayoutDate(),
							s.getPaidAt(),
							s.getTransferMemo(),
							s.isPayable(store),
							accountStatusLabel(s, store),
							s.getTransferReceiptUrl());
				})
				.toList();
	}

	private Map<Long, StoreEntity> storesByIdFor(List<SettlementEntity> settlements) {
		List<Long> storeIds = settlements.stream().map(SettlementEntity::getStoreId).distinct().toList();
		return storeRepository.findAllById(storeIds).stream()
				.collect(Collectors.toMap(StoreEntity::getId, s -> s));
	}

	// 수정됨 (2026-10-07, 계좌 보안) — 예전엔 "등록됨/미등록" 2단이었는데, 요구사항 5의 3단
	// (정상/변경 심사 중/미등록)으로 바꿨다. SettlementEntity.isPayable과 같은 기준(스냅샷 우선)을
	// 쓰되, 라벨은 화면 문구용으로 하나 더 세분화한다.
	private String accountStatusLabel(SettlementEntity s, StoreEntity store) {
		if (s.isPayable(store)) {
			return "정상";
		}
		if (store != null && StoreEntity.ACCOUNT_STATUS_UNDER_REVIEW.equals(store.getAccountStatus())) {
			return "변경 심사 중";
		}
		return "미등록";
	}

	private String periodLabel(SettlementEntity s) {
		return s.getPeriodStart().format(PERIOD_DATE_FORMAT) + " ~ " + s.getPeriodEnd().format(PERIOD_DATE_FORMAT);
	}

	private String statusLabel(String status) {
		return switch (status) {
			case SettlementEntity.STATUS_PENDING -> "지급 대기";
			case SettlementEntity.STATUS_PAID -> "지급 완료";
			case SettlementEntity.STATUS_CARRIED -> "이월";
			case SettlementEntity.STATUS_ROLLED -> "합산됨";
			default -> status;
		};
	}

	/**
	 * "지급 완료" 버튼 — 실제 상태 전환은 SettlementBatchService.markPaid()에 그대로 위임한다.
	 * receipt(이체 확인증 스크린샷, 선택)는 여기서 업로드만 하고 URL만 넘긴다 — 저장은 안 했으면
	 * (= 비어있으면) null을 넘겨서 SettlementBatchService가 기존 영수증을 안 건드리게 한다.
	 *
	 * 수정됨 (2026-10-07, 보안 리뷰) — 여러 매장을 한꺼번에 체크해서 지급 완료 처리하면서 영수증을
	 * 같이 올리면, 그 한 장이 선택된 매장 전부의 /store/settlement 화면에 똑같이 걸린다. 은행
	 * 대량이체 확인 화면은 보통 이체 건을 전부 한 줄씩 나열해서 보여주므로, 그 캡처 한 장을 다른
	 * 매장 점주에게도 그대로 보여주면 그 매장의 계좌·금액이 남의 점주에게 노출될 수 있다(IDOR은
	 * 아니지만 교차 매장 정보 노출) — 영수증을 첨부하려면 선택한 건이 전부 같은 매장이어야 한다.
	 * 여러 매장을 한 번에 처리하고 싶으면(기존 동작) 영수증 없이 진행하거나 매장별로 나눠서 올린다.
	 */
	@Transactional
	public int markPaid(List<Long> settlementIds, String memo, MultipartFile receipt) {
		if (receipt != null && !receipt.isEmpty() && !sameStore(settlementIds)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"이체 확인증은 한 매장 건을 선택했을 때만 첨부할 수 있어요. 여러 매장을 한 번에 처리하려면 영수증 없이 지급 완료 처리해 주세요.");
		}
		String receiptUrl = fileStorageUtil.store(receipt, "settlement-receipts");
		return settlementBatchService.markPaid(settlementIds, memo, receiptUrl);
	}

	private boolean sameStore(List<Long> settlementIds) {
		if (settlementIds == null || settlementIds.isEmpty()) {
			return true;
		}
		Set<Long> storeIds = settlementRepository.findAllById(settlementIds).stream()
				.map(SettlementEntity::getStoreId)
				.collect(Collectors.toSet());
		return storeIds.size() <= 1;
	}

	/**
	 * "이체 목록 Excel 다운로드" — 지금 지급 대기 중인 전체 건을 SettlementTransferExcelGenerator
	 * (문창호가 이미 만들어둔 유틸)로 변환한다. 개별 선택이 아니라 전체를 내려받는다 — 정산은 주 1회
	 * 배치(SettlementScheduler)로 한 번에 확정되므로, "이번 주 지급 대기 전체"가 곧 이번 배치 이체
	 * 대상과 같다.
	 */
	// 수정됨 (2026-10-07, 계좌 보안) — 계좌가 없거나 심사 중인 건은 보낼 곳이 없으니 이체 목록에서
	// 아예 뺀다(이전엔 store 계좌가 비어도 그냥 빈칸으로 한 줄 끼어 들어갔다). 계좌 값은 더 이상
	// StoreEntity를 그때그때 조회하지 않고 SettlementEntity 스냅샷을 우선 쓴다(확정 당시 계좌가 없어
	// 스냅샷이 비어있으면 지금 매장 계좌로 폴백 — isPayable과 같은 기준, SettlementEntity 주석 참고).
	// 전체 계좌번호 복호화는 바로 이 메서드(Excel 생성 시점)에서만 일어난다(요구사항 4).
	@Transactional(readOnly = true)
	public byte[] buildTransferExcel() throws IOException {
		List<SettlementEntity> pending =
				settlementRepository.findByStatusOrderByScheduledPayoutDateAsc(SettlementEntity.STATUS_PENDING);
		Map<Long, StoreEntity> storesById = storesByIdFor(pending);

		List<SettlementTransferExcelGenerator.Line> lines = pending.stream()
				.filter(s -> s.isPayable(storesById.get(s.getStoreId())))
				.map(s -> {
					StoreEntity store = storesById.get(s.getStoreId());
					boolean useSnapshot = s.getBankAccount() != null;
					return new SettlementTransferExcelGenerator.Line(
							useSnapshot ? s.getBankName() : store.getBankName(),
							useSnapshot ? s.getBankAccount() : store.getBankAccount(),
							useSnapshot ? s.getAccountHolder() : store.getAccountHolder(),
							s.getPayout(),
							"기리기리 정산",
							store != null ? store.getStoreName() : "알 수 없음",
							periodLabel(s));
				})
				.toList();

		return SettlementTransferExcelGenerator.generate(lines);
	}
}
