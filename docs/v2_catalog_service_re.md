# catalog-service v2 리팩토링

> **상태: 구현 완료 · 커밋 전 (2026-11-13).** catalog-service 저장소의 `refactor/catalog-v2` 브랜치(develop 에서 분기) 작업 트리에 있습니다. 커밋 · 푸시 · PR 은 하지 않았습니다.
> 테스트 37건 통과 (건너뜀 0 · Docker 필요). 다른 저장소에서 바꾼 것은 `manifests` 의 Kafka 토픽 목록 1개 파일뿐입니다 (9장).
> 2026-11-13(오후): 마이그레이션을 **V1(구조) · V2(값) 두 파일**로 합쳤고(6장), 관리자(Studio)용 내부 API 6개를 더했습니다(7.2). 브랜치 `refactor/flyway-v1-v2`, 테스트 41건 통과.
> 마지막 장(12장)에 배포 전에 확인할 것과 아직 정하지 않은 것을 모았습니다.

## 0. 한눈에 보기

- 테이블은 13개입니다: `label` · `artist` · `album` · `track` · `product` · `physical_album` · `goods` · `product_option` · `stock` · `cart_item` · `purchase_order` · `order_item` · `outbox`.
- **상품은 상속 구조입니다.** `product` 가 부모이고 `physical_album`(LP · CD · 카세트)과 `goods` 가 id 를 공유하는 자식입니다.
- **판매 단위는 옵션입니다.** 가격 · 재고 · 장바구니 · 주문이 모두 `product_option` 을 가리킵니다. 주문은 항상 상품 → 옵션을 거칩니다.
- **재고는 옵션과 1:1** 인 별도 테이블입니다. 주문 경합의 행 락 대상이라 수량만 둡니다.
- **장바구니가 catalog 에 생겼습니다** (`cart_item`, `/api/cart`). member 의 위시리스트를 대체합니다.
- **장르 · 트랙 메타데이터는 enum 배열 컬럼**입니다. 값 목록은 DB CHECK 와 Java enum 양쪽에 있고 테스트가 둘을 대조합니다.
- **`search_doc` · `band_member` · `lp` 테이블은 삭제했습니다.** 검색은 아티스트 · 앨범 · 트랙을 직접 조회합니다.
- **주문 상태에 `REFUNDED` 가 생겼습니다.** 환불 여부는 Payment 가 판정하고(`payment.cancelled` 의 `refunded`) catalog 는 그 값만 봅니다.
- 쿼리는 QueryDSL(컴파일 검증)과 JPQL 로 옮겼습니다. JDBC · native query 는 운영 코드에 남아 있지 않습니다.
- 마이그레이션 파일은 `V1__catalog_schema.sql`(구조) · `V2__catalog_values.sql`(값 목록 CHECK) 두 개입니다. **기존 DB 는 `catalog` 스키마를 비우고 다시 올리며, 데이터는 옮기지 않습니다.**

## 1. 배경과 범위

- indie-msa 전 서비스가 테이블을 개편하는 중이고, member 다음이 catalog 입니다. 기준은 member 의 `docs/v2_member_service_re.md` 8장(다른 서비스가 알아야 할 것)입니다.
- 포함: catalog 스키마 · 코드 전체, catalog 가 내고 받는 Kafka 이벤트, Gateway · member · payment · concert 와의 접점 확인
- 제외
  - 수집기(크롤링 · LLM 전처리) 구현 — 적재 API 의 요청 모양까지만 정했습니다 (7.3)
  - 프론트 구현 — 바뀐 API 만 정리했습니다 (7장)
  - 유사도(docs/33) — 트랙 메타데이터 컬럼만 준비했고 계산 로직은 넣지 않았습니다
  - 피처링 — 고려하지 않기로 했습니다. 아티스트 ↔ 트랙은 앨범을 거쳐서만 이어집니다

## 2. 테이블 정의 (확정)

id 는 모두 `BIGINT`(BIGSERIAL)입니다. 매핑만 하는 테이블(`cart_item` · `order_item`)은 참조 키 복합 PK 입니다.

### 2.1 카탈로그

| 테이블 | 컬럼 |
|---|---|
| `label` | `id` · `name`(UNIQUE) |
| `artist` | `id` · `label_id`(FK, NULL 가능) · `name` · `genres`(배열) · `debut_year` · `profile_url` · `source` · `external_id` · `source_hash` · `updated_at` |
| `album` | `id` · `artist_id`(FK) · `title` · `type` · `genres`(배열) · `released_at` · `thumbnail_url` · `source` · `external_id` · `source_hash` · `updated_at` |
| `track` | `id` · `album_id`(FK) · `track_no` · `title` · `genres`(배열) · `tempo` · `moods`(배열) · `emotions`(배열) · `styles`(배열) |

- 여러 아티스트가 한 소속사에 속합니다 (`artist.label_id`).
- 앨범이 아티스트와 트랙 사이를 잇습니다. 별도 매핑 테이블은 없습니다.
- 배열 컬럼은 `VARCHAR(32)[] NOT NULL DEFAULT '{}'` 입니다. 값이 없으면 NULL 이 아니라 빈 배열입니다.
- `tempo` 는 구간 enum 이고 모르면 NULL 입니다.

### 2.2 상품

