package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.ModerationReport;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.*;
import com.campusconnect.campus_ai.entity.PostComment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ModerationService {

    private static final Set<String> ALLOWED_TARGETS = Set.of("POST", "PROJECT", "STUDENT", "EVENT", "COMMENT", "MESSAGE");
    private static final Set<String> ALLOWED_REASONS = Set.of("SPAM", "HARASSMENT", "INAPPROPRIATE", "PLAGIARISM", "OTHER");

    private final ModerationReportRepository reportRepository;
    private final StudentRepository studentRepository;
    private final CommunityPostRepository postRepository;
    private final ProjectRepository projectRepository;
    private final EventRepository eventRepository;
    private final PostCommentRepository commentRepository;
    @Value("${campus.admin-ids:}")
    private String adminIds;

    public ModerationService(ModerationReportRepository reportRepository,
                             StudentRepository studentRepository,
                             CommunityPostRepository postRepository,
                             ProjectRepository projectRepository,
                             EventRepository eventRepository,
                             PostCommentRepository commentRepository) {
        this.reportRepository = reportRepository;
        this.studentRepository = studentRepository;
        this.postRepository = postRepository;
        this.projectRepository = projectRepository;
        this.eventRepository = eventRepository;
        this.commentRepository = commentRepository;
    }

    public ReportDto report(Long reporterId, String targetType, Long targetId, String reason, String details) {
        if (reporterId == null) throw new IllegalArgumentException("reporterId required");
        String t = normalizeTarget(targetType);
        if (targetId == null) throw new IllegalArgumentException("targetId required");
        String r = normalizeReason(reason);
        Student reporter = studentRepository.findById(reporterId)
                .orElseThrow(() -> new IllegalArgumentException("Reporter not found: " + reporterId));
        validateTarget(t, targetId);
        // self-report guard for STUDENT
        if (t.equals("STUDENT") && reporterId.equals(targetId)) throw new IllegalArgumentException("Cannot report yourself");
        ModerationReport rep = new ModerationReport(reporter, t, targetId, r, details);
        rep = reportRepository.save(rep);
        return toDto(rep);
    }

    public List<ReportDto> list(String status) {
        String s = status != null && !status.isBlank() ? status.trim().toUpperCase() : "OPEN";
        if (!Set.of("OPEN", "RESOLVED", "DISMISSED").contains(s)) throw new IllegalArgumentException("status must be OPEN, RESOLVED, or DISMISSED");
        return reportRepository.findByStatusOrderByCreatedAtDesc(s).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ReportDto> myReports(Long reporterId) {
        studentRepository.findById(reporterId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + reporterId));
        return reportRepository.findByReporterIdOrderByCreatedAtDesc(reporterId).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ReportDto> forTarget(String targetType, Long targetId) {
        return reportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(normalizeTarget(targetType), targetId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public ReportDto resolve(Long reportId, String action, String resolution) {
        return resolve(reportId, null, action, resolution);
    }

    public ReportDto resolve(Long reportId, Long actorId, String action, String resolution) {
        if (actorId != null) requireAdmin(actorId);
        ModerationReport rep = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        if (!"OPEN".equals(rep.getStatus())) throw new IllegalArgumentException("Report already " + rep.getStatus().toLowerCase());
        String act = action != null ? action.trim().toUpperCase() : "RESOLVED";
        if (!Set.of("RESOLVED", "DISMISSED").contains(act)) throw new IllegalArgumentException("action must be RESOLVED or DISMISSED");
        rep.setStatus(act);
        rep.setResolution(resolution);
        rep.setResolvedAt(LocalDateTime.now());
        rep = reportRepository.save(rep);
        if ("RESOLVED".equals(act)) applySideEffect(rep);
        return toDto(rep);
    }

    private void requireAdmin(Long actorId) {
        if (actorId == null) throw new IllegalArgumentException("X-Demo-User header required");
        // validate user exists
        studentRepository.findById(actorId).orElseThrow(() -> new IllegalArgumentException("Unknown demo user"));
        if (adminIds == null || adminIds.isBlank()) throw new IllegalArgumentException("No admins configured");
        var allowed = java.util.Arrays.stream(adminIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet());
        if (!allowed.contains(String.valueOf(actorId))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin required — user " + actorId + " is not in campus.admin-ids");
    }

    private void applySideEffect(ModerationReport rep) {
        try {
            String targetType = rep.getTargetType();
            Long targetId = rep.getTargetId();
            switch (targetType) {
                case "POST" -> {
                    var post = postRepository.findById(targetId).orElse(null);
                    if (post != null) { post.setHidden(true); postRepository.save(post); }
                }
                case "COMMENT" -> {
                    var c = commentRepository.findById(targetId).orElse(null);
                    if (c != null) { c.setHidden(true); commentRepository.save(c); }
                }
                case "STUDENT" -> {
                    var st = studentRepository.findById(targetId).orElse(null);
                    if (st != null) { st.setSuspended(true); studentRepository.save(st); }
                }
                case "PROJECT" -> {
                    var pr = projectRepository.findById(targetId).orElse(null);
                    if (pr != null) { pr.setStatus("HIDDEN"); projectRepository.save(pr); }
                }
                case "EVENT" -> {
                    var ev = eventRepository.findById(targetId).orElse(null);
                    if (ev != null) { ev.setHidden(true); eventRepository.save(ev); }
                }
                default -> {}
            }
        } catch (Exception ignored) {}
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("open", reportRepository.countByStatus("OPEN"));
        m.put("resolved", reportRepository.countByStatus("RESOLVED"));
        m.put("dismissed", reportRepository.countByStatus("DISMISSED"));
        m.put("total", reportRepository.count());
        return m;
    }

    public record ReportDto(Long id, Long reporterId, String reporterName, String targetType, Long targetId, String reason, String details, String status, String resolution, String createdAt, String resolvedAt) {}

    private ReportDto toDto(ModerationReport r) {
        return new ReportDto(r.getId(),
                r.getReporter() != null ? r.getReporter().getId() : null,
                r.getReporter() != null ? r.getReporter().getName() : null,
                r.getTargetType(), r.getTargetId(), r.getReason(), r.getDetails(), r.getStatus(), r.getResolution(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null,
                r.getResolvedAt() != null ? r.getResolvedAt().toString() : null);
    }

    private String normalizeTarget(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("targetType required (POST|PROJECT|STUDENT|EVENT)");
        String t = raw.trim().toUpperCase();
        if (!ALLOWED_TARGETS.contains(t)) throw new IllegalArgumentException("targetType must be POST, PROJECT, STUDENT, EVENT, COMMENT, or MESSAGE");
        return t;
    }

    private String normalizeReason(String raw) {
        if (raw == null || raw.isBlank()) return "OTHER";
        String r = raw.trim().toUpperCase();
        if (!ALLOWED_REASONS.contains(r)) return "OTHER";
        return r;
    }

    private void validateTarget(String targetType, Long targetId) {
        switch (targetType) {
            case "POST" -> postRepository.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Post not found: " + targetId));
            case "PROJECT" -> projectRepository.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Project not found: " + targetId));
            case "STUDENT" -> studentRepository.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + targetId));
            case "EVENT" -> eventRepository.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Event not found: " + targetId));
            default -> { /* COMMENT/MESSAGE skip strict check */ }
        }
    }
}
