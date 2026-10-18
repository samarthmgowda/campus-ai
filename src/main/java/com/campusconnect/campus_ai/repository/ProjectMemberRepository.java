package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    List<ProjectMember> findByStudentId(Long studentId);
    List<ProjectMember> findByProjectId(Long projectId);
}