| 테이블 | 컬럼 |
|---|---|
| `product` | `id` · `product_type` · `name` · `thumbnail_url` · `purchase_url` · `source` · `external_id` · `source_hash` · `created_at` · `updated_at` |
| `physical_album` | `id`(PK = FK → product) · `album_id`(FK) · `format` · `released_at` |
| `goods` | `id`(PK = FK → product) · `artist_id`(FK) · `category` |
| `product_option` | `id` · `product_id`(FK) · `name` · `vinyl_color` · `size` · `price` · `created_at` |
| `stock` | `option_id`(PK = FK → product_option) · `qty` · `updated_at` |

- 구매 흐름은 두 갈래입니다.
  - 아티스트 → 굿즈 → 상품 옵션 → 주문 항목 → 주문
  - 아티스트 → 앨범 → 피지컬 앨범 → 상품 옵션 → 주문 항목 → 주문
- 한 앨범에 같은 매체가 여러 개 있을 수 있습니다. 초판 · 재판 · N차는 상품 이름으로 구분합니다 (크롤링 원본 그대로).
- 가격은 옵션에만 있습니다. 목록의 `minPrice` 는 옵션 중 최저가입니다.
- 옵션은 지우지 않습니다. 주문 항목과 member 의 보유 음반이 옵션 id 를 가리키기 때문입니다.

### 2.3 장바구니 · 주문

| 테이블 | 컬럼 |
|---|---|
| `cart_item` | `user_id` · `option_id`(FK) · `qty` · `created_at` — PK(`user_id`, `option_id`) |
| `purchase_order` | `id` · `user_id` · `status` · `total_price` · `pg_order_id` · `recipient_name` · `recipient_phone` · `zonecode` · `address` · `address_detail` · `created_at` |
| `order_item` | `order_id`(FK) · `option_id`(FK) · `qty` · `price` — PK(`order_id`, `option_id`) |
| `outbox` | `id` · `event_id`(UNIQUE) · `event_type` · `payload`(JSONB) · `created_at` · `published_at` |

- `user_id` 는 member 의 회원을 가리키지만 스키마 간 FK 는 없습니다.
- `order_item.price` 는 주문 시점 가격입니다. 옵션 가격이 바뀌어도 주문 금액은 그대로입니다.
- 배송지는 주문 시점 복사본입니다. member 에서 배송지를 고치거나 지워도 주문에는 영향이 없습니다.
- `order_item` 에 별도 id 를 두지 않았습니다. 조회는 항상 `order_id` 로 하고 그건 복합 PK 의 앞 컬럼이라 인덱스를 그대로 탑니다.

## 3. 키 · 인덱스 (확정)

| 대상 | 정의 | 용도 |
|---|---|---|
| `artist` · `album` · `product` | UNIQUE(`source`, `external_id`) | 수집 멱등 upsert 기준 |
| `track` | UNIQUE(`album_id`, `track_no`) | 수집 갱신 기준 (트랙 id 유지) |
| `product_option` | UNIQUE(`product_id`, `name`) | 수집 갱신 기준 · 상품의 옵션 조회 |
| `artist` | `idx_artist_name(name, id)` | 이름순 목록 |
| `artist` | `idx_artist_genres` GIN(`genres`) | 장르 필터 (`genres @> …`) |
| `artist` · `album` · `track` | `idx_*_trgm` GIN(`lower(이름) public.gin_trgm_ops`) | 부분 일치 검색 |
| `album` | `idx_album_artist(artist_id, released_at DESC, id DESC)` | 아티스트 상세 |
| `album` | `idx_album_released(released_at DESC NULLS LAST, id DESC)` | 신보순 목록 |
| `physical_album` | `idx_physical_album_album(album_id)` | 앨범 상세 |
| `physical_album` | `idx_physical_album_released(released_at DESC NULLS LAST, id DESC)` | 발매 목록 |
| `goods` | `idx_goods_artist(artist_id, id DESC)` | 아티스트의 굿즈 |
| `purchase_order` | `idx_order_user(user_id, id DESC)` | 내 주문 목록 (커서) |
| `purchase_order` | `idx_order_pending(created_at) WHERE status = 'PENDING'` | 만료 스캔 |
| `outbox` | `idx_outbox_unpublished(id) WHERE published_at IS NULL` | 릴레이 |

CHECK 제약: 모든 enum 컬럼의 값 목록, `price >= 0`, `stock.qty >= 0`, `cart_item.qty >= 1`, `order_item.qty >= 1`, 그리고 `product_option` 의 `vinyl_color IS NULL OR size IS NULL`(둘을 동시에 쓸 수 없음).

**DB 가 막지 못하는 규칙**은 4.2에 있습니다. 서비스 계층이 막습니다.

## 4. enum 과 규칙

### 4.1 값 목록

