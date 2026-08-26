package net.dsa.girigiri.util;

import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.exception.InvalidImageFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 추가됨 (강노은) — 왜: 사진 리뷰를 "URL 입력"에서 "파일 업로드"로 바꾸면서 필요해진 공용 유틸.
 * 프로젝트에 아직 파일 업로드 인프라가 없어서(다른 도메인 imageUrl은 여전히 URL 문자열) 우선
 * 로컬 디스크 저장으로 구현한다 — 추후 다른 도메인(상품 사진 등)도 파일 업로드로 바뀌면 그대로
 * 재사용 가능하도록 subDir 파라미터로 분리해뒀다.
 *
 * 저장 위치는 프로젝트 루트의 upload/ 디렉터리(.gitignore에 이미 등록돼있던 이름을 그대로 씀),
 * app.upload.dir 프로퍼티로 변경 가능. WebMvcConfig가 이 디렉터리를 "/upload/**" 로 서빙한다.
 */
@Slf4j
@Component
public class FileStorageUtil {

	@Value("${app.upload.dir:upload}")
	private String uploadDir;

	private static final Set<String> ALLOWED_CONTENT_TYPES =
			Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
	private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB

	/**
	 * 이미지를 upload/{subDir}/ 아래 랜덤 파일명으로 저장하고, "/upload/{subDir}/파일명" 형태의
	 * 웹 접근 경로를 돌려준다. file이 null이거나 비어있으면 null을 돌려준다(=사진 없음).
	 */
	public String store(MultipartFile file, String subDir) {
		if (file == null || file.isEmpty()) {
			return null;
		}

		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
			throw new InvalidImageFileException("이미지 파일(jpg, png, webp, gif)만 업로드할 수 있어요.");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new InvalidImageFileException("이미지 파일은 5MB 이하만 업로드할 수 있어요.");
		}

		try {
			Path targetDir = Path.of(uploadDir, subDir);
			Files.createDirectories(targetDir);

			String filename = UUID.randomUUID() + extensionFor(contentType);
			Path target = targetDir.resolve(filename);
			file.transferTo(target);

			return "/" + uploadDir + "/" + subDir + "/" + filename;
		} catch (IOException e) {
			throw new UncheckedIOException("이미지 파일 저장에 실패했습니다.", e);
		}
	}

	/**
	 * webPath가 우리가 store()로 만든 파일(= "/{uploadDir}/{subDir}/..." 경로)일 때만 실제 디스크에서
	 * 지운다. 과거에 URL로 입력됐던 외부 이미지 등 우리 소관이 아닌 경로는 건드리지 않는다.
	 */
	public void deleteIfOwned(String webPath, String subDir) {
		if (webPath == null) {
			return;
		}
		String prefix = "/" + uploadDir + "/" + subDir + "/";
		if (!webPath.startsWith(prefix)) {
			return;
		}
		try {
			Files.deleteIfExists(Path.of(uploadDir, subDir, webPath.substring(prefix.length())));
		} catch (IOException e) {
			// 삭제 실패는 치명적이지 않다 — DB에서 참조만 없어지면 됨. 디스크에 파일이 남아도 다음 저장에 덮이지 않음(랜덤명).
			log.warn("리뷰 사진 파일 삭제 실패: {}", webPath, e);
		}
	}

	private String extensionFor(String contentType) {
		return switch (contentType.toLowerCase(Locale.ROOT)) {
			case "image/jpeg" -> ".jpg";
			case "image/png" -> ".png";
			case "image/webp" -> ".webp";
			case "image/gif" -> ".gif";
			default -> "";
		};
	}
}
