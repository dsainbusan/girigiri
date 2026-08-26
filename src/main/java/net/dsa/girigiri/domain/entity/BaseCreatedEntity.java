package net.dsa.girigiri.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 생성 후 내용이 바뀌지 않는(불변) 엔티티용 공통 상위 클래스.
 *
 * createdAt은 "레코드가 DB에 들어온 시각"이지 "업무가 일어난 시각"이 아니다.
 * 정산/통계/SLA 계산에는 절대 쓰지 말고, 각 엔티티가 가진 도메인 시각 컬럼
 * (reservedAt, pickedAt, generatedAt 등)을 쓸 것.
 *
 * 쓰기 좋은 곳: 목록 최신순 정렬, 증분 동기화, 장애 추적.
 *
 * 컬럼명을 바꾸고 싶으면 상속받는 쪽에서 @AttributeOverride 사용:
 *   @AttributeOverride(name = "createdAt",
 *                      column = @Column(name = "generated_at", updatable = false))
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;
}
