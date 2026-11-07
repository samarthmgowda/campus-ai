package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.SkillGraphService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/skills")
public class SkillGraphController {

    private final SkillGraphService skillGraphService;

    public SkillGraphController(SkillGraphService skillGraphService) {
        this.skillGraphService = skillGraphService;
    }

    /**
     * Normalize a raw skill string to canonical form.
     * GET /skills/normalize?raw=JS → "JavaScript"
     */
    @GetMapping("/normalize")
    public Map<String, String> normalize(@RequestParam String raw) {
        String canonical = skillGraphService.normalize(raw);
        return Map.of("raw", raw, "canonical", canonical != null ? canonical : "UNKNOWN");
    }

    /**
     * Normalize a comma-separated skill string.
     * GET /skills/normalize-csv?csv=js,react,ts → ["JavaScript","React","TypeScript"]
     */
    @GetMapping("/normalize-csv")
    public Map<String, List<String>> normalizeCsv(@RequestParam String csv) {
        List<String> canonical = skillGraphService.normalizeCsv(csv);
        return Map.of("canonical", canonical);
    }

    /**
     * Rebuild skill graph from current Student + Alumni profiles.
     * POST /skills/rebuild
     */
    @PostMapping("/rebuild")
    public Map<String, String> rebuild() {
        skillGraphService.rebuildFromProfiles();
        return Map.of("status", "rebuilt");
    }

    /**
     * Get related skills for a canonical skill.
     * GET /skills/related?skill=JavaScript&limit=10
     */
    @GetMapping("/related")
    public List<SkillGraphService.RelatedSkill> related(
            @RequestParam String skill,
            @RequestParam(defaultValue = "10") int limit) {
        return skillGraphService.getRelatedSkills(skill, limit);
    }

    /**
     * Get all canonical skills (for dropdowns).
     * GET /skills/all
     */
    @GetMapping("/all")
    public List<String> all() {
        return skillGraphService.getAllCanonicalSkills();
    }

    /**
     * Glossary: alias -> canonical + aliases + related + counts.
     * GET /skills/glossary?skill=js
     */
    @GetMapping("/glossary")
    public Map<String, Object> glossary(@RequestParam String skill) {
        return skillGraphService.getGlossary(skill);
    }

    /**
     * Search canonical skills by substring.
     * GET /skills/search?q=rea&limit=10
     */
    @GetMapping("/search")
    public List<String> search(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        return skillGraphService.searchSkills(q, limit);
    }

    /**
     * Skill graph dashboard: top skills, related pairs, category distribution,
     * and personalized "skills to grow" for the authenticated user.
     * GET /skills/dashboard
     */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Long userId = null;
        userId = AuthContext.idOrNull();
        return skillGraphService.getDashboardData(userId);
    }

    /**
     * Get skills by category.
     * GET /skills/category?category=Language
     */
    @GetMapping("/category")
    public List<String> byCategory(@RequestParam String category) {
        return skillGraphService.getSkillsByCategory(category);
    }
}