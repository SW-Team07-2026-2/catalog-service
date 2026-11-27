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
 * 크롤러 -> Studio 승인 -> Catalog 내부 관리자 API -> IngestService.
 * 관리자 내부 토큰으로 인증하며 일반 수집 API와 같은 저장 규칙을 사용합니다.
 */
@RestController
public class InternalAdminIngestController {

    private final IngestService ingestService;

    public InternalAdminIngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * Studio에서 승인한 아티스트 묶음을 적재합니다.
     *
     * @param batch 적재할 아티스트 묶음
     */
    @PostMapping("/internal/admin/catalog/ingest/artists")
    public ApiResponse<BatchResult> upsertArtists(@Valid @RequestBody ArtistBatch batch) {
        return ApiResponse.onSuccess(new BatchResult(ingestService.upsertArtists(batch.items())));
    }

    /**
     * Studio에서 승인한 앨범 묶음을 적재합니다.
     *
     * @param batch 적재할 앨범 묶음
     */
    @PostMapping("/internal/admin/catalog/ingest/albums")
    public ApiResponse<BatchResult> upsertAlbums(@Valid @RequestBody AlbumBatch batch) {
        return ApiResponse.onSuccess(new BatchResult(ingestService.upsertAlbums(batch.items())));
    }

    /**
     * 수집 해시와 저장된 해시를 비교합니다.
     *
     * @param request 저장된 해시와 비교할 수집 항목
     */
    @PostMapping("/internal/admin/catalog/ingest/diff")
    public ApiResponse<DiffResult> diff(@Valid @RequestBody DiffRequest request) {
        return ApiResponse.onSuccess(ingestService.diff(request));
    }
}
