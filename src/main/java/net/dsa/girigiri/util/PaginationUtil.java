package net.dsa.girigiri.util;

import java.util.List;

/**
 * 슈퍼어드민 목록 화면(회원/공지사항/신고·문의)에서 공통으로 쓰던 페이지 자르기 로직.
 * 2026-09-03, SuperAdminController를 도메인별로 분리하면서 중복을 피하려고 추출했다.
 * 데이터가 이 프로젝트 규모에서 몇백 건을 넘길 일이 없어 DB 레벨 Pageable 대신
 * 정렬된 List를 메모리에서 그냥 잘라 쓴다.
 */
public final class PaginationUtil {

	private PaginationUtil() {
	}

	// 이미 정렬된 리스트를 페이지 단위로 자른다 — page는 0부터 시작, 범위를 벗어나면 빈 리스트.
	public static <T> List<T> paginate(List<T> sorted, int page, int pageSize) {
		int from = Math.max(page, 0) * pageSize;
		if (from >= sorted.size()) {
			return List.of();
		}
		return sorted.subList(from, Math.min(from + pageSize, sorted.size()));
	}

	public static int totalPages(int totalItems, int pageSize) {
		return Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
	}
}
