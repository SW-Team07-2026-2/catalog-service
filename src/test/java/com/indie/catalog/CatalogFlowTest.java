package com.indie.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.album.dto.response.AlbumDetail;
import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.entity.Emotion;
import com.indie.catalog.domain.album.entity.Mood;
import com.indie.catalog.domain.album.entity.Style;
import com.indie.catalog.domain.album.entity.Tempo;
import com.indie.catalog.domain.album.service.AlbumService;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.artist.dto.response.ArtistItem;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.artist.service.ArtistService;
import com.indie.catalog.domain.cart.dto.response.CartLine;
import com.indie.catalog.domain.cart.dto.response.CartView;
import com.indie.catalog.domain.cart.exception.CartErrorCode;
import com.indie.catalog.domain.cart.service.CartService;
import com.indie.catalog.domain.ingest.dto.request.AlbumUpsert;
import com.indie.catalog.domain.ingest.dto.request.ArtistUpsert;
import com.indie.catalog.domain.ingest.dto.request.DiffItem;
import com.indie.catalog.domain.ingest.dto.request.DiffRequest;
import com.indie.catalog.domain.ingest.dto.request.DiffType;
import com.indie.catalog.domain.ingest.dto.request.GoodsUpsert;
import com.indie.catalog.domain.ingest.dto.request.OptionUpsert;
import com.indie.catalog.domain.ingest.dto.request.PhysicalAlbumUpsert;
import com.indie.catalog.domain.ingest.dto.request.TrackUpsert;
import com.indie.catalog.domain.ingest.dto.response.DiffResult;
import com.indie.catalog.domain.ingest.dto.response.ItemResult;
import com.indie.catalog.domain.ingest.dto.response.ItemResult.Result;
import com.indie.catalog.domain.ingest.service.IngestService;
import com.indie.catalog.domain.order.dto.response.OrderCreated;
import com.indie.catalog.domain.order.dto.response.OrderDetail;
import com.indie.catalog.domain.order.dto.response.OrderItemView;
import com.indie.catalog.domain.order.entity.ShippingSnapshot;
import com.indie.catalog.domain.order.exception.OrderErrorCode;
import com.indie.catalog.domain.order.service.OrderService;
import com.indie.catalog.domain.order.service.OrderService.Line;
import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.OptionView;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.entity.VinylColor;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.exception.BaseErrorCode;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 수집 적재 → 조회 → 장바구니 → 주문 · 환불 — 서비스 규칙과 이벤트 payload 를 실제 DB 로 확인합니다.
 * 서비스가 스스로 트랜잭션을 나누므로(항목별 적재 · 주문 Saga) 테스트 트랜잭션으로 감싸지 않고, 끝나면 테이블을 비웁니다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogFlowTest extends PostgresTest {

    private static final ShippingSnapshot HOME = new ShippingSnapshot("김인디", "010-1234-5678", "04043", "서울 마포구 양화로 45", "3층");
    private static final long USER = 501L;

    @Autowired IngestService ingest;
    @Autowired ArtistService artistService;
    @Autowired AlbumService albumService;
    @Autowired ProductService productService;
    @Autowired CartService cartService;
    @Autowired OrderService orderService;
    @Autowired FakePayment payment;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE label, artist, album, track, product, physical_album, goods, product_option, stock, cart_item, "
                + "purchase_order, order_item, outbox RESTART IDENTITY CASCADE");
        payment.cancelStatus = "CANCELLED";
        payment.down = false;
        payment.cancels.clear();
    }

    // ── 수집 적재 ────────────────────────────────────────────────────────────────────────────────

    @Test
    void ingestCreatesCatalogAndPublishesEventsOnlyForRealChanges() {
        ArtistUpsert artist = new ArtistUpsert("crawler", "ar-1", "a-h1", " 실리카겔 ",
                List.of(Genre.PSYCHEDELIC, Genre.INDIE_ROCK, Genre.PSYCHEDELIC), "매직스트로베리사운드", 2013, null,
                List.of(tshirt("gd-1", opt("M", null, OptionSize.M, 35000, 3), opt("L", null, OptionSize.L, 33000, 0))));
        ItemResult created = ingest.upsertArtists(List.of(artist)).get(0);
        long artistId = created.id();

        assertThat(created.result()).isEqualTo(Result.CREATED);
        JsonNode artistUpdated = events("artist.updated").get(0);
        assertThat(artistUpdated.path("artistId").asLong()).isEqualTo(artistId);
        assertThat(artistUpdated.path("name").asText()).isEqualTo("실리카겔");
        assertThat(artistUpdated.path("genre").asText()).isEqualTo("PSYCHEDELIC");               // Concert 가 읽는 대표 장르
        assertThat(texts(artistUpdated.path("genres"))).containsExactly("PSYCHEDELIC", "INDIE_ROCK");   // 중복 제거 · 순서 유지
        JsonNode goodsReleased = events("goods.released").get(0);
        assertThat(goodsReleased.path("artistId").asLong()).isEqualTo(artistId);
        assertThat(goodsReleased.path("artistName").asText()).isEqualTo("실리카겔");
        assertThat(goodsReleased.path("name").asText()).isEqualTo("로고 티셔츠 (블랙)");
        assertThat(goodsReleased.path("price").asInt()).isEqualTo(33000);                        // 옵션 최저가
        assertThat(goodsReleased.path("goodsId").asLong()).isPositive();

        // 같은 요청을 다시 → 아무것도 바뀌지 않고 이벤트도 없음
        assertThat(ingest.upsertArtists(List.of(artist)).get(0).result()).isEqualTo(Result.UNCHANGED);
        assertThat(events("artist.updated")).hasSize(1);
        assertThat(events("goods.released")).hasSize(1);

        AlbumUpsert album = album("al-1", "al-h1", artistId, LocalDate.now(),
                List.of(track(1, "NO PAIN"), track(2, "Tik Tak Tok")),
                List.of(lp("lp-1", "ph1", LocalDate.now(), opt("초판 블랙", VinylColor.BLACK, null, 42000, 5),
                                opt("초판 컬러", VinylColor.COLOR, null, 45000, 2)),
                        new PhysicalAlbumUpsert("cd-1", "ch1", PhysicalFormat.CD, "POWER ANDRE 99 CD", null, null, null,
                                List.of(opt("기본", null, null, 18000, 10)))));
        ItemResult albumCreated = ingest.upsertAlbums(List.of(album)).get(0);
        long albumId = albumCreated.id();

        assertThat(albumCreated.result()).isEqualTo(Result.CREATED);
        JsonNode albumReleased = events("album.released").get(0);
        assertThat(albumReleased.path("albumId").asLong()).isEqualTo(albumId);
        assertThat(albumReleased.path("artistId").asLong()).isEqualTo(artistId);
        assertThat(albumReleased.path("artistName").asText()).isEqualTo("실리카겔");
        assertThat(albumReleased.path("title").asText()).isEqualTo("POWER ANDRE 99");
        assertThat(albumReleased.path("type").asText()).isEqualTo("ALBUM");
        assertThat(albumReleased.path("releasedAt").asText()).isEqualTo(LocalDate.now().toString());
        List<JsonNode> physicalReleased = events("physical_album.released");
        assertThat(physicalReleased).extracting(e -> e.path("format").asText()).containsExactly("LP", "CD");
        assertThat(physicalReleased.get(0).path("albumTitle").asText()).isEqualTo("POWER ANDRE 99");
        assertThat(physicalReleased.get(0).path("artistName").asText()).isEqualTo("실리카겔");
        assertThat(physicalReleased.get(0).path("physicalAlbumId").asLong()).isPositive();
        assertThat(physicalReleased.get(0).path("options")).extracting(o -> o.path("name").asText() + ":" + o.path("price").asInt())
                .containsExactly("초판 블랙:42000", "초판 컬러:45000");

        AlbumDetail detail = albumService.detail(albumId);
        long firstTrackId = detail.tracks().get(0).id();
        assertThat(detail.genres()).containsExactly(Genre.PSYCHEDELIC);
        assertThat(detail.tracks()).hasSize(2);
        assertThat(detail.tracks().get(0).tempo()).isEqualTo(Tempo.FAST);
        assertThat(detail.tracks().get(0).moods()).containsExactly(Mood.ENERGETIC, Mood.DREAMY);
        assertThat(detail.tracks().get(0).emotions()).containsExactly(Emotion.FREEDOM);
        assertThat(detail.tracks().get(0).styles()).containsExactly(Style.GUITAR_DRIVEN, Style.SYNTH_DRIVEN);
        assertThat(detail.physicalAlbums()).extracting(PhysicalAlbumItem::format).containsExactlyInAnyOrder(PhysicalFormat.LP, PhysicalFormat.CD);
        assertThat(detail.physicalAlbums()).filteredOn(p -> p.format() == PhysicalFormat.LP)
                .extracting(PhysicalAlbumItem::minPrice).containsExactly(42000);

        // 트랙 1 제목 변경 · 트랙 2 삭제 · LP 에 새 옵션(2차 컬러) 추가
        AlbumUpsert changed = album("al-1", "al-h2", artistId, LocalDate.now(), List.of(track(1, "NO PAIN (Remaster)")),
                List.of(lp("lp-1", "ph2", LocalDate.now(), opt("초판 블랙", VinylColor.BLACK, null, 42000, null),
                        opt("2차 컬러", VinylColor.COLOR, null, 47000, 30))));
        assertThat(ingest.upsertAlbums(List.of(changed)).get(0).result()).isEqualTo(Result.UPDATED);

        AlbumDetail after = albumService.detail(albumId);
        assertThat(after.tracks()).hasSize(1);
        assertThat(after.tracks().get(0).id()).isEqualTo(firstTrackId);                          // 트랙 id 유지 (Member 메트릭이 참조)
        assertThat(after.tracks().get(0).title()).isEqualTo("NO PAIN (Remaster)");
        assertThat(events("album.released")).hasSize(1);
        assertThat(texts(events("album.updated").get(0).path("changedFields"))).contains("tracks", "physicalAlbums");
        List<JsonNode> again = events("physical_album.released");
        assertThat(again).hasSize(3);
        assertThat(again.get(2).path("options")).extracting(o -> o.path("name").asText()).containsExactly("2차 컬러");   // 새 옵션만
        long lpId = again.get(2).path("physicalAlbumId").asLong();
        assertThat(productService.product(lpId).options()).extracting(o -> o.name() + ":" + o.qty())
                .containsExactly("초판 블랙:5", "초판 컬러:2", "2차 컬러:30");                       // qty null → 그대로 · 빠진 옵션도 유지

        DiffResult diff = ingest.diff(new DiffRequest("crawler", DiffType.PHYSICAL_ALBUM, List.of(
                new DiffItem("lp-1", null, "ph2"), new DiffItem("cd-1", null, "other"), new DiffItem("lp-9", null, "x"))));
        assertThat(diff.unchanged()).containsExactly("lp-1");
        assertThat(diff.changed()).containsExactly("cd-1");
        assertThat(diff.created()).containsExactly("lp-9");
    }

    /** 옛 음반을 처음 수집할 때 발매 알림이 나가지 않습니다 */
    @Test
    void oldReleasesAreIngestedSilently() {
        long artistId = artist("ar-old");
        LocalDate old = LocalDate.now().minusYears(5);

        ingest.upsertAlbums(List.of(album("al-old", "h", artistId, old, List.of(track(1, "옛 노래")),
                List.of(lp("lp-old", "h", old, opt("블랙", VinylColor.BLACK, null, 30000, 1))))));

        assertThat(events("album.released")).isEmpty();
        assertThat(events("physical_album.released")).isEmpty();
        assertThat(productService.physicalAlbums(PhysicalFormat.LP, null, null, 0, 10).items()).hasSize(1);
    }

    /** ★ DB 가 못 막는 규칙 — 어긴 항목은 통째로 거절되고(그 항목의 앨범 · 상품도 저장되지 않음) 나머지 항목은 적재됩니다 */
    @Test
    void ingestRejectsItemsBreakingOptionRules() {
        long artistId = artist("ar-rule");
        LocalDate today = LocalDate.now();

        List<ItemResult> results = ingest.upsertAlbums(List.of(
                album("lp-no-color", "h", artistId, today, null, List.of(lp("p1", "h", today, opt("기본", null, null, 40000, 1)))),
                album("lp-with-size", "h", artistId, today, null,
                        List.of(lp("p2", "h", today, opt("기본", VinylColor.BLACK, OptionSize.FREE, 40000, 1)))),
                album("cd-with-color", "h", artistId, today, null, List.of(new PhysicalAlbumUpsert("p3", "h", PhysicalFormat.CD,
                        "CD", today, null, null, List.of(opt("기본", VinylColor.BLACK, null, 15000, 1))))),
                album("dup-option", "h", artistId, today, null, List.of(lp("p4", "h", today,
                        opt("블랙", VinylColor.BLACK, null, 40000, 1), opt(" 블랙 ", VinylColor.BLACK, null, 41000, 1)))),
                album("dup-track", "h", artistId, today, List.of(track(1, "a"), track(1, "b")), null),
                album("no-artist", "h", 999_999L, today, null, null),
                album("fine", "h", artistId, today, null, List.of(lp("p5", "h", today, opt("블랙", VinylColor.BLACK, null, 40000, 1))))));

        assertThat(results).extracting(ItemResult::result).containsExactly(Result.FAILED, Result.FAILED, Result.FAILED,
                Result.FAILED, Result.FAILED, Result.FAILED, Result.CREATED);
        assertThat(results.get(0).error()).contains("vinyl_color");
        assertThat(jdbc.queryForList("SELECT external_id FROM album", String.class)).containsExactly("fine");
        assertThat(jdbc.queryForList("SELECT external_id FROM product", String.class)).containsExactly("p5");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_option", Integer.class)).isEqualTo(1);
        assertThat(events("album.released")).hasSize(1);

        List<ItemResult> goods = ingest.upsertArtists(List.of(
                new ArtistUpsert("crawler", "goods-no-size", "h", "밴드A", null, null, null, null,
                        List.of(tshirt("g1", opt("기본", null, null, 30000, 1)))),
                new ArtistUpsert("crawler", "goods-with-color", "h", "밴드B", null, null, null, null,
                        List.of(tshirt("g2", opt("M", VinylColor.COLOR, OptionSize.M, 30000, 1))))));
        assertThat(goods).extracting(ItemResult::result).containsExactly(Result.FAILED, Result.FAILED);
        assertThat(jdbc.queryForList("SELECT external_id FROM artist", String.class)).containsExactly("ar-rule");
    }

    // ── 조회 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void listingsAndDetails() {
        Shop shop = shop();

        ArtistDetail artist = artistService.detail(shop.artistId);
        assertThat(artist.label()).isEqualTo("인디레이블");
        assertThat(artist.genres()).containsExactly(Genre.SHOEGAZE);
        assertThat(artist.albums()).hasSize(1);
        assertThat(artist.physicalAlbums()).extracting(PhysicalAlbumItem::id).containsExactly(shop.lpId);
        assertThat(artist.goods()).extracting(GoodsItem::id).containsExactly(shop.goodsId);
        assertThat(artistService.list(null, Genre.SHOEGAZE, 0, 10).items()).extracting(ArtistItem::id).containsExactly(shop.artistId);
        assertThat(artistService.list(null, Genre.JAZZ, 0, 10).items()).isEmpty();

        LocalDate today = LocalDate.now();
        assertThat(productService.physicalAlbums(PhysicalFormat.LP, today.minusDays(1), today, 0, 10).items())
                .extracting(PhysicalAlbumItem::id).containsExactly(shop.lpId);
        assertThat(productService.physicalAlbums(PhysicalFormat.CD, null, null, 0, 10).items()).isEmpty();
        assertThat(productService.physicalAlbums(null, today.plusDays(1), null, 0, 10).items()).isEmpty();
        assertThat(productService.goods(shop.artistId, GoodsCategory.TSHIRT, 0, 10).items()).extracting(GoodsItem::minPrice).containsExactly(35000);
        assertThat(productService.goods(null, GoodsCategory.KEYRING, 0, 10).items()).isEmpty();

        ProductDetail lp = productService.product(shop.lpId);
        assertThat(lp.productType()).isEqualTo(ProductEntity.Type.PHYSICAL_ALBUM);
        assertThat(lp.format()).isEqualTo(PhysicalFormat.LP);
        assertThat(lp.albumId()).isEqualTo(shop.albumId);
        assertThat(lp.artistId()).isEqualTo(shop.artistId);
        assertThat(lp.category()).isNull();
        assertThat(lp.thumbnailUrl()).isEqualTo("https://img/album.jpg");                        // 상품 이미지가 없으면 앨범 이미지
        assertThat(lp.options()).extracting(OptionView::vinylColor).containsExactly(VinylColor.BLACK);
        ProductDetail goods = productService.product(shop.goodsId);
        assertThat(goods.productType()).isEqualTo(ProductEntity.Type.GOODS);
        assertThat(goods.category()).isEqualTo(GoodsCategory.TSHIRT);
        assertThat(goods.albumId()).isNull();
        assertThat(goods.options()).extracting(OptionView::size).containsExactly(OptionSize.M);
        assertThat(productService.products(List.of(shop.lpId, shop.goodsId, 999_999L))).hasSize(2);
        assertCode(() -> productService.product(999_999L), CommonErrorCode.NOT_FOUND);
    }

    // ── 장바구니 ────────────────────────────────────────────────────────────────────────────────

    @Test
    void cartHoldsOptionsWithinOrderLimits() {
        Shop shop = shop();
        long[] extra = extraOptions(shop.artistId, 3);

        cartService.put(USER, shop.lpOption, 1);
        CartView cart = cartService.put(USER, shop.lpOption, 2);                                 // 더하지 않고 덮어씀
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.totalPrice()).isEqualTo(84000);
        CartLine line = cart.items().get(0);
        assertThat(line.productId()).isEqualTo(shop.lpId);
        assertThat(line.productType()).isEqualTo(ProductEntity.Type.PHYSICAL_ALBUM);
        assertThat(line.optionName()).isEqualTo("블랙");
        assertThat(line.stockQty()).isEqualTo(5);
        assertThat(line.soldOut()).isFalse();

        cartService.put(USER, extra[0], 1);
        cartService.put(USER, extra[1], 1);
        assertCode(() -> cartService.put(USER, extra[2], 1), CartErrorCode.CART_FULL);            // 옵션 3가지까지 (테스트 설정)
        assertThat(cartService.put(USER, shop.lpOption, 10).items()).hasSize(3);                 // 담긴 옵션의 수량 변경은 가능
        assertCode(() -> cartService.put(USER, shop.lpOption, 11), CommonErrorCode.INVALID_REQUEST);
        assertCode(() -> cartService.put(USER, 999_999L, 1), ProductErrorCode.OPTION_NOT_FOUND);
        assertThat(cartService.get(502L).items()).isEmpty();                                     // 남의 장바구니는 보이지 않음

        assertThat(cartService.remove(USER, extra[0]).items()).hasSize(2);
        assertThat(cartService.remove(USER, extra[0]).items()).hasSize(2);                       // 두 번 지워도 같음
        cartService.clear(USER);
        assertThat(cartService.get(USER).items()).isEmpty();
    }

    // ── 주문 ────────────────────────────────────────────────────────────────────────────────────

    /** 결제 완료 → 확정 → 사용자 취소 = 환불 (REFUNDED · 재고 복구 · order.refunded) */
    @Test
    void orderIsConfirmedThenRefunded() {
        Shop shop = shop();
        cartService.put(USER, shop.lpOption, 2);
        cartService.put(USER, shop.goodsOption, 1);
        cartService.put(502L, shop.lpOption, 1);

        OrderCreated created = orderService.create(USER, List.of(new Line(shop.goodsOption, 1), new Line(shop.lpOption, 2)), HOME);

        assertThat(created.amount()).isEqualTo(42000 * 2 + 35000);                               // 금액은 옵션 가격으로만
        assertThat(created.pgOrderId()).startsWith("pg-");
        assertThat(stock(shop.lpOption)).isEqualTo(3);
        assertThat(stock(shop.goodsOption)).isEqualTo(2);
        OrderDetail pending = orderService.get(USER, created.orderId());
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.pgOrderId()).isEqualTo(created.pgOrderId());
        assertThat(pending.shipping().recipientName()).isEqualTo("김인디");
        assertThat(pending.items()).extracting(OrderItemView::productType)
                .containsExactlyInAnyOrder(ProductEntity.Type.PHYSICAL_ALBUM, ProductEntity.Type.GOODS);
        assertThat(pending.items()).extracting(OrderItemView::optionName).containsExactlyInAnyOrder("블랙", "M");
        assertCode(() -> orderService.get(502L, created.orderId()), CommonErrorCode.NOT_FOUND);  // 남의 주문

        orderService.confirm(created.orderId());
        orderService.confirm(created.orderId());                                                 // 중복 payment.completed

        assertThat(orderService.get(USER, created.orderId()).status()).isEqualTo("CONFIRMED");
        List<JsonNode> confirmed = events("order.confirmed");
        assertThat(confirmed).hasSize(1);
        assertThat(confirmed.get(0).path("userId").asLong()).isEqualTo(USER);
        assertThat(confirmed.get(0).path("orderId").asLong()).isEqualTo(created.orderId());
        assertThat(confirmed.get(0).path("totalPrice").asInt()).isEqualTo(119000);
        JsonNode lpLine = item(confirmed.get(0), shop.lpOption);
        assertThat(lpLine.path("physicalAlbumId").asLong()).isEqualTo(shop.lpId);                // Member 가 보유 음반에 넣는 키
        assertThat(lpLine.path("name").asText()).isEqualTo("슬로우 다이브 LP");
        assertThat(lpLine.path("qty").asInt()).isEqualTo(2);
        JsonNode goodsLine = item(confirmed.get(0), shop.goodsOption);
        assertThat(goodsLine.path("goodsId").asLong()).isEqualTo(shop.goodsId);
        assertThat(goodsLine.has("physicalAlbumId")).isFalse();
        assertThat(cartService.get(USER).items()).isEmpty();                                     // 산 옵션은 장바구니에서 빠짐
        assertThat(cartService.get(502L).items()).hasSize(1);                                    // 남의 장바구니는 그대로

        OrderDetail refunded = orderService.cancel(USER, created.orderId());
        orderService.refundLocally(created.orderId());                                           // 뒤따라온 payment.cancelled(refunded=true)
        orderService.cancelLocally(created.orderId());

        assertThat(refunded.status()).isEqualTo("REFUNDED");
        assertThat(payment.cancels).containsExactly(created.orderId() + ":USER_CANCEL:true");
        assertThat(stock(shop.lpOption)).isEqualTo(5);                                           // 한 번만 복구
        assertThat(stock(shop.goodsOption)).isEqualTo(3);
        List<JsonNode> refundEvents = events("order.refunded");
        assertThat(refundEvents).hasSize(1);
        assertThat(refundEvents.get(0).path("userId").asLong()).isEqualTo(USER);
        assertThat(refundEvents.get(0).path("orderId").asLong()).isEqualTo(created.orderId());
        assertThat(refundEvents.get(0).path("itemName").asText()).isNotBlank();
        assertThat(refundEvents.get(0).path("itemCount").asInt()).isEqualTo(2);
        assertThat(refundEvents.get(0).path("refundAmount").asInt()).isEqualTo(119000);
    }

    /** 결제는 됐는데 payment.completed 보다 취소가 먼저 — CANCELLED 로 남았다가 payment.cancelled(refunded=true) 가 REFUNDED 로 바로잡습니다 */
    @Test
    void cancelledOrderIsCorrectedToRefundedWithoutRestoringStockTwice() {
        Shop shop = shop();
        long orderId = orderService.create(USER, List.of(new Line(shop.lpOption, 2)), HOME).orderId();

        assertThat(orderService.cancel(USER, orderId).status()).isEqualTo("CANCELLED");
        assertThat(stock(shop.lpOption)).isEqualTo(5);
        assertThat(events("order.refunded")).isEmpty();                                          // 돈이 오가지 않은 취소

        orderService.confirm(orderId);                                                           // 늦게 온 payment.completed — 무시
        orderService.refundLocally(orderId);
        orderService.refundLocally(orderId);

        assertThat(orderService.get(USER, orderId).status()).isEqualTo("REFUNDED");
        assertThat(stock(shop.lpOption)).isEqualTo(5);
        assertThat(events("order.refunded")).hasSize(1);
        assertThat(events("order.confirmed")).isEmpty();
    }

    /** 재고 부족 · 결제 준비 실패 — 차감한 재고를 되돌립니다. 요청 상한도 여기서 막습니다 */
    @Test
    void failedOrdersLeaveStockUntouched() {
        Shop shop = shop();

        assertCode(() -> orderService.create(USER, List.of(new Line(shop.lpOption, 2), new Line(shop.goodsOption, 4)), HOME),
                ProductErrorCode.OUT_OF_STOCK);
        assertThat(stock(shop.lpOption)).isEqualTo(5);                                           // 앞서 차감한 LP 도 롤백
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order", Integer.class)).isZero();

        payment.down = true;
        assertCode(() -> orderService.create(USER, List.of(new Line(shop.lpOption, 2)), HOME), OrderErrorCode.PAYMENT_UNAVAILABLE);
        assertThat(stock(shop.lpOption)).isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT status FROM purchase_order", String.class)).containsExactly("CANCELLED");
        payment.down = false;

        assertCode(() -> orderService.create(USER, List.of(new Line(999_999L, 1)), HOME), ProductErrorCode.OPTION_NOT_FOUND);
        assertCode(() -> orderService.create(USER, List.of(new Line(shop.lpOption, 1), new Line(shop.lpOption, 1)), HOME),
                CommonErrorCode.INVALID_REQUEST);
        assertCode(() -> orderService.create(USER, List.of(new Line(shop.lpOption, 11)), HOME), CommonErrorCode.INVALID_REQUEST);
        assertThat(stock(shop.lpOption)).isEqualTo(5);
    }

    /** 결제하지 않은 주문 — 만료 · 회원 탈퇴 때 무효화. 그 사이 결제가 승인됐으면(APPROVED) 건드리지 않습니다 */
    @Test
    void unpaidOrdersAreVoidedOnExpiryAndWithdrawal() {
        Shop shop = shop();
        long withdrawn = orderService.create(USER, List.of(new Line(shop.lpOption, 1)), HOME).orderId();
        long expired = orderService.create(502L, List.of(new Line(shop.lpOption, 1)), HOME).orderId();
        long paid = orderService.create(503L, List.of(new Line(shop.lpOption, 1)), HOME).orderId();
        assertThat(stock(shop.lpOption)).isEqualTo(2);

        orderService.cancelPendingOf(USER);
        assertThat(orderService.get(USER, withdrawn).status()).isEqualTo("CANCELLED");
        assertThat(payment.cancels).containsExactly(withdrawn + ":USER_WITHDRAWN:false");
        assertThat(stock(shop.lpOption)).isEqualTo(3);

        orderService.expirePending(10);                                                          // 아직 15분이 안 지남
        assertThat(orderService.get(502L, expired).status()).isEqualTo("PENDING");
        jdbc.update("UPDATE purchase_order SET created_at = now() - interval '16 minutes' WHERE id = ?", expired);
        orderService.expirePending(10);
        assertThat(orderService.get(502L, expired).status()).isEqualTo("CANCELLED");
        assertThat(stock(shop.lpOption)).isEqualTo(4);

        payment.cancelStatus = "APPROVED";
        jdbc.update("UPDATE purchase_order SET created_at = now() - interval '16 minutes' WHERE id = ?", paid);
        orderService.expirePending(10);
        assertThat(orderService.get(503L, paid).status()).isEqualTo("PENDING");                  // payment.completed 가 곧 확정
        assertThat(stock(shop.lpOption)).isEqualTo(4);
    }

    // ── 준비 ────────────────────────────────────────────────────────────────────────────────────

    private record Shop(long artistId, long albumId, long lpId, long lpOption, long goodsId, long goodsOption) {}

    /** 아티스트 1 · 앨범 1 · LP 1 (블랙 42,000원 · 5장) · 티셔츠 1 (M 35,000원 · 3장) */
    private Shop shop() {
        long artistId = ingest.upsertArtists(List.of(new ArtistUpsert("crawler", "shop-artist", "h", "파란노을",
                List.of(Genre.SHOEGAZE), "인디레이블", 2018, null, List.of(tshirt("shop-goods", opt("M", null, OptionSize.M, 35000, 3)))))).get(0).id();
        long albumId = ingest.upsertAlbums(List.of(new AlbumUpsert("crawler", "shop-album", "h", artistId, "슬로우 다이브",
                AlbumEntity.Type.ALBUM, List.of(Genre.SHOEGAZE), LocalDate.now(), "https://img/album.jpg", List.of(track(1, "아름다운 세상")),
                List.of(new PhysicalAlbumUpsert("shop-lp", "h", PhysicalFormat.LP, "슬로우 다이브 LP", LocalDate.now(), null, null,
                        List.of(opt("블랙", VinylColor.BLACK, null, 42000, 5))))))).get(0).id();
        long lpId = jdbc.queryForObject("SELECT id FROM product WHERE external_id = 'shop-lp'", Long.class);
        long goodsId = jdbc.queryForObject("SELECT id FROM product WHERE external_id = 'shop-goods'", Long.class);
        return new Shop(artistId, albumId, lpId, optionId(lpId), goodsId, optionId(goodsId));
    }

    /** 같은 아티스트의 굿즈 n 개 — 옵션 id 들 */
    private long[] extraOptions(long artistId, int n) {
        long[] ids = new long[n];
        for (int i = 0; i < n; i++) {
            long productId = jdbc.queryForObject("INSERT INTO product (product_type, name, source, external_id, created_at, updated_at) "
                    + "VALUES ('GOODS', '키링', 'test', ?, now(), now()) RETURNING id", Long.class, "extra-" + i);
            jdbc.update("INSERT INTO goods (id, artist_id, category) VALUES (?, ?, 'KEYRING')", productId, artistId);
            ids[i] = jdbc.queryForObject("INSERT INTO product_option (product_id, name, size, price, created_at) "
                    + "VALUES (?, 'FREE', 'FREE', 8000, now()) RETURNING id", Long.class, productId);
            jdbc.update("INSERT INTO stock (option_id, qty, updated_at) VALUES (?, 5, now())", ids[i]);
        }
        return ids;
    }

    private long artist(String externalId) {
        return ingest.upsertArtists(List.of(new ArtistUpsert("crawler", externalId, "h", "밴드", null, null, null, null, null))).get(0).id();
    }

    private static AlbumUpsert album(String externalId, String hash, long artistId, LocalDate releasedAt, List<TrackUpsert> tracks,
                                     List<PhysicalAlbumUpsert> physicalAlbums) {
        return new AlbumUpsert("crawler", externalId, hash, artistId, "POWER ANDRE 99", AlbumEntity.Type.ALBUM,
                List.of(Genre.PSYCHEDELIC), releasedAt, null, tracks, physicalAlbums);
    }

    private static TrackUpsert track(int no, String title) {
        return new TrackUpsert(no, title, List.of(Genre.PSYCHEDELIC), Tempo.FAST, List.of(Mood.ENERGETIC, Mood.DREAMY),
                List.of(Emotion.FREEDOM), List.of(Style.GUITAR_DRIVEN, Style.SYNTH_DRIVEN));
    }

    private static PhysicalAlbumUpsert lp(String externalId, String hash, LocalDate releasedAt, OptionUpsert... options) {
        return new PhysicalAlbumUpsert(externalId, hash, PhysicalFormat.LP, "POWER ANDRE 99 LP", releasedAt, null, null, List.of(options));
    }

    private static GoodsUpsert tshirt(String externalId, OptionUpsert... options) {
        return new GoodsUpsert(externalId, "h", "로고 티셔츠 (블랙)", GoodsCategory.TSHIRT, null, null, List.of(options));
    }

    private static OptionUpsert opt(String name, VinylColor vinylColor, OptionSize size, int price, Integer qty) {
        return new OptionUpsert(name, vinylColor, size, price, qty);
    }

    private long optionId(long productId) {
        return jdbc.queryForObject("SELECT id FROM product_option WHERE product_id = ?", Long.class, productId);
    }

    private int stock(long optionId) {
        return jdbc.queryForObject("SELECT qty FROM stock WHERE option_id = ?", Integer.class, optionId);
    }

    private List<JsonNode> events(String type) {
        return jdbc.queryForList("SELECT payload::text FROM outbox WHERE event_type = ? ORDER BY id", String.class, type).stream()
                .map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static JsonNode item(JsonNode orderConfirmed, long optionId) {
        for (JsonNode line : orderConfirmed.path("items")) if (line.path("optionId").asLong() == optionId) return line;
        throw new AssertionError("no item for option " + optionId);
    }

    private static void assertCode(ThrowingCallable call, BaseErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
