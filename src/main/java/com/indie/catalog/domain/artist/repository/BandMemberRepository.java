package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.BandMemberEntity;

import java.util.List;

/** 밴드 멤버 저장소 포트 — 구현은 BandMemberJpaRepository */
public interface BandMemberRepository {

    List<BandMemberEntity> findByArtistIdOrderById(Long artistId);

    void deleteByArtistId(Long artistId);

    BandMemberEntity save(BandMemberEntity member);
}
