package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.SourceHash;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 아티스트 저장소 포트 - 구현은 ArtistRepositoryAdapter. (source, external_id) 가 수집 멱등 upsert 기준 */
public interface ArtistRepository {

    Optional<ArtistEntity> findById(Long id);

    List<ArtistEntity> findAllById(Iterable<Long> ids);

    Optional<ArtistEntity> findBySourceAndExternalId(String source, String externalId);

    /** 이름 부분 일치(대소문자 무시), 장르 하나라도 포함, ids 안 - 모두 선택(null, 빈 목록). sort 없으면 이름순 */
    /** genders, compositions - 프론트 성별, 구성 필터 (여러 개면 OR, 비우면 조건 없음) */
    List<ArtistEntity> search(String nameContains, List<Genre> genres, List<ArtistGender> genders, List<ArtistComposition> compositions,
                              List<Long> ids, ListSort sort, long offset, int limit);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    /**
     * 팔로우 탭 업데이트순 - 아티스트별 가장 최근 콘텐츠 날짜 (앨범 발매일, 피지컬 발매일, 굿즈 등록 시각 중 가장 늦은 것).
     * 발매일(DATE)은 서울 기준 그날 0시. 콘텐츠가 없는 아티스트는 빠집니다
     */
    Map<Long, OffsetDateTime> findLatestContentAt(Collection<Long> artistIds);

    ArtistEntity save(ArtistEntity artist);

    /** 팔로워 수 사본 갱신 (artistId -> 팔로워 수) - 값이 바뀐 아티스트만, updated_at 은 그대로. 바뀐 아티스트 ID */
    List<Long> updateFollowerCounts(Map<Long, Long> counts);

    /** 전체 다시 맞추기용 - id 오름차순, afterId 다음부터 limit 개 */
    List<Long> findIdsAfter(long afterId, int limit);
}
