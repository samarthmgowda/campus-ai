package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.service.EventService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public Event create(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long userId = requireHeader(header);
        String title = body != null && body.get("title") instanceof String s ? s : null;
        String type = body != null && body.get("type") instanceof String s ? s : null;
        LocalDate eventDate = null;
        if (body != null && body.get("eventDate") instanceof String ds && !ds.isBlank()) {
            try { eventDate = LocalDate.parse(ds.trim()); } catch (Exception e) { throw new IllegalArgumentException("Invalid eventDate, expected YYYY-MM-DD"); }
        } else if (body != null && body.get("date") instanceof String ds2 && !ds2.isBlank()) {
            try { eventDate = LocalDate.parse(ds2.trim()); } catch (Exception e) { throw new IllegalArgumentException("Invalid eventDate, expected YYYY-MM-DD"); }
        }
        String description = body != null && body.get("description") instanceof String s ? s : null;
        String location = body != null && body.get("location") instanceof String s ? s : null;
        return service.createEvent(userId, title, type, eventDate, description, location);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        return DemoIdentity.requireId(header);
    }

    // GET /events?type=HACKATHON&upcoming=true
    @GetMapping
    public List<Event> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean upcoming
    ) {
        return service.search(type, upcoming);
    }
}