| enum | 값 | 쓰는 곳 |
|---|---|---|
| `Genre` | INDIE_ROCK · ALTERNATIVE · GARAGE · PUNK · HARDCORE · EMO · METAL · POST_ROCK · SHOEGAZE · PSYCHEDELIC · INDIE_POP · DREAM_POP · SYNTH_POP · CITY_POP · FOLK · ELECTRONIC · RNB_SOUL · HIPHOP · JAZZ · BLUES · FUNK · SKA_REGGAE · ETC | `artist` · `album` · `track` 의 `genres` |
| `Tempo` | SLOW · MID · FAST · VERY_FAST | `track.tempo` |
| `Mood` | DREAMY · DARK · BRIGHT · WARM · COLD · CALM · ENERGETIC · INTENSE · HEAVY · BOUNCY · GROOVY · URBAN | `track.moods` |
| `Emotion` | JOY · EXCITEMENT · LOVE · LONGING · SADNESS · LONELINESS · ANXIETY · ANGER · EMPTINESS · COMFORT · HOPE · FREEDOM | `track.emotions` |
| `Style` | LOFI · ACOUSTIC · GUITAR_DRIVEN · DISTORTED · SYNTH_DRIVEN · PIANO_DRIVEN · ELECTRONIC_BEAT · ORCHESTRAL · MINIMAL · VOCAL_FOCUSED · INSTRUMENTAL · LIVE_FEEL · RETRO · EXPERIMENTAL | `track.styles` |
| `AlbumEntity.Type` | ALBUM · EP · SINGLE | `album.type` |
| `ProductEntity.Type` | PHYSICAL_ALBUM · GOODS | `product.product_type` (상속 구분자) |
| `PhysicalFormat` | LP · CD · CASSETTE | `physical_album.format` |
| `GoodsCategory` | TSHIRT · OUTER · KEYRING · PICK · MUFFLER · SCARF · BAG · HAT · ETC | `goods.category` |
| `VinylColor` | BLACK · COLOR | `product_option.vinyl_color` |
| `OptionSize` | XS · S · M · L · XL · XXL · FREE | `product_option.size` |
| `PurchaseOrderEntity.Status` | PENDING · CONFIRMED · CANCELLED · REFUNDED | `purchase_order.status` |

값을 추가할 때는 **Java enum 과 마이그레이션의 CHECK 를 함께** 고칩니다. 한쪽만 고치면 `CatalogSchemaTest.checkConstraintValuesMatchJavaEnums` 가 깨집니다.

### 4.2 DB 가 못 막는 규칙 — 서비스가 막습니다

| 규칙 | 막는 곳 |
|---|---|
| LP 옵션은 `vinyl_color` 필수 · `size` 금지 | `OptionRule` (적재) |
| CD · 카세트 옵션은 둘 다 금지 | `OptionRule` |
| 굿즈 옵션은 `size` 필수(사이즈가 없으면 FREE) · `vinyl_color` 금지 | `OptionRule` |
| 한 상품 안에서 옵션 이름 중복 금지 (공백을 떼고 비교) | `ProductService.upsertOptions` |
| 한 앨범 안에서 `track_no` 중복 금지 | `IngestService.syncTracks` |
| 새 옵션에는 재고 행이 반드시 생김 (1:1) | `ProductService.upsertOptions` |
| 주문 금액은 옵션 가격으로만 계산 (요청에 금액 없음) | `OrderService.reserve` |
| 한 주문에 같은 옵션 두 번 금지 · 가짓수 · 수량 상한 | `OrderService.validate` |
| 장바구니 가짓수 · 수량 상한 (주문과 같은 값) | `CartService.put` |
| 재고는 음수가 될 수 없음 | `qty >= n` 조건부 UPDATE + CHECK |
| 주문 상태 전이는 한 번만 | 조건부 UPDATE (5.3) |

규칙을 어긴 적재 항목은 그 항목만 통째로 거절됩니다(같은 요청의 다른 항목은 적재됨).

## 5. 로직과 흐름

### 5.1 코드 구조

```
domain/
  artist/   album/   product/   cart/(신규)   order/   search/   ingest/
global/
  config/QuerydslConfig   outbox/   common/util/LikePattern   …
```

- 엔티티: `ProductEntity`(추상 · JOINED 상속) ← `PhysicalAlbumEntity` · `GoodsEntity`. `ProductOptionEntity` · `StockEntity`(키 = `optionId`) · `CartItemEntity`(복합 키).
- enum 배열은 `@Enumerated(STRING) @JdbcTypeCode(SqlTypes.ARRAY) List<Enum>` 으로 매핑합니다.
- 저장소: 서비스는 포트 인터페이스만 봅니다. 조건이 있는 쿼리는 `XRepositoryAdapter`(QueryDSL), 단순 조회는 Spring Data 파생 메서드입니다 (member 와 같은 방식).

### 5.2 쿼리 방식

| 쿼리 | 방식 |
|---|---|
| 목록 필터 · 정렬 · 페이지 | QueryDSL — 컬럼 · 타입이 컴파일에서 검증됨 |
| 재고 차감 · 복구 · 주문 상태 전이 | QueryDSL update (조건부) |
| 장르 필터 | QueryDSL + `array_contains` 템플릿 → `genres @> …` (GIN) |
| 검색 유사도 | QueryDSL + `function('public.similarity', …)` 템플릿 |
| 장바구니 담기 | HQL `insert … on conflict (userId, optionId) do update` |
| outbox 릴레이 잠금 | QueryDSL + `PESSIMISTIC_WRITE` + lock timeout `-2`(SKIP LOCKED) |

템플릿 문자열 두 곳(`array_contains` · `similarity`)과 HQL 한 곳은 컴파일로 검증되지 않습니다. 대신 실제 PostgreSQL 로 도는 테스트가 확인합니다 (10장).

### 5.3 주문 Saga 와 환불

