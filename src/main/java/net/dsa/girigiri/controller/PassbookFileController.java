package net.dsa.girigiri.controller;

import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.BankAccountChangeRequestEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.service.BankAccountChangeService;
import net.dsa.girigiri.service.LookupService;
import net.dsa.girigiri.util.FileStorageUtil;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 추가됨 (2026-10-07, 계좌 보안) — 통장 사본은 FileStorageUtil의 공개 업로드 경로(/upload/**)가 아니라
 * upload-private/에 저장돼서 URL만으로는 아무도 못 연다(FileStorageUtil#storePrivate 참고). 이 두
 * 엔드포인트가 유일한 접근 경로이고, 둘 다 /superadmin/** 아래라 SuperAdminAccessInterceptor가
 * role=ADMIN을 이미 강제한다 — 슈퍼어드민만 열 수 있다(요구사항 2: 입점 심사·계좌 변경 심사에서
 * 통장 사본 확인).
 */
@Controller
@RequestMapping("/superadmin")
@RequiredArgsConstructor
public class PassbookFileController {

	private final LookupService lookupService;
	private final BankAccountChangeService bankAccountChangeService;
	private final FileStorageUtil fileStorageUtil;

	/** 매장 상세(입점 심사)에서 보는, 지금 등록돼 있는 통장 사본. */
	@GetMapping("/stores/{id}/passbook")
	public ResponseEntity<Resource> storePassbook(@PathVariable Long id) {
		StoreEntity store = lookupService.getStore(id);
		return stream(store.getPassbookImageUrl());
	}

	/** 계좌 변경 신청 상세에서 보는, 이번에 새로 첨부된 통장 사본. */
	@GetMapping("/bank-account-requests/{id}/passbook")
	public ResponseEntity<Resource> changeRequestPassbook(@PathVariable Long id) {
		BankAccountChangeRequestEntity request = bankAccountChangeService.getRequestOrThrow(id);
		return stream(request.getNewPassbookImageUrl());
	}

	private ResponseEntity<Resource> stream(String relativePath) {
		if (relativePath == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 통장 사본이 없어요.");
		}
		Path path = fileStorageUtil.resolvePrivate(relativePath);
		if (!Files.exists(path)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없어요.");
		}
		MediaType contentType = detectContentType(path);
		return ResponseEntity.ok().contentType(contentType).body(new FileSystemResource(path));
	}

	private MediaType detectContentType(Path path) {
		try {
			String probed = Files.probeContentType(path);
			return probed != null ? MediaType.parseMediaType(probed) : MediaType.IMAGE_JPEG;
		} catch (IOException e) {
			return MediaType.IMAGE_JPEG;
		}
	}
}
