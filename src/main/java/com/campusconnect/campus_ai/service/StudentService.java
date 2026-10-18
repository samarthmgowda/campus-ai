package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ModerationReportRepository;
import com.campusconnect.campus_ai.repository.SkillEndorsementRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private final StudentRepository repository;
    private final SkillGraphService skillGraphService;
    private final SkillEndorsementRepository endorsementRepository;
    private final ModerationReportRepository moderationRepository;

    public StudentService(StudentRepository repository,
                          SkillGraphService skillGraphService,
                          SkillEndorsementRepository endorsementRepository,
                          ModerationReportRepository moderationRepository) {
        this.repository = repository;
        this.skillGraphService = skillGraphService;
        this.endorsementRepository = endorsementRepository;
        this.moderationRepository = moderationRepository;
    }

    public List<Student> search(String skill, String branch, String availability) {
        boolean hasSkill = skill != null && !skill.isBlank();
        boolean hasBranch = branch != null && !branch.isBlank();
        boolean hasAvailability = availability != null && !availability.isBlank();

        if (!hasSkill && !hasBranch && !hasAvailability) {
            return repository.findAll();
        }
        if (hasSkill) return findByWholeSkill(skill.trim());
        if (hasBranch) return repository.findByBranchIgnoreCase(branch.trim());
        return repository.findByAvailabilityIgnoreCase(availability.trim());
    }

    private List<Student> findByWholeSkill(String skill) {
        String canonical = skillGraphService.normalize(skill.trim());
        if (canonical == null) canonical = skill.trim();
        String wantedLower = canonical.toLowerCase(Locale.ROOT);
        String canonForCount = canonical;
        List<Student> filtered = repository.findAll().stream()
                .filter(s -> !s.isSuspended())
                .filter(s -> hasSkillAliasAware(s.getSkills(), wantedLower))
                .collect(Collectors.toList());
        // Trust-weighted sort: endorsed more for this skill first, reported students last, then name
        filtered.sort(Comparator
                .comparingLong((Student s) -> endorsementRepository.countByEndorseeIdAndSkillIgnoreCase(s.getId(), canonForCount)).reversed()
                .thenComparing(s -> hasOpenReport(s.getId()) ? 1 : 0)
                .thenComparing(s -> s.getName().toLowerCase(Locale.ROOT)));
        return filtered;
    }

    private boolean hasOpenReport(Long studentId) {
        try {
            return !moderationRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("STUDENT", studentId)
                    .stream().filter(r -> "OPEN".equals(r.getStatus())).toList().isEmpty();
        } catch (Exception e) {
            return false;
        }
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

    /** Related skills for a given raw skill via graph */
    public List<SkillGraphService.RelatedSkill> relatedSkills(String skill, int limit) {
        String canonical = skillGraphService.normalize(skill);
        if (canonical == null) return List.of();
        return skillGraphService.getRelatedSkills(canonical, limit);
    }
}
