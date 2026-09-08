package net.dsa.girigiri.util;

import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.exception.InvalidImageFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
		// 추가됨 (2026-09-08, 코드 감사) — 위 Content-Type 체크는 클라이언트(브라우저)가 요청 헤더에
		// 실어 보낸 값을 그대로 믿는 것이라, 확장자만 바꾸거나 헤더를 조작하면 임의 바이너리도 통과할
		// 수 있었다 — 저장 경로(upload/**)가 그대로 공개 서빙되기 때문에 실제 위험. 파일 내용 맨 앞
		// 바이트(매직바이트)가 실제 이미지 포맷과 일치하는지 한 번 더 확인한다.
		if (!looksLikeAllowedImage(file)) {
			throw new InvalidImageFileException("이미지 파일(jpg, png, webp, gif)만 업로드할 수 있어요.");
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

	/**
	 * 파일 맨 앞 12바이트(매직바이트)를 읽어 jpg/png/gif/webp 중 하나의 실제 시그니처와 일치하는지
	 * 확인한다. Content-Type 헤더는 안 보고 파일 내용 자체만 본다.
	 */
	private boolean looksLikeAllowedImage(MultipartFile file) {
		byte[] header = new byte[12];
		int read;
		try (InputStream in = file.getInputStream()) {
			read = in.readNBytes(header, 0, header.length);
		} catch (IOException e) {
			throw new UncheckedIOException("이미지 파일을 읽는 중 오류가 발생했어요.", e);
		}
		if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
			return true; // JPEG: FF D8 FF
		}
		if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
				&& header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
			return true; // PNG: 89 50 4E 47 0D 0A 1A 0A
		}
		if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8'
				&& (header[4] == '7' || header[4] == '9') && header[5] == 'a') {
			return true; // GIF: "GIF87a" / "GIF89a"
		}
		if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
				&& header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
			return true; // WEBP: "RIFF"....'WEBP'
		}
		return false;
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
