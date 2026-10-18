package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.SkillEndorsement;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.SkillEndorsementRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SkillEndorsementService {

    private final SkillEndorsementRepository endorsementRepository;
    private final StudentRepository studentRepository;
    private final SkillGraphService skillGraphService;
    private final NotificationService notificationService;

    public SkillEndorsementService(SkillEndorsementRepository endorsementRepository,
                                   StudentRepository studentRepository,
                                   SkillGraphService skillGraphService,
                                   NotificationService notificationService) {
        this.endorsementRepository = endorsementRepository;
        this.studentRepository = studentRepository;
        this.skillGraphService = skillGraphService;
        this.notificationService = notificationService;
    }

    public EndorsementDto endorse(Long endorserId, Long endorseeId, String rawSkill) {
        if (endorserId == null || endorseeId == null) throw new IllegalArgumentException("endorserId and endorseeId required");
        if (endorserId.equals(endorseeId)) throw new IllegalArgumentException("Cannot endorse yourself");
        if (rawSkill == null || rawSkill.isBlank()) throw new IllegalArgumentException("skill required");
        String canonical = skillGraphService.normalize(rawSkill.trim());
        if (canonical == null || canonical.isBlank()) throw new IllegalArgumentException("Invalid skill");
        Student endorser = studentRepository.findById(endorserId)
                .orElseThrow(() -> new IllegalArgumentException("Endorser not found: " + endorserId));
        Student endorsee = studentRepository.findById(endorseeId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + endorseeId));
        // Optional strict check: endorsee should have the skill on profile (kept permissive for demo)
        if (endorsementRepository.existsByEndorserIdAndEndorseeIdAndSkillIgnoreCase(endorserId, endorseeId, canonical)) {
            var existing = endorsementRepository.findByEndorserIdAndEndorseeIdAndSkillIgnoreCase(endorserId, endorseeId, canonical).get();
            return toDto(existing);
        }
        SkillEndorsement e = new SkillEndorsement(endorser, endorsee, canonical);
        e = endorsementRepository.save(e);
        try {
            notificationService.notify(endorseeId, "ENDORSEMENT", endorser.getName() + " endorsed your " + canonical + " skill", null, "SKILL_ENDORSEMENT", e.getId());
        } catch (Exception ignored) {}
        return toDto(e);
    }

    public Map<String, Object> withdraw(Long endorserId, Long endorseeId, String rawSkill) {
        if (rawSkill == null || rawSkill.isBlank()) throw new IllegalArgumentException("skill required");
        String canonical = skillGraphService.normalize(rawSkill.trim());
        SkillEndorsement e = endorsementRepository.findByEndorserIdAndEndorseeIdAndSkillIgnoreCase(endorserId, endorseeId, canonical)
                .orElseThrow(() -> new IllegalArgumentException("Endorsement not found for " + canonical));
        endorsementRepository.delete(e);
        return Map.of("endorserId", endorserId, "endorseeId", endorseeId, "skill", canonical, "status", "withdrawn");
    }

    public List<EndorsementDto> getForEndorsee(Long endorseeId, String skill) {
        studentRepository.findById(endorseeId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + endorseeId));
        List<SkillEndorsement> list;
        if (skill != null && !skill.isBlank()) {
            String canonical = skillGraphService.normalize(skill.trim());
            list = endorsementRepository.findByEndorseeIdAndSkillIgnoreCaseOrderByCreatedAtDesc(endorseeId, canonical);
        } else {
            list = endorsementRepository.findByEndorseeIdOrderByCreatedAtDesc(endorseeId);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<EndorsementDto> getByEndorser(Long endorserId) {
        return endorsementRepository.findByEndorserIdOrderByCreatedAtDesc(endorserId).stream().map(this::toDto).collect(Collectors.toList());
    }

    public Map<String, Object> getSummary(Long endorseeId) {
        studentRepository.findById(endorseeId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + endorseeId));
        List<SkillEndorsement> all = endorsementRepository.findByEndorseeIdOrderByCreatedAtDesc(endorseeId);
        Map<String, Long> bySkill = all.stream().collect(Collectors.groupingBy(SkillEndorsement::getSkill, Collectors.counting()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("endorseeId", endorseeId);
        m.put("totalEndorsements", all.size());
        m.put("bySkill", bySkill);
        return m;
    }

    public record EndorsementDto(Long id, Long endorserId, String endorserName, Long endorseeId, String endorseeName, String skill, String createdAt) {}

    private EndorsementDto toDto(SkillEndorsement e) {
        return new EndorsementDto(e.getId(),
                e.getEndorser() != null ? e.getEndorser().getId() : null,
                e.getEndorser() != null ? e.getEndorser().getName() : null,
                e.getEndorsee() != null ? e.getEndorsee().getId() : null,
                e.getEndorsee() != null ? e.getEndorsee().getName() : null,
                e.getSkill(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
    }
}