```
① 재고 차감 + 주문 PENDING            (로컬 트랜잭션 · 옵션 id 순으로 잠가 교착 방지)
② Payment prepare → pgOrderId         (동기 · 실패하면 ① 을 즉시 되돌리고 503)
③ 프론트가 토스 결제창 → Payment confirm
④ payment.completed                  → CONFIRMED + order.confirmed + 산 옵션을 장바구니에서 제거
   payment.failed                     → CANCELLED + 재고 복구
   payment.cancelled refunded=false   → CANCELLED + 재고 복구
   payment.cancelled refunded=true    → REFUNDED  + 재고 복구 + order.refunded
⑤ 15분 안에 결제하지 않으면 만료       (OrderExpiryJob)
```

Payment 는 바꾸지 않았습니다. Payment 가 내는 `payment.cancelled` 의 `refunded`(승인된 결제를 토스로 환불했을 때만 true · 전액 환불만 있음)로 환불 여부를 판단합니다.

상태 전이는 모두 조건부 UPDATE 입니다.

| 메서드 | 전이 |
|---|---|
| `confirm` | PENDING → CONFIRMED |
| `cancel` | PENDING → CANCELLED |
| `refund` | PENDING · CONFIRMED → REFUNDED (재고 복구함) |
| `refundCancelled` | CANCELLED → REFUNDED (재고는 이미 복구했으므로 복구하지 않음) |

`refundCancelled` 는 "결제는 승인됐는데 `payment.completed` 를 받기 전에 사용자가 취소한" 경우를 위한 것입니다. 그때 주문은 일단 CANCELLED 로 남고, 뒤따라오는 `payment.cancelled(refunded=true)` 가 REFUNDED 로 바로잡습니다. 재고가 두 번 복구되지 않는 것을 테스트로 확인했습니다.

사용자 취소(`POST /api/catalog/orders/{id}/cancel`)는 Payment 를 먼저 취소한 뒤 재고를 복구합니다. 순서가 반대면 "재고는 돌려놨는데 결제는 승인됨"이 생깁니다.

### 5.4 장바구니

- 담기는 **수량을 더하지 않고 덮어씁니다** (`PUT`, 멱등). 같은 옵션을 다시 담으면 한 줄에 수량만 바뀝니다.
- 재고는 읽기만 합니다. 차감은 주문 생성 때만 합니다.
- 응답에는 지금 가격 · 재고 · 품절 여부가 함께 나갑니다. 금액은 주문할 때 다시 계산합니다.
- 주문이 확정되면 그 옵션들이 장바구니에서 빠집니다.
- 회원이 탈퇴하면(`user.withdrawn`) 장바구니를 지우고 결제 대기 주문을 취소합니다. 확정 · 환불된 주문과 배송 정보는 남깁니다.

### 5.5 검색

`search_doc` 없이 아티스트 이름 · 앨범 제목 · 트랙 제목을 각각 `lower(이름) LIKE '%q%'` 로 조회하고 `similarity` 점수로 합쳐 정렬합니다. 트랙 결과에는 `albumId` 가 붙습니다. `%` · `_` 는 와일드카드가 아니라 글자 그대로 찾습니다.

### 5.6 수집 적재

- (`source`, `external_id`) 기준 멱등 upsert 입니다. `source_hash` 가 저장된 값과 같으면 그 항목은 건너뜁니다.
- 항목마다 트랜잭션입니다. 한 건이 실패해도 나머지는 적재되고, 결과에 `FAILED` 와 이유가 나옵니다.
- **트랙은 `track_no` 기준으로 맞춥니다.** 같은 번호는 행을 유지한 채 값만 바꾸고, 요청에 없는 번호는 지웁니다. 통째로 지우고 다시 넣지 않는 이유는 member 의 행동 메트릭이 트랙 id 를 들고 있기 때문입니다.
- 옵션은 (상품, 이름)이 같으면 갱신, 새 이름이면 새 옵션입니다. `qty` 는 입고 수량(절대값)이고 null 이면 수량을 건드리지 않습니다.
- 이벤트는 실제로 값이 바뀐 경우에만 나갑니다 (8장).

## 6. 마이그레이션 (V1 구조 · V2 값)

파일은 두 개만 둡니다 (2026-11-13 결정 · 모든 서비스 공통). 예전 V1 ~ V5 를 합친 것이고, 합치기 전후의 컬럼 · 제약 · 인덱스가 같은지 대조했습니다.

| 파일 | 들어 있는 것 |
|---|---|
| `V1__catalog_schema.sql` | 테이블 13개, PK · FK · UNIQUE, 인덱스(트라이그램 · GIN 포함), 구조 CHECK(가격 · 수량 ≥ 0, `ck_product_option_kind`) |
| `V2__catalog_values.sql` | 값 목록 CHECK 14개 — 장르(`ck_artist_genres` · `ck_album_genres` · `ck_track_genres`), `ck_album_type`, `ck_track_tempo` · `_moods` · `_emotions` · `_styles`, `ck_product_type`, `ck_physical_album_format`, `ck_goods_category`, `ck_product_option_vinyl_color` · `_size`, `ck_order_status` |

