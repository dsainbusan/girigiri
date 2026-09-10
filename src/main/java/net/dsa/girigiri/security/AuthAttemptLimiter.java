package net.dsa.girigiri.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 아주 단순한 인메모리 시도 제한기 (2026-09-10, 문창호).
 * 이메일 찾기·비밀번호 재설정처럼 "입력값만으로 조회"하는 엔드포인트를 IP당 시간당 N회로 제한한다
 * (계정 열거·무차별 대입 완화). 분산 환경/재시작 시 초기화되지만 이 프로젝트 단계에선 충분하다.
 */
@Component
public class AuthAttemptLimiter {

	private static final int MAX_PER_WINDOW = 5;
	private static final long WINDOW_MS = 60 * 60 * 1000L;   // 1시간

	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	/** 허용되면 시도 1회를 기록하고 true, 한도 초과면 false. */
	public boolean tryAcquire(String key) {
		long now = System.currentTimeMillis();
		Deque<Long> dq = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (dq) {
			while (!dq.isEmpty() && now - dq.peekFirst() > WINDOW_MS) {
				dq.pollFirst();
			}
			if (dq.isEmpty()) {
				// 창이 비었으면 이번 요청만 기록하고 통과
				dq.addLast(now);
				return true;
			}
			if (dq.size() >= MAX_PER_WINDOW) {
				return false;
			}
			dq.addLast(now);
			return true;
		}
	}
}
