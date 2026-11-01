-- 팔로워 수는 Member 가 원본 (FR-MEM-19 — Catalog 비정규화 없음). 갱신 경로가 없어 늘 0 이던 컬럼을 지웁니다 (10/24)
ALTER TABLE artist DROP COLUMN follower_count;
