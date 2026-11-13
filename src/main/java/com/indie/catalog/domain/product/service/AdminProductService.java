package com.indie.catalog.domain.product.service;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.repository.AlbumRepository;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.exception.ArtistErrorCode;
import com.indie.catalog.domain.artist.repository.ArtistCache;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.ingest.dto.event.CatalogEvents;
import com.indie.catalog.domain.product.dto.request.AdminOption;
import com.indie.catalog.domain.product.dto.request.AdminProductCreate;
import com.indie.catalog.domain.product.dto.request.AdminProductUpdate;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.dto.response.StockView;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.entity.StockEntity;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.domain.product.repository.GoodsRepository;
import com.indie.catalog.domain.product.repository.PhysicalAlbumRepository;
import com.indie.catalog.domain.product.repository.ProductOptionRepository;
import com.indie.catalog.domain.product.repository.ProductRepository;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.config.CatalogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 관리자(Studio)가 직접 넣는 상품 · 옵션 · 재고. 옵션 규칙(OptionRule)과 발매 알림 이벤트는 수집 적재(IngestService)와 같습니다.
 * 직접 넣은 상품의 출처는 source = "studio" 이고 external_id 는 여기서 만듭니다 — 수집기가 같은 상품을 덮어쓰지 않습니다.
 * 🔴 수집으로 들어온 상품을 여기서 고치면, 수집기가 그 상품의 원본이 바뀐 것을 다음에 가져올 때 다시 덮어씁니다.
 */
@Service
public class AdminProductService {

    public static final String SOURCE = "studio";
    private static final Logger log = LoggerFactory.getLogger(AdminProductService.class);

    private final ProductRepository productRepository;
    private final GoodsRepository goodsRepository;
    private final PhysicalAlbumRepository physicalAlbumRepository;
    private final ProductOptionRepository optionRepository;
    private final StockRepository stockRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final ProductService productService;
    private final CatalogEvents events;
    private final ArtistCache artistCache;
    private final int releaseWindowDays;
    private final Clock clock;

    public AdminProductService(ProductRepository productRepository, GoodsRepository goodsRepository,
                               PhysicalAlbumRepository physicalAlbumRepository, ProductOptionRepository optionRepository,
                               StockRepository stockRepository, AlbumRepository albumRepository,
                               ArtistRepository artistRepository, ProductService productService, CatalogEvents events,
                               ArtistCache artistCache, CatalogProperties props, Clock clock) {
        this.productRepository = productRepository;
        this.goodsRepository = goodsRepository;
        this.physicalAlbumRepository = physicalAlbumRepository;
        this.optionRepository = optionRepository;
        this.stockRepository = stockRepository;
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.productService = productService;
        this.events = events;
        this.artistCache = artistCache;
        this.releaseWindowDays = props.event().releaseWindowDays();
        this.clock = clock;
    }

