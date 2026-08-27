package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * POS 카탈로그에서 불러온 매장 메뉴 1건 — 2026-08-27 신규 (문창호, "POS json 카탈로그 연동 (가정)").
 *
 * 이건 "그날 파는 마감 상품(ProductEntity)"이 아니라 매장의 **영속 메뉴 카탈로그**다.
 * 점주가 상품/템플릿을 등록할 때 여기서 품목명·원가·사진을 자동완성으로 끌어다 쓴다.
 *
 * 변경됨 (2026-08-27) — 왜: "미리 만들어 파는 집(베이커리·김밥·초밥·반찬)" 가정으로 POS 자동화를
 * 살리기로 함(팀 논의). POS가 현재 재고(stockQuantity)를 push해주면, 마감 무렵 앱이 그 스냅샷으로
 * "지금 이만큼 남았는데 팔래요?" 초안을 자동 생성한다(B안). 그래서 재고/앱판매설정 필드 추가.
 *
 * ⚠️ 새 엔티티 — 공통 DB 오너(송보미)에게 스키마 공유 완료 필요.
 */
@Entity
@Table(name = "menu_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MenuItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	// POS 쪽 원본 식별자(SKU/바코드). mock 카탈로그에서도 upsert 기준으로 쓴다.
	@Column(name = "pos_sku", length = 50)
	private String posSku;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "original_price", nullable = false)
	private Integer originalPrice;

	@Column(name = "image_url", length = 255)
	private String imageUrl;

	// POS가 push하는 현재 재고. null이면 재고 정보 없음(수동 등록만 가능).
	@Column(name = "stock_quantity")
	private Integer stockQuantity;

	// 마감 무렵 이 메뉴를 "오늘의 구제" 초안으로 자동 생성할지. 점주가 연동 화면에서 켠다.
	@Builder.Default
	@Column(name = "app_sale_enabled", nullable = false)
	private boolean appSaleEnabled = false;

	// 앱 판매 시 할인율(%). null이면 마감까지 남은 시간 기준 자동(DiscountRateCalculator, 20/30/50).
	// 값이 있어도 자동값보다 낮으면 자동값으로 올려서 적용한다 — "더 깎기"만 되고 "덜 깎기"는 안 됨
	// (DiscountRateCalculator.effectiveRate, 2026-08-27 정책).
	@Column(name = "discount_rate")
	private Integer discountRate;

	// 앱에 올릴 최대 수량. null이면 마감 무렵 POS 재고 전량을 초안으로 만든다.
	// 값이 있으면 초안 수량 = min(POS 재고, 이 값) — 재고가 적은 날엔 자동으로 그만큼만 (2026-08-27 문창호).
	@Column(name = "app_sale_quantity")
	private Integer appSaleQuantity;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}
