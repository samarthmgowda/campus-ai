package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.EventTemplate;
import com.campusconnect.campus_ai.repository.EventTemplateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/event-templates")
public class EventTemplateController {

    private final EventTemplateRepository templateRepository;

    public EventTemplateController(EventTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /** GET /event-templates — list all active event templates */
    @GetMapping
    public ResponseEntity<List<EventTemplate>> list() {
        List<EventTemplate> templates = templateRepository.findAllByIsActiveTrueOrderByCreatedAtDesc();
        return ResponseEntity.ok(templates);
    }

    /** GET /event-templates/{name} — find by template name */
    @GetMapping("/{templateName}")
    public ResponseEntity<EventTemplate> findByName(@PathVariable String templateName) {
        List<EventTemplate> results = templateRepository.findByTemplateNameAndIsActiveTrue(templateName);
        if (results.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(results.get(0));
    }
}