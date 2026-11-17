package com.indie.catalog;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.entity.Mood;
import com.indie.catalog.domain.album.entity.Tempo;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.ingest.dto.request.AlbumUpsert;
import com.indie.catalog.domain.ingest.dto.request.ArtistUpsert;
import com.indie.catalog.domain.ingest.dto.request.GoodsUpsert;
import com.indie.catalog.domain.ingest.dto.request.OptionUpsert;
import com.indie.catalog.domain.ingest.dto.request.PhysicalAlbumUpsert;
import com.indie.catalog.domain.ingest.dto.request.TrackUpsert;
import com.indie.catalog.domain.ingest.service.IngestService;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.VinylColor;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱 전체 기동 + HTTP — 컨트롤러 · 컨슈머 · 필터까지 빈이 모두 엮이는지, 요청 · 응답 모양(enum 파라미터 · JSON 필드)이 맞는지.
 * DB 만 실제(Testcontainers)이고 Kafka · Redis · Payment 는 닿지 않는 주소입니다 — 캐시가 죽어도 조회는 되고,
 * 결제 준비가 안 되면 주문이 503 으로 끝나며 재고가 돌아오는 것까지 봅니다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "internal.shared-secret=test-secret-test-secret-test-secret-0123456789",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=1",
        "app.payment.base-url=http://localhost:1",
        "app.outbox.relay-interval-ms=3600000",
        "app.order.expiry-interval-ms=3600000"})
@AutoConfigureMockMvc
class CatalogApiTest {

