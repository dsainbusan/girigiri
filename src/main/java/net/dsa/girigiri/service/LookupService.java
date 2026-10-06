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

import java.util.Optional;

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
 * 슈퍼어드민 포함 전 컨트롤러가 이 헬퍼를 사용한다.
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

	// 추가됨 (2026-10-06, 코드 리뷰 #1) — ProductController가 StoreRepository를 직접 주입받아 쓰던
	// storeRepository.findById(id).orElse(null)을 대체. getStore()와 달리 매장이 없어도 404로
	// 끝내지 않고 화면이 성립해야 하는 호출부(상품은 있는데 매장이 고아가 된 경우)를 위한 것 —
	// 동작을 바꾸지 않는 게 목적이라 orElseThrow로 통일하지 않았다.
	@Transactional(readOnly = true)
	public Optional<StoreEntity> findStore(Long id) {
		return storeRepository.findById(id);
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
