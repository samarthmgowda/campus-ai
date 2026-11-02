package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves an event mentioned in free text and infers what the event is "about"
 * (its domain) so teammate discovery can be driven by the event, not just by a
 * project the user already has.
 *
 * Two pure-ish responsibilities, deliberately separated from scoring so they are
 * unit-testable without a database:
 *   1. {@link #resolveEvent(String)} — fuzzy-match the event named in a query
 *      ("the auto hack hacathon") against known events, preferring the nearest
 *      upcoming event when several titles are equally close.
 *   2. {@link #inferDomain(Event)} — map an event's title + description onto a
 *      {@link Domain} of representative skills, using a small topic lexicon.
 *      Returns the generic SOFTWARE domain when nothing specific is recognised,
 *      so downstream code always has a usable skill set.
 */
@Service
public class EventDomainService {

    /** A recognised event domain: a label, the representative skills it needs, and topic words. */
    public record Domain(String label, List<String> skills) {}

    /**
     * Topic lexicon. Each entry: label → keywords that signal the topic (matched as
     * whole words, case-insensitively) and the canonical skills a team for that
     * topic typically needs. Order matters only for tie-breaks: earlier = stronger
     * signal, so more specific topics are checked first.
     */
    private static final List<Topic> TOPICS = List.of(
            new Topic("AI / Machine Learning",
                    List.of("ai", "artificial intelligence", "machine learning", "ml", "deep learning",
                            "nlp", "computer vision", "llm", "genai", "data science", "neural"),
                    List.of("Python", "Machine Learning", "PyTorch", "TensorFlow", "Deep Learning")),
            new Topic("Cybersecurity",
                    List.of("cyber", "cybersecurity", "security", "infosec", "ctf", "penetration",
                            "hacking", "forensics"),
                    List.of("Python", "PostgreSQL", "Docker", "REST API")),
            new Topic("Cloud / DevOps",
                    List.of("cloud", "devops", "kubernetes", "docker", "aws", "azure", "infra",
                            "infrastructure", "deployment"),
                    List.of("AWS", "Docker", "Kubernetes", "CI/CD", "PostgreSQL")),
            new Topic("Web / Frontend",
                    List.of("web", "frontend", "front-end", "ui", "ux", "react", "javascript",
                            "website", "fullstack", "full-stack"),
                    List.of("React", "JavaScript", "TypeScript", "Figma", "Node.js")),
            new Topic("Mobile / App",
                    List.of("mobile", "android", "ios", "flutter", "app development", "appathon"),
                    List.of("Java", "React", "REST API", "Figma")),
            new Topic("Blockchain / Web3",
                    List.of("blockchain", "web3", "crypto", "solidity", "smart contract", "nft"),
                    List.of("JavaScript", "Node.js", "PostgreSQL", "REST API")),
            new Topic("Data / Analytics",
                    List.of("data", "analytics", "visualization", "dashboard", "sql", "bi"),
                    List.of("Python", "PostgreSQL", "Machine Learning", "REST API")),
            new Topic("Design / Product",
                    List.of("design", "product", "ideathon", "pitch", "prototype"),
                    List.of("Figma", "React", "JavaScript")));

    /** Fallback when the event text signals nothing specific. */
    private static final Domain GENERIC =
            new Domain("Software / Hackathon", List.of("Java", "Python", "React", "JavaScript", "Node.js"));

    private final EventRepository eventRepository;

    public EventDomainService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // ——— 1. event resolution ———

    /**
     * Find the event referenced by a free-text query. Strategy: score every
     * non-hidden event by how many of its meaningful title tokens appear in the
     * query; take the best score; among ties prefer the nearest upcoming event
     * (future before past), then the earliest date. Returns empty when no event
     * shares any distinctive token with the query.
     */
    public Optional<Event> resolveEvent(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String q = text.toLowerCase(Locale.ROOT);
        Set<String> queryTokens = tokens(q);
        if (queryTokens.isEmpty()) return Optional.empty();

        LocalDate today = LocalDate.now();
        Event best = null;
        double bestScore = 0;
        for (Event e : eventRepository.findAll()) {
            if (e == null || e.isHidden()) continue;
            Set<String> titleTokens = distinctiveTokens(e.getTitle());
            if (titleTokens.isEmpty()) continue;
            int overlap = 0;
            for (String t : titleTokens) {
                if (queryTokens.contains(t)) overlap++;
            }
            if (overlap == 0) continue;
            // Fraction of the title matched: avoids a long title out-scoring a short
            // exact match just by having more tokens to hit.
            double score = (double) overlap / titleTokens.size();
            if (score > bestScore || (score == bestScore && betterEvent(e, best, today))) {
                bestScore = score;
                best = e;
            }
        }
        // Require a meaningful share of the title — a single incidental word from a
        // long title is not enough to claim the user meant that event.
        if (best == null || bestScore < 0.5) return Optional.empty();
        return Optional.of(best);
    }

    private static boolean betterEvent(Event candidate, Event current, LocalDate today) {
        if (current == null) return true;
        boolean cUpcoming = candidate.getEventDate() != null && !candidate.getEventDate().isBefore(today);
        boolean curUpcoming = current.getEventDate() != null && !current.getEventDate().isBefore(today);
        if (cUpcoming != curUpcoming) return cUpcoming; // prefer upcoming
        if (candidate.getEventDate() == null) return false;
        if (current.getEventDate() == null) return true;
        return candidate.getEventDate().isBefore(current.getEventDate()); // earliest
    }

    // ——— 2. domain inference ———

    /**
     * Infer the domain of an event from its title and description. Picks the topic
     * with the most keyword hits (ties broken by lexicon order = specificity);
     * falls back to {@link #GENERIC} when nothing matches.
     */
    public Domain inferDomain(Event event) {
        if (event == null) return GENERIC;
        String text = ((event.getTitle() == null ? "" : event.getTitle()) + " "
                + (event.getDescription() == null ? "" : event.getDescription())
                + " " + (event.getType() == null ? "" : event.getType()))
                .toLowerCase(Locale.ROOT);

        Topic best = null;
        int bestHits = 0;
        for (Topic topic : TOPICS) {
            int hits = 0;
            for (String kw : topic.keywords) {
                if (containsPhrase(text, kw)) hits++;
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = topic;
            }
        }
        if (best == null) return GENERIC;
        return new Domain(best.label, List.copyOf(best.skills));
    }

    /**
     * Interest tokens for a domain: the label words plus the representative skills,
     * normalized to lowercase. Used for the ProjectInterest component of the score.
     */
    public Set<String> domainTokens(Domain domain, SkillGraphService skillGraphService) {
        Set<String> tokens = new LinkedHashSet<>();
        if (domain == null) return tokens;
        for (String w : domain.label().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() >= 3) tokens.add(w);
        }
        for (String s : domain.skills()) {
            String n = skillGraphService.normalize(s);
            if (n == null) n = s;
            tokens.add(n.toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    /** Free-text describing a domain, for the optional semantic arm. */
    public static String domainText(Event event, Domain domain) {
        StringBuilder sb = new StringBuilder();
        if (event != null && event.getTitle() != null) sb.append(event.getTitle()).append(' ');
        if (event != null && event.getDescription() != null) sb.append(event.getDescription()).append(' ');
        if (domain != null) {
            sb.append(domain.label()).append(' ').append(String.join(", ", domain.skills()));
        }
        return sb.toString().trim();
    }

    /**
     * Whether a project's domain/required-skills look like the event's domain.
     * Returns a 0..1 overlap: the fraction of the domain's representative skills
     * the project requires, with a bonus when the project's domain text mentions
     * the topic label. Comparing canonical skills keeps this robust to aliases.
     */
    public double projectMatchScore(ProjectLike project, Domain domain, SkillGraphService skillGraphService) {
        if (project == null || domain == null || domain.skills().isEmpty()) return 0.0;
        Set<String> projectSkills = new LinkedHashSet<>();
        for (String s : splitComma(project.requiredSkillsCsv())) {
            String n = skillGraphService.normalize(s);
            if (n == null) n = s;
            projectSkills.add(n.toLowerCase(Locale.ROOT));
        }
        int hits = 0;
        for (String s : domain.skills()) {
            String n = skillGraphService.normalize(s);
            if (n == null) n = s;
            if (projectSkills.contains(n.toLowerCase(Locale.ROOT))) hits++;
        }
        double skillOverlap = (double) hits / domain.skills().size();

        // Domain-label bonus: if the project's domain text names the topic.
        String projDomain = project.domainText() == null ? "" : project.domainText().toLowerCase(Locale.ROOT);
        double labelBonus = 0.0;
        for (String w : domain.label().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() >= 3 && containsWholeWord(projDomain, w)) { labelBonus = 0.25; break; }
        }
        return Math.min(1.0, skillOverlap + labelBonus);
    }

    /** Minimal view of a project so this service does not depend on the entity/JPA. */
    public interface ProjectLike {
        String domainText();
        String requiredSkillsCsv();
    }

    // ——— helpers ———

    private record Topic(String label, List<String> keywords, List<String> skills) {}

    private static Set<String> tokens(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : s.split("[^a-z0-9]+")) {
            if (t.length() >= 2) out.add(t);
        }
        return out;
    }

    /** Title tokens with stopwords and the generic event word removed. */
    private static Set<String> distinctiveTokens(String title) {
        if (title == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String t : title.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (t.length() < 3) continue;
            if (TITLE_STOPWORDS.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    private static final Set<String> TITLE_STOPWORDS = Set.of(
            "the", "and", "for", "with", "hackathon", "workshop", "seminar", "event", "events",
            "2026", "2025", "2024", "annual", "national", "college", "campus");

    /**
     * Phrase match: a multi-word keyword ("machine learning") is matched as a
     * whitespace-normalised substring so punctuation/extra spacing does not break
     * it; a single word is matched on a whole-word boundary so "ai" does not fire
     * on "main".
     */
    private static boolean containsPhrase(String haystack, String keyword) {
        if (haystack == null || keyword == null || keyword.isEmpty()) return false;
        if (keyword.indexOf(' ') >= 0) {
            String norm = keyword.replaceAll("\\s+", " ");
            return haystack.replaceAll("\\s+", " ").contains(norm);
        }
        return containsWholeWord(haystack, keyword);
    }

    private static boolean containsWholeWord(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return false;
        int idx = haystack.indexOf(needle);
        while (idx != -1) {
            boolean leftOk = idx == 0 || !Character.isLetterOrDigit(haystack.charAt(idx - 1));
            int end = idx + needle.length();
            boolean rightOk = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (leftOk && rightOk) return true;
            idx = haystack.indexOf(needle, idx + 1);
        }
        return false;
    }

    private static List<String> splitComma(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
