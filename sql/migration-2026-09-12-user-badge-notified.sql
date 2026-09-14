-- 2026-09-12 (문창호) — 뱃지 획득 토스트("🎉 새 뱃지 획득!")가 몇 번이고 다시 뜨지 않도록,
-- 이미 화면에 보여준 뱃지인지 표시하는 컬럼.
--
-- 기존엔 "이 build() 호출 안에서 방금 딴 것"만 토스트로 보여줬는데, 이러면 랭킹 뱃지(RANK_1/2/3)처럼
-- RankingBadgeScheduler가 매달 1일에 미리 심어주는 뱃지는 절대 토스트에 안 뜬다(그 순간엔 어떤 유저도
-- build()를 호출하지 않으니까). 그래서 "아직 안 보여준 것 = notified가 false인 것 전부"로 기준을 바꿨다
-- (LedgerService.build 참고).
--
-- 이미 있던 행(지금까지 딴 뱃지)은 전부 "이미 봤다"로 두는 게 안전해서 DEFAULT TRUE로 잡는다 —
-- 새로 딴 뱃지만 코드에서 명시적으로 notified=false로 넣는다.
-- 로컬은 ddl-auto=update로 자동 추가되지만, 운영/통합 DB엔 이 스크립트로 반영한다.

ALTER TABLE user_badge
    ADD COLUMN notified BOOLEAN NOT NULL DEFAULT TRUE;
