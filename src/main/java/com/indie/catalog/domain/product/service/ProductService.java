package com.indie.catalog.domain.product.service;

import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.ProductView;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.entity.StockEntity;
import com.indie.catalog.domain.product.repository.GoodsRepository;
import com.indie.catalog.domain.product.repository.ProductRepository;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.PageResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 상품 · 재고 · 굿즈 조회와 입고(판매 정보 upsert) */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final GoodsRepository goodsRepository;
    private final ArtistRepository artistRepository;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, StockRepository stockRepository,
                          GoodsRepository goodsRepository, ArtistRepository artistRepository, Clock clock) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.goodsRepository = goodsRepository;
        this.artistRepository = artistRepository;
        this.clock = clock;
    }

    /** C8 — 가격 · 재고. 🔴 캐시하지 않습니다 (재고는 주문마다 바뀜) */
    @Transactional(readOnly = true)
    public ProductView product(long id) {
        ProductEntity p = productRepository.findById(id).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        return view(p, stockRepository.findById(id));
    }

    /** ?ids= — 위시리스트 화면 (Member 위시리스트는 productId 를 들고 있음) · I6 어시스턴트 도구 */
    @Transactional(readOnly = true)
    public List<ProductView> products(List<Long> ids) {
        Map<Long, StockEntity> stocks = stockRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StockEntity::getProductId, Function.identity()));
        return productRepository.findAllById(ids).stream()
                .map(p -> view(p, Optional.ofNullable(stocks.get(p.getId()))))
                .toList();
    }

    /** C7 — 굿즈 목록 (최신 등록순) */
    @Transactional(readOnly = true)
    public PageResult<GoodsItem> goods(Long artistId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Slice<GoodsEntity> slice = artistId == null
                ? goodsRepository.findLatest(pageable) : goodsRepository.findLatestByArtist(artistId, pageable);
        return PageResult.of(slice, this::goodsItems);
    }

    @Transactional(readOnly = true)
    public List<GoodsItem> goodsOfArtist(long artistId) {
        return goodsItems(goodsRepository.findByArtistIdOrderById(artistId));
    }

    /** refId → 상품. 판매하지 않는 항목은 맵에 없습니다 */
    @Transactional(readOnly = true)
    public Map<Long, ProductEntity> productsByRef(ProductEntity.Type type, Collection<Long> refIds) {
        if (refIds.isEmpty()) return Map.of();
        return productRepository.findByProductTypeAndRefIdIn(type, refIds).stream()
                .collect(Collectors.toMap(ProductEntity::getRefId, Function.identity()));
    }

    /**
     * 입고 — 판매 정보(가격 · 링크 · 수량) upsert. 내부 upsert(I4 · I5)만 부릅니다.
     * qty 가 null 이면 수량은 건드리지 않습니다. 기존 재고가 0 이었는데 N 이 되면 restocked = true.
     * 처음 만드는 상품은 재입고가 아닙니다 (신규 상품 — 앨범이면 album.released 가 따로 나감).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SaleResult upsertSale(ProductEntity.Type type, long refId, String name, String thumbnailUrl, Sale sale) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ProductEntity product = productRepository.findByProductTypeAndRefId(type, refId)
                .orElseGet(() -> new ProductEntity(type, refId));
        boolean created = product.getId() == null;
        boolean changed = product.apply(name, sale.price(), thumbnailUrl, sale.purchaseUrl());
        product = productRepository.save(product);

        boolean restocked = false;
        Integer qty = null;
        if (sale.qty() != null) {
            Optional<StockEntity> stock = stockRepository.findForUpdate(product.getId());
            if (stock.isPresent()) {
                restocked = stock.get().restockTo(sale.qty(), now);
            } else {
                stockRepository.save(new StockEntity(product.getId(), sale.qty(), now));
            }
            qty = sale.qty();
        }
        return new SaleResult(product, created || changed, restocked, qty);
    }

    private List<GoodsItem> goodsItems(List<GoodsEntity> goods) {
        Map<Long, String> artistNames = artistRepository.findAllById(goods.stream().map(GoodsEntity::getArtistId).distinct().toList())
                .stream().collect(Collectors.toMap(ArtistEntity::getId, ArtistEntity::getName));
        Map<Long, ProductEntity> products = productsByRef(ProductEntity.Type.GOODS, goods.stream().map(GoodsEntity::getId).toList());
        return goods.stream().map(g -> {
            ProductEntity p = products.get(g.getId());
            return new GoodsItem(g.getId(), g.getArtistId(), artistNames.get(g.getArtistId()), g.getName(), g.getCategory(),
                    p == null ? null : p.getId(), p == null ? null : p.getPrice(), p == null ? null : p.getThumbnailUrl());
        }).toList();
    }

    private static ProductView view(ProductEntity p, Optional<StockEntity> stock) {
        int qty = stock.map(StockEntity::getQty).orElse(0);
        return new ProductView(p.getId(), p.getProductType().name(), p.getRefId(), p.getName(), p.getPrice(),
                p.getThumbnailUrl(), p.getPurchaseUrl(), qty, qty == 0, stock.map(StockEntity::getUpdatedAt).orElse(null));
    }

    public record Sale(int price, Integer qty, String purchaseUrl) {}

    public record SaleResult(ProductEntity product, boolean changed, boolean restocked, Integer qty) {}
}
