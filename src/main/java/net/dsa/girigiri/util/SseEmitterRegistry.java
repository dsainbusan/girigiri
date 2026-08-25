package net.dsa.girigiri.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 강노은: 로그인한 사용자별로 열려있는 SSE(Server-Sent Events) 연결을 붙잡아두는 인메모리 레지스트리.
 * 사용자 1명이 탭을 여러 개 열 수 있어서 userId 하나에 emitter가 여러 개 붙을 수 있다.
 *
 * 인메모리라 서버가 여러 대로 늘어나면(스케일아웃) 이 방식으로는 안 되고 Redis pub/sub 같은 걸
 * 써야 하는데, 지금 이 프로젝트는 단일 인스턴스라 이 정도로 충분하다.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

	// 30분 — 그 이상 연결이 유지 안 되면 브라우저의 EventSource가 알아서 재연결을 시도한다.
	private static final long TIMEOUT_MS = 30 * 60 * 1000L;

	private final Map<Long, List<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

	public SseEmitter register(Long userId) {
		SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
		List<SseEmitter> emitters = emittersByUserId.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
		emitters.add(emitter);

		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));

		return emitter;
	}

	/** 새 알림이 생기거나 읽음 처리가 됐을 때 — 최신 안읽음 개수를 실시간으로 밀어준다. */
	public void pushUnreadCount(Long userId, int unreadCount) {
		List<SseEmitter> emitters = emittersByUserId.get(userId);
		if (emitters == null || emitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : List.copyOf(emitters)) {
			try {
				emitter.send(SseEmitter.event().name("notification").data(unreadCount));
			} catch (IOException e) {
				emitters.remove(emitter);
			}
		}
	}
}
