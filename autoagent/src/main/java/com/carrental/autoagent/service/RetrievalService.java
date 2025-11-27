package com.carrental.autoagent.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class RetrievalService {
    private final RestTemplate rest = new RestTemplate();
    private static final String BASE = "http://localhost:8000";

    @SuppressWarnings("unchecked")
    public List<String> query(String query, int k) {
        Map<String, Object> req = Map.of("query", query, "k", k);
        ResponseEntity<Map> resp = rest.postForEntity(BASE + "/query", req, Map.class);
        Object results = resp.getBody().get("results");
        return results == null ? List.of() : (List<String>) results;
    }

    public void insert(String text) {
        rest.postForObject(BASE + "/insert", Map.of("text", text), Map.class);
    }
}