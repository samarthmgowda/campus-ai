package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ChatOrchestrator;
import com.campusconnect.campus_ai.service.AuthContext;
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
            @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.id();
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

}
