package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.ComplaintEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 추가됨 (2026-10-06, 코드 리뷰 #6 회귀 확인용) — ComplaintEntity의 블랭킷 @Setter를 없애고
 * resolve() 하나로 좁혔다. status/adminReply/resolvedAt 세 필드가 같이 채워지는지 확인한다.
 */
class ComplaintEntityResolveTest {

	@Test
	@DisplayName("resolve()는 adminReply/status(RESOLVED)/resolvedAt을 같이 채운다")
	void resolveSetsAllThreeFields() {
		ComplaintEntity complaint = ComplaintEntity.builder()
				.targetName("다이스키 베이커리")
				.reason("상품 상태 불량")
				.content("테스트")
				.reporterName("테스트유저")
				.build();
		assertEquals(ComplaintEntity.STATUS_PENDING, complaint.getStatus());

		complaint.resolve("환불 처리했습니다.");

		assertEquals(ComplaintEntity.STATUS_RESOLVED, complaint.getStatus());
		assertEquals("환불 처리했습니다.", complaint.getAdminReply());
		assertNotNull(complaint.getResolvedAt());
	}
}
