package com.campusconnect.campus_ai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight deterministic reranker: retrieve a candidate pool, then rescore.
 *
 * No new dependencies — pure-Java lexical features fused with the semantic
 * similarity already computed at retrieval time:
 *   score = 0.45 * semantic + 0.25 * idfTermOverlap + 0.15 * bigramOverlap
 *         + 0.10 * exactPhrase + 0.05 * titleSectionMatch
 *
 * IDF weighting: terms that appear in many candidate chunks (common words)
 * get lower weight, so query-specific terms like "2 to 4" or "hackathon"
 * dominate the lexical signal over generic words like "the" or "student".
 *
 * Behind {@code rag.rerank-enabled} so basic (fusion order) vs reranked order
 * can be A/B-tested before keeping the extra stage. Never throws: on any
 * unexpected input it returns candidates in their incoming order, trimmed.
 */
@Service
public class RerankService {

    private static final double W_SEMANTIC = 0.45;
    private static final double W_TERMS = 0.25;
    private static final double W_BIGRAM = 0.15;
    private static final double W_PHRASE = 0.10;
    private static final double W_TITLE = 0.05;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "is", "are", "was", "were", "be", "been", "how",
            "what", "when", "where", "who", "which", "that", "this", "these",
            "those", "it", "its", "as", "from", "do", "does", "did", "can",
            "could", "should", "would", "will", "shall", "may", "might", "must",
            "have", "has", "had", "not", "no", "yes", "i", "me", "my", "we",
            "our", "you", "your", "tell", "give", "show", "list", "find", "get",
            "about", "many", "much", "there", "their", "them", "they", "he",
            "she", "him", "her", "his", "hers", "ours", "us", "if", "then",
            "than", "so", "such", "into", "over", "under", "between", "through",
            "during", "per", "please", "kindly");

    public List<SearchService.SearchResult> rerank(String query,
                                                   List<SearchService.SearchResult> candidates,
                                                   int limit) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (limit < 1) limit = 5;
        try {
            List<String> terms = tokenize(query);
            String normalizedQuery = normalize(query);

            // Compute IDF weights for each query term based on candidate pool frequency.
            // Terms appearing in many chunks (generic words) get lower weight.
            Map<String, Double> idfWeights = computeIdfWeights(terms, candidates);

            List<Scored> scored = new ArrayList<>();
            for (SearchService.SearchResult r : candidates) {
                double score = score(r, terms, normalizedQuery, idfWeights);
                scored.add(new Scored(r, score));
            }
            scored.sort(Comparator
                    .comparingDouble(Scored::score).reversed()
                    .thenComparing((Scored s) -> s.result().hybridScore(), Comparator.reverseOrder())
                    .thenComparing(s -> s.result().id()));
            List<SearchService.SearchResult> out = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, scored.size()); i++) {
                Scored s = scored.get(i);
                SearchService.SearchResult r = s.result();
                out.add(new SearchService.SearchResult(
                        r.id(), r.documentId(), r.documentTitle(), r.content(),
                        r.pageNumber(), r.section(), r.similarity(), r.hybridScore(),
                        round4(s.score())));
            }
            return out;
        } catch (Exception e) {
            // Rerank must never break search.
            return candidates.size() > limit ? candidates.subList(0, limit) : List.copyOf(candidates);
        }
    }

    /**
     * Compute IDF weight for each query term using the candidate pool as the corpus.
     * idf(t) = log((N + 1) / (df(t) + 1)) + 1  — smoothed, always positive.
     * Terms appearing in few chunks get higher weight (more discriminative).
     */
    private Map<String, Double> computeIdfWeights(List<String> terms,
                                                   List<SearchService.SearchResult> candidates) {
        Map<String, Double> weights = new java.util.HashMap<>();
        if (terms.isEmpty() || candidates.isEmpty()) return weights;
        double N = candidates.size();
        for (String t : terms) {
            int df = 0;
            for (SearchService.SearchResult r : candidates) {
                if (r.content() != null && tokenize(r.content()).contains(t)) df++;
            }
            double idf = Math.log((N + 1.0) / (df + 1.0)) + 1.0;
            weights.put(t, idf);
        }
        return weights;
    }

    private double score(SearchService.SearchResult r, List<String> terms, String normalizedQuery,
                         Map<String, Double> idfWeights) {
        double semantic = clamp01(r.similarity());
        if (terms.isEmpty()) return semantic;

        String contentNorm = normalize(r.content());
        List<String> contentTokens = tokenize(contentNorm);
        Set<String> contentSet = new HashSet<>(contentTokens);

        // IDF-weighted term overlap: sum of IDF weights for matched terms / total IDF sum
        double matchedIdf = 0.0, totalIdf = 0.0;
        for (String t : terms) {
            double idf = idfWeights.getOrDefault(t, 1.0);
            totalIdf += idf;
            if (contentSet.contains(t)) matchedIdf += idf;
        }
        double termOverlap = totalIdf > 0 ? matchedIdf / totalIdf : 0.0;

        double bigram = 0.0;
        if (terms.size() >= 2 && contentTokens.size() >= 2) {
            Set<String> contentBigrams = new HashSet<>();
            for (int i = 0; i + 1 < contentTokens.size(); i++) {
                contentBigrams.add(contentTokens.get(i) + " " + contentTokens.get(i + 1));
            }
            int bMatched = 0;
            int bTotal = 0;
            for (int i = 0; i + 1 < terms.size(); i++) {
                bTotal++;
                if (contentBigrams.contains(terms.get(i) + " " + terms.get(i + 1))) bMatched++;
            }
            if (bTotal > 0) bigram = (double) bMatched / bTotal;
        }

        double phrase = 0.0;
        if (normalizedQuery.length() >= 8 && contentNorm.contains(normalizedQuery)) phrase = 1.0;

        double title = 0.0;
        String hay = normalize(
                (r.documentTitle() == null ? "" : r.documentTitle()) + " " + (r.section() == null ? "" : r.section()));
        if (!hay.isBlank()) {
            int tMatched = 0;
            for (String t : terms) {
                if (hay.contains(t)) tMatched++;
            }
            title = (double) tMatched / terms.size();
        }

        return W_SEMANTIC * semantic + W_TERMS * termOverlap + W_BIGRAM * bigram
                + W_PHRASE * phrase + W_TITLE * title;
    }

    private static List<String> tokenize(String text) {
        if (text == null) return List.of();
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p.length() >= 2 && !STOPWORDS.contains(p)) out.add(p);
        }
        return out;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private record Scored(SearchService.SearchResult result, double score) {}
}
