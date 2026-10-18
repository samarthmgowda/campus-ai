package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.ProjectMember;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecruiterService {

    private final StudentRepository studentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final SkillGraphService skillGraphService;

    public RecruiterService(StudentRepository studentRepository,
                             ProjectRepository projectRepository,
                             ProjectMemberRepository memberRepository,
                             SkillGraphService skillGraphService) {
        this.studentRepository = studentRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.skillGraphService = skillGraphService;
    }

    // ——— Student discovery for recruiters ———

    /**
     * Search students by skill (canonical-normalized) + availability + year range.
     * All filters are optional. Whole-skill match.
     * Returns up to 10, sorted by matched skill count desc → AVAILABLE first → year desc → name.
     */
    public List<StudentResult> searchStudents(String skill, String availability, Integer minYear, Integer maxYear) {
        boolean hasSkill = skill != null && !skill.isBlank();
        boolean hasAvailability = availability != null && !availability.isBlank();
        boolean hasYear = minYear != null || maxYear != null;

        List<Student> candidates = studentRepository.findAll();

        final String canonicalSkill = hasSkill ? skillGraphService.normalize(skill.trim()) : null;
        final boolean effectiveHasSkill = hasSkill && canonicalSkill != null;

        candidates = candidates.stream()
                .filter(s -> !effectiveHasSkill || hasWholeSkill(s.getSkills(), canonicalSkill))
                .filter(s -> !hasAvailability || availability.trim().equalsIgnoreCase(s.getAvailability()))
                .filter(s -> minYear == null || (s.getYear() != null && s.getYear() >= minYear))
                .filter(s -> maxYear == null || (s.getYear() != null && s.getYear() <= maxYear))
                .sorted(Comparator
                        .comparing((Student s) -> "AVAILABLE".equalsIgnoreCase(s.getAvailability()) ? 0 : 1)
                        .thenComparing(s -> s.getYear() != null ? -s.getYear() : 0)
                        .thenComparing(s -> s.getName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        int limit = Math.min(10, candidates.size());
        return candidates.subList(0, limit).stream()
                .map(s -> new StudentResult(s.getId(), s.getName(), s.getBranch(), s.getYear(),
                        s.getSkills(), s.getAvailability(), s.getBio()))
                .collect(Collectors.toList());
    }

    /**
     * View a student's project portfolio (all ACTIVE/COMPLETED projects where user is member).
     * Returns projects with name, domain, status, role, description.
     */
    public List<ProjectPortfolioEntry> getStudentPortfolio(Long studentId) {
        List<ProjectMember> memberships = memberRepository.findByStudentId(studentId);
        List<ProjectPortfolioEntry> portfolio = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ProjectMember m : memberships) {
            Project p = m.getProject();
            if (p == null || seen.contains(p.getId())) continue;
            seen.add(p.getId());
            portfolio.add(new ProjectPortfolioEntry(
                    p.getId(), p.getName(), p.getDomain(), p.getStatus(),
                    m.getRole(), p.getDescription(), p.getRequiredSkills()));
        }
        portfolio.sort(Comparator.comparing(ProjectPortfolioEntry::name, String.CASE_INSENSITIVE_ORDER));
        return portfolio;
    }

    /**
     * Full student profile + portfolio in one call (for recruiter detail view).
     */
    public Map<String, Object> getStudentDetail(Long studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", s.getId());
        detail.put("name", s.getName());
        detail.put("branch", s.getBranch());
        detail.put("year", s.getYear());
        detail.put("skills", s.getSkills());
        detail.put("availability", s.getAvailability());
        detail.put("bio", s.getBio());
        detail.put("githubUrl", s.getGithubUrl());
        detail.put("linkedinUrl", s.getLinkedinUrl());
        detail.put("interests", s.getInterests());
        detail.put("portfolio", getStudentPortfolio(studentId));
        return detail;
    }

    // ——— Helpers ———

    private boolean hasWholeSkill(String skillsCsv, String wanted) {
        if (skillsCsv == null || wanted == null) return false;
        String w = wanted.trim();
        if (w.isEmpty()) return false;
        for (String part : skillsCsv.split(",")) {
            if (part.trim().equalsIgnoreCase(w)) return true;
        }
        return false;
    }

    // ——— DTOs ———

    public record StudentResult(Long id, String name, String branch, Integer year, String skills, String availability, String bio) {}
    public record ProjectPortfolioEntry(Long id, String name, String domain, String status, String role, String description, String requiredSkills) {}
}