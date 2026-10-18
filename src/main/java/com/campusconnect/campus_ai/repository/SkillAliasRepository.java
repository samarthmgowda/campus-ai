package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.SkillAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, Long> {
    Optional<SkillAlias> findByAliasIgnoreCase(String alias);
    List<SkillAlias> findBySkillId(Long skillId);
}