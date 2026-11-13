-- catalog 값 목록 — 컬럼에 들어갈 수 있는 값(Java enum 과 같은 목록)을 CHECK 로 고정합니다. 구조는 V1__catalog_schema.sql.
-- 값을 넣거나 빼면 이 파일과 Java enum 을 함께 고칩니다 — CatalogSchemaTest 가 둘을 대조합니다.
-- 장르 목록은 아티스트 · 앨범 · 트랙이 같습니다 (Genre). Concert 의 장르도 이 목록을 씁니다 — 바꾸면 concert 의 V2__concert_values.sql 도 함께 고칩니다.

-- ── 장르 (Genre) ──
ALTER TABLE artist ADD CONSTRAINT ck_artist_genres CHECK (genres <@ ARRAY[
    'INDIE_ROCK','ALTERNATIVE','GARAGE','PUNK','HARDCORE','EMO','METAL','POST_ROCK','SHOEGAZE','PSYCHEDELIC',
    'INDIE_POP','DREAM_POP','SYNTH_POP','CITY_POP',
    'FOLK','ELECTRONIC','RNB_SOUL','HIPHOP','JAZZ','BLUES','FUNK','SKA_REGGAE','ETC']::varchar[]);
ALTER TABLE album ADD CONSTRAINT ck_album_genres CHECK (genres <@ ARRAY[
    'INDIE_ROCK','ALTERNATIVE','GARAGE','PUNK','HARDCORE','EMO','METAL','POST_ROCK','SHOEGAZE','PSYCHEDELIC',
    'INDIE_POP','DREAM_POP','SYNTH_POP','CITY_POP',
    'FOLK','ELECTRONIC','RNB_SOUL','HIPHOP','JAZZ','BLUES','FUNK','SKA_REGGAE','ETC']::varchar[]);
ALTER TABLE track ADD CONSTRAINT ck_track_genres CHECK (genres <@ ARRAY[
    'INDIE_ROCK','ALTERNATIVE','GARAGE','PUNK','HARDCORE','EMO','METAL','POST_ROCK','SHOEGAZE','PSYCHEDELIC',
    'INDIE_POP','DREAM_POP','SYNTH_POP','CITY_POP',
    'FOLK','ELECTRONIC','RNB_SOUL','HIPHOP','JAZZ','BLUES','FUNK','SKA_REGGAE','ETC']::varchar[]);

-- ── 앨범 종류 (AlbumType) · 트랙 메타데이터 (Tempo · Mood · Emotion · Style) ──
ALTER TABLE album ADD CONSTRAINT ck_album_type CHECK (type IN ('ALBUM','EP','SINGLE'));
ALTER TABLE track ADD CONSTRAINT ck_track_tempo CHECK (tempo IN ('SLOW','MID','FAST','VERY_FAST'));
ALTER TABLE track ADD CONSTRAINT ck_track_moods CHECK (moods <@ ARRAY[
    'DREAMY','DARK','BRIGHT','WARM','COLD','CALM','ENERGETIC','INTENSE','HEAVY','BOUNCY','GROOVY','URBAN']::varchar[]);
ALTER TABLE track ADD CONSTRAINT ck_track_emotions CHECK (emotions <@ ARRAY[
    'JOY','EXCITEMENT','LOVE','LONGING','SADNESS','LONELINESS','ANXIETY','ANGER','EMPTINESS','COMFORT','HOPE','FREEDOM']::varchar[]);
ALTER TABLE track ADD CONSTRAINT ck_track_styles CHECK (styles <@ ARRAY[
    'LOFI','ACOUSTIC','GUITAR_DRIVEN','DISTORTED','SYNTH_DRIVEN','PIANO_DRIVEN','ELECTRONIC_BEAT','ORCHESTRAL',
    'MINIMAL','VOCAL_FOCUSED','INSTRUMENTAL','LIVE_FEEL','RETRO','EXPERIMENTAL']::varchar[]);

-- ── 상품 (ProductType · Format · GoodsCategory · VinylColor · GoodsSize) ──
ALTER TABLE product ADD CONSTRAINT ck_product_type CHECK (product_type IN ('PHYSICAL_ALBUM','GOODS'));
ALTER TABLE physical_album ADD CONSTRAINT ck_physical_album_format CHECK (format IN ('LP','CD','CASSETTE'));
ALTER TABLE goods ADD CONSTRAINT ck_goods_category CHECK (category IN ('TSHIRT','OUTER','KEYRING','PICK','MUFFLER','SCARF','BAG','HAT','ETC'));
ALTER TABLE product_option ADD CONSTRAINT ck_product_option_vinyl_color CHECK (vinyl_color IN ('BLACK','COLOR'));
ALTER TABLE product_option ADD CONSTRAINT ck_product_option_size CHECK (size IN ('XS','S','M','L','XL','XXL','FREE'));

-- ── 주문 (OrderStatus) ──
ALTER TABLE purchase_order ADD CONSTRAINT ck_order_status CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','REFUNDED'));
