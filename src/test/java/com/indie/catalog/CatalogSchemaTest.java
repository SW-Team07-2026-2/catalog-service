package com.indie.catalog;

import com.indie.catalog.domain.album.entity.Emotion;
import com.indie.catalog.domain.album.entity.Mood;
import com.indie.catalog.domain.album.entity.Style;
import com.indie.catalog.domain.album.entity.Tempo;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.cart.entity.CartItemEntity;
import com.indie.catalog.domain.cart.repository.CartItemRepository;
import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import com.indie.catalog.domain.order.entity.PurchaseOrderEntity.Status;
import com.indie.catalog.domain.order.entity.ShippingSnapshot;
import com.indie.catalog.domain.order.repository.OrderRepository;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.entity.VinylColor;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.domain.search.dto.response.SearchHit;
import com.indie.catalog.domain.search.service.SearchService;
import com.indie.catalog.global.outbox.OutboxAppender;
import com.indie.catalog.global.outbox.OutboxEntity;
import com.indie.catalog.global.outbox.OutboxRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 스키마 · 엔티티 · 저장소 쿼리 (기동 자체가 Flyway V1 · V2 + ddl-auto: validate 검증입니다) */
class CatalogSchemaTest extends PostgresTest {

    @Autowired ArtistRepository artistRepository;
    @Autowired StockRepository stockRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired SearchService searchService;
    @Autowired OutboxAppender outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    /** 마이그레이션은 V1(구조) · V2(값) 두 파일 */
    @Test
    void migrationsAreSchemaThenValues() {
        assertThat(jdbc.queryForList("SELECT version || ' ' || description FROM flyway_schema_history "
                + "WHERE success AND version IS NOT NULL ORDER BY installed_rank", String.class))
                .containsExactly("1 catalog schema", "2 catalog values");
    }

    @Test
    void oldTablesAreGoneAndV2TablesExist() {
        assertThat(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'catalog' ORDER BY 1", String.class))
                .containsExactly("album", "artist", "cart_item", "flyway_schema_history", "goods", "label", "order_item",
                        "outbox", "physical_album", "product", "product_option", "purchase_order", "stock", "track");
    }

    /** ★ CHECK 의 허용 값 목록 = Java enum — 한쪽만 고치면 여기서 깨집니다 */
    @Test
    void checkConstraintValuesMatchJavaEnums() {
        assertCheck("ck_artist_genres", Genre.values());
        assertCheck("ck_album_genres", Genre.values());
        assertCheck("ck_track_genres", Genre.values());
        assertCheck("ck_track_tempo", Tempo.values());
        assertCheck("ck_track_moods", Mood.values());
        assertCheck("ck_track_emotions", Emotion.values());
        assertCheck("ck_track_styles", Style.values());
        assertCheck("ck_product_type", ProductEntity.Type.values());
        assertCheck("ck_physical_album_format", PhysicalFormat.values());
        assertCheck("ck_goods_category", GoodsCategory.values());
        assertCheck("ck_product_option_vinyl_color", VinylColor.values());
        assertCheck("ck_product_option_size", OptionSize.values());
        assertCheck("ck_order_status", Status.values());
    }

    private void assertCheck(String constraint, Enum<?>[] values) {
        String def = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, constraint);
        Set<String> inDb = new TreeSet<>();
        Matcher m = Pattern.compile("'([A-Z0-9_]+)'").matcher(def);
        while (m.find()) inDb.add(m.group(1));
        assertThat(inDb).as(constraint).isEqualTo(new TreeSet<>(Arrays.stream(values).map(Enum::name).toList()));
    }

    /** enum 배열 컬럼 — 저장 · 조회 · 장르 필터(genres @> …) · 이름 부분 일치(대소문자 무시 · % 는 글자 그대로) */
    @Test
    void artistGenresArrayAndFilters() {
        ArtistEntity shoegaze = artist("genre-1", "Parannoul", List.of(Genre.SHOEGAZE, Genre.EMO));
        artist("genre-2", "100% 밴드", List.of(Genre.INDIE_POP));
        em.flush();
        em.clear();

        assertThat(artistRepository.findById(shoegaze.getId()).orElseThrow().getGenres()).containsExactly(Genre.SHOEGAZE, Genre.EMO);
        assertThat(jdbc.queryForObject("SELECT array_to_string(genres, ',') FROM artist WHERE id = ?", String.class, shoegaze.getId()))
                .isEqualTo("SHOEGAZE,EMO");
        assertThat(artistRepository.search(null, Genre.EMO, 0, 10)).extracting(ArtistEntity::getName).containsExactly("Parannoul");
        assertThat(artistRepository.search("paran", null, 0, 10)).extracting(ArtistEntity::getName).containsExactly("Parannoul");
        assertThat(artistRepository.search("%", null, 0, 10)).extracting(ArtistEntity::getName).containsExactly("100% 밴드");
        assertThat(artistRepository.search("paran", Genre.JAZZ, 0, 10)).isEmpty();
    }

