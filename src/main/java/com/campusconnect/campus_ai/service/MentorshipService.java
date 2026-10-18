package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.MentorshipRequest;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.MentorshipRequestRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MentorshipService {

    private final MentorshipRequestRepository requestRepository;
    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final NotificationService notificationService;

    public MentorshipService(MentorshipRequestRepository requestRepository,
                             StudentRepository studentRepository,
                             AlumniRepository alumniRepository,
                             NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
        this.notificationService = notificationService;
    }

    public MentorshipDto request(Long studentId, Long alumniId, String message) {
        if (studentId == null || alumniId == null) throw new IllegalArgumentException("studentId and alumniId required");
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        Alumni alumni = alumniRepository.findById(alumniId)
                .orElseThrow(() -> new IllegalArgumentException("Alumni not found: " + alumniId));
        // availability guard: UNAVAILABLE alumni cannot be requested
        if ("UNAVAILABLE".equalsIgnoreCase(alumni.getAvailability())) {
            throw new IllegalArgumentException("Alumni is not available for mentorship");
        }
        String msg = message != null ? message.trim() : null;
        if (msg != null && msg.length() > 2000) throw new IllegalArgumentException("Message too long (max 2000)");
        var existing = requestRepository.findByStudentIdAndAlumniId(studentId, alumniId);
        if (existing.isPresent()) {
            String st = existing.get().getStatus();
            if ("PENDING".equals(st)) return toDto(existing.get());
            if ("ACCEPTED".equals(st)) throw new IllegalArgumentException("Already connected for mentorship");
            // REJECTED -> reset to PENDING
            existing.get().setStatus("PENDING");
            if (msg != null) existing.get().setMessage(msg);
            var saved = requestRepository.save(existing.get());
            return toDto(saved);
        }
        MentorshipRequest req = new MentorshipRequest(student, alumni, msg);
        req = requestRepository.save(req);
        // notify student-facing side: confirm request; alumni are not Students, so no inbox notification needed beyond requester trace
        // We do notify the requesting student via a system notification for traceability if needed, skip alumni DM
        return toDto(req);
    }

    public MentorshipDto respond(Long requestId, Long actorStudentId, String action) {
        if (requestId == null || actorStudentId == null || action == null)
            throw new IllegalArgumentException("requestId, actor and action required");
        MentorshipRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Mentorship request not found: " + requestId));
        // Only the requesting student can withdraw/cancel; but accept/reject is conceptually alumni action.
        // In this campus-only demo, we allow the requesting student to self-manage rejection flow, and also allow any actor that is the student owner to accept/reject.
        // Stricter: only the student who created it can act; alumni identity is not a Student row. So gate on owner.
        if (!req.getStudent().getId().equals(actorStudentId)) {
            throw new IllegalArgumentException("Not your mentorship request");
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
            return new MentorshipDto(req.getId(), req.getStudent().getId(), req.getStudent().getName(), req.getAlumni().getId(), req.getAlumni().getName(), "WITHDRAWN", req.getMessage(), req.getCreatedAt() != null ? req.getCreatedAt().toString() : null);
        }
        req.setStatus(accept ? "ACCEPTED" : "REJECTED");
        req = requestRepository.save(req);
        try {
            if (accept) {
                notificationService.notify(req.getStudent().getId(), "MENTORSHIP", "Mentorship accepted by " + req.getAlumni().getName(),
                        req.getAlumni().getCurrentRole() + " at " + req.getAlumni().getCurrentCompany(), "MENTORSHIP", req.getId());
            }
        } catch (Exception ignored) {}
        return toDto(req);
    }

    public Map<String, Object> withdraw(Long requestId, Long actorStudentId) {
        MentorshipRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Mentorship request not found: " + requestId));
        if (!req.getStudent().getId().equals(actorStudentId)) throw new IllegalArgumentException("Not your mentorship request");
        if (!"PENDING".equals(req.getStatus())) throw new IllegalArgumentException("Request already " + req.getStatus().toLowerCase());
        requestRepository.delete(req);
        return Map.of("id", requestId, "status", "withdrawn");
    }

    // Alumni-side simulation: accept/reject via alumniId without Student auth — kept for demo completeness
    public MentorshipDto alumniRespond(Long requestId, Long alumniId, String action) {
        MentorshipRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Mentorship request not found: " + requestId));
        if (!req.getAlumni().getId().equals(alumniId)) throw new IllegalArgumentException("Not your mentorship request");
        if (!"PENDING".equals(req.getStatus())) throw new IllegalArgumentException("Request already " + req.getStatus().toLowerCase());
        String act = action != null ? action.trim().toUpperCase() : "ACCEPTED";
        if (!act.equals("ACCEPT") && !act.equals("ACCEPTED") && !act.equals("REJECT") && !act.equals("REJECTED"))
            throw new IllegalArgumentException("Action must be ACCEPT or REJECT");
        boolean accept = act.startsWith("ACCEPT");
        req.setStatus(accept ? "ACCEPTED" : "REJECTED");
        req = requestRepository.save(req);
        try {
            if (accept) {
                notificationService.notify(req.getStudent().getId(), "MENTORSHIP", "Mentorship accepted by " + req.getAlumni().getName(),
                        req.getAlumni().getCurrentRole() + " at " + req.getAlumni().getCurrentCompany(), "MENTORSHIP", req.getId());
            } else {
                notificationService.notify(req.getStudent().getId(), "MENTORSHIP", "Mentorship declined by " + req.getAlumni().getName(),
                        "Try another mentor or refine your request", "MENTORSHIP", req.getId());
            }
        } catch (Exception ignored) {}
        return toDto(req);
    }

    public List<MentorshipDto> getMyRequests(Long studentId, String status) {
        studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        List<MentorshipRequest> list;
        if (status != null && !status.isBlank()) {
            list = requestRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, status.trim().toUpperCase());
        } else {
            list = requestRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<MentorshipDto> getIncomingRequests(Long alumniId, String status) {
        return getForAlumni(alumniId, status);
    }

    public List<MentorshipDto> getForAlumni(Long alumniId, String status) {
        alumniRepository.findById(alumniId).orElseThrow(() -> new IllegalArgumentException("Alumni not found: " + alumniId));
        List<MentorshipRequest> list;
        if (status != null && !status.isBlank()) {
            list = requestRepository.findByAlumniIdAndStatusOrderByCreatedAtDesc(alumniId, status.trim().toUpperCase());
        } else {
            list = requestRepository.findByAlumniIdOrderByCreatedAtDesc(alumniId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }


    public record MentorshipDto(Long id, Long studentId, String studentName, Long alumniId, String alumniName, String status, String message, String createdAt) {}

    private MentorshipDto toDto(MentorshipRequest r) {
        return new MentorshipDto(r.getId(),
                r.getStudent() != null ? r.getStudent().getId() : null,
                r.getStudent() != null ? r.getStudent().getName() : null,
                r.getAlumni() != null ? r.getAlumni().getId() : null,
                r.getAlumni() != null ? r.getAlumni().getName() : null,
                r.getStatus(), r.getMessage(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
    }
}
