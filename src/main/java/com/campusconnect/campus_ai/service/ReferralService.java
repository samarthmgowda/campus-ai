package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.ReferralRequest;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.ReferralRequestRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReferralService {

    private final ReferralRequestRepository requestRepository;
    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final NotificationService notificationService;

    public ReferralService(ReferralRequestRepository requestRepository,
                           StudentRepository studentRepository,
                           AlumniRepository alumniRepository,
                           NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
        this.notificationService = notificationService;
    }

    public ReferralDto request(Long studentId, Long alumniId, String message, String company, String role) {
        if (studentId == null || alumniId == null) throw new IllegalArgumentException("studentId and alumniId required");
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        Alumni alumni = alumniRepository.findById(alumniId)
                .orElseThrow(() -> new IllegalArgumentException("Alumni not found: " + alumniId));
        String avail = alumni.getAvailability() != null ? alumni.getAvailability().trim().toUpperCase() : "";
        if ("UNAVAILABLE".equals(avail) || (!avail.isEmpty() && !avail.contains("REFERRAL") && !avail.contains("BOTH"))) {
            throw new IllegalArgumentException("Alumni is not available for referrals");
        }
        String msg = message != null ? message.trim() : null;
        if (msg != null && msg.length() > 2000) throw new IllegalArgumentException("Message too long (max 2000)");
        String comp = company != null ? company.trim() : null;
        if (comp != null && comp.length() > 200) throw new IllegalArgumentException("Company too long (max 200)");
        String rl = role != null ? role.trim() : null;
        if (rl != null && rl.length() > 200) throw new IllegalArgumentException("Role too long (max 200)");
        var existing = requestRepository.findByStudentIdAndAlumniId(studentId, alumniId);
        if (existing.isPresent()) {
            String st = existing.get().getStatus();
            if ("PENDING".equals(st)) return toDto(existing.get());
            if ("ACCEPTED".equals(st)) throw new IllegalArgumentException("Referral already accepted");
            existing.get().setStatus("PENDING");
            if (msg != null) existing.get().setMessage(msg);
            if (comp != null) existing.get().setCompany(comp);
            if (rl != null) existing.get().setRole(rl);
            var saved = requestRepository.save(existing.get());
            return toDto(saved);
        }
        ReferralRequest req = new ReferralRequest(student, alumni, msg, comp, rl);
        req = requestRepository.save(req);
        return toDto(req);
    }

    public ReferralDto respond(Long requestId, Long actorStudentId, String action) {
        if (requestId == null || actorStudentId == null || action == null)
            throw new IllegalArgumentException("requestId, actor and action required");
        ReferralRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Referral request not found: " + requestId));
        if (!req.getStudent().getId().equals(actorStudentId)) {
            throw new IllegalArgumentException("Not your referral request");
        }
        if (!"PENDING".equals(req.getStatus())) throw new IllegalArgumentException("Request already " + req.getStatus().toLowerCase());
        String act = action.trim().toUpperCase();
        if (!act.equals("ACCEPT") && !act.equals("REJECT") && !act.equals("ACCEPTED") && !act.equals("REJECTED") && !act.equals("CANCEL")) {
            throw new IllegalArgumentException("Action must be ACCEPT or REJECT");
        }
        boolean accept = act.startsWith("ACCEPT");
        boolean cancel = act.equals("CANCEL");
        if (cancel) {
            requestRepository.delete(req);
            return new ReferralDto(req.getId(), req.getStudent().getId(), req.getStudent().getName(), req.getAlumni().getId(), req.getAlumni().getName(), "WITHDRAWN", req.getMessage(), req.getCompany(), req.getRole(), req.getCreatedAt() != null ? req.getCreatedAt().toString() : null);
        }
        req.setStatus(accept ? "ACCEPTED" : "REJECTED");
        req = requestRepository.save(req);
        try {
            if (accept) {
                notificationService.notify(req.getStudent().getId(), "REFERRAL", "Referral accepted by " + req.getAlumni().getName(),
                        (req.getCompany() != null ? req.getCompany() : req.getAlumni().getCurrentCompany()) + (req.getRole() != null ? " — " + req.getRole() : ""), "REFERRAL", req.getId());
            }
        } catch (Exception ignored) {}
        return toDto(req);
    }

    public Map<String, Object> withdraw(Long requestId, Long actorStudentId) {
        ReferralRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Referral request not found: " + requestId));
        if (!req.getStudent().getId().equals(actorStudentId)) throw new IllegalArgumentException("Not your referral request");
        if (!"PENDING".equals(req.getStatus())) throw new IllegalArgumentException("Request already " + req.getStatus().toLowerCase());
        requestRepository.delete(req);
        return Map.of("id", requestId, "status", "withdrawn");
    }

    public ReferralDto alumniRespond(Long requestId, Long alumniId, String action) {
        ReferralRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Referral request not found: " + requestId));
        if (!req.getAlumni().getId().equals(alumniId)) throw new IllegalArgumentException("Not your referral request");
        if (!"PENDING".equals(req.getStatus())) throw new IllegalArgumentException("Request already " + req.getStatus().toLowerCase());
        String act = action != null ? action.trim().toUpperCase() : "ACCEPTED";
        if (!act.equals("ACCEPT") && !act.equals("ACCEPTED") && !act.equals("REJECT") && !act.equals("REJECTED"))
            throw new IllegalArgumentException("Action must be ACCEPT or REJECT");
        boolean accept = act.startsWith("ACCEPT");
        req.setStatus(accept ? "ACCEPTED" : "REJECTED");
        req = requestRepository.save(req);
        try {
            if (accept) {
                notificationService.notify(req.getStudent().getId(), "REFERRAL", "Referral accepted by " + req.getAlumni().getName(),
                        (req.getCompany() != null ? req.getCompany() : req.getAlumni().getCurrentCompany()) + (req.getRole() != null ? " — " + req.getRole() : ""), "REFERRAL", req.getId());
            } else {
                notificationService.notify(req.getStudent().getId(), "REFERRAL", "Referral declined by " + req.getAlumni().getName(),
                        "Try another alumni or refine your request", "REFERRAL", req.getId());
            }
        } catch (Exception ignored) {}
        return toDto(req);
    }

    public List<ReferralDto> getMyRequests(Long studentId, String status) {
        studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        List<ReferralRequest> list;
        if (status != null && !status.isBlank()) {
            list = requestRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, status.trim().toUpperCase());
        } else {
            list = requestRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ReferralDto> getForAlumni(Long alumniId, String status) {
        alumniRepository.findById(alumniId).orElseThrow(() -> new IllegalArgumentException("Alumni not found: " + alumniId));
        List<ReferralRequest> list;
        if (status != null && !status.isBlank()) {
            list = requestRepository.findByAlumniIdAndStatusOrderByCreatedAtDesc(alumniId, status.trim().toUpperCase());
        } else {
            list = requestRepository.findByAlumniIdOrderByCreatedAtDesc(alumniId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public record ReferralDto(Long id, Long studentId, String studentName, Long alumniId, String alumniName, String status, String message, String company, String role, String createdAt) {}

    private ReferralDto toDto(ReferralRequest r) {
        return new ReferralDto(r.getId(),
                r.getStudent() != null ? r.getStudent().getId() : null,
                r.getStudent() != null ? r.getStudent().getName() : null,
                r.getAlumni() != null ? r.getAlumni().getId() : null,
                r.getAlumni() != null ? r.getAlumni().getName() : null,
                r.getStatus(), r.getMessage(), r.getCompany(), r.getRole(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
    }
}
