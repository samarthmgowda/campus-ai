package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ChatOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatOrchestrator orchestrator;

    public ChatController(ChatOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long userId = requireHeader(header);
        String question = body != null && body.get("question") instanceof String s ? s : null;
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question field required");
        }
        boolean forceRag = Boolean.TRUE.equals(body.get("forceRag"));
        boolean forceTool = Boolean.TRUE.equals(body.get("forceTool"));

        String answer = orchestrator.chat(userId, question, forceRag, forceTool);
        return ResponseEntity.ok(Map.of(
                "answer", answer,
                "userId", userId,
                "question", question
        ));
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("X-Demo-User header required");
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid X-Demo-User");
        }
    }
}
