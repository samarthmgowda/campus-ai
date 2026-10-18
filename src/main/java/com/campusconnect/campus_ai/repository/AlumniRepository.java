package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Alumni;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlumniRepository extends JpaRepository<Alumni, Long> {
    List<Alumni> findByAvailabilityIgnoreCase(String availability);
    Optional<Alumni> findByName(String name);
}