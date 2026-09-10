package net.dsa.girigiri;

import net.dsa.girigiri.domain.entity.NotificationSettingEntity;
import net.dsa.girigiri.repository.NotificationRepository;
import net.dsa.girigiri.repository.NotificationSettingRepository;
import net.dsa.girigiri.service.NotificationService;
import net.dsa.girigiri.util.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검증(더미데이터) — "NotificationService.getOrCreateSettings()에 @Transactional이 없어서
 * 동시 첫 요청 시 유니크 제약(user_id) 위반이 날 수 있다" 지적 수정 확인.
 *
 * 주의: 실제 동시성 레이스는 진짜 트랜잭션 매니저 + 진짜 DB 락이 있어야 재현/검증되고, 순수
 * 단위 테스트(Mockito)로는 만들 수 없다 — 그래서 여기선 (1) @Transactional 어노테이션이 실제로
 * 붙었는지 리플렉션으로 구조 확인 + (2) 이미 설정이 있는 정상 케이스에서 중복 저장을 안 하는지
 * 더미데이터로 확인하는 두 가지만 한다. 진짜 동시 요청 재현은 로컬 DB가 필요해서 이 테스트의
 * 범위 밖이다.
 */
class NotificationSettingsTransactionalTest {

	@Test
	void getOrCreateSettings에_Transactional이_붙어있다() throws Exception {
		Method method = NotificationService.class.getMethod("getOrCreateSettings", Long.class);
		assertNotNull(method.getAnnotation(Transactional.class),
				"getOrCreateSettings()에 @Transactional이 빠져있음 — 동시 첫 요청 시 유니크 제약 위반 재발 가능");
	}

	@Test
	void 이미_설정이_있으면_저장을_다시_시도하지_않는다() {
		NotificationRepository notificationRepository = mock(NotificationRepository.class);
		NotificationSettingRepository settingRepository = mock(NotificationSettingRepository.class);
		SseEmitterRegistry sseEmitterRegistry = mock(SseEmitterRegistry.class);
		NotificationService notificationService =
				new NotificationService(notificationRepository, settingRepository, sseEmitterRegistry);

		// 더미데이터: userId=1의 설정이 이미 존재하는 상황
		NotificationSettingEntity existing = NotificationSettingEntity.builder().userId(1L).build();
		when(settingRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

		NotificationSettingEntity result = notificationService.getOrCreateSettings(1L);

		assertEquals(existing, result);
		verify(settingRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(settingRepository, times(1)).findByUserId(1L);
	}
}
