-- catalog v2 (11/13) — 테이블을 새로 만듭니다. 기존 데이터는 옮기지 않습니다 (수집을 다시 돌려 채움).
--   · product 를 부모로 physical_album(LP · CD · 카세트) · goods 가 상속 (id 공유)
--   · 판매 단위는 product_option — 가격 · 재고 · 장바구니 · 주문이 모두 옵션을 가리킵니다
--   · search_doc · band_member · lp 삭제 — 검색은 artist · album · track 을 직접 조회 (lower(이름) 트라이그램 인덱스)
--   · 장르 · 트랙 메타데이터는 enum 배열 컬럼 — 값 목록은 CHECK 로 고정 (Java enum 과 같은 목록 · CatalogSchemaTest 가 대조)
-- 🔴 선행 DBA 작업: CREATE EXTENSION pg_trgm (public 스키마) — V1 과 같음
-- 스키마 간 FK 없음 — user_id 는 member.users 를 가리키지만 FK 를 걸지 않습니다

DROP TABLE IF EXISTS search_doc, order_item, purchase_order, stock, product, goods, lp, track, album,
    band_member, artist, label, outbox CASCADE;

CREATE TABLE label (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_label_name UNIQUE (name)
);

CREATE TABLE artist (
    id          BIGSERIAL PRIMARY KEY,
    label_id    BIGINT REFERENCES label(id),
    name        VARCHAR(100) NOT NULL,
    genres      VARCHAR(32)[] NOT NULL DEFAULT '{}',
    debut_year  INTEGER,
    profile_url VARCHAR(512),
    source      VARCHAR(32)  NOT NULL,                   -- 수집 출처
    external_id VARCHAR(128) NOT NULL,                   -- 출처 안의 식별자
    source_hash VARCHAR(128),                            -- 본문 정규화 해시 · 증분 판정
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_artist_source UNIQUE (source, external_id),   -- ★ 멱등 upsert 기준
    CONSTRAINT ck_artist_genres CHECK (genres <@ ARRAY[
        'INDIE_ROCK','ALTERNATIVE','GARAGE','PUNK','HARDCORE','EMO','METAL','POST_ROCK','SHOEGAZE','PSYCHEDELIC',
        'INDIE_POP','DREAM_POP','SYNTH_POP','CITY_POP',
        'FOLK','ELECTRONIC','RNB_SOUL','HIPHOP','JAZZ','BLUES','FUNK','SKA_REGGAE','ETC']::varchar[])
);
CREATE INDEX idx_artist_name ON artist(name, id);                                        -- 목록 이름순
CREATE INDEX idx_artist_name_trgm ON artist USING gin (lower(name) public.gin_trgm_ops); -- 이름 부분 일치 (목록 q · 검색)
CREATE INDEX idx_artist_genres ON artist USING gin (genres);                             -- 장르 필터

CREATE TABLE album (
    id            BIGSERIAL PRIMARY KEY,
    artist_id     BIGINT       NOT NULL REFERENCES artist(id),
    title         VARCHAR(200) NOT NULL,
    type          VARCHAR(16)  NOT NULL,
    genres        VARCHAR(32)[] NOT NULL DEFAULT '{}',
    released_at   DATE,
    thumbnail_url VARCHAR(512),
    source        VARCHAR(32)  NOT NULL,
    external_id   VARCHAR(128) NOT NULL,
    source_hash   VARCHAR(128),
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_album_source UNIQUE (source, external_id),
    CONSTRAINT ck_album_type CHECK (type IN ('ALBUM','EP','SINGLE')),
    CONSTRAINT ck_album_genres CHECK (genres <@ ARRAY[
        'INDIE_ROCK','ALTERNATIVE','GARAGE','PUNK','HARDCORE','EMO','METAL','POST_ROCK','SHOEGAZE','PSYCHEDELIC',
        'INDIE_POP','DREAM_POP','SYNTH_POP','CITY_POP',
        'FOLK','ELECTRONIC','RNB_SOUL','HIPHOP','JAZZ','BLUES','FUNK','SKA_REGGAE','ETC']::varchar[])
);
CREATE INDEX idx_album_artist ON album(artist_id, released_at DESC, id DESC);            -- 아티스트 상세
CREATE INDEX idx_album_released ON album(released_at DESC NULLS LAST, id DESC);          -- 신보순 목록
CREATE INDEX idx_album_title_trgm ON album USING gin (lower(title) public.gin_trgm_ops);

