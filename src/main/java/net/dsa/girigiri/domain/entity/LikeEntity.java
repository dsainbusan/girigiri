package net.dsa.girigiri.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
// "like"는 MySQL 예약어라 회피. uniqueConstraints 추가됨 (2026-09-08, 코드 감사) — (user_id, store_id)에
// 제약이 없어서 동시 찜 요청이 중복 행을 만들 수 있었다. LikeService.toggle()이 매칭되는 행을
// 전부 지우는 방식이라 심각한 버그로 이어지진 않지만(자체 치유), DB 차원에서도 막아두는 게 맞다.
// 반영 전 중복 데이터 없는 것 확인함.
@Table(name = "likes", uniqueConstraints = @UniqueConstraint(name = "uk_likes_user_store", columnNames = {"user_id", "store_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LikeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
}
