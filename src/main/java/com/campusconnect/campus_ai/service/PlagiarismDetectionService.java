package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.DocumentChunk;
import com.campusconnect.campus_ai.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.*;

@Service
public class PlagiarismDetectionService {

    private final DocumentChunkRepository chunkRepository;

    public PlagiarismDetectionService(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /**
     * Calculates similarity score between submission text and stored document chunks.
     * Uses keyword-based overlap as primary method (Jaccard-like: |A∩B|/|A∪B|).
     *
     * @param submissionText the student submission text to check
     * @return double[] similarity scores, one per top chunk (length = 5),
     *         each in range [0.0, 1.0] where 1.0 = identical token overlap
     */
    public double[] calculateSimilarity(String submissionText) {
        Assert.hasText(submissionText, "Submission text must not be empty");

        // Step 1: Get all document chunks with content
        List<DocumentChunk> chunks = chunkRepository.findAll();
        if (chunks.isEmpty()) {
            return new double[5];
        }

        // Step 2: Tokenize submission
        Set<String> submissionTokens = tokenize(submissionText);

        // Step 3: Calculate keyword similarity per chunk
        List<Map.Entry<Long, Double>> chunkScores = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Set<String> chunkTokens = tokenize(chunk.getContent());
            if (chunkTokens.isEmpty()) {
                continue;
            }

            long overlap = submissionTokens.stream().filter(chunkTokens::contains).count();
            double unionSize = (double) (submissionTokens.size() + chunkTokens.size() - overlap);
            double similarity = unionSize > 0 ? (double) overlap / unionSize : 0.0;

            chunkScores.add(Map.entry(chunk.getId(), similarity));
        }

        // Step 4: Sort by score descending
        chunkScores.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // Step 5: Return top-5 scores, padded with zeros
        double[] scores = new double[5];
        Arrays.fill(scores, 0.0);
        for (int i = 0; i < Math.min(chunkScores.size(), 5); i++) {
            scores[i] = chunkScores.get(i).getValue();
        }

        return scores;
    }

    /**
     * Simple tokenization: lowercase alphanumeric tokens > 2 chars, strip punctuation.
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(text.split("\\P{L}+"))
                .map(s -> s.toLowerCase().replaceAll("^\\p{P}+|\\p{P}+$", ""))
                .filter(s -> s.length() > 2)
                .collect(Collectors.toSet());
    }
}