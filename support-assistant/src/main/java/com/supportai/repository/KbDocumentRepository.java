package com.supportai.repository;

import com.supportai.entity.Classification;
import com.supportai.entity.KbDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    Optional<KbDocument> findBySlugAndCurrentVersionTrue(String slug);

    List<KbDocument> findBySlugOrderByVersionNumberDesc(String slug);

    Page<KbDocument> findByCurrentVersionTrue(Pageable pageable);

    /**
     * Documents this clearance may see.
     *
     * <p>The clearance filter is a query parameter rather than a post-filter, for
     * the same reason it is applied inside retrieval: a filter applied afterwards
     * lets a restricted document consume a slot and then vanish, silently
     * shrinking the result for exactly the users least able to notice.
     */
    @Query("select d from KbDocument d where d.currentVersion = true "
            + "and d.classification in :visible")
    Page<KbDocument> findVisible(@Param("visible") List<Classification> visible,
                                 Pageable pageable);

    @Query("select coalesce(max(d.versionNumber), 0) from KbDocument d where d.slug = :slug")
    int currentVersionNumber(@Param("slug") String slug);

    long countByCurrentVersionTrue();

    Optional<KbDocument> findByContentHash(String contentHash);
}
