package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Skill;
import com.campusconnect.campus_ai.entity.SkillAlias;
import com.campusconnect.campus_ai.entity.SkillCoOccurrence;
import com.campusconnect.campus_ai.repository.SkillAliasRepository;
import com.campusconnect.campus_ai.repository.SkillCoOccurrenceRepository;
import com.campusconnect.campus_ai.repository.SkillRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * TEMPORARY - remove after demo
 */
@RestController
@RequestMapping("/dev")
public class DevSeedSkillsController {

    private final SkillRepository skillRepository;
    private final SkillAliasRepository aliasRepository;
    private final SkillCoOccurrenceRepository coOccurrenceRepository;

    public DevSeedSkillsController(SkillRepository skillRepository,
                                   SkillAliasRepository aliasRepository,
                                   SkillCoOccurrenceRepository coOccurrenceRepository) {
        this.skillRepository = skillRepository;
        this.aliasRepository = aliasRepository;
        this.coOccurrenceRepository = coOccurrenceRepository;
    }

    /**
     * Seed canonical skills, aliases, and co-occurrences.
     * Idempotent: skips skills/aliases/co-occurrences that already exist.
     * POST /dev/seed-skills
     */
    @PostMapping("/seed-skills")
    public Map<String, Object> seedSkills() {
        int skillsCreated = 0, aliasesCreated = 0, coOccurrencesCreated = 0;
        List<String> skipped = new ArrayList<>();

        List<Object[]> data = List.of(
                new Object[]{"JavaScript", "Language", List.of("js", "javascript", "es6")},
                new Object[]{"Python", "Language", List.of("python", "py")},
                new Object[]{"Java", "Language", List.of("java")},
                new Object[]{"TypeScript", "Language", List.of("ts", "typescript")},
                new Object[]{"React", "Framework", List.of("react", "reactjs", "react.js")},
                new Object[]{"Node.js", "Framework", List.of("node", "nodejs", "node.js")},
                new Object[]{"Spring Boot", "Framework", List.of("spring", "spring boot")},
                new Object[]{"AWS", "Cloud/Infra", List.of("aws", "amazon web services")},
                new Object[]{"Docker", "Cloud/Infra", List.of("docker")},
                new Object[]{"Kubernetes", "Cloud/Infra", List.of("k8s", "kubernetes")},
                new Object[]{"PostgreSQL", "Cloud/Infra", List.of("postgres", "postgresql")},
                new Object[]{"MongoDB", "Cloud/Infra", List.of("mongo", "mongodb")},
                new Object[]{"Machine Learning", "ML/AI", List.of("ml", "machine learning")},
                new Object[]{"Deep Learning", "ML/AI", List.of("dl", "deep learning")},
                new Object[]{"PyTorch", "ML/AI", List.of("pytorch")},
                new Object[]{"TensorFlow", "ML/AI", List.of("tf", "tensorflow")},
                new Object[]{"Figma", "Design", List.of("figma")},
                new Object[]{"Git", "Tooling", List.of("git")},
                new Object[]{"CI/CD", "Tooling", List.of("ci/cd", "cicd")},
                new Object[]{"REST API", "Tooling", List.of("rest", "rest api")}
        );

        // Co-occurrence seed data: pairs of canonical skill names + weight
        List<Object[]> coOccurrences = List.of(
                new Object[]{"JavaScript", "React", 8},
                new Object[]{"JavaScript", "Node.js", 6},
                new Object[]{"JavaScript", "TypeScript", 5},
                new Object[]{"TypeScript", "React", 7},
                new Object[]{"TypeScript", "Node.js", 5},
                new Object[]{"Python", "Machine Learning", 9},
                new Object[]{"Python", "PyTorch", 4},
                new Object[]{"Python", "TensorFlow", 6},
                new Object[]{"Java", "Spring Boot", 10},
                new Object[]{"Java", "AWS", 3},
                new Object[]{"AWS", "Docker", 7},
                new Object[]{"Docker", "Kubernetes", 6},
                new Object[]{"AWS", "Kubernetes", 5},
                new Object[]{"PostgreSQL", "Docker", 3},
                new Object[]{"MongoDB", "Node.js", 4},
                new Object[]{"Machine Learning", "Deep Learning", 7},
                new Object[]{"Machine Learning", "PyTorch", 3},
                new Object[]{"Machine Learning", "TensorFlow", 5},
                new Object[]{"Deep Learning", "PyTorch", 3},
                new Object[]{"Deep Learning", "TensorFlow", 4}
        );

        // 1. Create canonical skills
        Map<String, Skill> canonical = new HashMap<>();
        for (Object[] row : data) {
            String name = (String) row[0];
            String category = (String) row[1];
            List<String> aliases = (List<String>) row[2];

            Skill skill = skillRepository.findByName(name).orElse(null);
            if (skill == null) {
                skill = new Skill(name, category);
                skill = skillRepository.save(skill);
                skillsCreated++;
            }
            canonical.put(name, skill);

            // 2. Create aliases (skip if already exists)
            for (String alias : aliases) {
                boolean exists = aliasRepository.findBySkillId(skill.getId()).stream()
                        .anyMatch(a -> a.getAlias() != null && a.getAlias().equalsIgnoreCase(alias));
                if (!exists) {
                    SkillAlias sa = new SkillAlias(alias, skill);
                    aliasRepository.save(sa);
                    aliasesCreated++;
                }
            }
        }

        // 3. Create co-occurrences
        Set<String> seenCoOccur = new HashSet<>();
        for (SkillCoOccurrence co : coOccurrenceRepository.findAll()) {
            if (co.getSkillA() != null && co.getSkillB() != null) {
                seenCoOccur.add(co.getSkillA().getName() + "|" + co.getSkillB().getName());
                seenCoOccur.add(co.getSkillB().getName() + "|" + co.getSkillA().getName());
            }
        }

        for (Object[] row : coOccurrences) {
            String nameA = (String) row[0];
            String nameB = (String) row[1];
            Integer count = (Integer) row[2];

            String key = nameA + "|" + nameB;
            if (seenCoOccur.contains(key)) continue;

            Skill skillA = canonical.get(nameA);
            Skill skillB = canonical.get(nameB);
            if (skillA == null || skillB == null) {
                skipped.add(nameA + "|" + nameB + " (missing skill)");
                continue;
            }

            SkillCoOccurrence co = new SkillCoOccurrence(skillA, skillB, count);
            coOccurrenceRepository.save(co);
            coOccurrencesCreated++;
            seenCoOccur.add(key);
            seenCoOccur.add(nameB + "|" + nameA);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("skillsCreated", skillsCreated);
        resp.put("aliasesCreated", aliasesCreated);
        resp.put("coOccurrencesCreated", coOccurrencesCreated);
        resp.put("skipped", skipped);
        return resp;
    }
}
