package com.indie.catalog.domain.album.dto.response;

import com.indie.catalog.domain.album.entity.Emotion;
import com.indie.catalog.domain.album.entity.Mood;
import com.indie.catalog.domain.album.entity.Style;
import com.indie.catalog.domain.album.entity.Tempo;
import com.indie.catalog.domain.artist.entity.Genre;

import java.util.List;

/** id 는 Member 행동 메트릭(TRACK)의 targetId 로 씁니다. tempo 는 모르면 null, 나머지는 빈 배열 */
public record TrackItem(long id, int trackNo, String title, List<Genre> genres, Tempo tempo, List<Mood> moods,
                        List<Emotion> emotions, List<Style> styles) {}
