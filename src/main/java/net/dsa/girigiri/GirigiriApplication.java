package net.dsa.girigiri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class GirigiriApplication {
	public static void main(String[] args) {
		SpringApplication.run(GirigiriApplication.class, args);
	}
}
