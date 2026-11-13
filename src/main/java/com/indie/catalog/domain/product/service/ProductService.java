package com.indie.catalog.domain.product.service;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.repository.AlbumRepository;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.OptionView;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.entity.ProductOptionEntity;
import com.indie.catalog.domain.product.entity.StockEntity;
import com.indie.catalog.domain.product.entity.VinylColor;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.domain.product.repository.GoodsRepository;
import com.indie.catalog.domain.product.repository.PhysicalAlbumRepository;
import com.indie.catalog.domain.product.repository.ProductOptionRepository;
import com.indie.catalog.domain.product.repository.ProductRepository;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 상품(피지컬 앨범 · 굿즈) · 옵션 · 재고 조회와 옵션 입고 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final PhysicalAlbumRepository physicalAlbumRepository;
    private final GoodsRepository goodsRepository;
    private final ProductOptionRepository optionRepository;
    private final StockRepository stockRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, PhysicalAlbumRepository physicalAlbumRepository,
                          GoodsRepository goodsRepository, ProductOptionRepository optionRepository,
                          StockRepository stockRepository, AlbumRepository albumRepository,
                          ArtistRepository artistRepository, Clock clock) {
        this.productRepository = productRepository;
        this.physicalAlbumRepository = physicalAlbumRepository;
        this.goodsRepository = goodsRepository;
        this.optionRepository = optionRepository;
        this.stockRepository = stockRepository;
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.clock = clock;
    }

    /** C8 — 상품 + 옵션별 가격 · 재고. 🔴 캐시하지 않습니다 (재고는 주문마다 바뀜) */
    @Transactional(readOnly = true)
    public ProductDetail product(long id) {
        ProductEntity product = productRepository.findById(id).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        return details(List.of(product)).get(0);
    }

    /** ?ids= — 보유 음반 화면 (Member 콜렉션은 physicalAlbumId · optionId 만 들고 있음) · 챗봇 도구 */
    @Transactional(readOnly = true)
    public List<ProductDetail> products(List<Long> ids) {
        return details(productRepository.findAllById(ids));
    }

    /** C7 — 굿즈 목록 (최신 등록순) */
    @Transactional(readOnly = true)
    public PageResult<GoodsItem> goods(Long artistId, GoodsCategory category, int page, int size) {
        return PageResult.of(goodsRepository.findLatest(artistId, category, (long) page * size, size + 1), page, size,
                this::goodsItems);
    }

    @Transactional(readOnly = true)
    public List<GoodsItem> goodsOfArtist(long artistId) {
        return goodsItems(goodsRepository.findByArtistId(artistId));
    }

    /** C6 — 피지컬 앨범 발매 목록 (발매일 기준 · FR-CAT-12). 매체 · 구간을 안 주면 전체 */
    @Transactional(readOnly = true)
    public PageResult<PhysicalAlbumItem> physicalAlbums(PhysicalFormat format, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "from 은 to 보다 이후일 수 없습니다");
        }
        return PageResult.of(physicalAlbumRepository.findReleased(format, from, to, (long) page * size, size + 1), page, size,
                this::physicalAlbumItems);
    }

    @Transactional(readOnly = true)
    public List<PhysicalAlbumItem> physicalAlbumsOfAlbums(Collection<Long> albumIds) {
        return albumIds.isEmpty() ? List.of() : physicalAlbumItems(physicalAlbumRepository.findByAlbumIdIn(albumIds));
    }

    /** 옵션 id → (옵션, 상품) — 장바구니 · 주문이 옵션에서 상품으로 거슬러 올라갈 때. 없는 옵션은 맵에 없습니다 */
    @Transactional(readOnly = true)
    public Map<Long, Sellable> sellables(Collection<Long> optionIds) {
        List<ProductOptionEntity> options = optionRepository.findAllById(optionIds);
        Map<Long, ProductEntity> products = productRepository.findAllById(
                        options.stream().map(ProductOptionEntity::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        return options.stream().collect(Collectors.toMap(ProductOptionEntity::getId,
                o -> new Sellable(o, products.get(o.getProductId()))));
    }

    /**
     * 옵션 입고 — 수집 적재(IngestService)와 관리자 등록(AdminProductService)이 부릅니다. (product_id, 옵션 이름) 이 같으면 갱신, 새 이름이면 새 옵션.
     * 관리자는 옵션 id 를 줄 수 있습니다 — 그 옵션을 고치고 이름도 바꿉니다 (다른 상품의 옵션이면 404 · 이미 있는 이름이면 400).
     * 요청에 없는 옵션은 그대로 둡니다 (주문 · 보유 음반이 가리킬 수 있어 지우지 않습니다).
     * qty 는 입고 수량(절대값) — null 이면 수량은 건드리지 않습니다. 새 옵션의 재고 행은 항상 만듭니다 (옵션과 1:1).
     *
     * @param format 피지컬 앨범의 매체 · 굿즈면 null (OptionRule)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OptionsResult upsertOptions(long productId, PhysicalFormat format, List<OptionInput> inputs) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, ProductOptionEntity> byName = optionRepository.findByProductIdOrderById(productId).stream()
                .collect(Collectors.toMap(ProductOptionEntity::getName, Function.identity()));
        if (inputs.stream().map(OptionInput::name).distinct().count() != inputs.size()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "옵션 이름이 중복됩니다");
        }
        boolean changed = false;
        List<ProductOptionEntity> created = new ArrayList<>();
        Map<Long, ProductOptionEntity> byId = byName.values().stream()
                .collect(Collectors.toMap(ProductOptionEntity::getId, Function.identity()));
        Set<String> freed = new HashSet<>();
        for (OptionInput input : inputs) {
            OptionRule.check(format, input.name(), input.vinylColor(), input.size());
            ProductOptionEntity option = byName.get(input.name());
            if (input.id() != null) {
                ProductOptionEntity target = byId.get(input.id());
                if (target == null) throw new GeneralException(ProductErrorCode.OPTION_NOT_FOUND);
                if (option != null && option != target) {
                    throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "옵션 이름 '" + input.name() + "' 이 이미 있습니다");
                }
                if (option == null) {
                    freed.add(target.getName());
                    byName.remove(target.getName());
                    target.rename(input.name());
                    byName.put(input.name(), target);
                    changed = true;
                }
                option = target;
            }
            boolean isNew = option == null;
            if (isNew && freed.contains(input.name())) {   // INSERT 가 이름 변경(UPDATE)보다 먼저 나가 UNIQUE 에 걸립니다
                throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "같은 요청에서 바꾼 옵션 이름 '" + input.name() + "' 은 다시 쓸 수 없습니다");
            }
            if (isNew) option = new ProductOptionEntity(productId, input.name(), now);
            changed |= option.apply(input.vinylColor(), input.size(), input.price()) || isNew;
            option = optionRepository.save(option);
            if (isNew) {
                created.add(option);
                stockRepository.save(new StockEntity(option.getId(), input.qty() == null ? 0 : input.qty(), now));
            } else if (input.qty() != null) {
                // 수량을 덮어쓰는 동안 주문 차감과 엇갈리지 않게 잠급니다
                stockRepository.findForUpdate(option.getId()).orElseThrow().restockTo(input.qty(), now);
            }
        }
        return new OptionsResult(changed, created);
    }

    private List<ProductDetail> details(List<ProductEntity> products) {
        if (products.isEmpty()) return List.of();
        Map<Long, List<ProductOptionEntity>> options = optionsOf(products);
        Map<Long, StockEntity> stocks = stockRepository.findAllById(
                        options.values().stream().flatMap(List::stream).map(ProductOptionEntity::getId).toList())
                .stream().collect(Collectors.toMap(StockEntity::getOptionId, Function.identity()));
        Map<Long, AlbumEntity> albums = albumsOf(products);
        Map<Long, String> artistNames = artistNames(products, albums);
        return products.stream().map(p -> {
            List<OptionView> views = options.getOrDefault(p.getId(), List.of()).stream()
                    .map(o -> optionView(o, Optional.ofNullable(stocks.get(o.getId())))).toList();
            if (p instanceof PhysicalAlbumEntity pa) {
                AlbumEntity album = albums.get(pa.getAlbumId());
                return new ProductDetail(pa.getId(), pa.getProductType(), pa.getName(), pa.getThumbnailUrl(), pa.getPurchaseUrl(),
                        album.getArtistId(), artistNames.get(album.getArtistId()), album.getId(), album.getTitle(),
                        pa.getFormat(), pa.getReleasedAt(), null, views);
            }
            GoodsEntity goods = (GoodsEntity) p;
            return new ProductDetail(goods.getId(), goods.getProductType(), goods.getName(), goods.getThumbnailUrl(),
                    goods.getPurchaseUrl(), goods.getArtistId(), artistNames.get(goods.getArtistId()), null, null,
                    null, null, goods.getCategory(), views);
        }).toList();
    }

    private List<GoodsItem> goodsItems(List<GoodsEntity> goods) {
        Map<Long, String> artistNames = artistNames(goods, Map.of());
        Map<Long, Integer> minPrices = minPrices(goods);
        return goods.stream().map(g -> new GoodsItem(g.getId(), g.getArtistId(), artistNames.get(g.getArtistId()), g.getName(),
                g.getCategory(), g.getThumbnailUrl(), minPrices.get(g.getId()))).toList();
    }

    private List<PhysicalAlbumItem> physicalAlbumItems(List<PhysicalAlbumEntity> physicalAlbums) {
        Map<Long, AlbumEntity> albums = albumsOf(physicalAlbums);
        Map<Long, String> artistNames = artistNames(physicalAlbums, albums);
        Map<Long, Integer> minPrices = minPrices(physicalAlbums);
        return physicalAlbums.stream().map(pa -> {
            AlbumEntity album = albums.get(pa.getAlbumId());
            return new PhysicalAlbumItem(pa.getId(), album.getId(), album.getTitle(), album.getArtistId(),
                    artistNames.get(album.getArtistId()), pa.getFormat(), pa.getName(), pa.getReleasedAt(),
                    pa.getThumbnailUrl(), minPrices.get(pa.getId()));
        }).toList();
    }

    private Map<Long, List<ProductOptionEntity>> optionsOf(List<? extends ProductEntity> products) {
        if (products.isEmpty()) return Map.of();
        return optionRepository.findByProductIdInOrderById(products.stream().map(ProductEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(ProductOptionEntity::getProductId));
    }

    private Map<Long, Integer> minPrices(List<? extends ProductEntity> products) {
        Map<Long, Integer> min = new HashMap<>();
        optionsOf(products).forEach((productId, options) ->
                min.put(productId, options.stream().mapToInt(ProductOptionEntity::getPrice).min().orElseThrow()));
        return min;
    }

    /** 피지컬 앨범이 가리키는 앨범 — albumId → 앨범 */
    private Map<Long, AlbumEntity> albumsOf(List<? extends ProductEntity> products) {
        List<Long> albumIds = products.stream().filter(PhysicalAlbumEntity.class::isInstance)
                .map(p -> ((PhysicalAlbumEntity) p).getAlbumId()).distinct().toList();
        if (albumIds.isEmpty()) return Map.of();
        return albumRepository.findAllById(albumIds).stream().collect(Collectors.toMap(AlbumEntity::getId, Function.identity()));
    }

    /** 굿즈는 아티스트를 바로, 피지컬 앨범은 앨범을 거쳐 — artistId → 이름 */
    private Map<Long, String> artistNames(List<? extends ProductEntity> products, Map<Long, AlbumEntity> albums) {
        List<Long> artistIds = products.stream()
                .map(p -> p instanceof GoodsEntity g ? g.getArtistId() : albums.get(((PhysicalAlbumEntity) p).getAlbumId()).getArtistId())
                .distinct().toList();
        return artistRepository.findAllById(artistIds).stream().collect(Collectors.toMap(ArtistEntity::getId, ArtistEntity::getName));
    }

    private static OptionView optionView(ProductOptionEntity o, Optional<StockEntity> stock) {
        int qty = stock.map(StockEntity::getQty).orElse(0);
        return new OptionView(o.getId(), o.getName(), o.getVinylColor(), o.getSize(), o.getPrice(), qty, qty == 0,
                stock.map(StockEntity::getUpdatedAt).orElse(null));
    }

    public record Sellable(ProductOptionEntity option, ProductEntity product) {}

    /** id — 관리자가 기존 옵션을 가리킬 때만 (수집은 null · 이름이 식별자) */
    public record OptionInput(Long id, String name, VinylColor vinylColor, OptionSize size, int price, Integer qty) {}

    /** created — 이번에 새로 생긴 옵션 (피지컬 앨범 발매 알림에 싣습니다) */
    public record OptionsResult(boolean changed, List<ProductOptionEntity> created) {}
}