- 값 목록을 넣거나 빼면 V2 와 Java enum 을 함께 고칩니다. `CatalogSchemaTest` 가 둘을 대조합니다.
- **장르 목록은 concert 도 씁니다** (2026-11-13 결정). `Genre` 를 바꾸면 concert 의 `V2__concert_values.sql` 과 concert 의 `Genre` 도 함께 고칩니다.
- 🔴 **적용된 파일을 고치면 Flyway 체크섬 검증에 걸려 서비스가 뜨지 않습니다.** 실데이터가 없는 지금은 파일을 고친 뒤 `catalog` 스키마를 비우고 다시 올립니다. 실데이터가 생긴 뒤에는 V3 이후 파일을 이어 붙여야 합니다 (12장).
- 기존 DB 에 올릴 때: 서비스를 내린 뒤 `DROP SCHEMA catalog CASCADE` → `terraform/scripts/db-init.sh` → 서비스 기동. 선행 조건은 `pg_trgm` 확장이 `public` 스키마에 있는 것입니다 (운영 `db-init.sql` · 로컬 `init-schema.sql` 에 이미 있음).
- 로컬 프로파일도 Flyway + `validate` 로 돕니다 (`ddl-auto: update` 를 뺐습니다).

## 7. API 변경표

### 7.1 공개 API

| 메서드 · 경로 | 변경 | 내용 |
|---|---|---|
| `GET /api/catalog/artists` | 변경 | `genre` 파라미터가 enum 값(`INDIE_ROCK` …). 응답 `genre`(문자열) → `genres`(배열) |
| `GET /api/catalog/artists/{id}` | 변경 | `members` 삭제 · `genre` → `genres` · `lps` → `physicalAlbums` · `goods` 항목 모양 변경 |
| `GET /api/catalog/albums` | 변경 | 항목에 `genres` 추가 |
| `GET /api/catalog/albums/{id}` | 변경 | `cd` · `lps` 삭제 → `physicalAlbums`. 트랙에 `genres` · `tempo` · `moods` · `emotions` · `styles` |
| `GET /api/catalog/lps` | **삭제** | → `GET /api/catalog/physical-albums` |
| `GET /api/catalog/physical-albums` | **신규** | `?format=LP\|CD\|CASSETTE&from=&to=&page=&size=` — 발매일순 |
| `GET /api/catalog/goods` | 변경 | `category` 필터 추가 (`?artistId=&category=`). 항목에 `category` · `minPrice` (상품 단위 `price` · `qty` 없음) |
| `GET /api/catalog/products/{id}` | 변경 | 상품 + `options[]`(옵션별 `price` · `qty` · `soldOut`). 상품 단위 `price` · `qty` 는 없음 |
| `GET /api/catalog/products?ids=` | 변경 | 위와 같은 모양의 목록 |
| `GET /api/catalog/search` | 유지 | 경로 · 응답 모양 그대로 (내부 구현만 바뀜) |
| `GET /api/cart` | **신규** | 내 장바구니 (`items[]` · `totalPrice`) |
| `PUT /api/cart/items/{optionId}` | **신규** | 본문 `{ "qty": n }` — 담기 · 수량 변경 (최종 수량) |
| `DELETE /api/cart/items/{optionId}` | **신규** | 한 줄 빼기 (없어도 성공) |
| `DELETE /api/cart` | **신규** | 비우기 |
| `POST /api/catalog/orders` | 변경 | `items[].productId` → **`items[].optionId`** |
| `GET /api/catalog/orders` · `/{id}` | 변경 | `status` 에 `REFUNDED` 추가. 항목이 `optionId` · `productId` · `productType` · `productName` · `optionName` · `qty` · `price` |
| `POST /api/catalog/orders/{id}/cancel` | 변경 | 결제된 주문을 취소하면 `CANCELLED` 가 아니라 `REFUNDED` |

enum 파라미터에 목록 밖 값을 주면 400 입니다. 장바구니는 조회까지 모두 로그인이 필요합니다.

### 7.2 내부 API

| 경로 | 변경 |
|---|---|
| `GET /internal/catalog/products` (`InternalProductController`) | **삭제** — 챗봇은 공개 `GET /api/catalog/products` 를 씁니다 (docs/32) |
| `POST /internal/catalog/artists` · `/albums` · `/ingest/diff` | 경로 유지 · 본문 변경 (7.3) |
| `POST /internal/admin/catalog/products` | **신규(11/13)** 관리자 상품 등록 |
| `PATCH /internal/admin/catalog/products/{id}` | **신규(11/13)** 상품 수정 (보낸 값만) |
| `PUT /internal/admin/catalog/products/{id}/options` | **신규(11/13)** 옵션 추가 · 수정 |
| `POST /internal/admin/catalog/options/{optionId}/stock` | **신규(11/13)** 재고 조정 |
| `GET /internal/admin/catalog/users/{userId}/orders` | **신규(11/13)** 회원 주문 목록 (CS) |
| `POST /internal/admin/catalog/orders/{id}/cancel` | **신규(11/13)** 주문 취소 대행 (CS) |

**관리자(Studio) API** — Studio 가 Gateway 를 거치지 않고 직접 부릅니다 (Studio 문서 6.5 · 6.6). 본문은 camelCase 입니다.

