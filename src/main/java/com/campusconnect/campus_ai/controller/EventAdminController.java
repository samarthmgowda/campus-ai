package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.service.EventService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events/admin")
public class EventAdminController {

    private final EventService eventService;

    public EventAdminController(EventService eventService) {
        this.eventService = eventService;
    }

    /** GET /events/admin/all — admin view including pending (hidden) */
    @GetMapping("/all")
    public List<Event> all() {
        eventService.requireAdmin();
        return eventService.getAllIncludingHidden();
    }

    /** POST /events/admin/{id}/visibility {hidden: true|false} — admin approve/hide */
    @PostMapping("/{id}/visibility")
    public Event visibility(@PathVariable Long id,
                            @RequestBody Map<String, Object> body) {
        boolean hidden = body != null && Boolean.TRUE.equals(body.get("hidden"));
        if (body != null && body.get("hidden") instanceof String s) hidden = Boolean.parseBoolean(s);
        return eventService.setVisibility(id, hidden);
    }

}
