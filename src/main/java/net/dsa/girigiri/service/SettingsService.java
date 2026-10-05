package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.SettingsViewDto;
import net.dsa.girigiri.domain.entity.NotificationSettingEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.domain.entity.UserEntity;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 마이페이지 하위 "환경설정" 화면 (2026-09-22, 문창호, WBS 6.1 인수 범위).
 *
 * 마케팅 동의는 새 값이 아니라 회원가입 때 받은 동의(UserEntity.marketingAgreed, AuthService.completeSignup
 * 참고)를 나중에 바꾸는 창구다 — 같은 컬럼을 그대로 읽고 쓴다. 매장 알림 토글은 저장만 되고 아직 실제
 * 알림 발송 로직과 연결돼 있지 않다. PUSH/찜한 가게 알림은 원래 NotificationController(알림함)가 따로
 * 관리하던 값인데, "알림 설정"으로 한 번 더 들어가야 하는 뎁스를 없애려고(사용자 요청, 2026-10-05) 이
 * 화면에서도 NotificationService를 통해 같은 값을 바로 보여주고 저장한다 — 값의 주인은 여전히
 * NotificationService/NotificationSettingEntity 쪽이고, 여긴 그냥 다른 진입점일 뿐이다.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final StoreAccessService storeAccessService;
	private final NotificationService notificationService;

	@Transactional(readOnly = true)
	public SettingsViewDto getSettingsView(Long userId) {
		UserEntity user = requireUser(userId);
		boolean isOwner = UserEntity.ROLE_OWNER.equals(user.getRole());
		StoreEntity store = isOwner ? storeAccessService.findMyStore(userId).orElse(null) : null;
		NotificationSettingEntity alertSettings = notificationService.getOrCreateSettings(userId);

		return new SettingsViewDto(
				user.isMarketingAgreed(),
				alertSettings.isPushEnabled(),
				alertSettings.isLikeAlertEnabled(),
				store != null,
				store == null ? null : !Boolean.FALSE.equals(store.getSettlementAlertEnabled()),
				store == null ? null : !Boolean.FALSE.equals(store.getAutomationAlertEnabled())
		);
	}

	@Transactional
	public void updateMarketingAgreed(Long userId, boolean agreed) {
		UserEntity user = requireUser(userId);
		user.setMarketingAgreed(agreed);
		userRepository.save(user);
	}

	@Transactional
	public void updatePushAlert(Long userId, boolean enabled) {
		NotificationSettingEntity current = notificationService.getOrCreateSettings(userId);
		notificationService.updateSettings(userId, enabled, current.isLikeAlertEnabled());
	}

	@Transactional
	public void updateLikeAlert(Long userId, boolean enabled) {
		NotificationSettingEntity current = notificationService.getOrCreateSettings(userId);
		notificationService.updateSettings(userId, current.isPushEnabled(), enabled);
	}

	@Transactional
	public void updateSettlementAlert(Long userId, boolean enabled) {
		StoreEntity store = requireStore(userId);
		store.setSettlementAlertEnabled(enabled);
		storeRepository.save(store);
	}

	@Transactional
	public void updateAutomationAlert(Long userId, boolean enabled) {
		StoreEntity store = requireStore(userId);
		store.setAutomationAlertEnabled(enabled);
		storeRepository.save(store);
	}

	private UserEntity requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + userId));
	}

	private StoreEntity requireStore(Long userId) {
		return storeAccessService.findMyStore(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "매장 정보가 없어요."));
	}
}
