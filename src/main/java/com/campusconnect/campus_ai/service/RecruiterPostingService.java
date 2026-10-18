package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.PostingApplication;
import com.campusconnect.campus_ai.entity.Recruiter;
import com.campusconnect.campus_ai.entity.RecruiterPosting;
import com.campusconnect.campus_ai.repository.PostingApplicationRepository;
import com.campusconnect.campus_ai.repository.RecruiterPostingRepository;
import com.campusconnect.campus_ai.repository.RecruiterRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RecruiterPostingService {

    private final RecruiterPostingRepository postingRepository;
    private final PostingApplicationRepository applicationRepository;
    private final RecruiterRepository recruiterRepository;
    private final StudentRepository studentRepository;
    private final SkillGraphService skillGraphService;
    private final NotificationService notificationService;

    public RecruiterPostingService(RecruiterPostingRepository postingRepository,
                                   PostingApplicationRepository applicationRepository,
                                   RecruiterRepository recruiterRepository,
                                   StudentRepository studentRepository,
                                   SkillGraphService skillGraphService,
                                   NotificationService notificationService) {
        this.postingRepository = postingRepository;
        this.applicationRepository = applicationRepository;
        this.recruiterRepository = recruiterRepository;
        this.studentRepository = studentRepository;
        this.skillGraphService = skillGraphService;
        this.notificationService = notificationService;
    }

    public PostingDto createPosting(Long recruiterId, String title, String description, String requiredSkills) {
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
                .orElseThrow(() -> new IllegalArgumentException("Recruiter not found: " + recruiterId));
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Posting title required");
        String tt = title.trim();
        if (tt.length() < 3) throw new IllegalArgumentException("Title must be at least 3 characters");
        if (tt.length() > 150) throw new IllegalArgumentException("Title too long (max 150)");
        String desc = description != null ? description.trim() : null;
        if (desc != null && desc.length() > 3000) throw new IllegalArgumentException("Description too long (max 3000)");
        String req = null;
        if (requiredSkills != null && !requiredSkills.isBlank()) {
            var normalized = skillGraphService.normalizeCsv(requiredSkills);
            req = normalized.isEmpty() ? requiredSkills.trim() : String.join(", ", normalized);
            if (req.length() > 600) throw new IllegalArgumentException("requiredSkills too long");
        }
        RecruiterPosting posting = new RecruiterPosting(recruiter, tt, desc, req);
        posting = postingRepository.save(posting);
        return toDto(posting);
    }

    public PostingDto closePosting(Long recruiterId, Long postingId) {
        RecruiterPosting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Posting not found: " + postingId));
        if (!posting.getRecruiter().getId().equals(recruiterId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your posting");
        posting.setStatus("CLOSED");
        posting = postingRepository.save(posting);
        return toDto(posting);
    }

    public List<PostingDto> listPostings(String status, Long recruiterId) {
        List<RecruiterPosting> list;
        if (recruiterId != null) {
            list = postingRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId);
            if (status != null && !status.isBlank()) {
                String s = status.trim().toUpperCase();
                list = list.stream().filter(po -> s.equals(po.getStatus())).collect(Collectors.toList());
            }
        } else if (status != null && !status.isBlank()) {
            list = postingRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
        } else {
            list = postingRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<PostingDto> getPostings(Long recruiterId) {
        return listPostings(null, recruiterId);
    }

    public PostingDto getPosting(Long postingId) {
        RecruiterPosting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Posting not found: " + postingId));
        return toDto(posting);
    }

    public ApplicationDto apply(Long studentId, Long postingId) {
        var student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        var posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Posting not found: " + postingId));
        if (!"OPEN".equals(posting.getStatus())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posting is not open");
        var existing = applicationRepository.findByPostingIdAndStudentId(postingId, studentId);
        if (existing.isPresent()) {
            String st = existing.get().getStatus();
            if ("APPLIED".equals(st) || "SHORTLISTED".equals(st)) return toAppDto(existing.get());
            // REJECTED -> allow re-apply as new APPLIED
            existing.get().setStatus("APPLIED");
            var saved = applicationRepository.save(existing.get());
            return toAppDto(saved);
        }
        PostingApplication app = new PostingApplication(posting, student);
        app = applicationRepository.save(app);
        return toAppDto(app);
    }

    public Map<String, Object> withdraw(Long studentId, Long applicationId) {
        PostingApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (!app.getStudent().getId().equals(studentId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your application");
        if (!"APPLIED".equals(app.getStatus())) throw new IllegalArgumentException("Application already " + app.getStatus().toLowerCase());
        applicationRepository.delete(app);
        return Map.of("id", applicationId, "status", "withdrawn");
    }

    public ApplicationDto decide(Long recruiterId, Long postingId, Long applicationId, String action) {
        var posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Posting not found: " + postingId));
        if (!posting.getRecruiter().getId().equals(recruiterId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your posting");
        PostingApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (!app.getPosting().getId().equals(postingId)) throw new IllegalArgumentException("Application not for this posting");
        if (!"APPLIED".equals(app.getStatus())) throw new IllegalArgumentException("Application already " + app.getStatus().toLowerCase());
        String act = action != null ? action.trim().toUpperCase() : "";
        if (!act.equals("SHORTLIST") && !act.equals("SHORTLISTED") && !act.equals("REJECT") && !act.equals("REJECTED"))
            throw new IllegalArgumentException("Action must be SHORTLIST or REJECT");
        boolean shortlist = act.startsWith("SHORTLIST");
        app.setStatus(shortlist ? "SHORTLISTED" : "REJECTED");
        app = applicationRepository.save(app);
        try {
            String title = shortlist ? "You were shortlisted for " + posting.getTitle() : "Update on your application for " + posting.getTitle();
            String body = shortlist ? "Recruiter " + posting.getRecruiter().getName() + " shortlisted you" : "Not shortlisted this time — keep building your portfolio";
            notificationService.notify(app.getStudent().getId(), shortlist ? "SHORTLIST" : "REJECT", title, body, "POSTING", posting.getId());
        } catch (Exception ignored) {}
        return toAppDto(app);
    }

    public List<ApplicationDto> getApplicationsForPosting(Long recruiterId, Long postingId, String status) {
        var posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Posting not found: " + postingId));
        if (!posting.getRecruiter().getId().equals(recruiterId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your posting");
        List<PostingApplication> list;
        if (status != null && !status.isBlank()) list = applicationRepository.findByPostingIdAndStatusOrderByCreatedAtDesc(postingId, status.trim().toUpperCase());
        else list = applicationRepository.findByPostingIdOrderByCreatedAtDesc(postingId);
        return list.stream().map(this::toAppDto).collect(Collectors.toList());
    }

    public List<ApplicationDto> getMyApplications(Long studentId, String status) {
        studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        List<PostingApplication> list;
        if (status != null && !status.isBlank()) list = applicationRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, status.trim().toUpperCase());
        else list = applicationRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        return list.stream().map(this::toAppDto).collect(Collectors.toList());
    }

    public record PostingDto(Long id, Long recruiterId, String recruiterName, String company, String title, String description, String requiredSkills, String status, String createdAt, long applicantCount) {}
    public record ApplicationDto(Long id, Long postingId, String postingTitle, Long studentId, String studentName, String status, String createdAt) {}

    private PostingDto toDto(RecruiterPosting po) {
        long count = 0;
        try { count = applicationRepository.countByPostingIdAndStatus(po.getId(), "APPLIED") + applicationRepository.countByPostingIdAndStatus(po.getId(), "SHORTLISTED"); } catch (Exception ignored) {}
        return new PostingDto(po.getId(), po.getRecruiter() != null ? po.getRecruiter().getId() : null,
                po.getRecruiter() != null ? po.getRecruiter().getName() : null,
                po.getRecruiter() != null ? po.getRecruiter().getCompany() : null,
                po.getTitle(), po.getDescription(), po.getRequiredSkills(), po.getStatus(),
                po.getCreatedAt() != null ? po.getCreatedAt().toString() : null, count);
    }

    private ApplicationDto toAppDto(PostingApplication app) {
        return new ApplicationDto(app.getId(), app.getPosting() != null ? app.getPosting().getId() : null,
                app.getPosting() != null ? app.getPosting().getTitle() : null,
                app.getStudent() != null ? app.getStudent().getId() : null,
                app.getStudent() != null ? app.getStudent().getName() : null,
                app.getStatus(), app.getCreatedAt() != null ? app.getCreatedAt().toString() : null);
    }
}
