package com.indie.catalog;

import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import com.indie.catalog.domain.order.entity.ShippingSnapshot;
import com.indie.catalog.domain.order.repository.OrderRepository;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.entity.StockEntity;
import com.indie.catalog.domain.product.repository.ProductRepository;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.domain.search.dto.response.SearchHit;
import com.indie.catalog.domain.search.repository.JdbcSearchIndex;
import com.indie.catalog.domain.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
@Import({SearchService.class, JdbcSearchIndex.class})
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

    @Autowired ProductRepository productRepository;
    @Autowired StockRepository stockRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired SearchService searchService;
    @Autowired JdbcTemplate jdbc;
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
