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
 * 내부 I4 · I5 · I15 — 수집 CronJob · 시드 스크립트 전용 (X-Internal-Token · Gateway 라우트 없음 → 외부에서는 404).
 * 🔴 X-User-Id 를 보지 않습니다. 운영자 입력(FR-ING-07)도 이 경로로 넣어야 이벤트 · 검색 색인이 함께 갱신됩니다.
 */
@RestController
public class InternalIngestController {

    private final IngestService ingestService;

    public InternalIngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/internal/catalog/artists")
    public ApiResponse<BatchResult> upsertArtists(@Valid @RequestBody ArtistBatch batch) {
        return ApiResponse.onSuccess(new BatchResult(ingestService.upsertArtists(batch.items())));
    }

    @PostMapping("/internal/catalog/albums")
    public ApiResponse<BatchResult> upsertAlbums(@Valid @RequestBody AlbumBatch batch) {
        return ApiResponse.onSuccess(new BatchResult(ingestService.upsertAlbums(batch.items())));
    }

    @PostMapping("/internal/catalog/ingest/diff")
    public ApiResponse<DiffResult> diff(@Valid @RequestBody DiffRequest request) {
        return ApiResponse.onSuccess(ingestService.diff(request));
    }
}
