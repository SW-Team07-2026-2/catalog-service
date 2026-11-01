package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.global.common.util.SourceHash;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArtistJpaRepository extends JpaRepository<ArtistEntity, Long>, JpaSpecificationExecutor<ArtistEntity>,
        ArtistRepository {

    @Override
    default Page<ArtistEntity> search(String nameContains, String genre, Pageable pageable) {
        return findAll(Specification.where(nameContains(nameContains)).and(genreEquals(genre)), pageable);
    }

    @Override
    @Query("select new com.indie.catalog.global.common.util.SourceHash(a.externalId, a.sourceHash) "
            + "from ArtistEntity a where a.source = :source and a.externalId in :ids")
    List<SourceHash> findHashes(@Param("source") String source, @Param("ids") Collection<String> externalIds);

    private static Specification<ArtistEntity> nameContains(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }

    private static Specification<ArtistEntity> genreEquals(String genre) {
        if (genre == null || genre.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("genre"), genre);
    }
}
