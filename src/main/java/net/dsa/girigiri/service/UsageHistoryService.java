package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.UsageHistoryRowDto;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 추가됨 (강노은, 2026-10-05) — 마이페이지 "나의 활동 → 이용 내역". 픽업완료·노쇼·취소 세 가지
 * 결과를 한 목록으로 모아 최신순으로 보여준다. LedgerService.loadLines와 같은 패턴(예약 목록 1번 +
 * product/store 배치조회 2번)을 쓰지만, 저긴 "절약" 집계가 목적이라 picked만 다루고, 여긴 세 상태를
 * 다 다뤄야 해서 별도 서비스로 뗐다.
 *
 * 환불 여부(noteText)는 ReservationService의 기존 정책을 그대로 따른다 — 취소는 환불, 노쇼는
 * 환불 없음(ReservationService#processOneNoShow 주석 참고). 상태 라벨/배지 variant도
 * ReservationService#resolveStatusBadge/getStatusVariant와 같은 값을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class UsageHistoryService {

	private static final List<String> USAGE_STATUSES = List.of("picked", "cancelled", "noshowed");
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	private final ReservationRepository reservationRepository;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;

	public List<UsageHistoryRowDto> getUsageHistory(Long userId) {
		List<ReservationEntity> reservations =
				reservationRepository.findByUserIdAndStatusInOrderByReservedAtDesc(userId, USAGE_STATUSES);
		if (reservations.isEmpty()) {
			return List.of();
		}

		Map<Long, ProductEntity> products = productRepository
				.findAllById(reservations.stream().map(ReservationEntity::getProductId).distinct().toList())
				.stream().collect(Collectors.toMap(ProductEntity::getId, p -> p));
		Map<Long, StoreEntity> stores = storeRepository
				.findAllById(reservations.stream().map(ReservationEntity::getStoreId).distinct().toList())
				.stream().collect(Collectors.toMap(StoreEntity::getId, s -> s));

		return reservations.stream().map(r -> toRow(r, products, stores)).toList();
	}

	private UsageHistoryRowDto toRow(ReservationEntity r, Map<Long, ProductEntity> products, Map<Long, StoreEntity> stores) {
		ProductEntity product = products.get(r.getProductId());
		StoreEntity store = stores.get(r.getStoreId());
		int quantity = r.getReservedQuantity() == null ? 0 : r.getReservedQuantity();
		int paid = r.getTotalPrice() == null ? 0 : r.getTotalPrice();

		boolean picked = "picked".equals(r.getStatus());
		// 픽업완료 건만 "절약"이 의미가 있다 — 노쇼/취소는 실제로 구제한 게 아니므로 할인/절약 표시를 안 보여준다.
		int original = picked && product != null && product.getOriginalPrice() != null
				? product.getOriginalPrice() * quantity : paid;
		int saved = picked ? Math.max(0, original - paid) : 0;

		LocalDateTime eventTime = picked && r.getPickedAt() != null ? r.getPickedAt() : r.getReservedAt();

		return new UsageHistoryRowDto(
				eventTime.format(DATE_FMT),
				store != null ? store.getStoreName() : "매장",
				r.getProductName(),
				quantity,
				original,
				paid,
				saved,
				resolveStatusLabel(r.getStatus()),
				resolveStatusVariant(r.getStatus()),
				resolveNote(r.getStatus())
		);
	}

	private String resolveStatusLabel(String status) {
		return switch (status) {
			case "picked" -> "픽업완료";
			case "cancelled" -> "취소";
			case "noshowed" -> "노쇼";
			default -> status;
		};
	}

	private String resolveStatusVariant(String status) {
		return switch (status) {
			case "picked" -> "done";
			case "cancelled" -> "cancelled";
			case "noshowed" -> "noshow";
			default -> "done";
		};
	}

	private String resolveNote(String status) {
		return switch (status) {
			case "cancelled" -> "결제 금액이 환불됐어요";
			case "noshowed" -> "결제 금액은 환불되지 않아요";
			default -> null;
		};
	}
}
