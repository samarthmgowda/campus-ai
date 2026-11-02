package com.campusconnect.campus_ai.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for teammate ranking order.
 *
 * The original comparator chained .reversed() onto the outer Comparator after
 * comparingDouble(score).reversed(), which reverses the WHOLE chain built so far —
 * so the ranking came back score-ascending. In practice a candidate scoring 0.2333
 * with no matching skill was listed above two candidates scoring 0.7667 who matched
 * the required skill. These tests pin the intended order.
 */
class TeammateRankingOrderTest {

    private static TeammateMatchingService.ScoredCandidate cand(
            String name, double score, long endorsements, boolean reported,
            String availability, int matchedSkillCount) {
        List<String> matched = new ArrayList<>();
        for (int i = 0; i < matchedSkillCount; i++) matched.add("Skill" + i);
        return new TeammateMatchingService.ScoredCandidate(
                1L, name, "CSE", 2, "java, react", availability, matched,
                0.0, 0.0, 0.0, 0.0, null, score,
                endorsements, null, reported);
    }

    @Test
    void highestScoreSortsFirst() {
        var weak = cand("Vikram Patel", 0.2333, 0, false, "AVAILABLE", 0);
        var strongA = cand("Ananya Rao", 0.7667, 0, false, "AVAILABLE", 1);
        var strongB = cand("Sneha Kulkarni", 0.7667, 0, false, "AVAILABLE", 1);

        List<TeammateMatchingService.ScoredCandidate> list = new ArrayList<>(List.of(weak, strongA, strongB));
        list.sort(TeammateMatchingService.candidateOrder());

        assertEquals("Ananya Rao", list.getFirst().name(),
                "a 0.7667 match must outrank a 0.2333 non-match: " + list);
        assertEquals("Vikram Patel", list.getLast().name(),
                "the weakest match must sort last, not first: " + list);
        assertTrue(list.get(0).score() > list.get(2).score(),
                "scores should be descending: " + list.stream().map(TeammateMatchingService.ScoredCandidate::score).toList());
    }

    @Test
    void endorsementsBreakScoreTies() {
        var low = cand("Low Endorsement", 0.5, 1, false, "AVAILABLE", 1);
        var high = cand("High Endorsement", 0.5, 9, false, "AVAILABLE", 1);

        List<TeammateMatchingService.ScoredCandidate> list = new ArrayList<>(List.of(low, high));
        list.sort(TeammateMatchingService.candidateOrder());

        assertEquals("High Endorsement", list.getFirst().name(),
                "more endorsements should win a score tie: " + list);
    }

    @Test
    void reportedCandidateSinksBelowEquallyScoredPeer() {
        var clean = cand("Clean", 0.5, 0, false, "AVAILABLE", 1);
        var reported = cand("Reported", 0.5, 0, true, "AVAILABLE", 1);

        List<TeammateMatchingService.ScoredCandidate> list = new ArrayList<>(List.of(reported, clean));
        list.sort(TeammateMatchingService.candidateOrder());

        assertEquals("Clean", list.getFirst().name(),
                "a candidate with an open report should sink: " + list);
    }

    @Test
    void availablePreferredOverBusyAtEqualScore() {
        var busy = cand("Busy", 0.5, 0, false, "BUSY", 1);
        var free = cand("Free", 0.5, 0, false, "AVAILABLE", 1);

        List<TeammateMatchingService.ScoredCandidate> list = new ArrayList<>(List.of(busy, free));
        list.sort(TeammateMatchingService.candidateOrder());

        assertEquals("Free", list.getFirst().name(),
                "AVAILABLE should be preferred at equal score: " + list);
    }

    @Test
    void fullOrderIsScoreDescendingFirst() {
        List<TeammateMatchingService.ScoredCandidate> list = new ArrayList<>(List.of(
                cand("D", 0.10, 0, false, "AVAILABLE", 0),
                cand("A", 0.90, 0, false, "AVAILABLE", 2),
                cand("C", 0.30, 0, false, "AVAILABLE", 1),
                cand("B", 0.70, 0, false, "AVAILABLE", 1)));
        list.sort(TeammateMatchingService.candidateOrder());

        assertEquals(List.of("A", "B", "C", "D"),
                list.stream().map(TeammateMatchingService.ScoredCandidate::name).toList());
    }
}
