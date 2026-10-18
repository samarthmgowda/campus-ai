package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.SkillEndorsement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillEndorsementRepository extends JpaRepository<SkillEndorsement, Long> {

    Optional<SkillEndorsement> findByEndorserIdAndEndorseeIdAndSkillIgnoreCase(Long endorserId, Long endorseeId, String skill);

    boolean existsByEndorserIdAndEndorseeIdAndSkillIgnoreCase(Long endorserId, Long endorseeId, String skill);

    List<SkillEndorsement> findByEndorseeIdOrderByCreatedAtDesc(Long endorseeId);

    List<SkillEndorsement> findByEndorseeIdAndSkillIgnoreCaseOrderByCreatedAtDesc(Long endorseeId, String skill);

    List<SkillEndorsement> findByEndorserIdOrderByCreatedAtDesc(Long endorserId);

    long countByEndorseeIdAndSkillIgnoreCase(Long endorseeId, String skill);

    long countByEndorseeId(Long endorseeId);
}
