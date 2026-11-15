package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.service.EventService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")

public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Event> create(
            @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.idOrNull();
        String title = null;
        if (body != null && body.get("title") instanceof String) {
            title = (String) body.get("title");
        }
        String type = null;
        if (body != null && body.get("type") instanceof String) {
            type = (String) body.get("type");
        }
        LocalDate eventDate = null;
        if (body != null && body.get("eventDate") instanceof String) {
            String ds = (String) body.get("eventDate");
            if (!ds.isBlank()) {
                try { eventDate = LocalDate.parse(ds.trim()); } catch (Exception e) { throw new IllegalArgumentException("Invalid eventDate, expected YYYY-MM-DD"); }
            }
        } else if (body != null && body.get("date") instanceof String) {
            String ds2 = (String) body.get("date");
            if (!ds2.isBlank()) {
                try { eventDate = LocalDate.parse(ds2.trim()); } catch (Exception e) { throw new IllegalArgumentException("Invalid eventDate, expected YYYY-MM-DD"); }
            }
        }
        String description = null;
        if (body != null && body.get("description") instanceof String) {
            description = (String) body.get("description");
        }
        String location = null;
        if (body != null && body.get("location") instanceof String) {
            location = (String) body.get("location");
        }

        if (userId == null) {
            throw new IllegalArgumentException("User ID required to create event");
        }

        Event event = service.createEvent(userId, title, type, eventDate, description, location);
        return ResponseEntity.ok(event);
    }

    // GET /events?type=HACKATHON&upcoming=true
    @GetMapping
    public List<Event> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean upcoming
    ) {
        return service.search(type, upcoming);
    }

    /** GET /events/{id} — a single event, for detail views and card deep links. */
    @GetMapping("/{id}")
    public Event getOne(@PathVariable Long id) {
        return service.getEvent(id);
    }
}