    private static final String USER = "X-User-Id";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        PostgresTest.POSTGRES.start();
        registry.add("spring.datasource.url", () -> PostgresTest.POSTGRES.getJdbcUrl() + "&currentSchema=catalog");
        registry.add("spring.datasource.username", PostgresTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTest.POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired IngestService ingest;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE label, artist, album, track, product, physical_album, goods, product_option, stock, cart_item, "
                + "purchase_order, order_item, outbox RESTART IDENTITY CASCADE");
    }

    @Test
    void publicApisCartAndOrderOverHttp() throws Exception {
        long artistId = ingest.upsertArtists(List.of(new ArtistUpsert("crawler", "api-artist", "h", "파란노을",
                List.of(Genre.SHOEGAZE, Genre.EMO), "인디레이블", 2018, null,
                List.of(new GoodsUpsert("api-goods", "h", "로고 티셔츠", GoodsCategory.TSHIRT, null, null,
                        List.of(new OptionUpsert("M", null, OptionSize.M, 35000, 3))))))).get(0).id();
        long albumId = ingest.upsertAlbums(List.of(new AlbumUpsert("crawler", "api-album", "h", artistId, "슬로우 다이브",
                AlbumEntity.Type.EP, List.of(Genre.SHOEGAZE), LocalDate.now(), null,
                List.of(new TrackUpsert(1, "아름다운 세상", List.of(Genre.SHOEGAZE), Tempo.MID, List.of(Mood.DREAMY), null, null)),
                List.of(new PhysicalAlbumUpsert("api-lp", "h", PhysicalFormat.LP, "슬로우 다이브 LP", LocalDate.now(), null, null,
                        List.of(new OptionUpsert("블랙", VinylColor.BLACK, null, 42000, 5))))))).get(0).id();
        long lpId = jdbc.queryForObject("SELECT id FROM product WHERE external_id = 'api-lp'", Long.class);
        long lpOption = jdbc.queryForObject("SELECT id FROM product_option WHERE product_id = ?", Long.class, lpId);

        // 조회 — enum 은 이름 그대로 주고받고, 목록 밖 값은 400
        mvc.perform(get("/api/catalog/artists").param("genre", "EMO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.items[0].genres[0]").value("SHOEGAZE"));
        mvc.perform(get("/api/catalog/artists").param("genre", "TROT")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/artists/" + artistId)).andExpect(status().isOk())   // Redis 가 없어도 응답
                .andExpect(jsonPath("$.result.label").value("인디레이블"))
                .andExpect(jsonPath("$.result.albums[0].type").value("EP"))
                .andExpect(jsonPath("$.result.physicalAlbums[0].format").value("LP"))
                .andExpect(jsonPath("$.result.goods[0].category").value("TSHIRT"));
        mvc.perform(get("/api/catalog/albums/" + albumId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tracks[0].tempo").value("MID"))
                .andExpect(jsonPath("$.result.tracks[0].moods[0]").value("DREAMY"))
                .andExpect(jsonPath("$.result.tracks[0].emotions", hasSize(0)))
                .andExpect(jsonPath("$.result.physicalAlbums[0].minPrice").value(42000));
        mvc.perform(get("/api/catalog/physical-albums").param("format", "LP").param("from", LocalDate.now().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items[0].id").value(lpId));
        mvc.perform(get("/api/catalog/physical-albums").param("format", "VINYL")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/goods").param("artistId", String.valueOf(artistId)).param("category", "TSHIRT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items[0].minPrice").value(35000));
        mvc.perform(get("/api/catalog/products/" + lpId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.productType").value("PHYSICAL_ALBUM"))
                .andExpect(jsonPath("$.result.options[0].vinylColor").value("BLACK"))
                .andExpect(jsonPath("$.result.options[0].qty").value(5))
                .andExpect(jsonPath("$.result.options[0].size").doesNotExist())
                .andExpect(jsonPath("$.result.category").doesNotExist());
        mvc.perform(get("/api/catalog/products").param("ids", lpId + ",999999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(1)));
        mvc.perform(get("/api/catalog/search").param("q", "슬로우")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].type").value("ALBUM"));
        mvc.perform(get("/api/catalog/lps")).andExpect(status().isNotFound());              // 옛 경로

        // 장바구니 — 신원은 X-User-Id (Gateway 가 붙임)
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/cart/items/" + lpOption).header(USER, "77").contentType(MediaType.APPLICATION_JSON).content("{\"qty\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalPrice").value(84000))
                .andExpect(jsonPath("$.result.items[0].optionName").value("블랙"));
        mvc.perform(put("/api/cart/items/" + lpOption).header(USER, "77").contentType(MediaType.APPLICATION_JSON).content("{\"qty\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cart").header(USER, "77")).andExpect(status().isOk()).andExpect(jsonPath("$.result.items", hasSize(1)));
        mvc.perform(delete("/api/cart/items/" + lpOption).header(USER, "77")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(0)));
        mvc.perform(delete("/api/cart").header(USER, "77")).andExpect(status().isOk());

        // 주문 — Payment 가 닿지 않으면 503 · 차감한 재고는 돌아옵니다. 옛 요청 모양(productId)은 400
        String shipping = "\"shipping\":{\"recipientName\":\"김인디\",\"phone\":\"010-1234-5678\",\"zonecode\":\"04043\",\"address\":\"서울 마포구 양화로 45\"}";
        mvc.perform(post("/api/catalog/orders").header(USER, "77").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"optionId\":" + lpOption + ",\"qty\":2}]," + shipping + "}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT qty FROM stock WHERE option_id = ?", Integer.class, lpOption)).isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT status FROM purchase_order", String.class)).containsExactly("CANCELLED");
        mvc.perform(post("/api/catalog/orders").header(USER, "77").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + lpId + ",\"qty\":1}]," + shipping + "}"))
                .andExpect(status().isBadRequest());

        // 수집 경로는 내부 토큰 없이는 닫혀 있습니다
        mvc.perform(post("/internal/catalog/artists").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    /** 11/17 — 크롤러 → Studio → 관리자 적재 경로. 수집 API 와 같은 본문 · 결과 · 규칙(같은 해시면 UNCHANGED) */
    @Test
    void studioIngestsThroughAdminPath() throws Exception {
        String studio = token("studio");
        String artists = "{\"items\":[{\"source\":\"crawler\",\"external_id\":\"adm-1\",\"source_hash\":\"h1\",\"name\":\"새소년\",\"genres\":[\"SHOEGAZE\"]}]}";
        mvc.perform(post("/internal/admin/catalog/ingest/artists").header("X-Internal-Token", studio)
                        .contentType(MediaType.APPLICATION_JSON).content(artists))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].external_id").value("adm-1"))
                .andExpect(jsonPath("$.result.items[0].result").value("CREATED"));
        mvc.perform(post("/internal/admin/catalog/ingest/artists").header("X-Internal-Token", studio)
                        .contentType(MediaType.APPLICATION_JSON).content(artists))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items[0].result").value("UNCHANGED"));
        mvc.perform(post("/internal/admin/catalog/ingest/diff").header("X-Internal-Token", studio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"crawler\",\"type\":\"ARTIST\",\"items\":[{\"external_id\":\"adm-1\",\"source_hash\":\"h2\"},{\"external_id\":\"adm-2\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.changed[0]").value("adm-1"))
                .andExpect(jsonPath("$.result.new[0]").value("adm-2"));
        long artistId = jdbc.queryForObject("SELECT id FROM artist WHERE external_id = 'adm-1'", Long.class);
        mvc.perform(post("/internal/admin/catalog/ingest/albums").header("X-Internal-Token", studio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"source\":\"crawler\",\"external_id\":\"adm-album\",\"source_hash\":\"h\",\"artist_id\":" + artistId
                                + ",\"title\":\"여름깃\",\"type\":\"EP\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items[0].result").value("CREATED"));

        // 토큰 없이 · 다른 발급자 · Studio 토큰으로 Gateway 수집 경로는 닫혀 있습니다
        mvc.perform(post("/internal/admin/catalog/ingest/artists").contentType(MediaType.APPLICATION_JSON).content(artists))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/admin/catalog/ingest/artists").header("X-Internal-Token", token("crawler"))
                        .contentType(MediaType.APPLICATION_JSON).content(artists))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/catalog/artists").header("X-Internal-Token", studio)
                        .contentType(MediaType.APPLICATION_JSON).content(artists))
                .andExpect(status().isUnauthorized());
    }

    private static String token(String iss) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer(iss).audience("catalog").expirationTime(Date.from(Instant.now().plusSeconds(60))).build());
        jwt.sign(new MACSigner("test-secret-test-secret-test-secret-0123456789".getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
