package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** GET /me with X-Demo-User — aggregated profile header (private + counts) */
    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long studentId = requireHeader(header);
        return profileService.getMe(studentId);
    }

    /** GET /profile/{studentId} — public profile */
    @GetMapping("/profile/{studentId}")
    public Map<String, Object> publicProfile(@PathVariable Long studentId) {
        return profileService.getPublicProfile(studentId);
    }

    /** GET /me/completeness with X-Demo-User */
    @GetMapping("/me/completeness")
    public Map<String, Object> completeness(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long studentId = requireHeader(header);
        return profileService.getCompleteness(studentId);
    }

    /** GET /me/recommendations?limit=5 with X-Demo-User */
    @GetMapping("/me/recommendations")
    public Map<String, Object> myRecommendations(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(defaultValue = "5") int limit) {
        Long studentId = requireHeader(header);
        return profileService.getSkillRecommendations(studentId, limit);
    }

    /** GET /profile/{studentId}/recommendations?limit=5 — public */
    @GetMapping("/profile/{studentId}/recommendations")
    public Map<String, Object> publicRecommendations(@PathVariable Long studentId,
                                                     @RequestParam(defaultValue = "5") int limit) {
        return profileService.getSkillRecommendations(studentId, limit);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        try { return Long.valueOf(header.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid X-Demo-User"); }
    }
}
