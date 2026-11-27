package com.indie.catalog.domain.ingest.controller;

import com.indie.catalog.domain.ingest.dto.request.AlbumBatch;
import com.indie.catalog.domain.ingest.dto.request.ArtistBatch;
import com.indie.catalog.domain.ingest.dto.request.DiffRequest;
import com.indie.catalog.domain.ingest.dto.response.BatchResult;
import com.indie.catalog.domain.ingest.dto.response.DiffResult;
import com.indie.catalog.domain.ingest.service.IngestService;
import com.indie.catalog.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 수집 작업 또는 시드 스크립트 -> Catalog IngestService.
 * 내부 토큰으로 인증하며 적재 시 이벤트 발행과 캐시 삭제를 함께 처리합니다.
 */
@RestController
public class InternalIngestController {

    private final IngestService ingestService;

    public InternalIngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * 아티스트 묶음을 적재합니다.
     *
     * @param batch 적재할 아티스트 묶음
     */
    @PostMapping("/internal/catalog/artists")
    public ApiResponse<BatchResult> upsertArtists(@Valid @RequestBody ArtistBatch batch) {
        return ApiResponse.onSuccess(new BatchResult(ingestService.upsertArtists(batch.items())));
    }

    /**
     * 앨범 묶음을 적재합니다.
     *
     * @param batch 적재할 앨범 묶음
     */
    @PostMapping("/internal/catalog/albums")
    public ApiResponse<BatchResult> upsertAlbums(@Valid @RequestBody AlbumBatch batch) {
        return ApiResponse.onSuccess(new BatchResult(ingestService.upsertAlbums(batch.items())));
    }

    /**
     * 수집 해시와 저장된 해시를 비교합니다.
     *
     * @param request 저장된 해시와 비교할 수집 항목
     */
    @PostMapping("/internal/catalog/ingest/diff")
    public ApiResponse<DiffResult> diff(@Valid @RequestBody DiffRequest request) {
        return ApiResponse.onSuccess(ingestService.diff(request));
    }
}
