package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.CommunityPost;
import com.campusconnect.campus_ai.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final CommunityPostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final PostCommentRepository commentRepository;
    private final MessageRepository messageRepository;
    private final ReviewRepository reviewRepository;
    private final EventRepository eventRepository;
    private final RoadmapRepository roadmapRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SkillGraphService skillGraphService;

    public AnalyticsService(StudentRepository studentRepository,
                            AlumniRepository alumniRepository,
                            ProjectRepository projectRepository,
                            DocumentRepository documentRepository,
                            DocumentChunkRepository chunkRepository,
                            CommunityPostRepository postRepository,
                            PostLikeRepository likeRepository,
                            PostCommentRepository commentRepository,
                            MessageRepository messageRepository,
                            ReviewRepository reviewRepository,
                            EventRepository eventRepository,
                            RoadmapRepository roadmapRepository,
                            ProjectMemberRepository projectMemberRepository,
                            SkillGraphService skillGraphService) {
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.messageRepository = messageRepository;
        this.reviewRepository = reviewRepository;
        this.eventRepository = eventRepository;
        this.roadmapRepository = roadmapRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.skillGraphService = skillGraphService;
    }

    public Map<String, Object> getSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("students", studentRepository.count());
        m.put("alumni", alumniRepository.count());
        m.put("projects", projectRepository.count());
        m.put("documents", documentRepository.count());
        m.put("chunks", chunkRepository.count());
        m.put("posts", postRepository.count());
        m.put("likes", likeRepository.count());
        m.put("comments", commentRepository.count());
        m.put("messages", messageRepository.count());
        m.put("reviews", reviewRepository.count());
        m.put("events", eventRepository.count());
        m.put("upcomingEvents", eventRepository.findByEventDateGreaterThanEqual(LocalDate.now()).stream().filter(e -> !e.isHidden()).count());
        m.put("roadmaps", roadmapRepository.count());
        return m;
    }

    public List<Map<String, Object>> topSkills(int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (var s : studentRepository.findAll()) {
            if (s.getSkills() == null || s.getSkills().isBlank()) continue;
            for (String part : s.getSkills().split(",")) {
                String raw = part.trim();
                if (raw.isEmpty()) continue;
                String canon = skillGraphService.normalize(raw);
                if (canon == null) canon = raw;
                counts.merge(canon, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit > 0 ? limit : 10)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("skill", e.getKey());
                    row.put("count", e.getValue());
                    return row;
                }).collect(Collectors.toList());
    }

    public Map<String, Object> projectStats() {
        var projects = projectRepository.findAll();
        Map<String, Long> byStatus = projects.stream()
                .collect(Collectors.groupingBy(p -> p.getStatus() != null ? p.getStatus() : "UNKNOWN", Collectors.counting()));
        Map<String, Long> byDomain = projects.stream()
                .collect(Collectors.groupingBy(p -> p.getDomain() != null ? p.getDomain() : "Unknown", Collectors.counting()));
        double avgMembers = 0;
        if (!projects.isEmpty()) {
            long totalMembers = 0;
            for (var p : projects) totalMembers += projectMemberRepository.findByProjectId(p.getId()).size();
            avgMembers = Math.round((totalMembers * 10.0 / projects.size())) / 10.0;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", projects.size());
        m.put("byStatus", byStatus);
        m.put("byDomain", byDomain);
        m.put("avgMembers", avgMembers);
        return m;
    }

    public Map<String, Object> communityStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("posts", postRepository.count());
        m.put("likes", likeRepository.count());
        m.put("comments", commentRepository.count());
        m.put("messages", messageRepository.count());
        m.put("reviews", reviewRepository.count());
        var posts = postRepository.findAll();
        if (!posts.isEmpty()) {
            var topLiked = posts.stream()
                    .sorted(Comparator.comparingLong((CommunityPost p) -> likeRepository.countByPostId(p.getId())).reversed())
                    .limit(3)
                    .map(p -> Map.of(
                            "id", p.getId(),
                            "author", p.getAuthor() != null ? p.getAuthor().getName() : "Unknown",
                            "likes", likeRepository.countByPostId(p.getId()),
                            "preview", p.getContent() != null && p.getContent().length() > 80 ? p.getContent().substring(0, 77) + "..." : p.getContent()))
                    .collect(Collectors.toList());
            m.put("topPosts", topLiked);
        } else {
            m.put("topPosts", List.of());
        }
        return m;
    }

    public Map<String, Long> availabilityBreakdown() {
        return studentRepository.findAll().stream()
                .collect(Collectors.groupingBy(s -> s.getAvailability() != null ? s.getAvailability() : "UNKNOWN", Collectors.counting()));
    }

    public Map<String, Long> branchBreakdown() {
        return studentRepository.findAll().stream()
                .collect(Collectors.groupingBy(s -> s.getBranch() != null ? s.getBranch() : "Unknown", Collectors.counting()));
    }
}
