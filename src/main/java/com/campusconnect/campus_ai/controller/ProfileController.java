package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.entity.enums.UserRole;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.service.AuthContext;
import com.campusconnect.campus_ai.service.CurrentUser;
import com.campusconnect.campus_ai.service.CurrentUserService;
import com.campusconnect.campus_ai.service.ProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserService currentUserService;
    private final StudentRepository studentRepository;

    private static final Set<String> EDITABLE_FIELDS = Set.of(
            "name", "branch", "year", "skills", "interests", "bio",
            "githubUrl", "github_url", "github",
            "linkedinUrl", "linkedin_url", "linkedin",
            "availability");

    private static final int MAX_FIELD_LENGTH = 2000;

    public ProfileController(ProfileService profileService,
                             CurrentUserService currentUserService,
                             StudentRepository studentRepository) {
        this.profileService = profileService;
        this.currentUserService = currentUserService;
        this.studentRepository = studentRepository;
    }

    /**
     * GET /me — the acting user's profile.
     *
     * Students get the full aggregated profile (counts, recommendations, …).
     * Alumni and recruiters resolve against their own table; an admin account
     * with no bound profile row gets a minimal identity instead of a 404.
     */
    @GetMapping("/me")
    public Map<String, Object> me() {
        CurrentUser cu = AuthContext.require();
        if (cu.role() == UserRole.ADMIN) {
            return Map.of(
                    "role", "admin",
                    "id", cu.userId(),
                    "email", cu.email());
        }
        if (cu.role() == UserRole.STUDENT) {
            return profileService.getMe(cu.actingId());
        }
        return currentUserService.resolveProfile(cu);
    }

    /** GET /profile/{studentId} — public profile */
    @GetMapping("/profile/{studentId}")
    public Map<String, Object> publicProfile(@PathVariable Long studentId) {
        return profileService.getPublicProfile(studentId);
    }

    /** GET /profiles/{studentId} — alias for the public profile, used by chat cards. */
    @GetMapping("/profiles/{studentId}")
    public Map<String, Object> publicProfileAlias(@PathVariable Long studentId) {
        return profileService.getPublicProfile(studentId);
    }

    /** GET /me/completeness */
    @GetMapping("/me/completeness")
    public Map<String, Object> completeness() {
        Long studentId = AuthContext.id();
        return profileService.getCompleteness(studentId);
    }

    /** GET /me/recommendations?limit=5 */
    @GetMapping("/me/recommendations")
    public Map<String, Object> myRecommendations(@RequestParam(defaultValue = "5") int limit) {
        Long studentId = AuthContext.id();
        return profileService.getSkillRecommendations(studentId, limit);
    }

    /** GET /profile/{studentId}/recommendations?limit=5 — public */
    @GetMapping("/profile/{studentId}/recommendations")
    public Map<String, Object> publicRecommendations(@PathVariable Long studentId,
                                                     @RequestParam(defaultValue = "5") int limit) {
        return profileService.getSkillRecommendations(studentId, limit);
    }

    /** PUT /me — update editable profile fields */
    @PutMapping("/me")
    public Map<String, Object> updateMe(@RequestBody Map<String, Object> body) {
        Long studentId = AuthContext.id();
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        List<String> updated = new ArrayList<>();
        if (body != null) {
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                String field = entry.getKey();
                Object raw = entry.getValue();
                if (raw == null || !EDITABLE_FIELDS.contains(field)) continue;

                String value = sanitize(raw.toString());
                if (value.length() > MAX_FIELD_LENGTH) {
                    throw new IllegalArgumentException(field + " too long (max " + MAX_FIELD_LENGTH + " chars)");
                }
                applyField(student, field, value);
                updated.add(field);
            }
        }

        Student saved = studentRepository.save(student);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("updatedFields", updated);
        result.put("name", saved.getName());
        result.put("skills", saved.getSkills());
        result.put("bio", saved.getBio());
        return result;
    }

    private void applyField(Student student, String field, String value) {
        switch (field) {
            case "name" -> student.setName(value);
            case "branch" -> student.setBranch(value);
            case "year" -> {
                try { student.setYear(Integer.parseInt(value.trim())); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Year must be a number (1-4)"); }
            }
            case "skills" -> student.setSkills(value);
            case "interests" -> student.setInterests(value);
            case "bio" -> student.setBio(value);
            case "githubUrl", "github_url", "github" -> student.setGithubUrl(value);
            case "linkedinUrl", "linkedin_url", "linkedin" -> student.setLinkedinUrl(value);
            case "availability" -> student.setAvailability(value);
        }
    }

    private String sanitize(String value) {
        if (value == null) return null;
        // Strip HTML-like tags; normalize whitespace; trim
        String cleaned = value.replaceAll("<[^>]+>", "").trim();
        return cleaned.replaceAll("\\s+", " ");
    }
}
