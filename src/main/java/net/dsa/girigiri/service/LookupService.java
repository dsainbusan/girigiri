package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ComplaintEntity;
import net.dsa.girigiri.domain.entity.NoticeEntity;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.ComplaintRepository;
import net.dsa.girigiri.repository.NoticeRepository;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단건 조회 공통 헬퍼 (2026-09-03 추가, 레이어 규칙 1단계).
 *
 * 컨트롤러 곳곳에 흩어져 있던
 *   xxxRepository.findById(id).orElseThrow(...)   (45건)
 * 패턴을 여기로 모은다. 컨트롤러가 Repository를 직접 주입받지 않게 하는 것이 목적.
 *
 * 던지는 예외는 EntityNotFoundException 하나로 통일한다.
 * GlobalExceptionHandler가 이미 이 예외를 errorView/custom-error-page로 처리하고 있다.
 *
 * ※ 주의: SuperAdminController는 현재 ResponseStatusException(404)를 던지고 있어
 *   이 헬퍼로 바꾸면 "404 응답" → "에러 페이지 렌더링"으로 동작이 바뀐다.
 *   슈퍼어드민은 마지막 순서(9/18 이후)에 옮기고, 그때 화면을 직접 확인할 것.
 */
@Service
@RequiredArgsConstructor
public class LookupService {

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final ProductRepository productRepository;
	private final ReservationRepository reservationRepository;
	private final NoticeRepository noticeRepository;
	private final ComplaintRepository complaintRepository;

	@Transactional(readOnly = true)
	public UserEntity getUser(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다: " + id));
	}

	@Transactional(readOnly = true)
	public StoreEntity getStore(Long id) {
		return storeRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다: " + id));
	}

	@Transactional(readOnly = true)
	public ProductEntity getProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다: " + id));
	}

	@Transactional(readOnly = true)
	public ReservationEntity getReservation(Long id) {
		return reservationRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다: " + id));
	}

	@Transactional(readOnly = true)
	public NoticeEntity getNotice(Long id) {
		return noticeRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("공지사항을 찾을 수 없습니다: " + id));
	}

	@Transactional(readOnly = true)
	public ComplaintEntity getComplaint(Long id) {
		return complaintRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("신고를 찾을 수 없습니다: " + id));
	}
}
