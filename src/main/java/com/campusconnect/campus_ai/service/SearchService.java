package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.repository.DocumentChunkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SearchService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final boolean rerankEnabled;
    private final int rerankPool;

    // Reciprocal-rank-fusion constant (standard k=60) and per-branch weights.
    private static final double RRF_K = 60.0;
    private final double semanticWeight;
    private final double keywordWeight;

    public SearchService(DocumentChunkRepository chunkRepository,
                         EmbeddingService embeddingService,
                         RerankService rerankService,
                         @Value("${rag.rerank-enabled:true}") boolean rerankEnabled,
                         @Value("${rag.rerank-pool:10}") int rerankPool,
                         @Value("${rag.fusion.semantic-weight:0.5}") double semanticWeight,
                         @Value("${rag.fusion.keyword-weight:0.5}") double keywordWeight) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
        this.rerankEnabled = rerankEnabled;
        this.rerankPool = rerankPool;
        this.semanticWeight = semanticWeight;
        this.keywordWeight = keywordWeight;
    }

    /**
     * Hybrid retrieval: semantic (pgvector) + keyword (Postgres full-text) fused
     * via weighted reciprocal-rank fusion. When rerank is enabled, retrieves a
     * wider pool and reranks down to {@code limit}; otherwise trims fusion order.
     */
    public List<SearchResult> search(String query, int limit) {
        validate(query);
        if (limit < 1) limit = 5;
        if (limit > 20) limit = 20;
        int poolSize = rerankEnabled
                ? Math.min(Math.max(limit * 3, rerankPool), 20)
                : Math.min(limit * 2, 20);

        List<SearchResult> fused = fusedSearch(query, poolSize);
        if (rerankEnabled) {
            // Retrieve-wide, then rerank-and-trim (never throws: falls back to fusion order).
            return rerankService.rerank(query, fused, limit);
        }
        return fused.size() > limit ? fused.subList(0, limit) : fused;
    }

    /** Exposed for the eval harness: fusion order without rerank. */
    public List<SearchResult> searchWithoutRerank(String query, int limit) {
        validate(query);
        if (limit < 1) limit = 5;
        if (limit > 20) limit = 20;
        List<SearchResult> fused = fusedSearch(query, Math.min(limit * 2, 20));
        return fused.size() > limit ? fused.subList(0, limit) : fused;
    }

    private List<SearchResult> fusedSearch(String query, int poolSize) {
        float[] queryEmbedding = embeddingService.embed(query);
        String vectorString = vectorToString(queryEmbedding);

        // Branch 1: semantic (pgvector cosine) — row: id, content, page, section, doc_id, doc_title, distance
        List<Object[]> semanticRows = chunkRepository.findNearestChunks(vectorString, poolSize);
        // Branch 2: keyword (Postgres full-text, ts_rank) — row: id, content, page, section, doc_id, doc_title, rank.
        // Full-text must never break search (e.g. stopword-only queries): fall back to semantic-only.
        List<Object[]> keywordRows = List.of();
        try {
            keywordRows = chunkRepository.findKeywordChunks(query, poolSize);
        } catch (Exception ignored) {}

        // Collect per-branch ranks (1-based) and chunk payloads.
        Map<Long, Integer> semanticRank = new HashMap<>();
        Map<Long, Integer> keywordRank = new HashMap<>();
        Map<Long, Object[]> payload = new HashMap<>();
        Map<Long, Double> semanticSimilarity = new HashMap<>();

        for (int i = 0; i < semanticRows.size(); i++) {
            Object[] row = semanticRows.get(i);
            Long id = ((Number) row[0]).longValue();
            semanticRank.putIfAbsent(id, i + 1);
            payload.putIfAbsent(id, row);
            semanticSimilarity.putIfAbsent(id, round4(1.0 - ((Number) row[6]).doubleValue()));
        }
        for (int i = 0; i < keywordRows.size(); i++) {
            Object[] row = keywordRows.get(i);
            Long id = ((Number) row[0]).longValue();
            keywordRank.putIfAbsent(id, i + 1);
            payload.putIfAbsent(id, row);
        }

        // Keyword-only chunks have no semantic distance from the query above:
        // compute cosine similarity locally against the query embedding (one batched load).
        Set<Long> keywordOnly = new HashSet<>(keywordRank.keySet());
        keywordOnly.removeAll(semanticSimilarity.keySet());
        if (!keywordOnly.isEmpty()) {
            for (var chunk : chunkRepository.findAllById(keywordOnly)) {
                float[] emb = chunk.getEmbedding();
                if (emb != null && emb.length == queryEmbedding.length) {
                    semanticSimilarity.put(chunk.getId(), round4(cosineSimilarity(queryEmbedding, emb)));
                } else {
                    semanticSimilarity.put(chunk.getId(), 0.0);
                }
            }
            // Chunks deleted between query and load: fall back to 0.0.
            for (Long id : keywordOnly) {
                semanticSimilarity.putIfAbsent(id, 0.0);
            }
        }

        // Weighted reciprocal-rank fusion, then sort.
        List<SearchResult> fused = new ArrayList<>();
        for (var entry : payload.entrySet()) {
            Long id = entry.getKey();
            Object[] row = entry.getValue();
            double score = 0.0;
            if (semanticRank.containsKey(id)) score += semanticWeight / (RRF_K + semanticRank.get(id));
            if (keywordRank.containsKey(id)) score += keywordWeight / (RRF_K + keywordRank.get(id));

            fused.add(new SearchResult(
                    id,
                    ((Number) row[4]).longValue(),
                    (String) row[5],
                    (String) row[1],
                    (Integer) row[2],
                    (String) row[3],
                    semanticSimilarity.getOrDefault(id, 0.0),
                    round4(score)));
        }
        fused.sort(Comparator
                .comparingDouble(SearchResult::hybridScore).reversed()
                .thenComparing(Comparator.comparingDouble(SearchResult::similarity).reversed())
                .thenComparing(SearchResult::id));
        return fused;
    }

    private static void validate(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be null or blank");
        }
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public record SearchResult(
            Long id,
            Long documentId,
            String documentTitle,
            String content,
            Integer pageNumber,
            String section,
            double similarity,
            double hybridScore,
            Double rerankScore
    ) {
        // Backward-compatible constructors (pre-rerank callers).
        public SearchResult(Long id, Long documentId, String documentTitle, String content,
                            Integer pageNumber, String section, double similarity, double hybridScore) {
            this(id, documentId, documentTitle, content, pageNumber, section, similarity, hybridScore, null);
        }
        public SearchResult(Long id, Long documentId, String documentTitle, String content,
                            Integer pageNumber, String section, double similarity) {
            this(id, documentId, documentTitle, content, pageNumber, section, similarity, similarity, null);
        }
    }
}