    /** 목록에 없는 값은 DB 가 거절합니다 */
    @Test
    void unknownGenreIsRejectedByCheck() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO artist (name, genres, source, external_id, updated_at) VALUES ('x', '{TROT}', 'manual', 'bad-1', now())"))
                .hasMessageContaining("ck_artist_genres");
    }

    /** 검색 — search_doc 없이 아티스트 · 앨범 · 트랙을 직접 조회해 유사도 순으로 합칩니다. 트랙에는 albumId 가 붙습니다 */
    @Test
    void searchAcrossArtistAlbumTrackUsesTrigramIndexes() {
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'catalog' AND indexname LIKE '%_trgm' ORDER BY 1",
                String.class)).containsExactly("idx_album_title_trgm", "idx_artist_name_trgm", "idx_track_title_trgm");
        long artistId = artist("search-1", "새소년", List.of()).getId();
        em.flush();
        long albumId = jdbc.queryForObject("INSERT INTO album (artist_id, title, type, source, external_id, updated_at) "
                + "VALUES (?, '새소년 라이브 앨범', 'ALBUM', 'manual', 'search-a1', now()) RETURNING id", Long.class, artistId);
        long trackId = jdbc.queryForObject("INSERT INTO track (album_id, track_no, title) VALUES (?, 1, '긴 꿈 (새소년 ver.)') RETURNING id",
                Long.class, albumId);

        List<SearchHit> hits = searchService.search("새소년", 20).items();

        assertThat(hits).extracting(SearchHit::type).containsExactly("ARTIST", "ALBUM", "TRACK");   // 짧을수록 유사도가 높음
        assertThat(hits.get(0).score()).isEqualTo(1.0);
        assertThat(hits.get(2).id()).isEqualTo(trackId);
        assertThat(hits.get(2).albumId()).isEqualTo(albumId);
        assertThat(hits.get(0).albumId()).isNull();
        assertThat(searchService.search("새소년", 2).items()).hasSize(2);
    }

    /** 같은 트랜잭션에 jsonb 로 저장 */
    @Test
    void outboxStoresJsonbPayloadInTransaction() {
        outbox.publish("catalog.test", Map.of("albumId", 7));
        em.flush();

        assertThat(jdbc.queryForObject("SELECT payload->>'albumId' FROM outbox WHERE event_type = 'catalog.test'",
                String.class)).isEqualTo("7");
    }

    /** ★ 릴레이 2개가 동시에 돌아도 같은 행을 집지 않습니다 — 잠긴 행은 기다리지 않고 건너뜁니다 (SKIP LOCKED) */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outboxLockSkipsRowsLockedByAnotherRelay() {
        tx.executeWithoutResult(s -> outbox.publish("catalog.lock-test", Map.of("n", 1)));
        try {
            tx.executeWithoutResult(s -> {
                List<OutboxEntity> mine = outboxRepository.lockUnpublished(100);
                assertThat(mine).extracting(OutboxEntity::getEventType).contains("catalog.lock-test");
                List<OutboxEntity> other = CompletableFuture.supplyAsync(() -> tx.execute(t -> outboxRepository.lockUnpublished(100))).join();
                assertThat(other).isEmpty();
            });
        } finally {
            jdbc.update("DELETE FROM outbox WHERE event_type = 'catalog.lock-test'");
        }
    }

    /** ★ 재고 5개에 동시 주문 20건 → 정확히 5건만 성공, 음수 재고 없음 (조건부 UPDATE) */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDecrementNeverOversells() throws Exception {
        long optionId = option("concurrency", 5);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Integer>> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) orders.add(() -> tx.execute(s -> stockRepository.decrement(optionId, 1)));
        int succeeded = 0;
        for (Future<Integer> f : pool.invokeAll(orders)) succeeded += f.get();
        pool.shutdown();

        assertThat(succeeded).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT qty FROM stock WHERE option_id = ?", Integer.class, optionId)).isZero();
        Integer restored = tx.execute(s -> stockRepository.restore(optionId, 2));
        assertThat(restored).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT qty FROM stock WHERE option_id = ?", Integer.class, optionId)).isEqualTo(2);
    }

    /** 장바구니 — 같은 옵션을 다시 담으면 한 줄에 수량만 바뀌고, 삭제는 본인 것만 */
    @Test
    void cartPutIsUpsertAndScopedToUser() {
        long optionId = option("cart", 5);
        OffsetDateTime first = OffsetDateTime.parse("2026-11-01T00:00:00Z");

        cartItemRepository.put(7001L, optionId, 1, first);
        cartItemRepository.put(7001L, optionId, 3, first.plusDays(1));
        cartItemRepository.put(7002L, optionId, 2, first);

        List<CartItemEntity> mine = cartItemRepository.findByUserId(7001L);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getQty()).isEqualTo(3);
        assertThat(mine.get(0).getCreatedAt().toInstant()).isEqualTo(first.toInstant());   // 담은 시각은 그대로
        assertThat(cartItemRepository.delete(7002L, optionId)).isTrue();
        assertThat(cartItemRepository.delete(7002L, optionId)).isFalse();
        assertThat(cartItemRepository.deleteOptions(7001L, List.of(optionId))).isEqualTo(1);
        assertThat(cartItemRepository.findByUserId(7001L)).isEmpty();
    }

    /** 주문 상태 전이 — 조건부 UPDATE 라 한 번만 바뀝니다. 취소로 기록된 주문도 환불로 바로잡을 수 있습니다 */
    @Test
    void orderStatusTransitionsHappenOnce() {
        ShippingSnapshot home = new ShippingSnapshot("김인디", "010-1234-5678", "04043", "서울 마포구 양화로 45", "3층");
        long paid = orderRepository.save(new PurchaseOrderEntity(1L, 10000, home, OffsetDateTime.now())).getId();
        long unpaid = orderRepository.save(new PurchaseOrderEntity(1L, 10000, home, OffsetDateTime.now())).getId();
        em.flush();

        assertThat(orderRepository.confirm(paid)).isEqualTo(1);
        assertThat(orderRepository.confirm(paid)).isZero();
        assertThat(orderRepository.refund(paid)).isEqualTo(1);
        assertThat(orderRepository.refund(paid)).isZero();
        assertThat(orderRepository.cancel(paid)).isZero();            // 환불된 주문은 취소로 돌아가지 않음

        assertThat(orderRepository.cancel(unpaid)).isEqualTo(1);
        assertThat(orderRepository.refund(unpaid)).isZero();          // 이미 취소 — 재고를 또 복구하면 안 됨
        assertThat(orderRepository.refundCancelled(unpaid)).isEqualTo(1);
        em.clear();
        assertThat(orderRepository.findById(unpaid).orElseThrow().getStatus()).isEqualTo(Status.REFUNDED);
        assertThat(orderRepository.findById(paid).orElseThrow().getShipping()).isEqualTo(home);
        assertThat(orderRepository.findPage(1L, null, 10)).extracting(PurchaseOrderEntity::getId).startsWith(unpaid, paid);
    }

    private ArtistEntity artist(String externalId, String name, List<Genre> genres) {
        ArtistEntity a = new ArtistEntity("test", externalId);
        a.apply(name, genres, null, null, null, null, OffsetDateTime.now());
        return artistRepository.save(a);
    }

    /** 굿즈 상품 1개 + 옵션 1개 + 재고 — 커밋된 행 (동시성 테스트용) */
    private long option(String key, int qty) {
        String externalId = key + "-" + System.nanoTime();
        long artistId = jdbc.queryForObject("INSERT INTO artist (name, source, external_id, updated_at) VALUES ('a', 'test', ?, now()) RETURNING id",
                Long.class, externalId);
        long productId = jdbc.queryForObject("INSERT INTO product (product_type, name, source, external_id, created_at, updated_at) "
                + "VALUES ('GOODS', '티셔츠', 'test', ?, now(), now()) RETURNING id", Long.class, externalId);
        jdbc.update("INSERT INTO goods (id, artist_id, category) VALUES (?, ?, 'TSHIRT')", productId, artistId);
        long optionId = jdbc.queryForObject("INSERT INTO product_option (product_id, name, size, price, created_at) "
                + "VALUES (?, 'L', 'L', 32000, now()) RETURNING id", Long.class, productId);
        jdbc.update("INSERT INTO stock (option_id, qty, updated_at) VALUES (?, ?, now())", optionId, qty);
        return optionId;
    }
}
