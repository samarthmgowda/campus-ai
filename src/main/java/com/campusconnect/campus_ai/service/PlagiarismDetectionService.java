package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Compares a piece of submitted text against the campus document corpus and
 * reports which stored documents it overlaps with.
 *
 * <h2>How the score is computed</h2>
 *
 * The original implementation used Jaccard similarity (|A∩B| / |A∪B|), which is
 * the wrong shape for this problem. A 300-word submission copied verbatim out of
 * a 2000-word document shares all 300 of its tokens but the union is ~2000, so
 * the score lands near 0.15 — a verbatim copy reported as barely related. Jaccard
 * punishes a match for the source being longer than the submission, and the source
 * is always longer.
 *
 * So this measures <em>containment</em> instead: what fraction of the submission
 * appears in the chunk. A verbatim copy scores 1.0 no matter how long the source
 * is, which is the question a plagiarism check is actually asking.
 *
 * Containment alone is still too generous, because a submission and an unrelated
 * document can share a lot of ordinary vocabulary. So the score blends two
 * signals:
 *
 *   - 60% unigram containment — the share of the submission's distinct words
 *     that appear in the chunk. Catches paraphrasing and reordering.
 *   - 40% bigram containment — the share of adjacent word pairs that appear.
 *     This is what separates real copying from shared vocabulary: two texts can
 *     use the same twenty words and share almost no pairs, unless one was
 *     written from the other.
 *
 * Stopwords are removed first. Without that, a submission and any chunk share
 * "the", "of", "and", "to" and every short submission looks like a match.
 *
 * <h2>Thresholds</h2>
 *
 * The verdict cut-offs are heuristics tuned against the containment scale, not
 * calibrated against a labelled corpus — there is no ground-truth set of known
 * plagiarised submissions to fit them to. They are deliberately conservative:
 * this flags text for a human to read, it does not accuse anyone. Treat HIGH as
 * "look at this" rather than "this is plagiarism".
 */
@Service
public class PlagiarismDetectionService {

    /** Adjacent-token pairs. Sentences are not reconstructed, only order. */
    private static final int BIGRAM = 2;

    private static final double W_UNIGRAM = 0.6;
    private static final double W_BIGRAM = 0.4;

    /**
     * Below this many distinct meaningful tokens the score is noise: "java
     * spring boot" matches half the corpus and means nothing. Reported as
     * INSUFFICIENT rather than as a low score, so the UI can ask for more text
     * instead of showing a reassuring 3%.
     */
    private static final int MIN_MEANINGFUL_TOKENS = 8;

    /** How many source documents to return, best first. */
    private static final int MAX_MATCHES = 5;

    private static final double HIGH_THRESHOLD = 0.70;
    private static final double MODERATE_THRESHOLD = 0.45;
    private static final double LOW_THRESHOLD = 0.25;

    /**
     * Function words carry no authorship signal. Kept local rather than shared
     * with RerankService: that list is tuned for search queries (it strips
     * "tell", "show", "list") and would quietly drop real prose here.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "than", "so",
            "such", "as", "of", "at", "by", "for", "from", "in", "into", "on",
            "onto", "to", "with", "within", "without", "over", "under", "between",
            "through", "during", "per", "via", "upon", "about", "above", "after",
            "against", "along", "among", "around", "before", "behind", "below",
            "beneath", "beside", "beyond", "despite", "except", "inside",
            "outside", "since", "toward", "towards", "until", "up", "down", "off",
            "out", "is", "are", "was", "were", "be", "been", "being", "am",
            "do", "does", "did", "doing", "done", "have", "has", "had", "having",
            "can", "could", "shall", "should", "will", "would", "may", "might",
            "must", "not", "no", "nor", "none", "yes", "also", "just", "only",
            "very", "too", "more", "most", "much", "many", "some", "any", "all",
            "each", "every", "both", "few", "several", "other", "another", "same",
            "own", "this", "that", "these", "those", "it", "its", "they", "them",
            "their", "theirs", "there", "here", "where", "when", "while", "who",
            "whom", "whose", "which", "what", "why", "how", "he", "him", "his",
            "she", "her", "hers", "i", "me", "my", "mine", "we", "us", "our",
            "ours", "you", "your", "yours", "one", "two", "first", "second",
            "next", "last", "new", "used", "use", "using", "get", "got", "make",
            "made", "like", "well", "even", "still", "however", "therefore",
            "thus", "hence", "because", "although", "though", "whether");

    private final DocumentChunkRepository chunkRepository;

    public PlagiarismDetectionService(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /** One stored document the submission overlaps with. */
    public record SourceMatch(
            Long documentId,
            String documentTitle,
            String section,
            Integer pageNumber,
            double score,
            /** Share of the submission's distinct words found in this document. */
            double wordCoverage,
            /** Share of the submission's adjacent word pairs found in this document. */
            double phraseCoverage) {}

