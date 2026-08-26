package net.dsa.girigiri.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 생성 후 내용이 바뀔 수 있는(가변) 엔티티용 공통 상위 클래스.
 *
 * updatedAt은 "어떤 컬럼이든 하나라도 바뀌면" 갱신된다. 마이그레이션 배치,
 * 필드 하나 수정, 낙관적 락 version 증가에도 바뀌기 때문에 업무적 의미가 없다.
 * "언제 답변이 달렸나", "언제 취소됐나" 같은 건 반드시 별도 도메인 컬럼으로 둘 것.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedEntity {

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
