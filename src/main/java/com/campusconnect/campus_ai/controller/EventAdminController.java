package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.service.EventService;
import com.campusconnect.campus_ai.service.DemoIdentity;
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
    public List<Event> all(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long actorId = requireHeader(header);
        eventService.requireAdmin(actorId);
        return eventService.getAllIncludingHidden();
    }

    /** POST /events/admin/{id}/visibility {hidden: true|false} — admin approve/hide */
    @PostMapping("/{id}/visibility")
    public Event visibility(@PathVariable Long id,
                            @RequestHeader(value = "X-Demo-User", required = false) String header,
                            @RequestBody Map<String, Object> body) {
        Long actorId = requireHeader(header);
        boolean hidden = body != null && Boolean.TRUE.equals(body.get("hidden"));
        if (body != null && body.get("hidden") instanceof String s) hidden = Boolean.parseBoolean(s);
        return eventService.setVisibility(id, actorId, hidden);
    }

    private Long requireHeader(String header) {
        Long resolved = DemoIdentity.parseId(header);
        if (resolved != null) return resolved;
        throw new IllegalArgumentException("X-Demo-User header required");
    }
}