    /**
     * @param verdict         NONE, LOW, MODERATE, HIGH or INSUFFICIENT
     * @param topScore        the best document score, or 0
     * @param scannedChunks   how many corpus chunks were compared (for the UI to be honest about scope)
     * @param submissionWords distinct meaningful words the score is based on
     * @param summary         a sentence the caller can display as-is
     * @param matches         up to five source documents, best first
     */
    public record ScanResult(
            String verdict,
            double topScore,
            int scannedChunks,
            int submissionWords,
            String summary,
            List<SourceMatch> matches) {}

    /**
     * Scores {@code submissionText} against every chunk in the corpus and
     * returns the best-matching documents.
     *
     * @throws IllegalArgumentException if the text is null or blank
     */
    public ScanResult scan(String submissionText) {
        if (submissionText == null || submissionText.isBlank()) {
            throw new IllegalArgumentException("Submission text must not be empty");
        }

        List<String> submissionTokens = tokens(submissionText);
        Set<String> submissionWords = new HashSet<>(submissionTokens);
        Set<String> submissionBigrams = bigrams(submissionTokens);

        // Too little text to say anything meaningful. Return no matches rather
        // than a confidently low score — "3% similar" from eight words reads as
        // a clean bill of health when it is really "I cannot tell".
        if (submissionWords.size() < MIN_MEANINGFUL_TOKENS) {
            return new ScanResult("INSUFFICIENT", 0.0, 0, submissionWords.size(),
                    "Add more text — a comparison needs at least " + MIN_MEANINGFUL_TOKENS
                            + " distinct words to mean anything.",
                    List.of());
        }

        List<Object[]> chunks = chunkRepository.findScannableChunks();
        if (chunks.isEmpty()) {
            return new ScanResult("NONE", 0.0, 0, submissionWords.size(),
                    "The library has no content to compare against yet. Upload documents first.",
                    List.of());
        }

        // A long document is split across many chunks, each holding part of it.
        // Keeping only the best chunk per document would score a submission that
        // spans two chunks at roughly half its true coverage, so coverage is
        // unioned per document before scoring.
        Map<Long, DocAccumulator> byDocument = new LinkedHashMap<>();

        for (Object[] row : chunks) {
            String content = (String) row[1];
            Integer pageNumber = (Integer) row[2];
            String section = (String) row[3];
            Long documentId = (Long) row[4];
            String documentTitle = (String) row[5];

            if (content == null || content.isBlank()) continue;

            List<String> chunkTokens = tokens(content);
            if (chunkTokens.isEmpty()) continue;

            Set<String> chunkWords = new HashSet<>(chunkTokens);
            Set<String> chunkBigrams = bigrams(chunkTokens);

            Set<String> wordsHere = intersect(submissionWords, chunkWords);
            Set<String> phrasesHere = intersect(submissionBigrams, chunkBigrams);

            DocAccumulator acc = byDocument.computeIfAbsent(documentId,
                    id -> new DocAccumulator(id, documentTitle));

            // Union across chunks: a word found in any chunk of the document
            // counts once for that document.
            acc.wordHits.addAll(wordsHere);
            acc.phraseHits.addAll(phrasesHere);

            // Remember where the densest single chunk sits, so the UI can point
            // at a page or section instead of just naming the document.
            double chunkScore = score(wordsHere.size(), submissionWords.size(),
                    phrasesHere.size(), submissionBigrams.size());
            if (chunkScore > acc.bestChunkScore) {
                acc.bestChunkScore = chunkScore;
                acc.bestSection = section;
                acc.bestPage = pageNumber;
            }
        }

        List<SourceMatch> matches = new ArrayList<>();
        for (DocAccumulator acc : byDocument.values()) {
            double wordCoverage = (double) acc.wordHits.size() / submissionWords.size();
            // Div-by-zero guard: a one- or two-word submission has no bigrams.
            double phraseCoverage = submissionBigrams.isEmpty() ? 0.0
                    : (double) acc.phraseHits.size() / submissionBigrams.size();
            double score = W_UNIGRAM * wordCoverage + W_BIGRAM * phraseCoverage;
            if (score <= LOW_THRESHOLD / 2) continue;
            matches.add(new SourceMatch(acc.documentId, acc.documentTitle, acc.bestSection,
                    acc.bestPage, round4(score), round4(wordCoverage), round4(phraseCoverage)));
        }

        matches.sort(Comparator.comparingDouble(SourceMatch::score).reversed());
        if (matches.size() > MAX_MATCHES) matches = matches.subList(0, MAX_MATCHES);

        double topScore = matches.isEmpty() ? 0.0 : matches.get(0).score();
        String verdict = verdictFor(topScore);

        return new ScanResult(verdict, round4(topScore), chunks.size(), submissionWords.size(),
                summarize(verdict, matches, chunks.size()), List.copyOf(matches));
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>();
        for (String s : a) if (b.contains(s)) out.add(s);
        return out;
    }

