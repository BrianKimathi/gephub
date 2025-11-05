package com.gephub.builder_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {
    private final String apiKey;
    private final String model;
    private final String provider;
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    public AiService(@Value("${gephub.builder.ai.apiKey:}") String apiKey,
                     @Value("${gephub.builder.ai.model:gpt-4-turbo-preview}") String model,
                     @Value("${gephub.builder.ai.provider:openai}") String provider) {
        this.apiKey = apiKey;
        this.model = model;
        this.provider = provider;
    }

    public Map<String, Object> generateComponentTree(String prompt, String context) {
        if (apiKey == null || apiKey.isBlank()) {
            return generateMockComponentTree(prompt);
        }
        try {
            String systemPrompt = "You are a web component generator. Generate a JSON structure representing a React component tree. " +
                    "Return ONLY valid JSON with this structure: {\"type\":\"Container\",\"props\":{},\"children\":[]}. " +
                    "Component types: Container, Header, Footer, Button, Text, Image, Form, Input, Card, Section.";
            String userPrompt = context != null ? context + "\n\n" + prompt : prompt;
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            request.put("response_format", Map.of("type", "json_object"));
            request.put("temperature", 0.7);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            String url = "openai".equals(provider) ?
                    "https://api.openai.com/v1/chat/completions" :
                    "https://api.anthropic.com/v1/messages";
            ResponseEntity<Map> resp = http.postForEntity(url, new HttpEntity<>(request, headers), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body != null && body.containsKey("choices")) {
                List<Map> choices = (List<Map>) body.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    return json.readValue(content, Map.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return generateMockComponentTree(prompt);
    }

    private Map<String, Object> generateMockComponentTree(String prompt) {
        Map<String, Object> tree = new HashMap<>();
        tree.put("type", "Container");
        Map<String, Object> props = new HashMap<>();
        props.put("className", "min-h-screen bg-gray-50");
        tree.put("props", props);
        tree.put("children", List.of(
                Map.of("type", "Header", "props", Map.of("title", "Generated Page"), "children", List.of()),
                Map.of("type", "Text", "props", Map.of("content", prompt), "children", List.of()),
                Map.of("type", "Button", "props", Map.of("text", "Get Started", "variant", "primary"), "children", List.of())
        ));
        return tree;
    }

    public List<String> suggestComponents(String context) {
        return List.of("Header", "Footer", "Hero", "Card", "Form", "Button", "Image", "Text", "Container", "Section");
    }
}

