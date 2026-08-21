package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PaymentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reservation_id", nullable = false)
	private Long reservationId;

	@Column(name = "merchant_uid", nullable = false, length = 50)
	private String merchantUid;   // 가맹점 주문번호 (우리 서버가 생성해서 PortOne에 넘김)

	@Column(name = "imp_uid", length = 50)
	private String impUid;        // PortOne(아임포트) 결제 고유번호 (결제 승인 후 채워짐)

	@Column(name = "amount", nullable = false)
	private Integer amount;

	@Column(name = "pay_method", length = 20)
	private String payMethod;     // card / kakaopay / naverpay 등

	@Column(name = "pay_status", nullable = false, length = 20)
	private String payStatus;     // ready / paid / failed / cancelled

	@Column(name = "fail_reason", length = 255)
	private String failReason;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@CreatedDate
	@Column(name = "requested_at", updatable = false)
	private LocalDateTime requestedAt;
}
