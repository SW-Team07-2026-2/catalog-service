package com.indie.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import com.indie.catalog.domain.order.entity.ShippingSnapshot;
import com.indie.catalog.domain.order.repository.OrderRepository;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.entity.StockEntity;
import com.indie.catalog.domain.product.repository.ProductRepository;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.domain.search.dto.response.SearchHit;
import com.indie.catalog.domain.search.repository.SearchIndex;
import com.indie.catalog.domain.search.repository.SearchIndexAdapter;
import com.indie.catalog.domain.search.service.SearchService;
import com.indie.catalog.global.outbox.OutboxAppender;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 경로 검증 — 로컬은 ddl-auto: update 라 Flyway V1 이 한 번도 돌지 않습니다.
 * 여기서 Flyway 로 스키마를 만들고 ddl-auto: validate 로 엔티티와 대조합니다 (어긋나면 컨텍스트 기동 실패).
 * Docker 가 없으면 건너뜁니다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SearchService.class, SearchIndexAdapter.class, OutboxAppender.class, CatalogSchemaTest.Beans.class})
class CatalogSchemaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withInitScript("schema-test-init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=catalog");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Beans {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @Autowired ProductRepository productRepository;
    @Autowired StockRepository stockRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired SearchService searchService;
    @Autowired SearchIndex searchIndex;
    @Autowired OutboxAppender outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @Test
    void searchUsesTrigramIndexAndSimilarityUnderFlywaySchema() {
        Integer gin = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'catalog' AND indexname = 'idx_search_doc_body'", Integer.class);
        assertThat(gin).isEqualTo(1);
        jdbc.update("INSERT INTO search_doc (target_type, target_id, body, updated_at) VALUES ('ARTIST', 1, '새소년', now())");
        jdbc.update("INSERT INTO search_doc (target_type, target_id, body, updated_at) VALUES ('ARTIST', 2, '100% 밴드', now())");

        assertThat(searchService.search("새소", 20).items()).extracting(SearchHit::id).containsExactly(1L);
        assertThat(searchService.search("%", 20).items()).extracting(SearchHit::id).containsExactly(2L);   // 와일드카드 아님
    }

    /** 같은 대상은 본문만 갈아끼우고 · 트랙 교체 전에 그 앨범의 트랙 문서만 지웁니다 */
    @Test
    void searchIndexUpsertsAndRemovesTracksOfAlbum() {
        long artistId = jdbc.queryForObject(
                "INSERT INTO artist (name, source, external_id, updated_at) VALUES ('a', 'manual', 'a1', now()) RETURNING id",
                Long.class);
        long album = album(artistId, "x1");
        long other = album(artistId, "x2");
        long t1 = track(album, 1, "첫곡");
        long t2 = track(other, 1, "다른곡");
        searchIndex.put(SearchIndex.TargetType.TRACK, t1, "첫곡");
        searchIndex.put(SearchIndex.TargetType.TRACK, t2, "다른곡");
        searchIndex.put(SearchIndex.TargetType.ALBUM, album, "앨범");
        searchIndex.put(SearchIndex.TargetType.ALBUM, album, "앨범 새이름");   // upsert

        assertThat(jdbc.queryForObject("SELECT body FROM search_doc WHERE target_type = 'ALBUM' AND target_id = ?",
                String.class, album)).isEqualTo("앨범 새이름");

        searchIndex.removeTracksOf(album);

        assertThat(jdbc.queryForList("SELECT target_type || ':' || target_id FROM search_doc ORDER BY 1", String.class))
                .containsExactly("ALBUM:" + album, "TRACK:" + t2);
    }

    /** 같은 트랜잭션에 jsonb 로 저장 (@DataJpaTest 는 테스트마다 트랜잭션) */
    @Test
    void outboxStoresJsonbPayloadInTransaction() {
        outbox.publish("catalog.test", Map.of("albumId", 7));
        em.flush();   // JPA 저장분을 JdbcTemplate 로 읽으려면

        assertThat(jdbc.queryForObject("SELECT payload->>'albumId' FROM outbox WHERE event_type = 'catalog.test'",
                String.class)).isEqualTo("7");
    }

    private long album(long artistId, String externalId) {
        return jdbc.queryForObject("INSERT INTO album (artist_id, title, type, source, external_id, updated_at) "
                + "VALUES (?, 't', 'ALBUM', 'manual', ?, now()) RETURNING id", Long.class, artistId, externalId);
    }

    private long track(long albumId, int no, String title) {
        return jdbc.queryForObject("INSERT INTO track (album_id, track_no, title) VALUES (?, ?, ?) RETURNING id",
                Long.class, albumId, no, title);
    }

    /** 배송지 스냅샷(V4)이 주문과 함께 저장되고, 배송지 도입 전 주문(컬럼 NULL)은 null 로 읽힙니다 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void orderKeepsShippingSnapshot() {
        ShippingSnapshot home = new ShippingSnapshot("김인디", "010-1234-5678", "04043", "서울 마포구 양화로 45", "3층");
        long orderId = tx.execute(s -> orderRepository.save(new PurchaseOrderEntity(1L, 10000, home, OffsetDateTime.now())).getId());
        long legacyId = tx.execute(s -> orderRepository.save(new PurchaseOrderEntity(1L, 10000, null, OffsetDateTime.now())).getId());

        assertThat(orderRepository.findById(orderId).orElseThrow().getShipping()).isEqualTo(home);
        assertThat(jdbc.queryForObject("SELECT recipient_phone FROM purchase_order WHERE id = ?", String.class, orderId)).isEqualTo("010-1234-5678");
        assertThat(orderRepository.findById(legacyId).orElseThrow().getShipping()).isNull();
    }

    /** ★ 재고 5개에 동시 주문 20건 → 정확히 5건만 성공, 음수 재고 없음 (락 없이 조건부 UPDATE) */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDecrementNeverOversells() throws Exception {
        long productId = tx.execute(s -> {
            ProductEntity p = new ProductEntity(ProductEntity.Type.GOODS, 999L);
            p.apply("동시성 테스트", 10000, null, null);
            p = productRepository.save(p);
            stockRepository.save(new StockEntity(p.getId(), 5, OffsetDateTime.now()));
            return p.getId();
        });

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Integer>> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) orders.add(() -> tx.execute(s -> stockRepository.decrement(productId, 1)));
        int succeeded = 0;
        for (Future<Integer> f : pool.invokeAll(orders)) succeeded += f.get();
        pool.shutdown();

        assertThat(succeeded).isEqualTo(5);
        assertThat(stockRepository.findById(productId).orElseThrow().getQty()).isZero();
    }
}
