package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AlumniService {

    private final AlumniRepository repository;
    private final SkillGraphService skillGraphService;

    public AlumniService(AlumniRepository repository, SkillGraphService skillGraphService) {
        this.repository = repository;
        this.skillGraphService = skillGraphService;
    }

    public List<Alumni> search(String skill, String availability, String role) {
        boolean hasSkill = skill != null && !skill.isBlank();
        boolean hasAvailability = availability != null && !availability.isBlank();
        boolean hasRole = role != null && !role.isBlank();

        if (!hasSkill && !hasAvailability && !hasRole) {
            return repository.findAll();
        }

        // Start with all alumni and apply filters
        return repository.findAll().stream()
                .filter(a -> !hasRole || a.getCurrentRole() != null && a.getCurrentRole().toLowerCase().contains(role.trim().toLowerCase()))
                .filter(a -> !hasSkill || hasSkillAliasAware(a.getSkills(), skill.trim().toLowerCase()))
                .filter(a -> !hasAvailability || "MENTORING".equalsIgnoreCase(a.getAvailability()) || "BOTH".equalsIgnoreCase(a.getAvailability()))
                .collect(Collectors.toList());
    }

    private List<Alumni> findByWholeSkill(String skill) {
        String canonical = skillGraphService.normalize(skill.trim());
        if (canonical == null) canonical = skill.trim();
        String wantedLower = canonical.toLowerCase(Locale.ROOT);
        return repository.findAll().stream()
                .filter(a -> hasSkillAliasAware(a.getSkills(), wantedLower))
                .collect(Collectors.toList());
    }

    private boolean hasSkillAliasAware(String skillsCsv, String wantedLower) {
        if (skillsCsv == null || wantedLower == null) return false;
        for (String part : skillsCsv.split(",")) {
            String raw = part.trim();
            if (raw.isEmpty()) continue;
            String norm = skillGraphService.normalize(raw);
            if (norm == null) norm = raw;
            if (norm.equalsIgnoreCase(wantedLower) || norm.toLowerCase(Locale.ROOT).equals(wantedLower)) return true;
            if (raw.equalsIgnoreCase(wantedLower)) return true;
        }
        return false;
    }

    static boolean hasWholeSkill(String skillsCsv, String wanted) {
        if (skillsCsv == null || wanted == null) return false;
        String w = wanted.trim();
        if (w.isEmpty()) return false;
        for (String part : skillsCsv.split(",")) {
            if (part.trim().equalsIgnoreCase(w)) return true;
        }
        return false;
    }

    public List<SkillGraphService.RelatedSkill> relatedSkills(String skill, int limit) {
        String canonical = skillGraphService.normalize(skill);
        if (canonical == null) return List.of();
        return skillGraphService.getRelatedSkills(canonical, limit);
    }
}
