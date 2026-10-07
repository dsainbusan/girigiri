package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.PayoutStatementDto;
import net.dsa.girigiri.domain.entity.SettlementEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.SettlementRepository;
import net.dsa.girigiri.util.BankAccountMaskUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * 추가됨 (2026-10-07) — 점주용 "지급 명세서". 배민·스마트스토어 같은 플랫폼들처럼 은행 이체확인증
 * 캡처가 아니라 플랫폼이 직접 발행하는 지급 내역으로 "지급됐다"를 보여준다. 이체확인증은 이제
 * 슈퍼어드민 내부 증빙으로만 남는다(SuperAdminSettlementService#markPaid).
 *
 * 숫자는 전부 SettlementEntity에 확정 시점에 저장된 값 그대로 — 다시 계산하지 않는다(지급된 금액과
 * 명세서 금액이 어긋날 여지를 없앤다). 계좌도 확정 시점 스냅샷을 쓰고, 스냅샷이 없는 건(확정 당시
 * 계좌 미등록 → 이후 등록돼 지급된 경우)만 지금 매장 계좌로 대신 보여준다.
 */
@Service
@RequiredArgsConstructor
public class PayoutStatementService {

	private final SettlementRepository settlementRepository;

	/**
	 * store(로그인한 점주의 매장)의 지급 완료 정산 1건을 명세서로 만든다. 다른 매장 건이거나 아직
	 * 지급 전이면 존재 여부를 드러내지 않도록 똑같이 404로 막는다.
	 */
	@Transactional(readOnly = true)
	public PayoutStatementDto getPaidStatement(StoreEntity store, Long settlementId) {
		SettlementEntity s = settlementRepository.findById(settlementId)
				.filter(found -> found.getStoreId().equals(store.getId()))
				.filter(found -> SettlementEntity.STATUS_PAID.equals(found.getStatus()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지급 명세서를 찾을 수 없어요."));

		boolean hasSnapshot = s.getBankAccount() != null;
		return new PayoutStatementDto(
				s.getId(),
				statementNo(s),
				store.getStoreName(),
				store.getBusinessNumber(),
				s.getPeriodStart(),
				s.getPeriodEnd(),
				s.getGross(),
				s.getRefund(),
				s.getNetAmount(),
				s.getCommissionRate(),
				s.getCommission(),
				s.getWeekAmount(),
				s.getCarriedIn(),
				s.getPayout(),
				s.getScheduledPayoutDate(),
				s.getPaidAt(),
				hasSnapshot ? s.getBankName() : store.getBankName(),
				BankAccountMaskUtil.mask(hasSnapshot ? s.getBankAccount() : store.getBankAccount()),
				hasSnapshot ? s.getAccountHolder() : store.getAccountHolder(),
				LocalDate.now());
	}

	/** 명세서 번호 — "PS-정산주간시작일-정산id"(예: PS-20260921-000123). 같은 건은 언제 뽑아도 같은 번호. */
	private String statementNo(SettlementEntity s) {
		return "PS-" + s.getPeriodStart().toString().replace("-", "") + "-" + String.format("%06d", s.getId());
	}
}
