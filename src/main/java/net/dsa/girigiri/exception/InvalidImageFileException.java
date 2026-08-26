package net.dsa.girigiri.exception;

/**
 * 추가됨 (강노은) — 왜: 리뷰 사진 업로드를 URL 입력에서 파일 업로드로 바꾸면서, 이미지가 아닌 파일이나
 * 너무 큰 파일을 올렸을 때 "알 수 없는 오류가 발생했습니다"(GlobalExceptionHandler의 기본 Exception
 * 핸들러) 대신 사용자에게 원인을 그대로 보여주기 위한 전용 예외.
 */
public class InvalidImageFileException extends RuntimeException {
	public InvalidImageFileException(String message) {
		super(message);
	}
}