    /** 상품 등록 — 굿즈는 goods.released, 피지컬 앨범은 발매일이 최근이거나 없을 때 physical_album.released 를 냅니다 (수집과 같은 규칙) */
    @Transactional
    public ProductDetail create(AdminProductCreate r) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String externalId = UUID.randomUUID().toString();
        List<ProductService.OptionInput> options = inputs(r.options());
        long id;
        if (r.productType() == ProductEntity.Type.GOODS) {
            require(r.artistId() != null && r.category() != null, "굿즈에는 artistId · category 가 필요합니다");
            require(r.albumId() == null && r.format() == null && r.releasedAt() == null,
                    "굿즈에는 albumId · format · releasedAt 을 쓸 수 없습니다");
            ArtistEntity artist = artist(r.artistId());
            GoodsEntity goods = new GoodsEntity(SOURCE, externalId);
            goods.apply(artist.getId(), r.category(), r.name().strip(), r.thumbnailUrl(), r.purchaseUrl(), null, now);
            goods = goodsRepository.save(goods);
            productService.upsertOptions(goods.getId(), null, options);
            events.goodsReleased(goods, artist, r.options().stream().mapToInt(AdminOption::price).min().orElseThrow());
            artistCache.evictAfterCommit(artist.getId());
            id = goods.getId();
        } else {
            require(r.albumId() != null && r.format() != null, "피지컬 앨범에는 albumId · format 이 필요합니다");
            require(r.artistId() == null && r.category() == null, "피지컬 앨범에는 artistId · category 를 쓸 수 없습니다");
            AlbumEntity album = albumRepository.findById(r.albumId())
                    .orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND, "앨범 " + r.albumId() + " 이 없습니다"));
            ArtistEntity artist = artist(album.getArtistId());
            PhysicalAlbumEntity physicalAlbum = new PhysicalAlbumEntity(SOURCE, externalId);
            physicalAlbum.apply(album.getId(), r.format(), r.releasedAt(), r.name().strip(),
                    r.thumbnailUrl() == null ? album.getThumbnailUrl() : r.thumbnailUrl(), r.purchaseUrl(), null, now);
            physicalAlbum = physicalAlbumRepository.save(physicalAlbum);
            ProductService.OptionsResult created = productService.upsertOptions(physicalAlbum.getId(), r.format(), options);
            if (r.releasedAt() == null || !r.releasedAt().isBefore(LocalDate.now(clock).minusDays(releaseWindowDays))) {
                events.physicalAlbumReleased(physicalAlbum, album, artist, created.created());
            }
            artistCache.evictAfterCommit(artist.getId());
            id = physicalAlbum.getId();
        }
        return productService.product(id);
    }

    /** 상품 수정 — 보낸 값만. 매체(format) · 연결된 앨범 · 아티스트는 바꾸지 않습니다 (옵션 규칙과 주문 이력이 거기에 묶여 있습니다) */
    @Transactional
    public ProductDetail update(long id, AdminProductUpdate r) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ProductEntity product = product(id);
        String name = r.name() == null ? product.getName() : r.name().strip();
        String thumbnailUrl = r.thumbnailUrl() == null ? product.getThumbnailUrl() : r.thumbnailUrl();
        String purchaseUrl = r.purchaseUrl() == null ? product.getPurchaseUrl() : r.purchaseUrl();
        if (product instanceof GoodsEntity goods) {
            require(r.releasedAt() == null, "굿즈에는 releasedAt 을 쓸 수 없습니다");
            goods.apply(goods.getArtistId(), r.category() == null ? goods.getCategory() : r.category(), name, thumbnailUrl,
                    purchaseUrl, goods.getSourceHash(), now);
        } else {
            PhysicalAlbumEntity physicalAlbum = (PhysicalAlbumEntity) product;
            require(r.category() == null, "피지컬 앨범에는 category 를 쓸 수 없습니다");
            physicalAlbum.apply(physicalAlbum.getAlbumId(), physicalAlbum.getFormat(),
                    r.releasedAt() == null ? physicalAlbum.getReleasedAt() : r.releasedAt(), name, thumbnailUrl, purchaseUrl,
                    physicalAlbum.getSourceHash(), now);
        }
        artistCache.evictAfterCommit(artistIdOf(product));
        return productService.product(id);
    }

    /** 옵션 추가 · 수정 — 요청에 없는 옵션은 그대로 둡니다 (주문 · 보유 음반이 가리킬 수 있어 지우지 않습니다). 새 옵션의 재고는 0 입니다 */
    @Transactional
    public ProductDetail options(long id, List<AdminOption> options) {
        ProductEntity product = product(id);
        PhysicalFormat format = product instanceof PhysicalAlbumEntity pa ? pa.getFormat() : null;
        productService.upsertOptions(id, format, options.stream()
                .map(o -> new ProductService.OptionInput(o.id(), o.name().strip(), o.vinylColor(), o.size(), o.price(), null)).toList());
        artistCache.evictAfterCommit(artistIdOf(product));
        return productService.product(id);
    }

    /** 재고 조정 — 주문 차감과 같은 조건부 UPDATE 라 동시에 주문이 들어와도 0 아래로 내려가지 않습니다 (모자라면 409 OUT_OF_STOCK) */
    @Transactional
    public StockView adjustStock(long optionId, int delta, String reason, String adminId) {
        if (delta == 0) throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "delta 는 0 일 수 없습니다");
        optionRepository.findById(optionId).orElseThrow(() -> new GeneralException(ProductErrorCode.OPTION_NOT_FOUND));
        int updated = delta > 0 ? stockRepository.restore(optionId, delta) : stockRepository.decrement(optionId, -delta);
        if (updated == 0) throw new GeneralException(ProductErrorCode.OUT_OF_STOCK, "재고가 조정하려는 수량보다 적습니다");
        StockEntity stock = stockRepository.findAllById(List.of(optionId)).get(0);
        log.info("stock_adjusted optionId={} delta={} qty={} adminId={} reason={}", optionId, delta, stock.getQty(), adminId, reason);
        return new StockView(optionId, stock.getQty(), stock.getUpdatedAt());
    }

    private ProductEntity product(long id) {
        return productRepository.findById(id).orElseThrow(() -> new GeneralException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private ArtistEntity artist(long id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new GeneralException(ArtistErrorCode.ARTIST_NOT_FOUND, "아티스트 " + id + " 가 없습니다"));
    }

    private long artistIdOf(ProductEntity product) {
        return product instanceof GoodsEntity goods ? goods.getArtistId()
                : albumRepository.findById(((PhysicalAlbumEntity) product).getAlbumId()).orElseThrow().getArtistId();
    }

    private static List<ProductService.OptionInput> inputs(List<AdminOption> options) {
        return options.stream().map(o -> {
            require(o.id() == null, "등록할 때는 옵션 id 를 쓸 수 없습니다");
            return new ProductService.OptionInput(null, o.name().strip(), o.vinylColor(), o.size(), o.price(), o.qty());
        }).toList();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GeneralException(CommonErrorCode.INVALID_REQUEST, message);
    }
}
