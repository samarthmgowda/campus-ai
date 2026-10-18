package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

@Service
public class ProfileService {

    private final StudentRepository studentRepository;
    private final FollowService followService;
    private final ProjectToolService projectToolService;
    private final ProjectInviteService projectInviteService;
    private final BookmarkService bookmarkService;
    private final SkillEndorsementService skillEndorsementService;
    private final ReviewService reviewService;
    private final EventAttendanceService eventAttendanceService;
    private final NotificationService notificationService;
    private final CommunityService communityService;
    private final MessageService messageService;
    private final SkillGraphService skillGraphService;

    public ProfileService(StudentRepository studentRepository,
                          FollowService followService,
                          ProjectToolService projectToolService,
                          ProjectInviteService projectInviteService,
                          BookmarkService bookmarkService,
                          SkillEndorsementService skillEndorsementService,
                          ReviewService reviewService,
                          EventAttendanceService eventAttendanceService,
                          NotificationService notificationService,
                          CommunityService communityService,
                          MessageService messageService,
                          SkillGraphService skillGraphService) {
        this.studentRepository = studentRepository;
        this.followService = followService;
        this.projectToolService = projectToolService;
        this.projectInviteService = projectInviteService;
        this.bookmarkService = bookmarkService;
        this.skillEndorsementService = skillEndorsementService;
        this.reviewService = reviewService;
        this.eventAttendanceService = eventAttendanceService;
        this.notificationService = notificationService;
        this.communityService = communityService;
        this.messageService = messageService;
        this.skillGraphService = skillGraphService;
    }

    public Map<String, Object> getMe(Long studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        Map<String, Object> m = new LinkedHashMap<>();

        // identity
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", s.getId());
        profile.put("name", s.getName());
        profile.put("branch", s.getBranch());
        profile.put("year", s.getYear());
        profile.put("skills", s.getSkills());
        profile.put("interests", s.getInterests());
        profile.put("bio", s.getBio());
        profile.put("githubUrl", s.getGithubUrl());
        profile.put("linkedinUrl", s.getLinkedinUrl());
        profile.put("availability", s.getAvailability());
        m.put("profile", profile);

        // connections
        try { m.put("connections", followService.getStats(studentId)); } catch (Exception e) { m.put("connections", Map.of()); }

        // projects (my active)
        try {
            var projects = projectToolService.getMyProjects(studentId);
            m.put("projects", Map.of("count", projects.size(), "items", projects));
        } catch (Exception e) { m.put("projects", Map.of("count", 0, "items", List.of())); }

        // invites
        try {
            var invites = projectInviteService.getMyInvites(studentId, "PENDING");
            m.put("pendingInvites", Map.of("count", invites.size(), "items", invites));
        } catch (Exception e) { m.put("pendingInvites", Map.of("count", 0, "items", List.of())); }

        // bookmarks
        try { m.put("bookmarks", bookmarkService.saveSummary(studentId)); } catch (Exception e) { m.put("bookmarks", Map.of()); }

        // endorsements
        try { m.put("endorsements", skillEndorsementService.getSummary(studentId)); } catch (Exception e) { m.put("endorsements", Map.of()); }

        // reputation
        try { m.put("reputation", reviewService.getReputationForStudent(studentId)); } catch (Exception e) { m.put("reputation", Map.of()); }

        // rsvps
        try {
            var rsvps = eventAttendanceService.getMyEvents(studentId);
            m.put("rsvps", Map.of("count", rsvps.size(), "items", rsvps));
        } catch (Exception e) { m.put("rsvps", Map.of("count", 0, "items", List.of())); }

        // community + messaging counts
        try {
            var posts = communityService.getPostsByAuthor(studentId);
            m.put("posts", Map.of("count", posts.size()));
        } catch (Exception e) { m.put("posts", Map.of("count", 0)); }
        try { m.put("unreadNotifications", notificationService.getUnreadCount(studentId)); } catch (Exception e) { m.put("unreadNotifications", 0); }
        try { m.put("unreadMessages", messageService.getUnreadCount(studentId)); } catch (Exception e) { m.put("unreadMessages", 0); }

        return m;
    }