| API | 요청 | 응답 · 규칙 |
|---|---|---|
| 상품 등록 | `{ productType, name, thumbnailUrl?, purchaseUrl?, options: [{ name, vinylColor?, size?, price, qty? }] }` + 굿즈는 `artistId` · `category`, 피지컬 앨범은 `albumId` · `format` · `releasedAt?` | 상품 상세(C8 과 같은 모양). `source` 는 `studio`. 옵션 규칙은 수집과 같습니다(4.2). 굿즈는 `goods.released`, 피지컬 앨범은 발매일이 최근이거나 없을 때 `physical_album.released` 를 냅니다 |
| 상품 수정 | `{ name?, thumbnailUrl?, purchaseUrl?, category?(굿즈), releasedAt?(피지컬) }` | 보낸 값만 바꿉니다. 매체 · 연결된 앨범 · 아티스트는 바꾸지 않습니다 |
| 옵션 | `{ options: [{ id?, name, vinylColor?, size?, price }] }` | `id` 가 있으면 그 옵션을 고치고 이름도 바꿉니다. 없으면 이름으로 찾아 고치거나 새로 만듭니다(재고 0). 요청에 없는 옵션은 그대로 둡니다 |
| 재고 조정 | `{ delta, reason }` | `{ optionId, qty, updatedAt }`. 입고 +, 회수 · 파손 −. 주문 차감과 같은 조건부 UPDATE 라 0 아래로 내려가지 않습니다 (모자라면 409 `OUT_OF_STOCK`) |
| 회원 주문 목록 | `?cursor=&size=` | 내 주문 목록(C10)과 같은 모양 |
| 주문 취소 대행 | `{ reason }` (Studio 작업 기록용 · catalog 는 읽지 않음) | 주문 상세. 회원 본인 취소(C12)와 같은 흐름이고 Payment 에 남는 사유만 `ADMIN_CANCEL` 입니다 |

- **내부 토큰 발급자는 경로로 나눕니다.** `/internal/admin/**` 는 `app.internal.admin-issuers`(`studio`)만, 수집 3경로는 Gateway 수집 토큰만 받습니다. 관리자 토큰으로 수집 API 를 부를 수 없습니다. 쓰이지 않던 `allowed-issuers: [assistant]` 는 지웠습니다.
- 🔴 수집으로 들어온 상품을 관리자가 고치면, 수집기가 그 상품의 원본이 바뀐 것을 다음에 가져올 때 다시 덮어씁니다.
- 🔴 운영에서는 catalog 의 NetworkPolicy 가 Studio(`app: studio`)를 아직 허용하지 않습니다. Studio 매니페스트를 만들 때 함께 엽니다 (KT 클라우드 준비 뒤).

### 7.3 수집 적재 본문 (snake_case)

수집기 담당자에게 전달할 계약입니다.

```jsonc
// POST /ingest/catalog/artists  (Gateway → /internal/catalog/artists)
{ "items": [{
  "source": "…", "external_id": "…", "source_hash": "…",
  "name": "…", "genres": ["SHOEGAZE", "EMO"], "label": "소속사 이름", "debut_year": 2018, "profile_url": "…",
  "goods": [{                                   // null 이면 굿즈는 건드리지 않음
    "external_id": "…", "source_hash": "…", "name": "로고 티셔츠 (블랙)", "category": "TSHIRT",
    "thumbnail_url": "…", "purchase_url": "…",
    "options": [{ "name": "M", "size": "M", "price": 35000, "qty": 3 }]
  }]
}]}

// POST /ingest/catalog/albums  (Gateway → /internal/catalog/albums)
{ "items": [{
  "source": "…", "external_id": "…", "source_hash": "…",
  "artist_id": 1, "title": "…", "type": "ALBUM", "genres": ["SHOEGAZE"], "released_at": "2026-11-01", "thumbnail_url": "…",
  "tracks": [{ "track_no": 1, "title": "…", "genres": [], "tempo": "MID",
               "moods": ["DREAMY"], "emotions": ["LONGING"], "styles": ["GUITAR_DRIVEN"] }],
  "physical_albums": [{
    "external_id": "…", "source_hash": "…", "format": "LP", "name": "… LP (초판)", "released_at": "2026-11-01",
    "thumbnail_url": "…", "purchase_url": "…",
    "options": [{ "name": "블랙", "vinyl_color": "BLACK", "price": 42000, "qty": 5 }]
  }]
}]}

// POST /ingest/catalog/diff — type: ARTIST | ALBUM | PHYSICAL_ALBUM | GOODS
```

- 없어진 필드: 아티스트의 `members`, 앨범의 `cd` · `lps` · `sale`, 굿즈의 상품 단위 `price` · `qty`.
- 굿즈의 색상은 상품 이름에 넣고(색상마다 상품 1개) 옵션은 사이즈만 둡니다.
- **enum 필드에 목록 밖 값이 하나라도 있으면 그 요청 전체가 400 입니다** (JSON 파싱 단계에서 실패). 전처리에서 목록 밖 값은 `ETC` 로 바꾸거나 빼서 보내야 합니다.

## 8. Kafka 이벤트

봉투는 그대로입니다: `{ eventId, type, version, occurredAt, payload }` · outbox 로 발행.

### 8.1 발행

