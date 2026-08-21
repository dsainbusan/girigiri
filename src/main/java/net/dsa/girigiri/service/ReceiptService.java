package net.dsa.girigiri.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.dsa.girigiri.domain.entity.ProductEntity;
import net.dsa.girigiri.domain.entity.ReceiptEntity;
import net.dsa.girigiri.domain.entity.ReservationEntity;
import net.dsa.girigiri.domain.entity.StoreEntity;
import net.dsa.girigiri.repository.ProductRepository;
import net.dsa.girigiri.repository.ReceiptRepository;
import net.dsa.girigiri.repository.ReservationRepository;
import net.dsa.girigiri.repository.StoreRepository;
import net.dsa.girigiri.util.ReceiptPdfGenerator;
import net.dsa.girigiri.util.SupabaseStorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 결제 완료된 예약의 영수증 PDF를 실제로 만들어서 Supabase Storage(클라우드 파일 저장소)에 올리고,
 * 그 URL을 DB(Receipt)에 남기는 서비스.
 *
 * 이미 따로 테스트해둔 ReceiptPdfGenerator(HTML→PDF 변환)와 QrCodeUtil(QR 이미지)을 그대로 재사용하고,
 * 여기서는 "예약 정보를 가져와서 PDF를 만들 재료로 조립 + Supabase에 업로드 + DB에 기록"만 새로 담당한다.
 *
 * Supabase 프로젝트(.env의 SUPABASE_URL/SUPABASE_SERVICE_KEY)가 아직 준비 안 됐으면, 예전처럼
 * 프로젝트 폴더 밑 receipts/에 로컬 파일로 저장하는 걸로 자동 대체된다 — 그래야 Supabase 계정이
 * 생기기 전까지도 예약/영수증 흐름 전체가 안 깨지고 계속 테스트 가능하다. .env를 채우고 나면
 * 코드를 안 건드려도 자동으로 Supabase 쪽을 쓰게 된다 (SupabaseStorageClient.isConfigured() 참고).
 */
@Service
@RequiredArgsConstructor
public class ReceiptService {

	// Supabase 미설정 시 로컬 폴백 저장 위치
	private static final String LOCAL_FALLBACK_DIR = "receipts";

	private final ReservationRepository reservationRepository;
	private final ProductRepository productRepository;
	private final StoreRepository storeRepository;
	private final ReceiptRepository receiptRepository;
	private final SupabaseStorageClient supabaseStorageClient;

	@Transactional
	public ReceiptEntity generateReceipt(Long reservationId) {
		ReservationEntity reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다. id=" + reservationId));

		ProductEntity product = productRepository.findById(reservation.getProductId())
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. id=" + reservation.getProductId()));

		StoreEntity store = storeRepository.findById(reservation.getStoreId())
				.orElseThrow(() -> new EntityNotFoundException("매장을 찾을 수 없습니다. id=" + reservation.getStoreId()));

		// 취소/노쇼는 더 이상 픽업이 일어날 수 없는 상태라 QR을 넣을 필요가 없다.
		// (예약 생성 시점(confirmed)과 매장 수락 후(ready)엔 당연히 QR 포함 — ready일 때 QR을 빼면
		// 정작 픽업하러 갈 때 보여줄 QR이 없어진다. picked도 "방금 픽업했다"는 확인용으로 유지한다.)
		boolean includeQr = switch (reservation.getStatus()) {
			case "confirmed", "ready", "picked" -> true;
			default -> false;
		};

		// 취소/노쇼는 결제 상태가 정상 예약이랑 다르니까, 영수증에도 그걸 명확히 표시해서
		// "결제완료 3000원"만 덩그러니 보고 오해하는 일이 없게 한다.
		String noticeMessage = null;
		boolean refundNotice = false;
		switch (reservation.getStatus()) {
			case "cancelled" -> {
				refundNotice = true;
				noticeMessage = "STORE".equals(reservation.getCancelledBy())
						? "이 예약은 매장 사정으로 취소되었어요 (사유: " + reservation.getCancelReason() + "). 결제하신 금액은 환불됩니다."
						: "이 예약은 취소되었어요. 결제하신 금액은 환불됩니다.";
			}
			case "noshowed" -> {
				refundNotice = false;
				noticeMessage = "픽업 시간이 지나 노쇼 처리된 예약이에요. 결제하신 금액은 환불되지 않아요.";
			}
			default -> { }   // confirmed/ready/picked/pending은 정상 영수증, 배너 없음
		}

		ReceiptPdfGenerator.ReceiptData data = new ReceiptPdfGenerator.ReceiptData(
				store.getStoreName(),
				product.getName(),
				reservation.getReservedQuantity(),
				reservation.getTotalPrice(),
				reservation.getPickupTime(),
				reservation.getPickupCode(),
				includeQr,
				noticeMessage,
				refundNotice
		);

		byte[] pdf;
		try {
			pdf = ReceiptPdfGenerator.generate(data);
		} catch (IOException e) {
			throw new IllegalStateException("영수증 PDF 생성에 실패했습니다. reservationId=" + reservationId, e);
		}

		// 파일명은 항상 reservationId 기준으로 고정 -> 취소/노쇼로 다시 만들 때 같은 자리에 덮어써진다
		// (Supabase는 x-upsert:true로, 로컬 폴백은 그냥 같은 파일에 덮어쓰기 때문에 같은 예약 URL/경로가
		//  계속 최신 내용으로 유지됨).
		String fileName = "receipt-" + reservationId + ".pdf";
		String pdfUrl = supabaseStorageClient.isConfigured()
				? supabaseStorageClient.uploadPdf(fileName, pdf)
				: saveLocalFallback(reservationId, fileName, pdf);

		// 이미 이 예약의 영수증 레코드가 있으면(재생성 케이스) 그 레코드를 갱신하고, 없으면 새로 만든다.
		// (이렇게 안 하면 취소/노쇼로 재생성할 때마다 Receipt 테이블에 중복 row가 계속 쌓인다.)
		ReceiptEntity receipt = receiptRepository.findByReservationId(reservationId)
				.orElseGet(() -> ReceiptEntity.builder().reservationId(reservationId).build());
		receipt.setPdfUrl(pdfUrl);
		return receiptRepository.save(receipt);
	}

	/** Supabase 설정 전까지 쓰는 로컬 저장 폴백. pdfUrl 자리엔 로컬 파일 경로가 그대로 들어간다. */
	private String saveLocalFallback(Long reservationId, String fileName, byte[] pdf) {
		try {
			Path dir = Paths.get(LOCAL_FALLBACK_DIR);
			Files.createDirectories(dir);
			Path savedPath = dir.resolve(fileName);
			Files.write(savedPath, pdf);
			return savedPath.toString();
		} catch (IOException e) {
			throw new IllegalStateException("영수증 파일 저장에 실패했습니다. reservationId=" + reservationId, e);
		}
	}
}
