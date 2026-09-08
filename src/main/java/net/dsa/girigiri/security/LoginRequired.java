package net.dsa.girigiri.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 추가됨 (2026-09-08, 코드 감사) — "로그인 안 했으면 되돌려보낸다"는 순수 반복 코드가
 * 컨트롤러 13개·38곳에 아래 형태로 손으로 복붙돼 있었다:
 * <pre>
 *   Long userId = (Long) session.getAttribute("userId");
 *   if (userId == null) {
 *       return "redirect:/auth/loginForm";
 *   }
 * </pre>
 * 이 어노테이션을 메서드(또는 클래스 전체)에 붙이면 {@link LoginRequiredInterceptor}가 컨트롤러
 * 진입 전에 세션을 확인해서 대신 처리한다 — 뷰를 돌려주는 일반 컨트롤러는 로그인 화면으로
 * 리다이렉트, {@code @RestController}는 401 JSON을 내려준다. 어노테이션이 없는 메서드는
 * 이 인터셉터가 아예 관여하지 않는다(기존 동작 그대로).
 *
 * 컨트롤러 쪽엔 여전히 {@code Long userId = (Long) session.getAttribute("userId");} 한 줄은
 * 남겨둔다 — 그 값 자체(누구인지)는 메서드 본문에서 계속 쓰이고, 이 어노테이션이 보장하는 건
 * "null이 아니다"뿐이라 조회 자체를 없앨 수는 없다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {
}
