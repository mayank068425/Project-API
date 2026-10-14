package com.supportai.repository;

import com.supportai.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByOrdinalAsc(Long documentId);

    List<DocumentChunk> findByIdIn(List<Long> ids);

    /**
     * Removes every chunk of a superseded document.
     *
     * <p>Called when a new version lands. Skipping this is the quiet failure of
     * every RAG system shipped without thinking about updates: the old chunks keep
     * matching, keep scoring well, and keep carrying a citation that resolves to a
     * real document.
     */
    @Modifying
    @Query("delete from DocumentChunk c where c.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);

    long countByDocumentId(Long documentId);

    @Query("select count(c) from DocumentChunk c where c.completeProcedure = false")
    long countPartialProcedures();
}
