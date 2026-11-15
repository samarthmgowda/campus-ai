package com.campusconnect.campus_ai.service;

/**
 * Turns a raw embedding cosine into a <em>boost</em> on top of a word-overlap
 * score, rather than into a score of its own.
 *
 * <h2>Why not just use cosine</h2>
 *
 * Cosine over sentence embeddings is dominated by subject matter. Two final
 * reports on "placement readiness", written independently by two students who
 * never met, routinely land between 0.75 and 0.88 — they talk about the same
 * things in the same register. Averbatim copy of one of them lands around 0.97.
 * That gap is real but narrow, and it sits on top of a large common baseline.
 *
 * <p>So a 50/50 blend of cosine and word overlap would push every same-subject
 * pair over the MODERATE threshold, which is the exact failure that makes an
 * originality tool useless: it flags everyone who took the same course. And
 * taking cosine as the score outright is worse.
 *
 * <h2>What this does instead</h2>
 *
 * Below {@link #TOPIC_BASELINE} the cosine is assumed to be "same subject,
 * different work" and contributes nothing. Above it, the excess is treated as
 * evidence of paraphrase and lifts the word score toward 1 without ever
 * lowering it:
 *
 * <pre>
 *   boost  = clamp((cosine - 0.90) / 0.10, 0, 1)
 *   score  = wordScore + (1 - wordScore) * boost
 * </pre>
 *
 * <p>Three properties fall out of that shape, and each of them matters:
 *
 * <ul>
 *   <li><b>It can only raise a score.</b> A missing or broken embedding can
 *       never make a submission look cleaner than the words alone say. "Ollama
 *       is down" must not become "we found nothing".</li>
 *   <li><b>It needs near-identity to escalate on its own.</b> From a word score
 *       of 0.10, cosine 0.95 reaches 0.55 (MODERATE) and only cosine ~0.98
 *       reaches HIGH. A loose paraphrase is surfaced as something to read; a
 *       tight one is called out. Same-subject work at 0.85 never moves.</li>
 *   <li><b>It is monotone and continuous</b>, so the number does not jump when
 *       a document crosses a threshold — no cliff at the boundary.</li>
 * </ul>
 *
 * <h2>What the constant is not</h2>
 *
 * {@link #TOPIC_BASELINE} is a hand-set guess, not a fitted parameter. It comes
 * from reading the pairwise distribution of a dozen hand-written sample
 * submissions, which is enough to avoid the "flag every same-subject pair"
 * failure and nowhere near enough to claim accuracy. Fitting it properly needs
 * a labelled set of (pair, copied?) judgements; until there is one, the verdict
 * stays a pointer to a human and the score is reported with its two components
 * visible so a reader can see which signal drove it.
 */
final class SemanticSimilarity {

    /**
     * Cosine at or below which two texts are assumed to merely share a subject.
     * See the class comment for why this is 0.90 and why it is a guess.
     */
    static final double TOPIC_BASELINE = 0.90;

    /** Cosine at or above which the semantic signal alone means "read these two". */
    static final double NEAR_IDENTICAL = 1.0;

    private SemanticSimilarity() {}

    /**
     * Cosine similarity of two vectors, or 0 when either is unusable.
     *
     * <p>Returns 0 rather than throwing for a null/empty/mismatched vector: an
     * embedding that was never computed is a normal state (Ollama was down when
     * the submission was stored), and the caller's fallback is "score on words
     * alone", which is what a 0 boost produces.
     */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0.0;

        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        // A zero vector has no direction; treating it as "similar to anything"
        // would let a failed embed masquerade as a perfect match.
        if (normA == 0.0 || normB == 0.0) return 0.0;

        double c = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        // Float error can push an identical pair a hair past 1.
        return Math.max(0.0, Math.min(1.0, c));
    }

    /**
     * How much a cosine should lift a word score, in [0, 1].
     *
     * @param cosine raw cosine, or 0 when no embedding was available
     */
    static double boost(double cosine) {
        if (cosine <= TOPIC_BASELINE) return 0.0;
        double excess = (cosine - TOPIC_BASELINE) / (NEAR_IDENTICAL - TOPIC_BASELINE);
        return Math.min(1.0, excess);
    }

    /**
     * Blend a word score with a semantic boost.
     *
     * @param wordScore  containment score over words and phrases, in [0, 1]
     * @param cosine     raw cosine, or 0 when no embedding was available
     * @return the blended score, never below {@code wordScore}
     */
    static double combine(double wordScore, double cosine) {
        double w = Math.max(0.0, Math.min(1.0, wordScore));
        return w + (1.0 - w) * boost(cosine);
    }
}
