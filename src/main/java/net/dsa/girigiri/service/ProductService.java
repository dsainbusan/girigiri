package net.dsa.girigiri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dsa.girigiri.domain.dto.ProductRegisterForm;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * TODO(송채현): 실 서비스 배포 시 로컬 static 디렉터리 저장 대신
 *   S3 등 외부 스토리지 연동으로 교체할 것. 현재는 로컬 개발(bootRun) 전용 구현이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

	private static final Path UPLOAD_DIR = Path.of("src/main/resources/static/images/products");
	private static final String UPLOAD_URL_PREFIX = "/images/products/";

	private final ProductRepository productRepository;

	public ProductEntity register(Long storeId, ProductRegisterForm form) {
		ProductEntity product = ProductEntity.builder()
				.storeId(storeId)
				.name(form.getName())
				.originalPrice(form.getOriginalPrice())
				.discountedPrice(form.getDiscountedPrice())
				.quantity(form.getQuantity())
				.remainingQuantity(form.getQuantity())
				.imageUrl(storeImage(form.getImage()))
				.description(form.getDescription())
				.status("active")
				.build();

		return productRepository.save(product);
	}

	private String storeImage(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			return null;
		}

		try {
			Files.createDirectories(UPLOAD_DIR);

			String originalName = StringUtils.cleanPath(image.getOriginalFilename() == null ? "" : image.getOriginalFilename());
			String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
			String savedName = UUID.randomUUID() + ext;

			Path target = UPLOAD_DIR.resolve(savedName);
			image.transferTo(target);

			return UPLOAD_URL_PREFIX + savedName;
		} catch (IOException e) {
			log.warn("> [ProductService] 상품 이미지 저장 실패: {}", e.getMessage());
			throw new UncheckedIOException(e);
		}
	}
}