CREATE TABLE track (
    id       BIGSERIAL PRIMARY KEY,
    album_id BIGINT       NOT NULL REFERENCES album(id),
    track_no INTEGER      NOT NULL,
    title    VARCHAR(200) NOT NULL,
    genres   VARCHAR(32)[] NOT NULL DEFAULT '{}',
    tempo    VARCHAR(16),                                -- 구간 · 모르면 NULL
    moods    VARCHAR(32)[] NOT NULL DEFAULT '{}',        -- 분위기 — 소리가 주는 공기
    emotions VARCHAR(32)[] NOT NULL DEFAULT '{}',        -- 감정 — 화자가 느끼는 마음
    styles   VARCHAR(32)[] NOT NULL DEFAULT '{}',        -- 스타일 — 악기 · 제작 방식
    CONSTRAINT uq_track_no UNIQUE (album_id, track_no),  -- 수집 갱신 기준 (트랙 id 를 유지)
    CONSTRAINT ck_track_tempo CHECK (tempo IN ('SLOW','MID','FAST','VERY_FAST')),
    CONSTRAINT ck_track_genres CHECK (genres <@ ARRAY[
        'INDIE_ROCK','ALTERNATIVE','GARAGE','PUNK','HARDCORE','EMO','METAL','POST_ROCK','SHOEGAZE','PSYCHEDELIC',
        'INDIE_POP','DREAM_POP','SYNTH_POP','CITY_POP',
        'FOLK','ELECTRONIC','RNB_SOUL','HIPHOP','JAZZ','BLUES','FUNK','SKA_REGGAE','ETC']::varchar[]),
    CONSTRAINT ck_track_moods CHECK (moods <@ ARRAY[
        'DREAMY','DARK','BRIGHT','WARM','COLD','CALM','ENERGETIC','INTENSE','HEAVY','BOUNCY','GROOVY','URBAN']::varchar[]),
    CONSTRAINT ck_track_emotions CHECK (emotions <@ ARRAY[
        'JOY','EXCITEMENT','LOVE','LONGING','SADNESS','LONELINESS','ANXIETY','ANGER','EMPTINESS','COMFORT','HOPE','FREEDOM']::varchar[]),
    CONSTRAINT ck_track_styles CHECK (styles <@ ARRAY[
        'LOFI','ACOUSTIC','GUITAR_DRIVEN','DISTORTED','SYNTH_DRIVEN','PIANO_DRIVEN','ELECTRONIC_BEAT','ORCHESTRAL',
        'MINIMAL','VOCAL_FOCUSED','INSTRUMENTAL','LIVE_FEEL','RETRO','EXPERIMENTAL']::varchar[])
);
CREATE INDEX idx_track_title_trgm ON track USING gin (lower(title) public.gin_trgm_ops);

-- ★ 상품 부모 — physical_album · goods 가 id 를 공유합니다. 가격 · 재고는 product_option 에
CREATE TABLE product (
    id            BIGSERIAL PRIMARY KEY,
    product_type  VARCHAR(16)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    thumbnail_url VARCHAR(512),
    purchase_url  VARCHAR(1024),                         -- 실제 판매처(크롤링) 링크
    source        VARCHAR(32)  NOT NULL,
    external_id   VARCHAR(128) NOT NULL,
    source_hash   VARCHAR(128),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_product_source UNIQUE (source, external_id),
    CONSTRAINT ck_product_type CHECK (product_type IN ('PHYSICAL_ALBUM','GOODS'))
);

-- 한 앨범에 같은 포맷이 여러 개일 수 있습니다 (초판 · 재판 · N차 — 이름으로 구분)
CREATE TABLE physical_album (
    id          BIGINT PRIMARY KEY REFERENCES product(id),
    album_id    BIGINT      NOT NULL REFERENCES album(id),
    format      VARCHAR(16) NOT NULL,
    released_at DATE,
    CONSTRAINT ck_physical_album_format CHECK (format IN ('LP','CD','CASSETTE'))
);
CREATE INDEX idx_physical_album_album ON physical_album(album_id);
CREATE INDEX idx_physical_album_released ON physical_album(released_at DESC NULLS LAST, id DESC);

