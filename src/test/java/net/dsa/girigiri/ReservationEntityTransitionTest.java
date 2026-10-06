package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ReservationEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 추가됨 (2026-10-06, 코드 리뷰 #6 회귀 확인용) — ReservationEntity의 블랭킷 @Setter를 없애고
 * confirm()/markReady()/markPicked()/cancel()/markNoShowed()/refund() 6개 전이 메서드로 좁혔다.
 * 각 메서드가 상태값과 같이 채워야 하는 필드(pickedAt, acceptedAt, cancelledBy/cancelReason)를
 * 빠뜨리지 않는지 엔티티 단위로 직접 확인한다(Spring 컨텍스트 불필요).
 */
class ReservationEntityTransitionTest {

	@Test
	@DisplayName("confirm()은 status만 confirmed로 바꾼다")
	void confirmSetsStatus() {
		ReservationEntity r = ReservationEntity.builder().status("pending").build();
		r.confirm();
		assertEquals("confirmed", r.getStatus());
	}

	@Test
	@DisplayName("markReady()는 status=ready와 acceptedAt을 같이 채운다")
	void markReadySetsStatusAndAcceptedAt() {
		ReservationEntity r = ReservationEntity.builder().status("confirmed").build();
		LocalDateTime now = LocalDateTime.now();
		r.markReady(now);
		assertEquals("ready", r.getStatus());
		assertEquals(now, r.getAcceptedAt());
	}

	@Test
	@DisplayName("markPicked()는 status=picked와 pickedAt을 같이 채운다")
	void markPickedSetsStatusAndPickedAt() {
		ReservationEntity r = ReservationEntity.builder().status("ready").build();
		LocalDateTime now = LocalDateTime.now();
		r.markPicked(now);
		assertEquals("picked", r.getStatus());
		assertEquals(now, r.getPickedAt());
	}

	@Test
	@DisplayName("cancel()은 status=cancelled와 cancelledBy/cancelReason을 같이 채운다 (reason은 null 허용 — USER 취소)")
	void cancelSetsStatusAndWho() {
		ReservationEntity r = ReservationEntity.builder().status("confirmed").build();
		r.cancel("USER", null);
		assertEquals("cancelled", r.getStatus());
		assertEquals("USER", r.getCancelledBy());
		assertNull(r.getCancelReason());

		ReservationEntity r2 = ReservationEntity.builder().status("confirmed").build();
		r2.cancel("STORE", "재고 소진");
		assertEquals("STORE", r2.getCancelledBy());
		assertEquals("재고 소진", r2.getCancelReason());
	}

	@Test
	@DisplayName("markNoShowed()는 status만 noshowed로 바꾼다")
	void markNoShowedSetsStatus() {
		ReservationEntity r = ReservationEntity.builder().status("ready").build();
		r.markNoShowed();
		assertEquals("noshowed", r.getStatus());
	}

	@Test
	@DisplayName("refund()는 status=refunded와 cancelledBy/cancelReason을 같이 채운다")
	void refundSetsStatusAndWho() {
		ReservationEntity r = ReservationEntity.builder().status("picked").build();
		r.refund("ADMIN", "상품 상태 불량 확인되어 전액 환불");
		assertEquals("refunded", r.getStatus());
		assertEquals("ADMIN", r.getCancelledBy());
		assertEquals("상품 상태 불량 확인되어 전액 환불", r.getCancelReason());
	}
}
