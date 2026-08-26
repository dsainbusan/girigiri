package net.dsa.girigiri.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 추가됨 (강노은) — 왜: 리뷰 사진을 파일 업로드로 받게 되면서(FileStorageUtil 참고) 로컬 디스크에
 * 저장한 파일을 브라우저가 "/upload/..." URL로 내려받을 수 있게 정적 리소스 매핑이 필요해졌다.
 * 프로젝트에 config/ 패키지가 아직 없어서 새로 만든다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

	@Value("${app.upload.dir:upload}")
	private String uploadDir;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler("/" + uploadDir + "/**")
				.addResourceLocations(location);
	}
}