CREATE TABLE goods (
    id        BIGINT PRIMARY KEY REFERENCES product(id),
    artist_id BIGINT      NOT NULL REFERENCES artist(id),
    category  VARCHAR(16) NOT NULL,
    CONSTRAINT ck_goods_category CHECK (category IN ('TSHIRT','OUTER','KEYRING','PICK','MUFFLER','SCARF','BAG','HAT','ETC'))
);
CREATE INDEX idx_goods_artist ON goods(artist_id, id DESC);

-- 🔴 "vinyl_color 는 LP 옵션에만 · size 는 굿즈 옵션에만" 은 테이블이 달라 CHECK 로 못 겁니다 — 적재 서비스(OptionRule)가 막습니다
CREATE TABLE product_option (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES product(id),
    name        VARCHAR(100) NOT NULL,                   -- 블랙반 · 투명 레드 컬러반 · 기본 · L …
    vinyl_color VARCHAR(16),
    size        VARCHAR(8),
    price       INTEGER      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_product_option_name UNIQUE (product_id, name),   -- 수집 갱신 기준 · 상품의 옵션 조회
    CONSTRAINT ck_product_option_price CHECK (price >= 0),
    CONSTRAINT ck_product_option_vinyl_color CHECK (vinyl_color IN ('BLACK','COLOR')),
    CONSTRAINT ck_product_option_size CHECK (size IN ('XS','S','M','L','XL','XXL','FREE')),
    CONSTRAINT ck_product_option_kind CHECK (vinyl_color IS NULL OR size IS NULL)
);

-- ★ 재고 — 옵션과 1:1. 주문 경합의 행 락 대상이라 옵션과 떼어 좁게 둡니다 (차감은 qty >= n 조건부 UPDATE)
CREATE TABLE stock (
    option_id  BIGINT      PRIMARY KEY REFERENCES product_option(id),
    qty        INTEGER     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_stock_qty CHECK (qty >= 0)
);

-- 장바구니 — 재고를 읽기만 합니다 (차감은 주문 생성 때). TTL 없음
CREATE TABLE cart_item (
    user_id    BIGINT      NOT NULL,                     -- member.users (FK 없음)
    option_id  BIGINT      NOT NULL REFERENCES product_option(id),
    qty        INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, option_id),
    CONSTRAINT ck_cart_item_qty CHECK (qty >= 1)
);

CREATE TABLE purchase_order (                            -- order 는 예약어
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,               -- member.users (FK 없음)
    status          VARCHAR(16)  NOT NULL,
    total_price     INTEGER      NOT NULL,
    pg_order_id     VARCHAR(64),                         -- Payment prepare 응답 (NULL = 준비 전)
    recipient_name  VARCHAR(20)  NOT NULL,               -- 주문 시점 배송지 복사본
    recipient_phone VARCHAR(20)  NOT NULL,
    zonecode        VARCHAR(5)   NOT NULL,
    address         VARCHAR(200) NOT NULL,
    address_detail  VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','REFUNDED')),
    CONSTRAINT ck_order_total_price CHECK (total_price >= 0)
);
CREATE INDEX idx_order_user ON purchase_order(user_id, id DESC);
CREATE INDEX idx_order_pending ON purchase_order(created_at) WHERE status = 'PENDING';   -- 만료 스캔

CREATE TABLE order_item (
    order_id  BIGINT  NOT NULL REFERENCES purchase_order(id),
    option_id BIGINT  NOT NULL REFERENCES product_option(id),
    qty       INTEGER NOT NULL,
    price     INTEGER NOT NULL,                          -- ★ 주문 시점 가격 고정
    PRIMARY KEY (order_id, option_id),
    CONSTRAINT ck_order_item_qty CHECK (qty >= 1),
    CONSTRAINT ck_order_item_price CHECK (price >= 0)
);

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    event_id     UUID        NOT NULL UNIQUE,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ                             -- NULL = 미발행
);
CREATE INDEX idx_outbox_unpublished ON outbox(id) WHERE published_at IS NULL;
