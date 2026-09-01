package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 신고 접수(슈퍼어드민 "신고 · 문의" 화면의 신고 접수 탭). 원래는 정적 데모 텍스트였는데 매장 문의/유저
 * 문의처럼 클릭 → 상세 → 답변이 되게 해달라는 요청으로 실제 테이블을 만들었다. "Report"라는 이름은 이미
 * 매장 판매/폐기 리포트(ReportEntity, report 테이블)가 선점하고 있어 Complaint로 지었다.
 *
 * 추가됨 — 왜: "신고자/매장 클릭하면 디테일로" 요청으로 reporterId/targetStoreId(둘 다 plain Long,
 * nullable)를 붙였다. 신고 제출 화면(소비자용)이 아직 없고 비회원 신고·매장 무관 신고도 있을 수 있어
 * 강제(FK 필수)로는 안 두고, reporterName/targetName 표시 문자열은 그대로 남겨서 id가 없어도 화면엔
 * 항상 보여줄 게 있게 한다 — id가 있으면 그 이름을 클릭 가능한 링크로 감싼다(값 자체는 각자
 * 회원/매장의 실제 이름과 맞춰서 저장, 매번 다시 조회하지 않음 — 문의만큼 자주 안 바뀌는 정보라 스냅샷도
 * 허용).
 */
@Entity
@Table(name = "complaint")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ComplaintEntity {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_RESOLVED = "RESOLVED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "target_name", nullable = false, length = 100)
	private String targetName;   // 신고 대상(매장/유저) 이름

	@Column(name = "target_store_id")
	private Long targetStoreId;   // 대상이 매장이면 채움 — 있으면 targetName을 매장 상세 링크로 감쌈

	@Column(name = "reason", nullable = false, length = 100)
	private String reason;   // 신고 사유 요약 — 목록 제목으로 씀

	@Column(name = "content", nullable = false, length = 1000)
	private String content;   // 신고 상세 내용

	@Column(name = "reporter_name", nullable = false, length = 50)
	private String reporterName;

	@Column(name = "reporter_id")
	private Long reporterId;   // 신고자가 실제 회원이면 채움 — 있으면 reporterName을 회원 상세 링크로 감쌈

	@Column(name = "status", nullable = false, length = 20)
	@Builder.Default
	private String status = STATUS_PENDING;

	@Column(name = "admin_reply", length = 1000)
	private String adminReply;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;
}
