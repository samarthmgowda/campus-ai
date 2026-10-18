package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Skill;
import com.campusconnect.campus_ai.entity.SkillAlias;
import com.campusconnect.campus_ai.entity.SkillCoOccurrence;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.SkillAliasRepository;
import com.campusconnect.campus_ai.repository.SkillCoOccurrenceRepository;
import com.campusconnect.campus_ai.repository.SkillRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillGraphService {

    private final SkillRepository skillRepository;
    private final SkillAliasRepository aliasRepository;
    private final SkillCoOccurrenceRepository coOccurrenceRepository;
    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;

    // Known aliases mapping (lowercase alias -> canonical name)
    private static final Map<String, String> BUILTIN_ALIASES = Map.ofEntries(
            Map.entry("js", "JavaScript"), Map.entry("ts", "TypeScript"),
            Map.entry("react.js", "React"), Map.entry("reactjs", "React"),
            Map.entry("vue.js", "Vue"), Map.entry("vuejs", "Vue"),
            Map.entry("node.js", "Node.js"), Map.entry("nodejs", "Node.js"),
            Map.entry("express.js", "Express"), Map.entry("expressjs", "Express"),
            Map.entry("next.js", "Next.js"), Map.entry("nextjs", "Next.js"),
            Map.entry("ml", "Machine Learning"), Map.entry("ai", "Artificial Intelligence"),
            Map.entry("dl", "Deep Learning"), Map.entry("cv", "Computer Vision"),
            Map.entry("nlp", "Natural Language Processing"), Map.entry("cnn", "Convolutional Neural Network"),
            Map.entry("rnn", "Recurrent Neural Network"), Map.entry("transformer", "Transformers"),
            Map.entry("pytorch", "PyTorch"), Map.entry("tf", "TensorFlow"),
            Map.entry("sklearn", "Scikit-learn"), Map.entry("scikit", "Scikit-learn"),
            Map.entry("k8s", "Kubernetes"), Map.entry("kubernetes", "Kubernetes"),
            Map.entry("aws", "AWS"), Map.entry("gcp", "Google Cloud Platform"),
            Map.entry("az", "Azure"), Map.entry("azure", "Azure"),
            Map.entry("ci/cd", "CI/CD"), Map.entry("cicd", "CI/CD"),
            Map.entry("rest", "REST API"), Map.entry("graphql", "GraphQL"),
            Map.entry("db", "Database"), Map.entry("sql", "SQL"),
            Map.entry("nosql", "NoSQL"), Map.entry("postgres", "PostgreSQL"),
            Map.entry("mongo", "MongoDB"), Map.entry("redis", "Redis"),
            Map.entry("docker", "Docker"), Map.entry("kafka", "Kafka"),
            Map.entry("spark", "Apache Spark"), Map.entry("hadoop", "Hadoop"),
            Map.entry("git", "Git"), Map.entry("linux", "Linux"),
            Map.entry("figma", "Figma"), Map.entry("ui/ux", "UI/UX"),
            Map.entry("html", "HTML"), Map.entry("css", "CSS"),
            Map.entry("c++", "C++"), Map.entry("cpp", "C++"),
            Map.entry("c#", "C#"), Map.entry("dotnet", ".NET"),
            Map.entry("spring", "Spring Boot"), Map.entry("spring boot", "Spring Boot"),
            Map.entry("django", "Django"), Map.entry("flask", "Flask"),
            Map.entry("fastapi", "FastAPI"), Map.entry("rails", "Ruby on Rails"),
            Map.entry("go", "Go"), Map.entry("golang", "Go"),
            Map.entry("rust", "Rust"), Map.entry("ruby", "Ruby"),
            Map.entry("php", "PHP"), Map.entry("laravel", "Laravel")
    );

    public SkillGraphService(SkillRepository skillRepository,
                              SkillAliasRepository aliasRepository,
                              SkillCoOccurrenceRepository coOccurrenceRepository,
                              StudentRepository studentRepository,
                              AlumniRepository alumniRepository) {
        this.skillRepository = skillRepository;
        this.aliasRepository = aliasRepository;
        this.coOccurrenceRepository = coOccurrenceRepository;
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
    }

    // ——— Normalization ———

    /**
     * Normalize a raw skill string to its canonical form.
     * 1. Check built-in aliases
     * 2. Check DB aliases
     * 3. Check exact skill name (case-insensitive)
     * 4. Return original if no match (preserve for new skills)
     */
    public String normalize(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        String lower = trimmed.toLowerCase(Locale.ROOT);

        // 1. Built-in aliases
        if (BUILTIN_ALIASES.containsKey(lower)) {
            return BUILTIN_ALIASES.get(lower);
        }

        // 2. DB aliases
        Optional<SkillAlias> dbAlias = aliasRepository.findByAliasIgnoreCase(trimmed);
        if (dbAlias.isPresent()) {
            return dbAlias.get().getSkill().getName();
        }

        // 3. Exact skill name match
        Optional<Skill> skill = skillRepository.findByName(trimmed);
        if (skill.isPresent()) {
            return skill.get().getName();
        }

        // 4. Case-insensitive exact match
        Optional<Skill> skillCi = skillRepository.findAll().stream()
                .filter(s -> s.getName().equalsIgnoreCase(trimmed))
                .findFirst();
        if (skillCi.isPresent()) return skillCi.get().getName();

        // 5. No match - return original (will be created as new canonical if needed)
        return trimmed;
    }

    /**
     * Normalize a comma-separated skill string to a sorted list of canonical skills.
     * Deduplicates after normalization.
     */
    public List<String> normalizeCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    // ——— Graph building from profiles ———

    /**
     * Rebuild the entire skill graph from current Student and Alumni profiles.
     * Clears existing co-occurrences and aliases (keeps built-in aliases in code only).
     * Creates canonical Skill rows, then counts co-occurrences per profile.
     */
    public void rebuildFromProfiles() {
        // 1. Collect all unique raw skills from students + alumni
        Map<String, Integer> rawSkillCounts = new HashMap<>();
        List<Set<String>> profileSkills = new ArrayList<>();

        for (Student s : studentRepository.findAll()) {
            Set<String> normalized = new LinkedHashSet<>(normalizeCsv(s.getSkills()));
            profileSkills.add(normalized);
            for (String sk : normalized) {
                rawSkillCounts.put(sk, rawSkillCounts.getOrDefault(sk, 0) + 1);
            }
        }
        for (Alumni a : alumniRepository.findAll()) {
            Set<String> normalized = new LinkedHashSet<>(normalizeCsv(a.getSkills()));
            profileSkills.add(normalized);
            for (String sk : normalized) {
                rawSkillCounts.put(sk, rawSkillCounts.getOrDefault(sk, 0) + 1);
            }
        }

        // 2. Ensure canonical Skill rows exist
        Map<String, Skill> canonical = new HashMap<>();
        for (String name : rawSkillCounts.keySet()) {
            Skill skill = skillRepository.findByName(name).orElseGet(() -> {
                Skill sk = new Skill(name, inferCategory(name));
                return skillRepository.save(sk);
            });
            canonical.put(name, skill);
        }

        // 3. Clear old co-occurrences
        coOccurrenceRepository.deleteAll();

        // 4. Compute pairwise co-occurrences per profile
        Map<Pair<Long, Long>, Integer> pairCounts = new HashMap<>();
        for (Set<String> skills : profileSkills) {
            List<String> skillList = new ArrayList<>(skills);
            for (int i = 0; i < skillList.size(); i++) {
                Skill sa = canonical.get(skillList.get(i));
                for (int j = i + 1; j < skillList.size(); j++) {
                    Skill sb = canonical.get(skillList.get(j));
                    if (sa.getId().equals(sb.getId())) continue;
                    long aId = Math.min(sa.getId(), sb.getId());
                    long bId = Math.max(sa.getId(), sb.getId());
                    Pair<Long, Long> key = new Pair<>(aId, bId);
                    pairCounts.merge(key, 1, Integer::sum);
                }
            }
        }

        // 5. Persist co-occurrences
        for (Map.Entry<Pair<Long, Long>, Integer> e : pairCounts.entrySet()) {
            Skill sa = skillRepository.findById(e.getKey().first).orElseThrow();
            Skill sb = skillRepository.findById(e.getKey().second).orElseThrow();
            SkillCoOccurrence co = new SkillCoOccurrence(sa, sb, e.getValue());
            coOccurrenceRepository.save(co);
        }
    }

    // ——— Query API ———

    /**
     * Get related skills for a canonical skill, sorted by co-occurrence count desc.
     * Returns list of {skillName, count}.
     */
    public List<RelatedSkill> getRelatedSkills(String canonicalSkillName, int limit) {
        Skill skill = skillRepository.findByName(canonicalSkillName).orElse(null);
        if (skill == null) return List.of();

        List<SkillCoOccurrence> asA = coOccurrenceRepository.findBySkillAId(skill.getId());
        List<SkillCoOccurrence> asB = coOccurrenceRepository.findBySkillBId(skill.getId());

        List<RelatedSkill> related = new ArrayList<>();
        for (SkillCoOccurrence co : asA) {
            related.add(new RelatedSkill(co.getSkillB().getName(), co.getCount()));
        }
        for (SkillCoOccurrence co : asB) {
            related.add(new RelatedSkill(co.getSkillA().getName(), co.getCount()));
        }
        related.sort(Comparator.comparingInt(RelatedSkill::count).reversed());
        return related.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Get all canonical skills (for dropdowns, etc.)
     */
    public List<String> getAllCanonicalSkills() {
        return skillRepository.findAll().stream()
                .map(Skill::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * Get skills by category
     */
    public List<String> getSkillsByCategory(String category) {
        return skillRepository.findByCategory(category).stream()
                .map(Skill::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * Dashboard view: top skills by profile count, top related pairs,
     * category distribution, and "skills to grow" for a given user
     * (their skills' related skills they don't yet have).
     */
    public Map<String, Object> getDashboardData(Long userId) {
        // 1. Top 15 skills by how many profiles list them
        Map<String, Long> skillCounts = new HashMap<>();
        for (Student s : studentRepository.findAll()) {
            for (String sk : normalizeCsv(s.getSkills())) {
                skillCounts.merge(sk, 1L, Long::sum);
            }
        }
        for (Alumni a : alumniRepository.findAll()) {
            for (String sk : normalizeCsv(a.getSkills())) {
                skillCounts.merge(sk, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> topSkills = new ArrayList<>();
        skillCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .forEach(e -> topSkills.add(Map.of("skill", e.getKey(), "count", e.getValue())));

        // 2. Top 10 related skill pairs by co-occurrence count (from graph)
        List<Map<String, Object>> topPairs = new ArrayList<>();
        var allSkills = skillRepository.findAll();
        for (Skill sk : allSkills) {
            var related = getRelatedSkills(sk.getName(), 3);
            for (var r : related) {
                topPairs.add(Map.of("skill", sk.getName(), "related", r.skillName(), "count", r.count()));
            }
        }
        topPairs.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        List<Map<String, Object>> topRelated = topPairs.stream().limit(10).collect(Collectors.toList());

        // 3. Category distribution
        Map<String, Long> categoryDist = new HashMap<>();
        for (Skill sk : allSkills) {
            String cat = sk.getCategory() != null ? sk.getCategory() : "Other";
            // Count profiles that have at least one skill in this category
            categoryDist.merge(cat, 1L, Long::sum);
        }

        // 4. Skills to grow for user (related to their existing skills)
        List<Map<String, Object>> toGrow = new ArrayList<>();
        if (userId != null) {
            try {
                var studentOpt = studentRepository.findById(userId);
                if (studentOpt.isPresent()) {
                    Set<String> userSkills = new HashSet<>(normalizeCsv(studentOpt.get().getSkills()));
                    for (String userSkill : userSkills) {
                        String canonical = normalize(userSkill);
                        if (canonical == null) continue;
                        var related = getRelatedSkills(canonical, 5);
                        for (var r : related) {
                            if (!userSkills.contains(r.skillName())) {
                                toGrow.add(Map.of(
                                        "skill", r.skillName(),
                                        "fromSkill", canonical,
                                        "cooccurrence", r.count(),
                                        "category", inferCategory(r.skillName())));
                            }
                        }
                    }
                    toGrow.sort((a, b) -> Integer.compare(-(Integer) a.get("cooccurrence"), -(Integer) b.get("cooccurrence")));
                    toGrow = toGrow.stream().limit(10).collect(Collectors.toList());
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> dash = new LinkedHashMap<>();
        dash.put("topSkills", topSkills);
        dash.put("topRelated", topRelated);
        dash.put("categoryDistribution", categoryDist);
        dash.put("skillsToGrow", toGrow);
        dash.put("totalSkills", allSkills.size());
        dash.put("totalProfiles", studentRepository.findAll().size() + alumniRepository.findAll().size());
        return dash;
    }


    // ——— Glossary / search helpers ———

    /** Glossary for a raw skill: canonical, category, aliases, related, profileCount */
    public Map<String, Object> getGlossary(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("skill required");
        String canonical = normalize(raw.trim());
        if (canonical == null) throw new IllegalArgumentException("Invalid skill");
        Skill skill = skillRepository.findByName(canonical).orElse(null);
        String category = skill != null ? skill.getCategory() : inferCategory(canonical);
        java.util.Set<String> aliases = new java.util.LinkedHashSet<>();
        for (var e : BUILTIN_ALIASES.entrySet()) {
            if (e.getValue().equalsIgnoreCase(canonical)) aliases.add(e.getKey());
        }
        if (skill != null) {
            for (var a : aliasRepository.findBySkillId(skill.getId())) {
                if (a.getAlias() != null) aliases.add(a.getAlias());
            }
        }
        var related = getRelatedSkills(canonical, 8);
        long studentCount = studentRepository.findAll().stream()
                .filter(s -> hasSkillAliasAwareLocal(s.getSkills(), canonical)).count();
        long alumniCount = alumniRepository.findAll().stream()
                .filter(a -> hasSkillAliasAwareLocal(a.getSkills(), canonical)).count();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("query", raw.trim());
        m.put("canonical", canonical);
        m.put("category", category);
        m.put("aliases", aliases.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        m.put("related", related.stream().map(r -> Map.of("skill", r.skillName(), "count", r.count())).toList());
        m.put("studentCount", studentCount);
        m.put("alumniCount", alumniCount);
        m.put("profileCount", studentCount + alumniCount);
        return m;
    }

    /** Prefix/contains search over canonical names (case-insensitive) */
    public List<String> searchSkills(String q, int limit) {
        String needle = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        int lim = Math.max(1, Math.min(limit, 50));
        var all = getAllCanonicalSkills();
        if (needle.isEmpty()) return all.stream().limit(lim).toList();
        return all.stream()
                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).contains(needle))
                .limit(lim).toList();
    }

    private boolean hasSkillAliasAwareLocal(String csv, String canonical) {
        if (csv == null || canonical == null) return false;
        String wantedLower = canonical.toLowerCase(java.util.Locale.ROOT);
        for (String part : csv.split(",")) {
            String raw2 = part.trim(); if (raw2.isEmpty()) continue;
            String n = normalize(raw2); if (n == null) n = raw2;
            if (n.toLowerCase(java.util.Locale.ROOT).equals(wantedLower)) return true;
        }
        return false;
    }

    // ——— Helpers ———


    private String inferCategory(String skillName) {
        String lower = skillName.toLowerCase(Locale.ROOT);
        if (Set.of("java", "python", "javascript", "typescript", "c++", "c#", "go", "rust", "ruby", "php", "kotlin", "swift", "scala", "r").contains(lower)) {
            return "Language";
        }
        if (Set.of("react", "vue", "angular", "svelte", "next.js", "django", "flask", "fastapi", "spring boot", "express", "rails", "laravel", ".net").contains(lower)) {
            return "Framework";
        }
        if (Set.of("aws", "google cloud platform", "azure", "kubernetes", "docker", "kafka", "redis", "postgresql", "mongodb", "mysql", "sql", "nosql").contains(lower)) {
            return "Cloud/Infra";
        }
        if (Set.of("machine learning", "deep learning", "computer vision", "natural language processing", "pytorch", "tensorflow", "scikit-learn", "transformers").contains(lower)) {
            return "ML/AI";
        }
        if (Set.of("figma", "ui/ux", "html", "css", "design").contains(lower)) {
            return "Design";
        }
        if (Set.of("git", "linux", "ci/cd", "rest api", "graphql").contains(lower)) {
            return "Tooling";
        }
        return "Other";
    }

    // ——— Records ———

    public record RelatedSkill(String skillName, int count) {}

    private record Pair<A, B>(A first, B second) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pair<?, ?> p)) return false;
            return Objects.equals(first, p.first) && Objects.equals(second, p.second);
        }
        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
    }
}