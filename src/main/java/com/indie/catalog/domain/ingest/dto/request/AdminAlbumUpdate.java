package com.indie.catalog.domain.ingest.dto.request;

import com.indie.catalog.domain.album.entity.Emotion;
import com.indie.catalog.domain.album.entity.Mood;
import com.indie.catalog.domain.album.entity.Style;
import com.indie.catalog.domain.album.entity.Tempo;
import com.indie.catalog.domain.artist.entity.Genre;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 관리자 앨범 수정 - 이미지, 트랙 태그 (: 둘 다 관리자가 넣는 값). 보낸 값만 바꿉니다 (null 은 그대로).
 * tracks 는 바꿀 트랙만, 트랙 안에서도 보낸 칸만 (빈 배열 = 지움). 트랙 추가, 삭제, 제목은 수집으로만 바뀝니다.
 * description: 소개 글 (빈 글은 지움).
 */
public record AdminAlbumUpdate(@Size(max = 512) String thumbnailUrl,
                               @Size(max = 100) List<@Valid @NotNull TrackTags> tracks,
                               @Size(max = 1000) String description) {

    public record TrackTags(@NotNull @Positive Integer trackNo,
                            List<@NotNull Genre> genres,
                            Tempo tempo,
                            List<@NotNull Mood> moods,
                            List<@NotNull Emotion> emotions,
                            List<@NotNull Style> styles) {}
}