    public Map<String, Object> getPublicProfile(Long studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("branch", s.getBranch());
        m.put("year", s.getYear());
        m.put("skills", s.getSkills());
        m.put("interests", s.getInterests());
        m.put("bio", s.getBio());
        m.put("githubUrl", s.getGithubUrl());
        m.put("linkedinUrl", s.getLinkedinUrl());
        m.put("availability", s.getAvailability());
        try { m.put("connections", followService.getStats(studentId)); } catch (Exception ignored) {}
        try { m.put("reputation", reviewService.getReputationForStudent(studentId)); } catch (Exception ignored) {}
        try { m.put("endorsements", skillEndorsementService.getSummary(studentId)); } catch (Exception ignored) {}
        return m;
    }

    // ——— Profile completeness + skill suggestions ———

    public Map<String, Object> getCompleteness(Long studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        int score = 0;
        java.util.List<String> missing = new java.util.ArrayList<>();
        // weighted fields (sum 100)
        if (notBlank(s.getName())) score += 10; else missing.add("name");
        if (notBlank(s.getBranch())) score += 10; else missing.add("branch");
        if (s.getYear() != null) score += 10; else missing.add("year");
        if (notBlank(s.getSkills())) score += 20; else missing.add("skills");
        if (notBlank(s.getInterests())) score += 10; else missing.add("interests");
        if (notBlank(s.getBio())) score += 15; else missing.add("bio");
        if (notBlank(s.getGithubUrl())) score += 10; else missing.add("githubUrl");
        if (notBlank(s.getLinkedinUrl())) score += 10; else missing.add("linkedinUrl");
        if (notBlank(s.getAvailability())) score += 5; else missing.add("availability");
        String level = score >= 90 ? "complete" : score >= 70 ? "strong" : score >= 40 ? "getting there" : "incomplete";
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("studentId", s.getId());
        m.put("score", score);
        m.put("level", level);
        m.put("missingFields", missing);
        m.put("suggestions", missing.stream().map(f -> "Add " + f).collect(java.util.stream.Collectors.toList()));
        return m;
    }

    public Map<String, Object> getSkillRecommendations(Long studentId, int limit) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        int lim = Math.max(1, Math.min(limit, 20));
        java.util.Set<String> ownedLower = new java.util.HashSet<>();
        java.util.List<String> ownedCanonical = new java.util.ArrayList<>();
        if (s.getSkills() != null) {
            for (String part : s.getSkills().split(",")) {
                String raw = part.trim(); if (raw.isEmpty()) continue;
                String c = skillGraphService.normalize(raw);
                if (c == null) c = raw;
                ownedCanonical.add(c);
                ownedLower.add(c.toLowerCase(java.util.Locale.ROOT));
            }
        }
        Map<String, Integer> freq = new java.util.LinkedHashMap<>();
        Map<String, String> reason = new java.util.LinkedHashMap<>();
        for (String canon : ownedCanonical) {
            for (var rel : skillGraphService.getRelatedSkills(canon, 10)) {
                String r = rel.skillName();
                String rl = r.toLowerCase(java.util.Locale.ROOT);
                if (ownedLower.contains(rl)) continue;
                freq.merge(r, rel.count(), Integer::sum);
                reason.putIfAbsent(r, "pairs with " + canon);
            }
        }
        java.util.List<Map<String, Object>> recs;
        if (freq.isEmpty()) {
            // fallback: trending canonical skills not yet owned
            var trending = skillGraphService.getAllCanonicalSkills().stream()
                    .filter(n -> !ownedLower.contains(n.toLowerCase(java.util.Locale.ROOT)))
                    .limit(lim).toList();
            recs = trending.stream().map(n -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("skill", n);
                try { m.put("category", skillGraphService.getGlossary(n).get("category")); } catch (Exception e) { m.put("category", "Other"); }
                m.put("reason", "trending on campus");
                return m;
            }).collect(java.util.stream.Collectors.toList());
        } else {
            recs = freq.entrySet().stream()
                    .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(lim)
                    .map(e -> {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("skill", e.getKey());
                        try { m.put("category", skillGraphService.getGlossary(e.getKey()).get("category")); } catch (Exception ex) { m.put("category", "Other"); }
                        m.put("reason", reason.getOrDefault(e.getKey(), "related to your skills"));
                        m.put("score", e.getValue());
                        return m;
                    }).collect(java.util.stream.Collectors.toList());
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("studentId", s.getId());
        out.put("ownedSkills", ownedCanonical);
        out.put("recommendations", recs);
        return out;
    }

    private static boolean notBlank(String v) { return v != null && !v.isBlank(); }

}
