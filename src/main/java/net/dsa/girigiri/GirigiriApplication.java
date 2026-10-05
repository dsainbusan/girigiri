package net.dsa.girigiri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableJpaAuditing
@EnableScheduling   // (채현)노쇼 자동 처리(NoShowScheduler) 등 주기적으로 실행되는 작업을 위해 추가
@SpringBootApplication
public class GirigiriApplication {

	// 추가됨 (2026-10-06) — LocalDateTime.now()/LocalDate.now() 등 "오늘/지금" 계산이 전부 JVM
	// 기본 타임존에 의존하는데 어디에도 명시적으로 고정돼 있지 않았다(.env의 serverTimezone=
	// Asia/Seoul은 JDBC 드라이버의 타임스탬프 변환에만 적용되고, 애플리케이션 코드엔 영향이 없다).
	// 로컬 개발 환경은 OS가 우연히 KST라 지금까지 맞아 보였을 뿐 — 배포 환경 타임존이 다르면
	// "오늘" 기준이 깨진다. main() 안이 아니라 정적 초기화 블록으로 둬서, jar로 기동할 때(main())는
	// 물론 @SpringBootTest가 이 클래스를 로드하는 시점(./gradlew test)에도 똑같이 적용되게 한다.
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(GirigiriApplication.class, args);
	}
}