| 토픽 | 변경 | 언제 | payload |
|---|---|---|---|
| `artist.updated` | 변경 | 아티스트 생성 · 변경 | `artistId` · `name` · `genre`(대표 1개 = `genres` 의 첫 값) · `genres` · `profileUrl` |
| `album.released` | 변경 | 앨범 신규 등록 · 발매일이 최근 30일 안일 때만 | `albumId` · `artistId` · `artistName` · `title` · `type` · `genres` · `releasedAt` · `thumbnailUrl` |
| `album.updated` | 변경 | 앨범 · 트랙 · 피지컬 앨범 변경 | 위 + `changedFields` |
| `physical_album.released` | **신규** | 피지컬 앨범 신규 등록 · 기존 상품에 새 옵션 추가 | `physicalAlbumId` · `albumId` · `albumTitle` · `artistId` · `artistName` · `format` · `name` · `thumbnailUrl` · `options[optionId, name, vinylColor, price]` |
| `goods.released` | **신규** | 굿즈 신규 등록 | `goodsId` · `artistId` · `artistName` · `name` · `category` · `price`(최저가) · `thumbnailUrl` |
| `order.confirmed` | **신규** | 결제 완료로 주문 확정 | `userId` · `orderId` · `totalPrice` · `items[optionId, physicalAlbumId 또는 goodsId, name, qty]` |
| `order.refunded` | **신규** | 결제된 주문 환불 | `userId` · `orderId` · `itemName` · `itemCount` · `refundAmount` |
| `stock.restocked` | **삭제** | — | 받는 서비스가 없습니다 |

필드 이름은 member v2 의 `CatalogEventConsumer` 가 읽는 이름에 맞췄습니다 (member 코드와 대조 확인).

- 새 피지컬 앨범은 발매일이 최근이거나 발매일을 모를 때만 알립니다. 옛 음반을 처음 수집할 때 알림이 쏟아지지 않게 하기 위해서입니다.
- 기존 피지컬 앨범에 옵션이 새로 생기면 그 옵션만 실어 보냅니다 (컬러반 추가 등).
- 주문 차감 · 보상 복구로 재고가 바뀌는 것은 이벤트가 아닙니다.

### 8.2 구독

| 토픽 | 변경 | 처리 |
|---|---|---|
| `payment.completed` · `payment.failed` · `payment.cancelled` | 변경 | 5.3 — `payment.cancelled` 의 `refunded` 를 봅니다 |
| `user.withdrawn` (member) | **신규** | 장바구니 삭제 + 결제 대기 주문 취소 (`UserEventConsumer`) |

## 9. 다른 서비스 · 저장소

| 대상 | 수정 | 내용 |
|---|---|---|
| **member** | 없음 | 이미 v2 입니다. catalog 가 member 의 컨슈머 필드 이름에 맞췄습니다. 작업 트리 변경 0건 확인 |
| **Gateway** | 없음 | `/api/cart` · `/api/cart/**` → catalog 라우트와 로그인 필수 규칙이 이미 있습니다 (member v2 때 추가). `/api/catalog/**` · 검색 폴백 · 수집 3경로(`/ingest/catalog/{artists,albums,diff}`)는 경로가 그대로라 손대지 않았습니다 |
| **payment** | 없음 | 바꾸지 않는 것이 조건이었습니다. `prepare` 요청에서 쓰이지 않던 `albumIds` 만 catalog 쪽에서 보내지 않습니다 |
| **concert** | 없음 | `artist.updated` 의 `artistId` · `name` · `genre` 를 그대로 읽습니다. 12장 ④ 참고 |
| **manifests** | **1개 파일** | `platform/kafka/topics-job.yaml` — 운영은 토픽 자동 생성이 꺼져 있어 목록에 없으면 발행이 실패합니다. `physical_album.released` · `goods.released` · `order.confirmed` · `order.refunded` · `user.withdrawn` 을 추가하고(구독 토픽은 DLT 도) `stock.restocked` 를 뺐습니다. 커밋 전입니다 |

수정하지 않았지만 낡은 것 (손대지 않고 알려만 둡니다):

- `backend/local/commerce-demo.sh` — 옛 흐름(위시리스트 · `stock.restocked` · `lps` · `productId` 주문) 기준이라 지금은 통과하지 않습니다. 4개 서비스와 토스 결제창이 떠 있어야 검증할 수 있어 고치지 않았습니다.
- `backend/README.md` 의 catalog 테스트 수 · 설명, `docs/` 의 API 명세(15 등), `demo_ui/index.html` 의 개발자 메모, `scripts/render_*.py` 다이어그램의 `stock.restocked`.
- Gateway `IngestRoute` 의 `TODO: /ingest/catalog/goods` — 굿즈는 아티스트 적재 본문 안에 들어가므로 이 경로는 필요 없어졌습니다.
- ~~catalog `application.yml` 의 `app.internal.allowed-issuers: [assistant]`~~ → 11/13 오후에 지웠습니다 (`admin-issuers: [studio]` 로 교체 · 7.2). `manifests` 의 catalog NetworkPolicy 에 남아 있던 `app: assistant` 허용도 함께 지웁니다.

## 10. 테스트

`./gradlew test` — 41건 통과 · 건너뜀 0 (2026-11-13). **Docker 가 꺼져 있으면 DB 테스트 20건이 실패가 아니라 건너뜀으로 끝나므로** 결과에서 건너뜀 수를 확인해야 합니다.

