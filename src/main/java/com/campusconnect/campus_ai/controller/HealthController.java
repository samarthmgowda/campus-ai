package com.campusconnect.campus_ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liveness probe for the browser.
 *
 * When the SPA loses its connection it shows a "server unreachable" screen with
 * a retry button. That retry needs something to ask — polling a real data
 * endpoint would 401 without a token and misreport a healthy server as down, so
 * this answers unauthenticated and does no work beyond proving the request
 * reached a live Spring context.
 *
 * It intentionally does not touch the database. "The API process is answering"
 * is the question the error page is asking; a DB outage is reported by the
 * endpoints that need the DB.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "campus-ai");
        body.put("time", System.currentTimeMillis());
        return ResponseEntity.ok(body);
    }
}
