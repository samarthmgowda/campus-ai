package com.campusconnect.campus_ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The arithmetic behind the semantic boost, tested apart from any repository or
 * model server.
 *
 * <p>The properties that matter are not "cosine is correct" — that is a library
 * formula — but the three guarantees the design rests on: a missing embedding
 * can never make a submission look cleaner, same-subject similarity never moves
 * the score, and near-identity does. Each of those is a boundary, and each
 * boundary is where this would quietly go wrong.
 */
class SemanticSimilarityTest {

    /**
     * Two-dimensional vectors are enough: cosine does not care about dimension,
     * and a 1024-element literal would only obscure which boundary is being
     * pinned. The first axis is always the shared direction.
     */
    private static float[] at(double cosine) {
        return new float[]{1.0f, (float) Math.sqrt(Math.max(0.0, 1.0 - cosine * cosine))};
    }

    // ---------------------------------------------------------------- cosine

    @Test
    void identicalVectorsHaveCosineOne() {
        float[] v = {0.3f, -0.7f, 0.2f};
        assertThat(SemanticSimilarity.cosine(v, v)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void orthogonalVectorsHaveCosineZero() {
        assertThat(SemanticSimilarity.cosine(new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0.0, within(1e-9));
    }

    /**
     * A missing vector is a normal state — Ollama was down when the row was
     * stored — so it must produce "no signal", never an exception. Several
     * shapes are unusable for different reasons and all must land on 0.
     */
    @Test
    void unusableVectorsScoreZeroRatherThanThrowing() {
        float[] good = {1f, 0f};
        assertThat(SemanticSimilarity.cosine(null, good)).isZero();
        assertThat(SemanticSimilarity.cosine(good, null)).isZero();
        assertThat(SemanticSimilarity.cosine(new float[0], good)).isZero();
        // Different models produce different dimensions; comparing them would
        // read past the end of the shorter one.
        assertThat(SemanticSimilarity.cosine(new float[]{1f, 0f, 0f}, good)).isZero();
    }

    /**
     * A zero vector has no direction. If it scored 1 (or anything) a failed
     * embed would masquerade as a perfect match — the worst possible failure
     * for a check that accuses people.
     */
    @Test
    void aZeroVectorIsNotSimilarToAnything() {
        assertThat(SemanticSimilarity.cosine(new float[]{0f, 0f}, new float[]{1f, 0f})).isZero();
        assertThat(SemanticSimilarity.cosine(new float[]{0f, 0f}, new float[]{0f, 0f})).isZero();
    }

    /** Cosine cannot be negative in this domain, and a negative would break the blend. */
    @Test
    void oppositeVectorsClampToZero() {
        assertThat(SemanticSimilarity.cosine(new float[]{1f, 0f}, new float[]{-1f, 0f})).isZero();
    }

    // ----------------------------------------------------------------- boost

    @Test
    void cosineAtOrBelowTheTopicBaselineContributesNothing() {
        assertThat(SemanticSimilarity.boost(0.0)).isZero();
        assertThat(SemanticSimilarity.boost(0.75)).isZero();
        // The boundary itself is inclusive: 0.90 is "same subject", not evidence.
        assertThat(SemanticSimilarity.boost(SemanticSimilarity.TOPIC_BASELINE)).isZero();
    }

    @Test
    void boostRampsLinearlyBetweenBaselineAndIdentity() {
        assertThat(SemanticSimilarity.boost(0.95)).isCloseTo(0.5, within(1e-6));
        assertThat(SemanticSimilarity.boost(0.98)).isCloseTo(0.8, within(1e-6));
        assertThat(SemanticSimilarity.boost(1.0)).isCloseTo(1.0, within(1e-9));
    }

    /** Float error can push a cosine a hair past 1; the boost must not exceed 1. */
    @Test
    void boostNeverExceedsOne() {
        assertThat(SemanticSimilarity.boost(1.0001)).isLessThanOrEqualTo(1.0);
        assertThat(SemanticSimilarity.boost(2.0)).isLessThanOrEqualTo(1.0);
    }

    // --------------------------------------------------------------- combine

    /**
     * The guarantee the whole design leans on: the semantic signal can only
     * raise a score. If it could lower one, a flaky Ollama would make copied
     * work look cleaner than the words say, which is the one direction this
     * must never fail in.
     */
    @Test
    void combineNeverLowersAWordScore() {
        for (double word = 0.0; word <= 1.0; word += 0.1) {
            for (double cosine = 0.0; cosine <= 1.0; cosine += 0.05) {
                assertThat(SemanticSimilarity.combine(word, cosine))
                        .as("word %.2f cosine %.2f", word, cosine)
                        .isGreaterThanOrEqualTo(Math.min(1.0, word) - 1e-9);
            }
        }
    }

    @Test
    void combineReturnsTheWordScoreUnchangedBelowTheBaseline() {
        assertThat(SemanticSimilarity.combine(0.31, 0.88)).isCloseTo(0.31, within(1e-9));
        assertThat(SemanticSimilarity.combine(0.0, 0.0)).isZero();
    }

    /**
     * Same-subject work sits around 0.75–0.88 and must stay where the words put
     * it. This is the "flags everyone who took the same course" failure.
     */
    @Test
    void sameSubjectSimilarityDoesNotEscalateAnUnrelatedScore() {
        assertThat(SemanticSimilarity.combine(0.10, 0.85)).isCloseTo(0.10, within(1e-9));
        assertThat(SemanticSimilarity.combine(0.10, 0.88)).isCloseTo(0.10, within(1e-9));
    }

    /**
     * A machine paraphrase shares almost no words. Near-identical meaning has to
     * be able to carry such a pair to HIGH on its own, or the feature cannot do
     * the job it was added for.
     */
    @Test
    void nearIdenticalMeaningLiftsANearZeroWordScoreToHigh() {
        double score = SemanticSimilarity.combine(0.10, 0.98);
        assertThat(score).isCloseTo(0.82, within(1e-6));
        assertThat(score).isGreaterThanOrEqualTo(0.70);
    }

    /** A loose paraphrase should surface as something to read, not as an accusation. */
    @Test
    void aLooseParaphraseLandsInTheMiddleRatherThanAtTheTop() {
        double score = SemanticSimilarity.combine(0.10, 0.95);
        assertThat(score).isCloseTo(0.55, within(1e-6));
        assertThat(score).isLessThan(0.70);
    }

    /** The full-cosine case must not produce a number above 1. */
    @Test
    void combineStaysWithinZeroToOne() {
        assertThat(SemanticSimilarity.combine(1.0, 1.0)).isCloseTo(1.0, within(1e-9));
        // Out-of-range inputs are clamped rather than propagated: a caller bug
        // must not turn into a 140% match in the UI.
        assertThat(SemanticSimilarity.combine(1.4, 1.0)).isEqualTo(1.0);
        assertThat(SemanticSimilarity.combine(-0.2, 0.0)).isZero();
    }

    @Test
    void combineIsMonotoneInCosine() {
        double previous = -1.0;
        for (double cosine = 0.0; cosine <= 1.0; cosine += 0.02) {
            double score = SemanticSimilarity.combine(0.2, cosine);
            assertThat(score).isGreaterThanOrEqualTo(previous);
            previous = score;
        }
    }
}
