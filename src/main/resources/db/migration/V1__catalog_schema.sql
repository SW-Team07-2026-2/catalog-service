-- catalog 스키마 — ERD v6 기준 + 수집 멱등키(source · external_id · source_hash) + 상품 구매처 링크(purchase_url)
-- 🔴 선행 DBA 작업: CREATE EXTENSION pg_trgm (public 스키마 · 수퍼유저) — 없으면 아래 GIN 인덱스에서 실패합니다
-- 스키마 간 FK 없음 — order.user_id 는 member.users 를 가리키지만 FK 를 걸지 않습니다

CREATE TABLE label (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_label_name UNIQUE (name)
);

CREATE TABLE artist (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    genre          VARCHAR(50),
    label_id       BIGINT REFERENCES label(id),
    follower_count INTEGER      NOT NULL DEFAULT 0,      -- TODO(결정대기): 갱신 경로 없음 (member.activity 를 구독하지 않음)
    debut_year     INTEGER,
    profile_url    VARCHAR(512),
    source         VARCHAR(32)  NOT NULL,                -- 수집 출처 (spotify · lpick · manual …)
    external_id    VARCHAR(128) NOT NULL,                -- 출처 안의 식별자
    source_hash    VARCHAR(128),                         -- 본문 정규화 해시 (FR-ING-11) · I15 증분 판정
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_artist_source UNIQUE (source, external_id)   -- ★ 멱등 upsert 기준
);
CREATE INDEX idx_artist_name ON artist(name);

CREATE TABLE band_member (
    id        BIGSERIAL PRIMARY KEY,
    artist_id BIGINT       NOT NULL REFERENCES artist(id),
    name      VARCHAR(100) NOT NULL,
    role      VARCHAR(50)
);
CREATE INDEX idx_band_member_artist ON band_member(artist_id);

CREATE TABLE album (
    id            BIGSERIAL PRIMARY KEY,
    artist_id     BIGINT       NOT NULL REFERENCES artist(id),
    title         VARCHAR(200) NOT NULL,
    type          VARCHAR(16)  NOT NULL,                 -- ALBUM | EP | SINGLE
    released_at   DATE,
    thumbnail_url VARCHAR(512),
    source        VARCHAR(32)  NOT NULL,
    external_id   VARCHAR(128) NOT NULL,
    source_hash   VARCHAR(128),
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_album_source UNIQUE (source, external_id)
);
CREATE INDEX idx_album_artist ON album(artist_id);
CREATE INDEX idx_album_released ON album(released_at DESC);

CREATE TABLE track (
    id       BIGSERIAL PRIMARY KEY,
    album_id BIGINT       NOT NULL REFERENCES album(id),
    track_no INTEGER      NOT NULL,
    title    VARCHAR(200) NOT NULL,
    CONSTRAINT uq_track_no UNIQUE (album_id, track_no)
);

CREATE TABLE lp (
    id          BIGSERIAL PRIMARY KEY,
    album_id    BIGINT       NOT NULL REFERENCES album(id),
    press_type  VARCHAR(50),                             -- 컬러반 · 한정반 …
    released_at DATE,
    source      VARCHAR(32)  NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    source_hash VARCHAR(128),
    CONSTRAINT uq_lp_source UNIQUE (source, external_id)
);
CREATE INDEX idx_lp_album ON lp(album_id);
CREATE INDEX idx_lp_released ON lp(released_at DESC);

CREATE TABLE goods (
    id          BIGSERIAL PRIMARY KEY,
    artist_id   BIGINT       NOT NULL REFERENCES artist(id),
    name        VARCHAR(200) NOT NULL,
    category    VARCHAR(32),                             -- TSHIRT · POSTER …
    source      VARCHAR(32)  NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    source_hash VARCHAR(128),
    CONSTRAINT uq_goods_source UNIQUE (source, external_id)
);
CREATE INDEX idx_goods_artist ON goods(artist_id);

-- ★ 판매 단위 — LP · CD · GOODS 를 하나로. 우리가 주 판매처, purchase_url 은 실제 판매처(크롤링) 링크 1개
CREATE TABLE product (
    id            BIGSERIAL PRIMARY KEY,
    product_type  VARCHAR(16)  NOT NULL,                 -- LP | CD | GOODS
    ref_id        BIGINT       NOT NULL,                 -- lp.id | album.id | goods.id
    name          VARCHAR(255) NOT NULL,
    price         INTEGER      NOT NULL CHECK (price >= 0),
    thumbnail_url VARCHAR(512),
    purchase_url  VARCHAR(1024),
    CONSTRAINT uq_product_ref UNIQUE (product_type, ref_id)
);

-- ★ 재고 — 좌석과 달리 수량이라 락 없이 조건부 UPDATE (qty >= n) 로 원자적 차감
CREATE TABLE stock (
    product_id BIGINT      PRIMARY KEY REFERENCES product(id),
    qty        INTEGER     NOT NULL CHECK (qty >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE purchase_order (                            -- order 는 예약어
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,                    -- member.users (FK 없음)
    status      VARCHAR(16) NOT NULL,                    -- PENDING | CONFIRMED | CANCELLED
    total_price INTEGER     NOT NULL,
    payment_key VARCHAR(64),                             -- Payment prepare 응답 (NULL = 준비 전)
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_order_user ON purchase_order(user_id, id DESC);
CREATE INDEX idx_order_pending ON purchase_order(created_at) WHERE status = 'PENDING';   -- 만료 스캔

CREATE TABLE order_item (
    order_id   BIGINT  NOT NULL REFERENCES purchase_order(id),
    product_id BIGINT  NOT NULL REFERENCES product(id),
    qty        INTEGER NOT NULL,
    price      INTEGER NOT NULL,                         -- ★ 주문 시점 가격 고정
    PRIMARY KEY (order_id, product_id)
);

-- ★ pg_trgm 통합 검색 (FR-CAT-08 · AI 검색의 폴백 최종단)
CREATE TABLE search_doc (
    id          BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(16)  NOT NULL,                   -- ARTIST | ALBUM | TRACK
    target_id   BIGINT       NOT NULL,
    body        TEXT         NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_search_doc_target UNIQUE (target_type, target_id)
);
-- Flyway 는 search_path 를 catalog 로 두므로 연산자 클래스를 public 으로 한정합니다
CREATE INDEX idx_search_doc_body ON search_doc USING gin (body public.gin_trgm_ops);

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    event_id     UUID        NOT NULL UNIQUE,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ                             -- NULL = 미발행
);
CREATE INDEX idx_outbox_unpublished ON outbox(id) WHERE published_at IS NULL;  -- ★ 부분 인덱스
