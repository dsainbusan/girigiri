package net.dsa.girigiri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling   // (채현)노쇼 자동 처리(NoShowScheduler) 등 주기적으로 실행되는 작업을 위해 추가
@SpringBootApplication
public class GirigiriApplication {
	public static void main(String[] args) {
		SpringApplication.run(GirigiriApplication.class, args);
	}
}
