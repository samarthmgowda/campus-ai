package com.campusconnect.campus_ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ContextBudgetTest {

    @Test
    void estimateTokens_nullAndBlank_areZero() {
        assertEquals(0, ContextBudget.estimateTokens(null));
        assertEquals(0, ContextBudget.estimateTokens(""));
    }

    @Test
    void estimateTokens_usesFourCharsPerToken() {
        // 8 chars / 4 = 2 tokens
        assertEquals(2, ContextBudget.estimateTokens("abcdefgh"));
        // 5 chars rounds UP to 2 (conservative: never underestimate)
        assertEquals(2, ContextBudget.estimateTokens("abcde"));
        // 4 chars = exactly 1
        assertEquals(1, ContextBudget.estimateTokens("abcd"));
    }

    @Test
    void estimateTokens_roundsUpNeverDown() {
        // The guard is only safe if it over-estimates. One char must not be free.
        assertEquals(1, ContextBudget.estimateTokens("a"));
        assertEquals(1, ContextBudget.estimateTokens("ab"));
        assertEquals(1, ContextBudget.estimateTokens("abc"));
    }

    @Test
    void estimateTotal_sumsParts() {
        // "abcd" and "efgh" are 4 chars each = 1 token each.
        assertEquals(2, ContextBudget.estimateTotal("abcd", "efgh"));
        assertEquals(1, ContextBudget.estimateTotal("abcd", null));
        assertEquals(0, ContextBudget.estimateTotal((String[]) null));
    }

    @Test
    void fit_emptyInput_isNotTruncated() {
        var sized = ContextBudget.fit(List.<String>of(), Function.identity(), 100);
        assertTrue(sized.kept().isEmpty());
        assertEquals(0, sized.dropped());
        assertFalse(sized.truncated());
        assertTrue(sized.complete());
    }

    @Test
    void fit_everythingFits_keepsAllInOrder() {
        List<String> items = List.of("abcd", "efgh", "ijkl"); // 1 token each
        var sized = ContextBudget.fit(items, Function.identity(), 100);
        assertEquals(items, sized.kept());
        assertEquals(0, sized.dropped());
        assertFalse(sized.truncated());
    }

    @Test
    void fit_dropsFromTheEndPreservingRankOrder() {
        // Each item is 8 chars = 2 tokens; budget 4 admits exactly the top two.
        List<String> items = List.of("aaaaaaaa", "bbbbbbbb", "cccccccc", "dddddddd");
        var sized = ContextBudget.fit(items, Function.identity(), 4);

        assertEquals(List.of("aaaaaaaa", "bbbbbbbb"), sized.kept(),
                "must keep the leading (best-ranked) items, not an arbitrary slice");
        assertEquals(2, sized.dropped());
        assertTrue(sized.truncated());
        assertEquals(4, sized.tokens());
    }

    @Test
    void fit_spendsBudgetOnRenderedFormNotRawItem() {
        // The item is tiny, but its rendered prompt form is what costs tokens.
        // A budget that would fit the raw items must still truncate once the
        // renderer expands them — otherwise the guard under-counts exactly as
        // the real prompt would.
        List<String> ids = List.of("1", "2", "3");
        Function<String, String> render = id -> "Document: X | Content: " + "y".repeat(40);

        var sized = ContextBudget.fit(ids, render, 12); // render ~60 chars = 15 tokens

        assertEquals(List.of("1"), sized.kept(),
                "first item always kept; second cannot fit");
        assertEquals(2, sized.dropped());
    }

    @Test
    void fit_alwaysKeepsAtLeastOneEvenWhenItAloneExceedsBudget() {
        // Returning an empty context would surface as "no documents found",
        // which is a worse failure than an over-long prompt.
        List<String> items = List.of("z".repeat(4000), "b");
        var sized = ContextBudget.fit(items, Function.identity(), 1);

        assertEquals(List.of(items.get(0)), sized.kept());
        assertEquals(1, sized.dropped());
        assertTrue(sized.truncated());
    }

    @Test
    void fit_zeroBudget_keepsOnlyTheTopItem() {
        List<String> items = List.of("aaaa", "bbbb");
        var sized = ContextBudget.fit(items, Function.identity(), 0);

        assertEquals(List.of("aaaa"), sized.kept());
        assertEquals(1, sized.dropped());
    }

    @Test
    void fit_exactBoundary_keepsTheItemThatExactlyFits() {
        List<String> items = List.of("aaaa", "bbbb"); // 1 token each
        var sized = ContextBudget.fit(items, Function.identity(), 2);

        assertEquals(items, sized.kept(), "budget == total must not truncate");
        assertFalse(sized.truncated());
    }

    @Test
    void fit_oneTokenOverBoundary_truncates() {
        List<String> items = List.of("aaaa", "bbbb"); // 2 tokens
        var sized = ContextBudget.fit(items, Function.identity(), 1);

        assertEquals(List.of("aaaa"), sized.kept());
        assertTrue(sized.truncated());
    }

    @Test
    void fit_nullRender_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ContextBudget.fit(List.of("a"), null, 10));
    }

    @Test
    void fit_nullItems_isEmptyNotThrow() {
        var sized = ContextBudget.fit(null, Function.identity(), 10);
        assertTrue(sized.kept().isEmpty());
        assertFalse(sized.truncated());
    }
}
