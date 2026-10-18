package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.AskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/ask")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @GetMapping
    public AskService.AskResponse ask(
            @RequestParam String q,
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long userId = null;
        if (header != null) {
            try {
                userId = Long.valueOf(header.trim());
            } catch (NumberFormatException ignored) {}
        }
        return askService.ask(q, userId);
    }
}