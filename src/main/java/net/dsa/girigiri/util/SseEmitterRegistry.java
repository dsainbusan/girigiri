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

	/**
	 * 변경됨 (강노은, 2026-09-21) — 왜: 읽음 처리(markRead/markAllRead)도 "새 알림이 생겼을 때"와
	 * 똑같이 "notification" 이벤트로 밀어주고 있어서, 알림함에서 이미 읽은 알림을 다시 클릭하기만
	 * 해도(=안읽은 개수는 그대로인데 push는 여전히 발생) 클라이언트가 "새 알림 도착" 배너를 잘못
	 * 띄우는 문제가 있었다. "배지 숫자만 갱신하면 되는 경우"(읽음 처리)와 "진짜 새 알림이 왔다는
	 * 걸 알려야 하는 경우"(생성)를 이벤트 이름으로 분리한다 — 배지 갱신(home.html)은 아래 두
	 * 메서드 다 걸려있는 "unread-count"만 구독하고, "새 알림 도착" 배너(alertView/list.html)는
	 * pushNewNotification()에서만 같이 보내는 "new-notification"을 구독한다.
	 */
	public void pushUnreadCount(Long userId, int unreadCount) {
		send(userId, "unread-count", unreadCount);
	}

	/** 진짜 새 알림이 저장됐을 때 — 배지 갱신용 unread-count와 함께, "새 알림 도착" 배너를 띄울
	 *  new-notification 이벤트도 같이 보낸다. */
	public void pushNewNotification(Long userId, int unreadCount) {
		send(userId, "unread-count", unreadCount);
		send(userId, "new-notification", unreadCount);
	}

	private void send(Long userId, String eventName, int data) {
		List<SseEmitter> emitters = emittersByUserId.get(userId);
		if (emitters == null || emitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : List.copyOf(emitters)) {
			try {
				emitter.send(SseEmitter.event().name(eventName).data(data));
			} catch (IOException e) {
				emitters.remove(emitter);
			}
		}
	}
}
