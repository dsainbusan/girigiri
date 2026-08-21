package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "receipt")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReceiptEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reservation_id", nullable = false)
	private Long reservationId;

	@Column(name = "pdf_url", length = 255)
	private String pdfUrl;

	@CreatedDate
	@Column(name = "generated_at", updatable = false)
	private LocalDateTime generatedAt;
}
