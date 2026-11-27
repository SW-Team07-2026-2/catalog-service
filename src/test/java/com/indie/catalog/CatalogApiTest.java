package com.indie.catalog;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.entity.Mood;
import com.indie.catalog.domain.album.entity.Tempo;
import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.follow.client.ConcertClient;
import com.indie.catalog.domain.follow.client.MemberClient;
import com.indie.catalog.domain.follow.client.MemberClient.FollowedArtist;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱 전체 기동 + HTTP - 컨트롤러, 컨슈머, 필터까지 빈이 모두 엮이는지, 요청, 응답 모양(enum 파라미터, JSON 필드)이 맞는지.
 * DB 만 실제(Testcontainers)이고 Kafka, Redis, Payment 는 닿지 않는 주소입니다 - 캐시가 죽어도 조회는 되고,
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
    @MockBean MemberClient memberClient;
    @MockBean ConcertClient concertClient;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE label, artist, album, track, product, physical_album, goods, product_option, stock, cart_item, "
                + "purchase_order, order_item, outbox RESTART IDENTITY CASCADE");
    }

    @Test
    void publicApisCartAndOrderOverHttp() throws Exception {
        long artistId = ingest.upsertArtists(List.of(new ArtistUpsert("crawler", "api-artist", "h", "파란노을",
                List.of(Genre.SHOEGAZE, Genre.EMO), "인디레이블", 2018, null, null, null,
                List.of(new GoodsUpsert("api-goods", "h", "로고 티셔츠", GoodsCategory.TSHIRT, null,
                        List.of(new OptionUpsert("M", null, OptionSize.M, 35000, 3)), null)), "2018년 시작한 1인 프로젝트입니다."))).get(0).id();
        long albumId = ingest.upsertAlbums(List.of(new AlbumUpsert("crawler", "api-album", "h", artistId, "슬로우 다이브",
                AlbumEntity.Type.EP, List.of(Genre.SHOEGAZE), LocalDate.now(), null,
                List.of(new TrackUpsert(1, "아름다운 세상", List.of(Genre.SHOEGAZE), Tempo.MID, List.of(Mood.DREAMY), null, null)),
                List.of(new PhysicalAlbumUpsert("api-lp", "h", PhysicalFormat.LP, "슬로우 다이브 LP", LocalDate.now(), null,
                        List.of(new OptionUpsert("블랙", VinylColor.BLACK, null, 42000, 5)), null)), "첫 EP 입니다."))).get(0).id();
        long lpId = jdbc.queryForObject("SELECT id FROM product WHERE external_id = 'api-lp'", Long.class);
        long lpOption = jdbc.queryForObject("SELECT id FROM product_option WHERE product_id = ?", Long.class, lpId);

        // 조회 - enum 은 이름 그대로 주고받고, 목록 밖 값은 400
        mvc.perform(get("/api/catalog/artists").param("genre", "EMO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.items[0].genres[0]").value("SHOEGAZE"));
        mvc.perform(get("/api/catalog/artists").param("genre", "TROT")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/artists/" + artistId)).andExpect(status().isOk())   // Redis 가 없어도 응답
                .andExpect(jsonPath("$.result.label").value("인디레이블"))
                .andExpect(jsonPath("$.result.bio").value("2018년 시작한 1인 프로젝트입니다."))
                .andExpect(jsonPath("$.result.albums[0].type").value("EP"))
                .andExpect(jsonPath("$.result.physicalAlbums[0].format").value("LP"))
                .andExpect(jsonPath("$.result.goods[0].category").value("TSHIRT"));
        mvc.perform(get("/api/catalog/albums/" + albumId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.description").value("첫 EP 입니다."))
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

        // 장바구니 - 신원은 X-User-Id (Gateway 가 붙임)
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

        // 주문 - Payment 가 닿지 않으면 503, 차감한 재고는 돌아옵니다. 옛 요청 모양(productId)은 400
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

    /** - 크롤러 -> Studio -> 관리자 적재 경로. 수집 API 와 같은 본문, 결과, 규칙(같은 해시면 UNCHANGED) */
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

        // - 숫자 사이즈는 JSON 값 "0" 그대로, 적재 때 qty 를 안 보내면 재고 0 -> 관리자가 설정
        // - 상세 이미지는 받지 않습니다: 옛 본문의 detail_image_urls 는 무시하고, 상품 상세에도 칸이 없습니다
        mvc.perform(post("/internal/admin/catalog/ingest/artists").header("X-Internal-Token", studio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"source\":\"crawler\",\"external_id\":\"adm-1\",\"source_hash\":\"h3\",\"name\":\"새소년\",\"genres\":[\"SHOEGAZE\"],"
                                + "\"goods\":[{\"external_id\":\"adm-tee\",\"name\":\"로고 티셔츠\",\"category\":\"TSHIRT\",\"options\":[{\"name\":\"0\",\"size\":\"0\",\"price\":59000}],"
                                + "\"description\":\"## 상품 정보\\n- 소재: 면 100%\",\"detail_image_urls\":[\"https://img.indig.co.kr/goods/2026/11/a.jpg\"]}]}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items[0].result").value("UPDATED"));
        long teeId = jdbc.queryForObject("SELECT id FROM product WHERE external_id = 'adm-tee'", Long.class);
        long teeOption = jdbc.queryForObject("SELECT id FROM product_option WHERE product_id = ?", Long.class, teeId);
        mvc.perform(get("/api/catalog/products/" + teeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.options[0].size").value("0"))
                .andExpect(jsonPath("$.result.options[0].qty").value(0))
                .andExpect(jsonPath("$.result.description").value("## 상품 정보\n- 소재: 면 100%"))
                .andExpect(jsonPath("$.result.detailImageUrls").doesNotExist());
        mvc.perform(put("/internal/admin/catalog/options/" + teeOption + "/stock").header("X-Internal-Token", studio)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qty\":15,\"reason\":\"첫 입고\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.qty").value(15));
        mvc.perform(put("/internal/admin/catalog/options/" + teeOption + "/stock").header("X-Internal-Token", studio)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qty\":-1,\"reason\":\"오타\"}"))
                .andExpect(status().isBadRequest());

        // 토큰 없이, 다른 발급자, Studio 토큰으로 Gateway 수집 경로는 닫혀 있습니다
        mvc.perform(post("/internal/admin/catalog/ingest/artists").contentType(MediaType.APPLICATION_JSON).content(artists))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/admin/catalog/ingest/artists").header("X-Internal-Token", token("crawler"))
                        .contentType(MediaType.APPLICATION_JSON).content(artists))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/catalog/artists").header("X-Internal-Token", studio)
                        .contentType(MediaType.APPLICATION_JSON).content(artists))
                .andExpect(status().isUnauthorized());
    }

    /** 팔로우 탭 - 여러 값 필터(반복, 쉼표), 검색어, 가격대, ARTIST_ORDER(넘긴 순서), UPDATED(최근 수정순) */
    @Test
    void followTabFiltersAndSorts() throws Exception {
        long a = artist("ft-a", "에이밴드", Genre.SHOEGAZE, new GoodsUpsert("ft-a-tee", "h", "에이 티셔츠", GoodsCategory.TSHIRT, null,
                List.of(new OptionUpsert("M", null, OptionSize.M, 30000, 1)), null));
        long b = artist("ft-b", "비밴드", Genre.EMO, new GoodsUpsert("ft-b-cap", "h", "비 모자", GoodsCategory.HAT, null,
                List.of(new OptionUpsert("FREE", null, OptionSize.FREE, 20000, 1)), null));
        long c = artist("ft-c", "씨밴드", Genre.INDIE_ROCK, new GoodsUpsert("ft-c-bag", "h", "씨 에코백", GoodsCategory.BAG, null,
                List.of(new OptionUpsert("기본", null, OptionSize.FREE, 60000, 1)), null));
        album(a, "ft-a-album", "첫 노을", new PhysicalAlbumUpsert("ft-a-lp", "h", PhysicalFormat.LP, "첫 노을 LP", LocalDate.parse("2024-01-01"), null,
                List.of(new OptionUpsert("블랙", VinylColor.BLACK, null, 40000, 1)), null));
        album(b, "ft-b-album", "두번째 비", new PhysicalAlbumUpsert("ft-b-cd", "h", PhysicalFormat.CD, "두번째 비 CD", LocalDate.parse("2025-01-01"), null,
                List.of(new OptionUpsert("기본", null, null, 15000, 1)), null));
        album(c, "ft-c-album", "세번째 길", new PhysicalAlbumUpsert("ft-c-tape", "h", PhysicalFormat.CASSETTE, "세번째 길 카세트", LocalDate.parse("2023-01-01"), null,
                List.of(new OptionUpsert("기본", null, null, 10000, 1)), null));

        // 아티스트 - 장르 여러 개(반복, 쉼표), ids 순서대로, ids 와 장르 함께
        mvc.perform(get("/api/catalog/artists").param("genre", "SHOEGAZE").param("genre", "EMO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[*].id").value(org.hamcrest.Matchers.contains((int) b, (int) a)));   // 가나다순
        mvc.perform(get("/api/catalog/artists").param("genre", "SHOEGAZE,EMO")).andExpect(jsonPath("$.result.items", hasSize(2)));
        mvc.perform(get("/api/catalog/artists").param("ids", c + "," + a + "," + b).param("sort", "ARTIST_ORDER"))
                .andExpect(jsonPath("$.result.items[*].id").value(org.hamcrest.Matchers.contains((int) c, (int) a, (int) b)));
        mvc.perform(get("/api/catalog/artists").param("ids", c + "," + a + "," + b).param("genre", "EMO,INDIE_ROCK").param("sort", "ARTIST_ORDER"))
                .andExpect(jsonPath("$.result.items[*].id").value(org.hamcrest.Matchers.contains((int) c, (int) b)));
        mvc.perform(get("/api/catalog/artists").param("sort", "ARTIST_ORDER")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/artists").param("sort", "POPULAR")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE artist SET updated_at = now() + interval '1 hour' WHERE id = ?", b);
        mvc.perform(get("/api/catalog/artists").param("ids", a + "," + b).param("sort", "UPDATED"))
                .andExpect(jsonPath("$.result.items[*].id").value(org.hamcrest.Matchers.contains((int) b, (int) a)));

        // 굿즈 - 품목 여러 개, 가격대(최저가), 검색어(아티스트 이름), 넘긴 순서
        mvc.perform(get("/api/catalog/goods").param("category", "TSHIRT,HAT")).andExpect(jsonPath("$.result.items", hasSize(2)));
        mvc.perform(get("/api/catalog/goods").param("minPrice", "20000").param("maxPrice", "30000"))
                .andExpect(jsonPath("$.result.items[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("에이 티셔츠", "비 모자")));
        mvc.perform(get("/api/catalog/goods").param("q", "씨밴드")).andExpect(jsonPath("$.result.items[0].name").value("씨 에코백"));
        mvc.perform(get("/api/catalog/goods").param("artistIds", b + "," + c + "," + a).param("sort", "ARTIST_ORDER"))
                .andExpect(jsonPath("$.result.items[*].artistId").value(org.hamcrest.Matchers.contains((int) b, (int) c, (int) a)));
        mvc.perform(get("/api/catalog/goods").param("artistId", String.valueOf(a)).param("artistIds", String.valueOf(b)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/goods").param("minPrice", "50000").param("maxPrice", "10000")).andExpect(status().isBadRequest());

        // 피지컬 - 매체 여러 개, 검색어(앨범 제목), 가격대, 넘긴 순서, 기존 단일 format 도 그대로
        mvc.perform(get("/api/catalog/physical-albums").param("format", "LP").param("format", "CD"))
                .andExpect(jsonPath("$.result.items[*].format").value(org.hamcrest.Matchers.contains("CD", "LP")));   // 발매일 최신순
        mvc.perform(get("/api/catalog/physical-albums").param("q", "세번째")).andExpect(jsonPath("$.result.items[0].format").value("CASSETTE"));
        mvc.perform(get("/api/catalog/physical-albums").param("maxPrice", "15000")).andExpect(jsonPath("$.result.items", hasSize(2)));
        mvc.perform(get("/api/catalog/physical-albums").param("artistIds", c + "," + a).param("sort", "ARTIST_ORDER"))
                .andExpect(jsonPath("$.result.items[*].artistId").value(org.hamcrest.Matchers.contains((int) c, (int) a)));
    }

    /** 아티스트 성별, 구성 필터 - 목록, 전체 검색, 팔로우 같음, 여러 개 OR, 서로 AND, 수집에서 null 이면 값 유지 */
    @Test
    void artistGenderCompositionFilters() throws Exception {
        long male = artist("gc-m", "h", "성별남팀", Genre.INDIE_ROCK, ArtistGender.MALE, ArtistComposition.GROUP, null);
        long female = artist("gc-f", "h", "성별여솔로", Genre.FOLK, ArtistGender.FEMALE, ArtistComposition.SOLO, null);
        long mixed = artist("gc-x", "h", "성별혼성팀", Genre.INDIE_POP, ArtistGender.MIXED, ArtistComposition.GROUP, null);
        long unknown = artist("gc-u", "h", "성별모름", Genre.INDIE_POP, null, null, null);

        mvc.perform(get("/api/catalog/search/artists").param("q", "성별")).andExpect(jsonPath("$.result.items", hasSize(4)));   // 전체 = 조건 없음
        mvc.perform(get("/api/catalog/search/artists").param("q", "성별").param("gender", "FEMALE"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) female)));
        mvc.perform(get("/api/catalog/search/artists").param("q", "성별").param("gender", "MALE,MIXED"))
                .andExpect(jsonPath("$.result.items[*].id").value(containsInAnyOrder((int) male, (int) mixed)));
        mvc.perform(get("/api/catalog/search/artists").param("q", "성별").param("composition", "GROUP").param("gender", "MIXED"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) mixed)));
        mvc.perform(get("/api/catalog/artists").param("q", "성별").param("composition", "SOLO"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) female)));
        when(memberClient.follows(11L)).thenReturn(List.of(new FollowedArtist(unknown, OffsetDateTime.now(), 1),
                new FollowedArtist(mixed, OffsetDateTime.now().minusDays(1), 1), new FollowedArtist(male, OffsetDateTime.now().minusDays(2), 1)));
        mvc.perform(get("/api/catalog/following/artists").header(USER, "11").param("composition", "GROUP"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) mixed, (int) male)));   // 최근 팔로우순 유지
        mvc.perform(get("/api/catalog/search/artists").param("gender", "BOTH")).andExpect(status().isBadRequest());

        artist("gc-f", "h2", "성별여솔로", Genre.FOLK, null, null, null);                                   // 수집기가 다시 보냄 (값 없음)
        mvc.perform(get("/api/catalog/search/artists").param("q", "성별").param("gender", "FEMALE").param("composition", "SOLO"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) female)));
    }

    /** 검색어 최대 100자 - 붙여넣기까지 생각해 지금 가장 긴 이름(공연 제목 62자)보다 넉넉하게. 넘으면 400, 통합 검색 /api/catalog/search 는 전처럼 50자 SEARCH_QUERY_TOO_LONG */
    @Test
    void searchQueryLengthLimit() throws Exception {
        String limit = "가".repeat(100);
        mvc.perform(get("/api/catalog/search/artists").param("q", limit)).andExpect(status().isOk());
        for (String path : List.of("/api/catalog/search/artists", "/api/catalog/search/physical-albums",
                "/api/catalog/search/goods", "/api/catalog/artists", "/api/catalog/physical-albums", "/api/catalog/goods",
                "/api/catalog/following/artists", "/api/catalog/following/physical-albums", "/api/catalog/following/goods")) {
            mvc.perform(get(path).header(USER, "11").param("q", limit + "가"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    /** 전체 검색, 팔로우 탭 (프론트 요구 1-1 ~ 1-4) - 팔로우 목록은 Member 대역 */
    @Test
    void searchAndFollowingApis() throws Exception {
        long a = artist("fw-a", "에이밴드", Genre.SHOEGAZE, new GoodsUpsert("fw-a-tee", "h", "에이 티셔츠", GoodsCategory.TSHIRT, null,
                List.of(new OptionUpsert("M", null, OptionSize.M, 30000, 1)), null));
        long b = artist("fw-b", "비밴드", Genre.EMO, new GoodsUpsert("fw-b-cap", "h", "비 모자", GoodsCategory.HAT, null,
                List.of(new OptionUpsert("FREE", null, OptionSize.FREE, 20000, 1)), null));
        long c = artist("fw-c", "씨밴드", Genre.INDIE_ROCK, new GoodsUpsert("fw-c-bag", "h", "씨 에코백", GoodsCategory.BAG, null,
                List.of(new OptionUpsert("기본", null, OptionSize.FREE, 160000, 1)), null));
        long d = artist("fw-d", "디밴드", Genre.EMO, new GoodsUpsert("fw-d-tee", "h", "디 티셔츠", GoodsCategory.TSHIRT, null,
                List.of(new OptionUpsert("M", null, OptionSize.M, 25000, 1)), null));   // 팔로우 안 함
        album(a, "fw-a-album", "첫 노을", new PhysicalAlbumUpsert("fw-a-lp", "h", PhysicalFormat.LP, "첫 노을 LP", LocalDate.parse("2024-01-01"), null,
                List.of(new OptionUpsert("블랙", VinylColor.BLACK, null, 40000, 1)), null));
        album(b, "fw-b-album", "두번째 비", new PhysicalAlbumUpsert("fw-b-cd", "h", PhysicalFormat.CD, "두번째 비 CD", LocalDate.parse("2025-01-01"), null,
                List.of(new OptionUpsert("기본", null, null, 15000, 1)), null));
        album(d, "fw-d-album", "디 앨범", new PhysicalAlbumUpsert("fw-d-cd", "h", PhysicalFormat.CD, "디 앨범 CD", LocalDate.parse("2025-06-01"), null,
                List.of(new OptionUpsert("기본", null, null, 18000, 1)), null));

        // 전체 검색 - q(LIKE) 는 선택, 칩 여러 개(OR), 다른 칩끼리 AND, 15만원 이상은 상한 없음
        mvc.perform(get("/api/catalog/search/artists").param("q", "밴드").param("genre", "EMO,SHOEGAZE"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) d, (int) b, (int) a)))   // 가나다순
                .andExpect(jsonPath("$.result.hasNext").value(false));
        mvc.perform(get("/api/catalog/search/artists").param("genre", "INDIE_ROCK")).andExpect(jsonPath("$.result.items[*].id").value(contains((int) c)));
        mvc.perform(get("/api/catalog/search/goods").param("q", "씨밴드")).andExpect(jsonPath("$.result.items[*].name").value(contains("씨 에코백")));
        mvc.perform(get("/api/catalog/search/goods").param("category", "TSHIRT").param("q", "디"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("디 티셔츠")));
        mvc.perform(get("/api/catalog/search/goods").param("minPrice", "30000").param("maxPrice", "150000"))
                .andExpect(jsonPath("$.result.items[*].name").value(containsInAnyOrder("에이 티셔츠", "씨 에코백")));
        mvc.perform(get("/api/catalog/search/goods").param("minPrice", "30000").param("maxPrice", "145000"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("에이 티셔츠")));
        mvc.perform(get("/api/catalog/search/physical-albums").param("q", "비").param("format", "CD"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("두번째 비 CD")));
        mvc.perform(get("/api/catalog/search/physical-albums").param("format", "CD").param("size", "1"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("디 앨범 CD"))).andExpect(jsonPath("$.result.hasNext").value(true));

        // 팔로우 - Member 는 최근 팔로우순(c, a, b)으로 줌, 팔로워 a 5 > b 3 > c 1
        OffsetDateTime t = OffsetDateTime.parse("2026-11-01T00:00:00Z");
        when(memberClient.follows(7L)).thenReturn(List.of(new FollowedArtist(c, t.plusDays(3), 1),
                new FollowedArtist(a, t.plusDays(2), 5), new FollowedArtist(b, t.plusDays(1), 3)));
        mvc.perform(get("/api/catalog/following/artists").header(USER, "7"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) c, (int) a, (int) b)));
        mvc.perform(get("/api/catalog/following/artists").header(USER, "7").param("sort", "FOLLOWERS"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) a, (int) b, (int) c)));

        // 아티스트 업데이트순 - 새소식 날짜: 앨범, 피지컬 발매일, 굿즈 등록 시각, 공연(Concert) 중 가장 늦은 것
        jdbc.update("UPDATE product SET created_at = '2023-01-01T00:00:00Z' WHERE external_id IN ('fw-a-tee', 'fw-b-cap')");
        jdbc.update("UPDATE product SET created_at = '2020-01-01T00:00:00Z' WHERE external_id = 'fw-c-bag'");
        // a 2024-01-01(LP), b 2025-01-01(CD), c 2020-01-01(굿즈) -> 공연 없으면 b, a, c
        when(concertClient.latestConcertAt(anyList())).thenReturn(Map.of());
        mvc.perform(get("/api/catalog/following/artists").header(USER, "7").param("sort", "UPDATED"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) b, (int) a, (int) c)));
        // c 의 공연이 가장 최근 -> c, b, a
        when(concertClient.latestConcertAt(anyList())).thenReturn(Map.of(c, t.plusDays(10)));
        mvc.perform(get("/api/catalog/following/artists").header(USER, "7").param("sort", "UPDATED"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) c, (int) b, (int) a)));
        // Concert 불통 -> 공연 없이 정렬 (목록은 그대로 나옴)
        when(concertClient.latestConcertAt(anyList())).thenThrow(new ConcertClient.Unavailable(new java.io.IOException("connection refused")));
        mvc.perform(get("/api/catalog/following/artists").header(USER, "7").param("sort", "UPDATED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) b, (int) a, (int) c)));
        mvc.perform(get("/api/catalog/following/artists").header(USER, "7").param("genre", "EMO").param("q", "밴드"))
                .andExpect(jsonPath("$.result.items[*].id").value(contains((int) b)));   // d 는 팔로우 밖
        mvc.perform(get("/api/catalog/following/goods").header(USER, "7"))
                .andExpect(jsonPath("$.result.items[*].artistId").value(contains((int) c, (int) a, (int) b)));
        mvc.perform(get("/api/catalog/following/goods").header(USER, "7").param("category", "TSHIRT"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("에이 티셔츠")));
        mvc.perform(get("/api/catalog/following/goods").header(USER, "7").param("minPrice", "25000").param("maxPrice", "150000").param("sort", "FOLLOWERS"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("에이 티셔츠", "씨 에코백")));
        jdbc.update("UPDATE product SET updated_at = now() + interval '1 hour' WHERE external_id = 'fw-b-cap'");
        mvc.perform(get("/api/catalog/following/goods").header(USER, "7").param("sort", "UPDATED"))
                .andExpect(jsonPath("$.result.items[0].name").value("비 모자"));
        // 품절 안 보기 - 기본은 품절, 판매중 전부, excludeSoldOut=true 면 재고 남은 옵션이 있는 것만 (목록, 검색, 팔로우 같음)
        jdbc.update("UPDATE stock SET qty = 0 WHERE option_id IN (SELECT o.id FROM product_option o JOIN product p ON p.id = o.product_id "
                + "WHERE p.external_id IN ('fw-c-bag', 'fw-b-cd'))");
        mvc.perform(get("/api/catalog/search/goods").param("q", "씨")).andExpect(jsonPath("$.result.items[0].soldOut").value(true));
        mvc.perform(get("/api/catalog/search/goods").param("q", "씨").param("excludeSoldOut", "true")).andExpect(jsonPath("$.result.items", hasSize(0)));
        mvc.perform(get("/api/catalog/goods").param("artistIds", String.valueOf(c)).param("excludeSoldOut", "true")).andExpect(jsonPath("$.result.items", hasSize(0)));
        mvc.perform(get("/api/catalog/following/goods").header(USER, "7").param("excludeSoldOut", "true"))
                .andExpect(jsonPath("$.result.items[*].artistId").value(contains((int) a, (int) b)));
        mvc.perform(get("/api/catalog/search/physical-albums").param("format", "CD").param("excludeSoldOut", "true"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("디 앨범 CD")));
        mvc.perform(get("/api/catalog/physical-albums").param("excludeSoldOut", "true").param("q", "두번째")).andExpect(jsonPath("$.result.items", hasSize(0)));
        mvc.perform(get("/api/catalog/following/physical-albums").header(USER, "7").param("excludeSoldOut", "true"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("첫 노을 LP")));
        mvc.perform(get("/api/catalog/following/physical-albums").header(USER, "7").param("sort", "FOLLOWERS"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("첫 노을 LP", "두번째 비 CD")));
        mvc.perform(get("/api/catalog/following/physical-albums").header(USER, "7").param("format", "CD").param("q", "두번째"))
                .andExpect(jsonPath("$.result.items[*].name").value(contains("두번째 비 CD")));
        mvc.perform(get("/api/catalog/following/goods").header(USER, "7").param("sort", "ARTIST_ORDER")).andExpect(status().isBadRequest());

        // 팔로우 없음 -> 빈 페이지, 비로그인 -> 401, Member 불통 -> 503
        when(memberClient.follows(8L)).thenReturn(List.of());
        mvc.perform(get("/api/catalog/following/goods").header(USER, "8")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(0))).andExpect(jsonPath("$.result.hasNext").value(false));
        mvc.perform(get("/api/catalog/following/artists")).andExpect(status().isUnauthorized());
        when(memberClient.follows(9L)).thenThrow(new MemberClient.Unavailable(new java.io.IOException("connection refused")));
        mvc.perform(get("/api/catalog/following/artists").header(USER, "9")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FOLLOW_UNAVAILABLE"));
    }

    private long artist(String externalId, String name, Genre genre, GoodsUpsert goods) {
        return artist(externalId, "h", name, genre, null, null, List.of(goods));
    }

    private long artist(String externalId, String hash, String name, Genre genre, ArtistGender gender, ArtistComposition composition,
                        List<GoodsUpsert> goods) {
        return ingest.upsertArtists(List.of(new ArtistUpsert("crawler", externalId, hash, name, List.of(genre), null, 2020, null,
                gender, composition, goods, null))).get(0).id();
    }

    private void album(long artistId, String externalId, String title, PhysicalAlbumUpsert physical) {
        ingest.upsertAlbums(List.of(new AlbumUpsert("crawler", externalId, "h", artistId, title, AlbumEntity.Type.ALBUM, List.of(Genre.ETC),
                physical.releasedAt(), null, List.of(), List.of(physical), null)));
    }

    private static String token(String iss) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer(iss).audience("catalog").expirationTime(Date.from(Instant.now().plusSeconds(60))).build());
        jwt.sign(new MACSigner("test-secret-test-secret-test-secret-0123456789".getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
