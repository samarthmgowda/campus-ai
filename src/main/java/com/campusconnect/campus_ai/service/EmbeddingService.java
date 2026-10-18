package com.campusconnect.campus_ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
public class EmbeddingService {

    private final RestClient restClient;
    private final String model;
    private final String baseUrl;

    public EmbeddingService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.embedding-model}") String model
    ) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.create(baseUrl);
        this.model = model;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Input text must not be null or blank");
        }

        OllamaEmbedRequest request = new OllamaEmbedRequest(model, text);

        OllamaEmbedResponse response;
        try {
            response = restClient.post()
                    .uri("/api/embed")
                    .body(request)
                    .retrieve()
                    .body(OllamaEmbedResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not reach Ollama at " + baseUrl, e);
        }

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new IllegalStateException("Ollama returned empty or missing embeddings");
        }

        List<Double> embedding = response.embeddings().getFirst();
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("Ollama returned empty embedding array");
        }

        if (embedding.size() != 1024) {
            throw new IllegalStateException("Expected embedding dimension 1024, got " + embedding.size());
        }

        float[] result = new float[1024];
        for (int i = 0; i < 1024; i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }

    private record OllamaEmbedRequest(String model, String input) {}
    private record OllamaEmbedResponse(List<List<Double>> embeddings) {}
}