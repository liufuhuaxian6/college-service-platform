package com.ruc.college.module.qa.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruc.college.module.qa.service.ai.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class HttpAiProvider implements AiProvider {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;

    public HttpAiProvider(
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ai.api-url:}") String apiUrl,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.timeout-ms:5000}") int timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public String chat(String question, String context) {
        if (!StringUtils.hasText(apiUrl)) {
            return "暂未找到匹配的标准答案，建议您联系辅导员获取准确信息。";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(apiKey)) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("question", question);
            body.put("context", context == null ? "" : context);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            String raw = response.getBody();
            if (!StringUtils.hasText(raw)) {
                return "暂未找到匹配的标准答案，建议您联系辅导员获取准确信息。";
            }

            JsonNode root = objectMapper.readTree(raw);
            String answer = pickAnswer(root);
            if (StringUtils.hasText(answer)) {
                return answer.trim();
            }
            return "暂未找到匹配的标准答案，建议您联系辅导员获取准确信息。";
        } catch (Exception e) {
            return "暂未找到匹配的标准答案，建议您联系辅导员获取准确信息。";
        }
    }

    @Override
    public String getName() {
        return "http";
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiUrl);
    }

    private static String pickAnswer(JsonNode root) {
        String direct = textOrNull(root, "answer");
        if (StringUtils.hasText(direct)) return direct;

        JsonNode data = root.path("data");
        if (!data.isMissingNode()) {
            String dataAnswer = textOrNull(data, "answer");
            if (StringUtils.hasText(dataAnswer)) return dataAnswer;
        }

        String result = textOrNull(root, "result");
        if (StringUtils.hasText(result)) return result;

        JsonNode choices0 = root.path("choices").path(0);
        if (!choices0.isMissingNode()) {
            String msgContent = choices0.path("message").path("content").asText(null);
            if (StringUtils.hasText(msgContent)) return msgContent;
            String text = choices0.path("text").asText(null);
            if (StringUtils.hasText(text)) return text;
        }

        return null;
    }

    private static String textOrNull(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode v = node.get(fieldName);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        String text = v.asText(null);
        return StringUtils.hasText(text) ? text : null;
    }
}

