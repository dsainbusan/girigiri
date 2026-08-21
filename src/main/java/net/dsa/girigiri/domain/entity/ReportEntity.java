package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "report")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReportEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "report_date", nullable = false)
	private LocalDate reportDate;

	@Column(name = "registered_count")
	private Integer registeredCount;

	@Column(name = "sold_count")
	private Integer soldCount;

	@Column(name = "expired_count")
	private Integer expiredCount;

	@Column(name = "total_sales")
	private Integer totalSales;

	@Column(name = "total_discount")
	private Integer totalDiscount;

	@Column(name = "saved_co2")
	private Double savedCO2;

	@Column(name = "excel_url", length = 255)
	private String excelUrl;

	@Column(name = "pdf_url", length = 255)
	private String pdfUrl;

	@CreatedDate
	@Column(name = "generated_at", updatable = false)
	private LocalDateTime generatedAt;
}
