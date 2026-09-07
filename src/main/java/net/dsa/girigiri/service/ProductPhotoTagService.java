package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.PhotoTagSuggestionDto;
import net.dsa.girigiri.exception.InvalidImageFileException;
import net.dsa.girigiri.util.ProductPhotoTagClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 강노은: "상품 사진 자동 태깅" 비즈니스 로직 — 점주가 상품 등록 폼에서 사진을 고르면(제출 전)
 * 그 사진만 보고 카테고리·품목명을 추측해준다. 실제 상품 저장(ProductService, 문창호 담당)과는
 * 완전히 별개 흐름 — 여기서 뭘 실패해도 상품 등록/수정 폼 제출 자체엔 영향이 없어야 한다.
 *
 * ProductPhotoTagClient(원문 텍스트만 받아오는 얇은 API 클라이언트)와 역할을 나눈다 —
 * 파일 검증, Gemini 응답 텍스트 파싱("카테고리: .../품목명: ..." → DTO), 허용 카테고리 판단은
 * 전부 비즈니스 로직이라 여기(서비스 레이어)에 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPhotoTagService {

	private final ProductPhotoTagClient productPhotoTagClient;

	// FileStorageUtil(강노은, 리뷰/문의 사진 업로드)과 같은 제한을 그대로 맞춘다 — 어차피 이 사진은
	// 그대로 상품 사진으로 올라갈 사진이라, 여기서 미리 걸러두면 "추천은 됐는데 실제 등록은 거부되는"
	// 불일치가 안 생긴다.
	private static final Set<String> ALLOWED_CONTENT_TYPES =
			Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
	private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB

	private static final Set<String> ALLOWED_CATEGORIES = Set.of("베이커리", "반찬", "도시락", "카페");

	/**
	 * 사진 1장을 보고 카테고리·품목명을 추측한다. AI가 응답을 못 주거나(.env 미설정/API 실패)
	 * 뭘 알아보지 못했으면 두 필드 다 null인 빈 결과를 돌려준다 — "추천 실패"도 정상 흐름이라
	 * 예외를 던지지 않는다(호출부인 productForm.html은 null이면 그 칸을 그냥 안 채운다).
	 * 사진 자체가 이미지가 아니거나 너무 크면(폼에 실제로 올릴 수 없는 파일) InvalidImageFileException.
	 */
	public PhotoTagSuggestionDto suggest(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			return PhotoTagSuggestionDto.empty();
		}
		validate(image);

		byte[] bytes;
		try {
			bytes = image.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("이미지 파일을 읽는 데 실패했습니다.", e);
		}

		Optional<String> rawText = productPhotoTagClient.suggest(bytes, image.getContentType());
		return rawText.map(this::parse).orElse(PhotoTagSuggestionDto.empty());
	}

	private void validate(MultipartFile image) {
		String contentType = image.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
			throw new InvalidImageFileException("이미지 파일(jpg, png, webp, gif)만 업로드할 수 있어요.");
		}
		if (image.getSize() > MAX_FILE_SIZE) {
			throw new InvalidImageFileException("이미지 파일은 5MB 이하만 업로드할 수 있어요.");
		}
	}

	/** "카테고리: 베이커리\n품목명: 크루아상" 형식의 AI 원문 응답을 DTO로 파싱한다. */
	private PhotoTagSuggestionDto parse(String text) {
		String category = extract(text, "카테고리");
		String itemName = extract(text, "품목명");

		// 모델이 "기타"라고 답했거나 지시를 안 따르고 딴 값을 붙인 경우 — 확실하지 않은 추천은
		// 아예 안 보여주는 쪽이 "잘못된 추천을 그대로 등록"하는 것보다 안전하다.
		if (category != null && !ALLOWED_CATEGORIES.contains(category)) {
			category = null;
		}
		if (itemName != null && itemName.isBlank()) {
			itemName = null;
		}
		log.debug("> [ProductPhotoTagService] 태깅 추천 - category={}, itemName={}", category, itemName);
		return new PhotoTagSuggestionDto(category, itemName);
	}

	/** "라벨: 값" 줄을 찾아 값만 뽑아낸다. 전각 콜론(：)도 방어한다. */
	private String extract(String text, String label) {
		if (text == null) {
			return null;
		}
		for (String line : text.split("\n")) {
			int labelIdx = line.indexOf(label);
			if (labelIdx < 0) {
				continue;
			}
			int colonIdx = line.indexOf(':', labelIdx);
			if (colonIdx < 0) {
				colonIdx = line.indexOf('：', labelIdx);
			}
			if (colonIdx < 0) {
				continue;
			}
			return line.substring(colonIdx + 1).trim();
		}
		return null;
	}
}
