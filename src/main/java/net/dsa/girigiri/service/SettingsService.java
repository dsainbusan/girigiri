package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.Language;
import net.dsa.girigiri.domain.dto.SettingsViewDto;
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
 * 참고)를 나중에 바꾸는 창구다 — 같은 컬럼을 그대로 읽고 쓴다. 언어·매장 알림 토글은 이번에 새로 추가한
 * 컬럼이고, 매장 알림 토글은 아직 실제 알림 발송 로직과 연결돼 있지 않다(저장만 됨).
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final StoreAccessService storeAccessService;

	@Transactional(readOnly = true)
	public SettingsViewDto getSettingsView(Long userId) {
		UserEntity user = requireUser(userId);
		boolean isOwner = UserEntity.ROLE_OWNER.equals(user.getRole());
		StoreEntity store = isOwner ? storeAccessService.findMyStore(userId).orElse(null) : null;
		Language language = Language.findByCode(user.getLanguage()).orElse(Language.KO);

		return new SettingsViewDto(
				user.isMarketingAgreed(),
				language.getCode(),
				language.getLabel(),
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

	@Transactional(readOnly = true)
	public String getLanguageCode(Long userId) {
		UserEntity user = requireUser(userId);
		return Language.findByCode(user.getLanguage()).orElse(Language.KO).getCode();
	}

	@Transactional
	public void updateLanguage(Long userId, String code) {
		Language language = Language.findByCode(code)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 언어예요."));
		UserEntity user = requireUser(userId);
		user.setLanguage(language.getCode());
		userRepository.save(user);
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
