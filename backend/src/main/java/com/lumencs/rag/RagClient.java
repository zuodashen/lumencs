package com.lumencs.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class RagClient {

    private static final Logger log = LoggerFactory.getLogger(RagClient.class);
    private final RestClient ragRestClient;

    public RagClient(RestClient ragRestClient) {
        this.ragRestClient = ragRestClient;
    }

    public boolean healthy() {
        try {
            Map<String, Object> body = ragRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body != null && "healthy".equals(String.valueOf(body.get("status")));
        } catch (Exception e) {
            return false;
        }
    }

    public int ingest(List<Map<String, Object>> points) {
        IngestResponse response = ragRestClient.post()
                .uri("/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", points))
                .retrieve()
                .body(IngestResponse.class);
        return response == null ? 0 : response.upserted;
    }

    public List<RagHit> search(String query, int topK) {
        return search(query, topK, null);
    }

    public List<RagHit> search(String query, int topK, Long documentId) {
        SearchResponse response = ragRestClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(documentId == null
                        ? Map.of("query", query, "top_k", topK)
                        : Map.of("query", query, "top_k", topK, "document_id", documentId))
                .retrieve()
                .body(SearchResponse.class);
        if (response == null || response.hits == null) {
            return List.of();
        }
        return response.hits.stream().map(hit -> {
            RagHit mapped = new RagHit();
            mapped.setId(hit.id);
            mapped.setScore(hit.score);
            Map<String, Object> payload = hit.payload == null ? Map.of() : hit.payload;
            mapped.setContent(String.valueOf(payload.getOrDefault("content", "")));
            mapped.setSource(String.valueOf(payload.getOrDefault("source", "")));
            Object docId = payload.get("document_id");
            if (docId instanceof Number n) {
                mapped.setDocumentId(n.longValue());
            }
            return mapped;
        }).toList();
    }

    public void deleteDocument(long documentId) {
        try {
            ragRestClient.post()
                    .uri("/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("document_id", documentId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("delete qdrant vectors failed for doc {}", documentId, e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class IngestResponse {
        private int upserted;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SearchResponse {
        private List<Hit> hits;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Hit {
        private String id;
        private double score;
        private Map<String, Object> payload;
    }
}
