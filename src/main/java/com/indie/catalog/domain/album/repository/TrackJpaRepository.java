package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.TrackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackJpaRepository extends JpaRepository<TrackEntity, Long>, TrackRepository {

    @Override
    @Modifying
    @Query("delete from TrackEntity t where t.albumId = :albumId")
    void deleteByAlbumId(@Param("albumId") Long albumId);
}
