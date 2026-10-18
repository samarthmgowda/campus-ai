package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.EmbeddingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * TEMPORARY - remove once semantic search endpoint exists.
 */
@RestController
@RequestMapping("/dev")
public class DevEmbeddingController {

    private final EmbeddingService embeddingService;

    public DevEmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam String text) {
        float[] vector = embeddingService.embed(text);
        float[] first5 = new float[Math.min(5, vector.length)];
        System.arraycopy(vector, 0, first5, 0, first5.length);

        Map<String, Object> response = new HashMap<>();
        response.put("dimension", vector.length);
        response.put("first5", first5);
        return response;
    }
}