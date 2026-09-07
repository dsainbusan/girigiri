package net.dsa.girigiri.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.dto.PhotoTagSuggestionDto;
import net.dsa.girigiri.exception.InvalidImageFileException;
import net.dsa.girigiri.service.ProductPhotoTagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 강노은: "상품 사진 자동 태깅" API — 점주 상품 등록/수정 폼(storeView/productForm.html)에서
 * 사진을 고르면(폼 제출 전) fetch로 이 API를 호출해 카테고리·품목명 추천을 받는다.
 * 실제 상품 저장은 여전히 StoreProductController → ProductService(문창호 담당)가 처리 — 이
 * 컨트롤러는 추천만 하고 아무것도 저장하지 않는다.
 *
 * GlobalExceptionHandler는 화면(뷰 이름)을 반환하는 MVC 예외 처리용이라, fetch로 호출하는 이
 * API에는 안 맞는다(그대로 두면 JSON을 기대하는 프론트가 HTML 에러 페이지를 받게 된다) —
 * InvalidImageFileException은 여기서 직접 잡아 JSON 400으로 응답한다.
 */
@RestController
@RequestMapping("/api/store/products")
@RequiredArgsConstructor
public class ProductPhotoTagApiController {

	private final ProductPhotoTagService productPhotoTagService;

	@PostMapping("/photo-tag")
	public ResponseEntity<?> suggest(@RequestParam("image") MultipartFile image, HttpSession session) {
		Long ownerId = (Long) session.getAttribute("userId");
		if (ownerId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "login_required"));
		}
		try {
			PhotoTagSuggestionDto suggestion = productPhotoTagService.suggest(image);
			return ResponseEntity.ok(suggestion);
		} catch (InvalidImageFileException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}
}