| 테스트 | 건수 | 확인하는 것 |
|---|---|---|
| `CatalogSchemaTest` | 11 | Flyway 이력이 V1 · V2 둘인지 · `ddl-auto: validate` 로 엔티티 ↔ 스키마 일치 · CHECK 값 목록 = Java enum · enum 배열 저장/조회 · 장르 필터 · 검색(트라이그램 · `%` 는 글자 그대로) · 재고 5개에 동시 주문 20건 → 5건만 성공 · 장바구니 upsert · outbox SKIP LOCKED · 주문 상태 전이 |
| `CatalogFlowTest` | 11 | 관리자 상품 등록 · 수정 · 옵션(이름 변경) · 재고 조정과 주문 취소 대행(11/13). 수집 → 조회 → 장바구니 → 주문 · 환불. 이벤트 payload 필드, 값이 안 바뀌면 이벤트 없음, 트랙 id 유지, 옵션 규칙 위반 거절, 재고 부족 · 결제 준비 실패 시 재고 복구, 취소 뒤 환불 정정(재고 이중 복구 없음), 만료 · 탈퇴 |
| `CatalogApiTest` | 1 | 앱 전체 기동 + HTTP — enum 파라미터 · JSON 필드 · 장바구니 · 주문 요청 모양 · 캐시(Redis) 없이 조회 · Payment 불통 시 503 과 재고 복구 |
| `OptionRuleTest` | 3 | LP · CD/카세트 · 굿즈 옵션 규칙 |
| `SearchServiceTest` · `InternalTokenFilterTest` | 1 · 14 | 검색 / 내부 토큰 — 관리자(`studio`) 토큰은 `/internal/admin/**` 만 열고 수집 API 는 못 엽니다(11/13) |

테스트가 잡아서 고친 버그: 장바구니 수량을 바꾼 직후 응답에 이전 수량이 내려가던 문제 (upsert 쿼리가 영속성 컨텍스트를 건너뛰어 옛 엔티티가 읽힘 → 해당 행을 떼어내도록 수정).

검증하지 못한 것: 실제 Kafka 를 거친 발행 · 구독(컨슈머는 서비스 메서드 호출까지만 확인), 실제 Payment · 토스 연동, 운영 배포. 관리자 API 는 서비스 메서드까지만 확인했습니다 (Studio 를 띄워 HTTP 로 부르는 것은 하지 않았습니다).

## 11. 삭제한 것

- 테이블: `search_doc` · `band_member` · `lp` (+ `product` · `goods` · `stock` 은 새 구조로 다시 만듦)
- 코드: `SearchDocEntity` · `SearchIndex` · `SearchIndexAdapter` · `SearchDocJpaRepository`, `BandMemberEntity` · `BandMember(Jpa)Repository` · `MemberItem` · `MemberUpsert`, `LpEntity` · `Lp(Jpa)Repository` · `LpItem` · `LpUpsert`, `SaleInfo` · `SaleUpsert`, `ProductView`, `InternalProductController`
- 이벤트: `stock.restocked`
- JDBC · native query 로 쓰던 저장소 구현 (QueryDSL · JPQL 로 교체)
- 테스트: `SearchIndexAdapterTest` · `StockEntityTest` (대상이 사라짐)

## 12. 배포 전에 확인할 것 · 아직 정하지 않은 것

**배포 전 확인**

1. **스키마를 비우고 다시 올리므로 catalog 의 데이터가 모두 지워집니다.** 수집을 다시 돌려야 하고, id 가 1부터 다시 매겨집니다. member 의 팔로우 · 보유 음반 · 행동 메트릭과 concert 가 복제해 둔 아티스트 id 는 옛 id 를 가리키게 됩니다. 다른 서비스의 데이터도 함께 비울지 정해야 합니다.
2. **로컬 DB 는 `catalog` 스키마를 한 번 비우고** 띄워야 합니다. 로컬도 Flyway 로 돌기 때문에 예전에 `ddl-auto` 로 만든 테이블이 남아 있으면 V1 이 실패합니다.
3. **`manifests` 의 토픽 목록 변경을 catalog 보다 먼저(또는 함께) 배포**해야 합니다. 토픽이 없으면 outbox 에 이벤트가 쌓이기만 합니다.
4. `backend/local/commerce-demo.sh` 는 새 흐름으로 다시 써야 합니다.

**정하지 않은 것 (제가 기본값으로 둔 것)**

- ① 발매 알림 기간 30일 (`app.event.release-window-days`).
- ② `goods.released` 는 굿즈가 새로 등록되면 항상 나갑니다 (굿즈에는 발매일이 없음). 처음 대량 수집할 때 팔로워에게 알림이 몰릴 수 있습니다.
- ③ 장바구니 상한은 주문 상한을 그대로 씁니다 (옵션 20가지 · 옵션당 10개).
- ④ ~~concert 의 장르 표기~~ → 11/13 결정: concert 의 장르도 catalog 의 `Genre` 목록을 씁니다 (6장).
- ⑤ ~~`app.internal.allowed-issuers: [assistant]` 를 비울지~~ → 11/13 지웠습니다 (7.2).
- ⑦ 마이그레이션 두 파일 규칙은 실데이터가 생기면 쓸 수 없습니다 (스키마를 비울 수 없음). 그 시점부터 V3 이후 파일을 허용할지 정해야 합니다.
- ⑥ 유사도 계산(docs/33)에 트랙 메타데이터를 어떻게 쓸지.

## 부록 A. 관계

```
label 1 ─── N artist 1 ─── N album 1 ─── N track
                 │              │
                 │ 1            │ 1
                 N              N
               goods      physical_album
                 └──────┬───────┘
                     product (부모 · id 공유)
                        │ 1
                        N
                  product_option 1 ─── 1 stock
                    │ 1         │ 1
                    N           N
                cart_item    order_item N ─── 1 purchase_order
```
