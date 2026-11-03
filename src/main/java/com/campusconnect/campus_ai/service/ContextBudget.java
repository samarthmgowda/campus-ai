package com.campusconnect.campus_ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Deterministic prompt-size budgeting for local-model RAG.
 *
 * Why this exists
 * ───────────────
 * Ollama silently truncates a prompt that exceeds the model's context window
 * (`num_ctx`, default 2048 for many local models). Truncation is not surfaced as
 * an error: the model simply never sees the tail of the context and then answers
 * from partial evidence — or abstains — with no indication that anything was
 * dropped. For a RAG prompt whose numbered passages sit at the *end* of the
 * message, the lost tail is exactly the citations the answer depends on.
 *
 * A real tokenizer (tiktoken / HF) is a heavy dependency for a single guard, so
 * this uses the standard heuristic of ~4 characters per token for English. That
 * is deliberately conservative: for prompt-size estimation an overestimate is
 * safe (it drops a passage that maybe would have fit) while an underestimate is
 * not (it lets a prompt through that gets silently truncated).
 *
 * Scope: estimation and selection only. No I/O, no Spring, no state — so the
 * whole thing is unit-testable.
 */
public final class ContextBudget {

    /**
     * Conservative characters-per-token ratio for English text.
     *
     * Natural English averages roughly 4 chars/token with BPE tokenizers, but
     * prose with long numbers, URLs or code tokenizes worse. 4.0 with the
     * {@code Math.ceil} in {@link #estimateTokens} biases toward overestimating
     * the token count, which is the safe direction for a truncation guard.
     */
    public static final double CHARS_PER_TOKEN = 4.0;

    private ContextBudget() {
        // static utility
    }

    /** Estimated token count for one string; 0 for null/blank. */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Estimated token count for the concatenation of several parts.
     *
     * Named distinctly rather than overloading {@link #estimateTokens(String)}:
     * an overload taking varargs makes a bare {@code estimateTokens(null)} call
     * ambiguous at compile time, which is a trap for callers.
     */
    public static int estimateTotal(String... parts) {
        if (parts == null) return 0;
        int total = 0;
        for (String p : parts) total += estimateTokens(p);
        return total;
    }

    /**
     * The result of fitting a candidate list into a token budget.
     *
     * @param kept        the prefix of the input that fits, in input order
     * @param dropped     how many trailing items were excluded
     * @param tokens      estimated tokens of the kept items
     * @param truncated   true when at least one item was dropped, i.e. the model
     *                    will see fewer passages than retrieval produced
     */
    public record Sized<T>(List<T> kept, int dropped, int tokens, boolean truncated) {
        /** True when nothing was dropped. */
        public boolean complete() {
            return !truncated;
        }
    }

    /**
     * Keep as many leading items as fit within {@code budgetTokens}.
     *
     * Items are considered in the order given — callers pass them already ranked,
     * so a truncation drops the *least* relevant passages rather than an arbitrary
     * slice. This is the whole point: bounded context that degrades by relevance.
     *
     * At least one item is always kept when the input is non-empty, even if that
     * single item alone blows the budget. Returning an empty context would turn a
     * size problem into a "no documents found" abstention, which is a worse and
     * more confusing failure than an over-long prompt.
     *
     * @param items       candidates, best-first
     * @param render      maps an item to the exact text that will occupy the prompt;
     *                    the budget is spent on the rendered form, not the raw item,
     *                    because that is what actually reaches the model
     * @param budgetTokens positive token budget for the rendered items
     */
    public static <T> Sized<T> fit(List<T> items,
                                   Function<T, String> render,
                                   int budgetTokens) {
        if (items == null || items.isEmpty()) {
            return new Sized<>(List.of(), 0, 0, false);
        }
        if (render == null) throw new IllegalArgumentException("render must not be null");

        // A non-positive budget still yields the top item: see the "at least one"
        // note above. Treat the effective floor as one item's worth of space.
        List<T> kept = new ArrayList<>();
        int used = 0;
        int budget = budgetTokens;

        for (T item : items) {
            String rendered = render.apply(item);
            int cost = estimateTokens(rendered);
            if (kept.isEmpty()) {
                // Always admit the first item, whatever it costs.
                kept.add(item);
                used += cost;
                continue;
            }
            if (used + cost > budget) break;
            kept.add(item);
            used += cost;
        }

        int dropped = items.size() - kept.size();
        return new Sized<>(kept, dropped, used, dropped > 0);
    }
}
