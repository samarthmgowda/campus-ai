package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.SkillCoOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillCoOccurrenceRepository extends JpaRepository<SkillCoOccurrence, Long> {
    Optional<SkillCoOccurrence> findBySkillAIdAndSkillBId(Long skillAId, Long skillBId);
    List<SkillCoOccurrence> findBySkillAId(Long skillAId);
    List<SkillCoOccurrence> findBySkillBId(Long skillBId);
}