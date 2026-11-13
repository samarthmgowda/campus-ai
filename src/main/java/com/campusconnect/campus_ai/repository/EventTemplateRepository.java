package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.EventTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EventTemplateRepository extends JpaRepository<EventTemplate, Long> {

    List<EventTemplate> findByTemplateNameAndIsActiveTrue(String templateName);

    List<EventTemplate> findAllByIsActiveTrueOrderByCreatedAtDesc();
}