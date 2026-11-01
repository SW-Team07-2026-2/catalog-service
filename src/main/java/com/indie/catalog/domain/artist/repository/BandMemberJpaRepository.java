package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.BandMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BandMemberJpaRepository extends JpaRepository<BandMemberEntity, Long>, BandMemberRepository {

    @Override
    @Modifying
    @Query("delete from BandMemberEntity m where m.artistId = :artistId")
    void deleteByArtistId(@Param("artistId") Long artistId);
}
