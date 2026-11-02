package com.campusconnect.campus_ai.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the two answer-quality fixes surfaced by the chat transcript:
 *  - multi-fact answers leaking "Fact 1:/Fact 2:" scaffolding and self-contradicting
 *    (assert a value, then claim the documents have nothing),
 *  - short deictic follow-ups ("when does it start") failing to resolve against the
 *    subject of the prior turn.
 */
class AnswerSanitizeAndFollowupTest {

    // ——— sanitizeAnswer ———

    @Test
    void stripsFactScaffoldingLabels() {
        String raw = "Fact 1: You need 80% attendance for the 2nd internal. " +
                     "Fact 2: You need 85% attendance for the 3rd internal.";
        String out = AskService.sanitizeAnswer(raw);
        assertFalse(out.toLowerCase().contains("fact 1"), "labels should be stripped: " + out);
        assertFalse(out.toLowerCase().contains("fact 2"), "labels should be stripped: " + out);
        assertTrue(out.contains("80%"));
        assertTrue(out.contains("85%"));
    }

    @Test
    void dropsContradictoryAbstainAfterAssertion() {
        String raw = "Fact 1: The 2nd and 3rd internals require at least 80% and 85% attendance respectively. " +
                     "Fact 2: There is no information provided about 3rd internals in the context. " +
                     "I don't have that information in the college documents.";
        String out = AskService.sanitizeAnswer(raw);
        assertTrue(out.contains("80%"), "the grounded assertion should survive: " + out);
        assertTrue(out.contains("85%"), "the grounded assertion should survive: " + out);
        assertFalse(out.toLowerCase().contains("don't have that information"),
                "contradictory abstain should be dropped: " + out);
        assertFalse(out.toLowerCase().contains("no information provided"),
                "contradictory abstain should be dropped: " + out);
    }

    @Test
    void keepsPureAbstainWhenNoAssertionWasMade() {
        String raw = "I don't have that information in the college documents.";
        String out = AskService.sanitizeAnswer(raw);
        assertTrue(out.contains("don't have that information"));
    }

    @Test
    void fixesAttendanceTypoAndWhitespace() {
        String out = AskService.sanitizeAnswer("You need 75%  attendence  overall.");
        assertTrue(out.contains("attendance"));
        assertFalse(out.contains("  "), "collapsed whitespace expected: " + out);
    }

    @Test
    void nullAndBlankAreHandled() {
        assertNull(AskService.sanitizeAnswer(null));
        assertEquals("", AskService.sanitizeAnswer("   "));
    }

    // ——— follow-up detection / expansion (private, via reflection) ———

    @Test
    void whenDoesItStartIsAFollowup() throws Exception {
        assertTrue(invokeIsFollowup("when does it start", "how can i prepare for the upcoming placement drive"));
    }

    @Test
    void shortDeicticQuestionWithPrNumberIsFollowup() throws Exception {
        assertTrue(invokeIsFollowup("where is that held", "upcoming events"));
    }

    @Test
    void selfContainedQuestionIsNotAFollowup() throws Exception {
        assertFalse(invokeIsFollowup(
                "what are the attendance requirements for the semester exams this year",
                "when is the submission deadline"));
    }

    /**
     * Regression: a question must not be treated as a follow-up just because it shares a
     * weak temporal qualifier with the prior turn. Previously "how can i prepare for the
     * upcoming placement drive" matched "any upcoming ai workshops" purely on the word
     * "upcoming", so the unrelated answer got prefixed with "you asked about ..." and
     * framed against the wrong question.
     */
    @Test
    void sharedTemporalQualifierAloneIsNotAFollowup() throws Exception {
        assertFalse(invokeIsFollowup(
                "how can i prepare for the upcoming placement drive",
                "any upcoming ai workshops"),
                "sharing only the word 'upcoming' must not make this a follow-up");
    }

    @Test
    void otherTemporalQualifiersDoNotTriggerFollowup() throws Exception {
        assertFalse(invokeIsFollowup("what are the recent alumni events", "recent notifications"));
        assertFalse(invokeIsFollowup("give me the latest hackathon rules", "latest bookmarks"));
        assertFalse(invokeIsFollowup("what is the next placement deadline", "next workshop"));
    }

    @Test
    void genuineSharedSubjectStillDetectedAsFollowup() throws Exception {
        assertTrue(invokeIsFollowup("when is the engineering hackathon", "any upcoming hackathon events"),
                "a real shared subject noun should still resolve as a follow-up");
    }

    @Test
    void pronounResolvesAgainstPriorSubject() throws Exception {
        String expanded = invokeExpandFollowup("when does it start",
                "how can i prepare for the upcoming placement drive");
        assertTrue(expanded.contains("placement drive"),
                "pronoun should resolve to the prior subject: " + expanded);
        assertFalse(expanded.contains(" it "), "the pronoun should be substituted: " + expanded);
    }

    // ——— event-aware deictic subject extraction ———

    /**
     * Regression: the generic "the &lt;noun phrase&gt;" branch must win over the
     * event-keyword fallback. Matching the bare keyword "placement" on
     * "how can i prepare for the upcoming placement drive" used to recover only the
     * trailing token "drive", so the pronoun resolved to "the drive" and the
     * existing pronounResolvesAgainstPriorSubject expectation broke.
     */
    @Test
    void genericNounPhraseWinsOverEventKeyword() throws Exception {
        String subject = invokeExtractSubject("how can i prepare for the upcoming placement drive");
        assertEquals("the placement drive", subject,
                "the full noun phrase must survive, not just the trailing token: " + subject);
    }

    @Test
    void eventOnlySubjectFallsBackToEventKeyword() throws Exception {
        String subject = invokeExtractSubject("any upcoming hackathon events");
        assertEquals("the hackathon", subject,
                "a bare event with no 'the <noun>' chunk should resolve to the event type");
    }

    @Test
    void deicticFollowupOnBareEventResolves() throws Exception {
        String expanded = invokeExpandFollowup("when does it start", "any upcoming hackathon");
        assertTrue(expanded.contains("hackathon"),
                "pronoun should resolve to the event named in the prior turn: " + expanded);
        assertFalse(expanded.matches(".*\\bit\\b.*"),
                "the pronoun should be substituted: " + expanded);
    }

    @Test
    void workshopSubjectKeepsAdjective() throws Exception {
        assertEquals("the cybersecurity workshop",
                invokeExtractSubject("when is the cybersecurity workshop"));
    }

    // ——— reflection helpers for private methods ———

    private static boolean invokeIsFollowup(String q, String last) throws Exception {
        Method m = AgentOrchestrator.class.getDeclaredMethod("isLikelyFollowup", String.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, q, last);
    }

    private static String invokeExpandFollowup(String q, String last) throws Exception {
        AgentOrchestrator orch = new AgentOrchestrator(null, null, null, null, null, null);
        Method m = AgentOrchestrator.class.getDeclaredMethod("expandFollowup", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(orch, q, last);
    }

    private static String invokeExtractSubject(String last) throws Exception {
        Method m = AgentOrchestrator.class.getDeclaredMethod("extractSubject", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, last);
    }
}
