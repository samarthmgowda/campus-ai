package com.campusconnect.campus_ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
            @Value("${ollama.embedding-model}") String model,
            @Value("${ollama.embed-timeout-ms:30000}") int embedTimeoutMs
    ) {
        this.baseUrl = baseUrl;
        this.model = model;

        // A read timeout is not optional here. RestClient.create(baseUrl) — which this
        // used to call — has no timeout at all, so a hung or wedged Ollama leaves the
        // calling thread blocked indefinitely. Embedding runs inside retrieval, so
        // that is not one slow request: under load it is thread starvation, since every
        // in-flight query parks a request thread on a socket that will never answer.
        // Chat has always had a 120s cap; embedding had none.
        //
        // 30s is deliberate, and shorter than chat's 120s: an embed is a single forward
        // pass over a short chunk, whereas chat generates num_predict tokens on CPU and
        // legitimately needs minutes. Anything an embed has not answered in 30s is hung,
        // not slow.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(embedTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
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