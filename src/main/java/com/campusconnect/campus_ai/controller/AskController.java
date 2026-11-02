package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.AskService;
import com.campusconnect.campus_ai.service.ChatHistoryService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/ask")
public class AskController {

    private final AskService askService;
    private final ChatHistoryService chatHistoryService;

    public AskController(AskService askService, ChatHistoryService chatHistoryService) {
        this.askService = askService;
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping
    public AskService.AskResponse ask(
            @RequestParam String q,
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long userId = DemoIdentity.parseId(header);
        try {
            AskService.AskResponse resp = askService.ask(q, userId);
            if (userId != null) {
                chatHistoryService.save(userId, q, resp.answer(), resp.answered());
            }
            return resp;
        } catch (IllegalStateException e) {
            // Ollama unreachable or model not found — return a graceful response.
            AskService.AskResponse err = new AskService.AskResponse(
                    false, "RAG unavailable", java.util.List.of());
            if (userId != null) {
                chatHistoryService.save(userId, q, err.answer(), err.answered());
            }
            return err;
        }
    }
}