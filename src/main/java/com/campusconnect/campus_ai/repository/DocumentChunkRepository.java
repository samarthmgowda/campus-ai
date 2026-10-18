package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocument_Id(Long documentId);
    long countByDocument_Id(Long documentId);

    @Query(value = """
        SELECT c.id, c.content, c.page_number, c.section,
               c.document_id, d.title as document_title,
               (c.embedding <=> CAST(:queryEmbedding AS vector)) as distance
        FROM document_chunk c
        JOIN document d ON c.document_id = d.id
        WHERE d.hidden = false
        ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findNearestChunks(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);

    @Query(value = """
        SELECT c.id, c.content, c.page_number, c.section,
               c.document_id, d.title as document_title,
               ts_rank(
                 to_tsvector('english', COALESCE(c.content, '') || ' ' || COALESCE(c.section, '')),
                 plainto_tsquery('english', :queryText)
               ) as rank
        FROM document_chunk c
        JOIN document d ON c.document_id = d.id
        WHERE d.hidden = false
          AND to_tsvector('english', COALESCE(c.content, '') || ' ' || COALESCE(c.section, ''))
              @@ plainto_tsquery('english', :queryText)
        ORDER BY rank DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findKeywordChunks(@Param("queryText") String queryText, @Param("limit") int limit);
}
