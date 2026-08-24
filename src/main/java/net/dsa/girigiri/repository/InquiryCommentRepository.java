package net.dsa.girigiri.repository;

import net.dsa.girigiri.domain.entity.InquiryCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InquiryCommentRepository extends JpaRepository<InquiryCommentEntity, Long> {
}
