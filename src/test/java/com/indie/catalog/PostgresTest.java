package com.indie.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.artist.repository.ArtistCache;
import com.indie.catalog.domain.ingest.dto.event.CatalogEvents;
import com.indie.catalog.domain.order.client.PaymentClient;
import com.indie.catalog.domain.order.dto.event.OrderEvents;
import com.indie.catalog.global.config.CatalogProperties;
import com.indie.catalog.global.config.QuerydslConfig;
import com.indie.catalog.global.outbox.OutboxAppender;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 운영 경로 검증의 공통 바탕 — 로컬은 ddl-auto: update 라 Flyway 가 한 번도 돌지 않습니다.
 * 여기서 Flyway(V1 ~ V5)로 스키마를 만들고 ddl-auto: validate 로 엔티티와 대조합니다 (어긋나면 컨텍스트 기동 실패).
 * 저장소 어댑터(@Repository) · 서비스(@Service)를 모두 올리고, 바깥(Payment · Redis)만 가짜로 바꿉니다.
 * 컨테이너는 테스트 클래스들이 하나를 같이 씁니다. Docker 가 없으면 건너뜁니다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate",
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = {Repository.class, Service.class}))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, OutboxAppender.class, CatalogEvents.class, OrderEvents.class, PostgresTest.Beans.class})
public abstract class PostgresTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withInitScript("schema-test-init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        POSTGRES.start();   // 이미 떠 있으면 아무것도 하지 않습니다 — JVM 이 끝날 때 함께 내려갑니다
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=catalog");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Beans {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        CatalogProperties catalogProperties() {
            return new CatalogProperties(null, null, null, new CatalogProperties.Order(3, 10, 15, 60000),
                    new CatalogProperties.Page(20, 50), new CatalogProperties.Cache(600),
                    new CatalogProperties.Outbox(1000, 100), new CatalogProperties.Event(30));
        }

        @Bean
        FakePayment paymentClient() {
            return new FakePayment();
        }

        /** 캐시 없음 — 항상 DB 로 */
        @Bean
        ArtistCache artistCache() {
            return new ArtistCache() {
                @Override public Optional<ArtistDetail> get(long id) { return Optional.empty(); }
                @Override public void put(ArtistDetail detail) {}
                @Override public void evictAfterCommit(long id) {}
            };
        }
    }

    /** Payment 대역 — down 이면 불통, 아니면 준비는 성공하고 취소는 cancelStatus 를 돌려줍니다 */
    public static class FakePayment implements PaymentClient {

        private final AtomicLong seq = new AtomicLong();
        public volatile String cancelStatus = "CANCELLED";
        public volatile boolean down;
        public final List<String> cancels = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public Prepared prepare(long orderId, long userId, int amount) {
            if (down) throw new Unavailable("prepare", new java.io.IOException("connection refused"));
            return new Prepared("pg-" + seq.incrementAndGet(), "PENDING", OffsetDateTime.now().plusMinutes(15));
        }

        @Override
        public String cancel(long orderId, String reason, boolean refundApproved) {
            if (down) throw new Unavailable("cancel", new java.io.IOException("connection refused"));
            cancels.add(orderId + ":" + reason + ":" + refundApproved);
            return cancelStatus;
        }
    }
}