    private static double score(int wordHits, int words, int phraseHits, int phrases) {
        double wordCoverage = words == 0 ? 0.0 : (double) wordHits / words;
        double phraseCoverage = phrases == 0 ? 0.0 : (double) phraseHits / phrases;
        return W_UNIGRAM * wordCoverage + W_BIGRAM * phraseCoverage;
    }

    private static String verdictFor(double topScore) {
        if (topScore >= HIGH_THRESHOLD) return "HIGH";
        if (topScore >= MODERATE_THRESHOLD) return "MODERATE";
        if (topScore >= LOW_THRESHOLD) return "LOW";
        return "NONE";
    }

    /**
     * The summary is written here rather than in the UI so the thresholds and
     * the wording that explains them cannot drift apart.
     */
    private static String summarize(String verdict, List<SourceMatch> matches, int scannedChunks) {
        return switch (verdict) {
            case "HIGH" -> "Close match with \"" + matches.get(0).documentTitle()
                    + "\". Read both side by side before drawing a conclusion.";
            case "MODERATE" -> "Substantial overlap with \"" + matches.get(0).documentTitle()
                    + "\" — possibly shared source material or close paraphrasing.";
            case "LOW" -> "Some shared vocabulary with \"" + matches.get(0).documentTitle()
                    + "\", which usually means a common topic rather than copying.";
            default -> "No meaningful overlap with the " + scannedChunks
                    + " library chunks compared.";
        };
    }

    private static final class DocAccumulator {
        final Long documentId;
        final String documentTitle;
        final Set<String> wordHits = new HashSet<>();
        final Set<String> phraseHits = new HashSet<>();
        double bestChunkScore = -1;
        String bestSection;
        Integer bestPage;

        DocAccumulator(Long documentId, String documentTitle) {
            this.documentId = documentId;
            this.documentTitle = documentTitle;
        }
    }

    /**
     * Ordered, stopword-free, lightly stemmed tokens.
     *
     * Order matters — the bigram signal depends on it — so this returns a List,
     * not a Set. Duplicates are kept for the same reason: "the system fails and
     * the system retries" has the pairs ("system","fails") and ("system","retries")
     * that a de-duplicated list would destroy.
     */
    static List<String> tokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : text.toLowerCase(Locale.ROOT).split("\\P{L}+")) {
            if (part.length() < 3) continue;
            if (STOPWORDS.contains(part)) continue;
            out.add(stem(part));
        }
        return out;
    }

    /**
     * Trims plurals only: "students"/"student" and "systems"/"system" should not
     * read as different words. Anything more aggressive starts merging words
     * that genuinely differ ("analysis" / "analyst"), and this is a comparison
     * aid, not a search index.
     */
    private static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 4 && word.endsWith("sses")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 4 && word.endsWith("s") && !word.endsWith("ss")
                && !word.endsWith("us") && !word.endsWith("is")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    /** Adjacent-token pairs from the ordered token list. */
    static Set<String> bigrams(List<String> tokens) {
        if (tokens.size() < BIGRAM) return Set.of();
        Set<String> out = new HashSet<>();
        for (int i = 0; i + BIGRAM <= tokens.size(); i++) {
            out.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        return out;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}
