package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findBySkillsContainingIgnoreCase(String skill);

    List<Student> findByBranchIgnoreCase(String branch);

    List<Student> findByAvailabilityIgnoreCase(String availability);
}
