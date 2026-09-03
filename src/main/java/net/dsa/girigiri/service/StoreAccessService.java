package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * "로그인한 사장님의 매장" 조회 공통 헬퍼 (2026-09-03 추가, 레이어 규칙 1단계).
 *
 * storeRepository.findByOwnerId(userId) 패턴이 컨트롤러에 17건 흩어져 있었다.
 *
 * ★ 중요: 호출부마다 "매장이 없을 때" 처리가 전부 다르다.
 *    - StoreController:76   → 매장 없어도 화면은 띄우고 "매장 정보 없음" 표시
 *    - StoreController:251  → 400 응답
 *    - StoreController:271  → /auth/owner-apply 로 리다이렉트
 *    - StoreController:337  → /auth/owner-apply 로 리다이렉트
 *
 *    그래서 예외를 던지는 버전 하나로 통일하면 동작이 바뀐다.
 *    Optional을 돌려주는 findMyStore()를 기본으로 쓰고,
 *    "없으면 무조건 에러"가 확실한 곳에서만 getMyStore()를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class StoreAccessService {

	private final StoreRepository storeRepository;

	/**
	 * 기본형. 호출부가 없음(empty) 상황을 직접 처리한다.
	 * 기존 `storeRepository.findByOwnerId(userId).orElse(null)` 자리를 그대로 대체할 수 있다.
	 *
	 * 사용 예:
	 *   StoreEntity store = storeAccessService.findMyStore(userId).orElse(null);
	 *   if (store == null) return "redirect:/auth/owner-apply";
	 */
	@Transactional(readOnly = true)
	public Optional<StoreEntity> findMyStore(Long ownerId) {
		if (ownerId == null) {
			return Optional.empty();
		}
		return storeRepository.findByOwnerId(ownerId);
	}

	/**
	 * 매장이 반드시 있어야 하는 화면에서만 사용.
	 * 없으면 EntityNotFoundException → GlobalExceptionHandler가 에러 페이지로 처리.
	 */
	@Transactional(readOnly = true)
	public StoreEntity getMyStore(Long ownerId) {
		return findMyStore(ownerId)
				.orElseThrow(() -> new EntityNotFoundException("등록된 매장이 없습니다."));
	}

	/**
	 * 이 매장이 로그인한 사장님 소유인지 확인.
	 * 남의 매장 데이터에 접근하는 것을 막는 용도 — 상세/수정 화면에서 사용한다.
	 */
	@Transactional(readOnly = true)
	public boolean isMyStore(Long ownerId, Long storeId) {
		if (ownerId == null || storeId == null) {
			return false;
		}
		return findMyStore(ownerId)
				.map(s -> storeId.equals(s.getId()))
				.orElse(false);
	}
}
