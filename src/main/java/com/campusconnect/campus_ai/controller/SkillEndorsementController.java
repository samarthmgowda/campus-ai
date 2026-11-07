package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.SkillEndorsementService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SkillEndorsementController {

    private final SkillEndorsementService endorsementService;

    public SkillEndorsementController(SkillEndorsementService endorsementService) {
        this.endorsementService = endorsementService;
    }

 /** POST /endorsements {endorseeId, skill} (endorser) */
    @PostMapping("/endorsements")
    public SkillEndorsementService.EndorsementDto endorse(
            @RequestBody Map<String, Object> body) {
        Long endorserId = AuthContext.id();
        Long endorseeId = body != null ? toLong(body.get("endorseeId")) : null;
        if (endorseeId == null) endorseeId = body != null ? toLong(body.get("studentId")) : null;
        if (endorseeId == null) throw new IllegalArgumentException("endorseeId required");
        String skill = body != null && body.get("skill") instanceof String s ? s : "";
        return endorsementService.endorse(endorserId, endorseeId, skill);
    }

 /** POST /endorsements/withdraw {endorseeId, skill} (endorser) */
    @PostMapping("/endorsements/withdraw")
    public Map<String, Object> withdraw(
            @RequestBody Map<String, Object> body) {
        Long endorserId = AuthContext.id();
        Long endorseeId = body != null ? toLong(body.get("endorseeId")) : null;
        if (endorseeId == null) endorseeId = body != null ? toLong(body.get("studentId")) : null;
        if (endorseeId == null) throw new IllegalArgumentException("endorseeId required");
        String skill = body != null && body.get("skill") instanceof String s ? s : "";
        return endorsementService.withdraw(endorserId, endorseeId, skill);
    }

    /** GET /endorsements/{endorseeId}?skill= — endorsements for a student */
    @GetMapping("/endorsements/{endorseeId}")
    public List<SkillEndorsementService.EndorsementDto> getForEndorsee(
            @PathVariable Long endorseeId,
            @RequestParam(required = false) String skill) {
        return endorsementService.getForEndorsee(endorseeId, skill);
    }

    /** GET /endorsements/{endorseeId}/summary — grouped counts by skill */
    @GetMapping("/endorsements/{endorseeId}/summary")
    public Map<String, Object> summary(@PathVariable Long endorseeId) {
        return endorsementService.getSummary(endorseeId);
    }

 /** GET /endorsements/given/my — endorsements I gave */
    @GetMapping("/endorsements/given/my")
    public List<SkillEndorsementService.EndorsementDto> given() {
        Long endorserId = AuthContext.id();
        return endorsementService.getByEndorser(endorserId);
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
