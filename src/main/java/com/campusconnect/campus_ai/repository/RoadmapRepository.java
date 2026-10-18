package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
    List<Roadmap> findByAlumniId(Long alumniId);
}