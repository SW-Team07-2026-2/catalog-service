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
 * 관리자(Studio) 적재 — 크롤러 → Studio → 여기 (11/17 결정: 크롤러는 Gateway /ingest 로 보내지 않음).
 * 본문 · 결과는 수집 API(I4 · I5 · I15)와 같고 저장 규칙 · 이벤트 · 캐시 삭제도 같은 IngestService 를 씁니다.
 * 토큰은 iss=studio (InternalTokenFilter ⑥ — /internal/admin/** 만). 관리자 터널 규칙 `^/internal/admin/catalog` 로 KT 에서 닿습니다.
 */
@RestController
public class InternalAdminIngestController {

    private final IngestService ingestService;

    public InternalAdminIngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/internal/admin/catalog/ingest/artists")
    public ApiResponse<BatchResult> upsertArtists(@Valid @RequestBody ArtistBatch batch) {
        return ApiResponse.onSuccess(new BatchResult(ingestService.upsertArtists(batch.items())));
    }

    @PostMapping("/internal/admin/catalog/ingest/albums")
    public ApiResponse<BatchResult> upsertAlbums(@Valid @RequestBody AlbumBatch batch) {
        return ApiResponse.onSuccess(new BatchResult(ingestService.upsertAlbums(batch.items())));
    }

    @PostMapping("/internal/admin/catalog/ingest/diff")
    public ApiResponse<DiffResult> diff(@Valid @RequestBody DiffRequest request) {
        return ApiResponse.onSuccess(ingestService.diff(request));
    }
}
