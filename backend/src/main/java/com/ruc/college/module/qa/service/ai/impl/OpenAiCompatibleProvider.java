package com.ruc.college.module.qa.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruc.college.module.qa.service.ai.AiProvider;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions compatible provider.
 *
 * <p>可直接对接 OpenAI、通义千问 DashScope 兼容模式、DeepSeek 等兼容
 * /v1/chat/completions 协议的服务。</p>
 */
@Slf4j
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleProvider(
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ai.api-url:}") String apiUrl,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.model:}") String model,
            @Value("${ai.timeout-ms:5000}") int timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public String chat(String question, String context) {
        if (!isAvailable()) {
            log.warn("AI provider 'openai' 未配置完整 (api-url/api-key/model 至少一项空), 走 fallback");
            return fallback();
        }

        long t0 = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String systemPrompt = """
                    你是学院学生综合服务与党团管理平台的政策问答助手。
                    只能基于给定上下文回答；上下文没有明确依据时，请说明未找到明确依据并建议联系辅导员。
                    回答要正式、简洁，涉及制度条款时尽量保留条款编号和原文要点。
                    """;

            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content",
                                    "【政策上下文】\n" + (context == null ? "" : context)
                                            + "\n\n【学生问题】\n" + question)
                    )
            );

            log.info("AI chat -> {} model={} qLen={} ctxLen={}",
                    apiUrl, model, question == null ? 0 : question.length(),
                    context == null ? 0 : context.length());

            ResponseEntity<String> response = restTemplate.postForEntity(
                    apiUrl,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            long cost = System.currentTimeMillis() - t0;
            JsonNode root = objectMapper.readTree(response.getBody());
            String answer = root.path("choices").path(0).path("message").path("content").asText(null);
            if (StringUtils.hasText(answer)) {
                log.info("AI chat OK cost={}ms answerLen={}", cost, answer.length());
                return answer.trim();
            }
            log.warn("AI chat 返回空 content, cost={}ms raw[0..200]={}",
                    cost,
                    response.getBody() == null ? "null" :
                            response.getBody().substring(0, Math.min(200, response.getBody().length())));
            return fallback();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - t0;
            log.error("AI chat 调用失败 url={} model={} cost={}ms err={}",
                    apiUrl, model, cost, e.toString(), e);
            return fallback();
        }
    }

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiUrl)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(model);
    }

    private static String fallback() {
        return "暂未找到匹配的标准答案，建议您联系辅导员获取准确信息。";
    }
}
