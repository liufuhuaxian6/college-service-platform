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
        return chat(question, context, null);
    }

    @Override
    public String chat(String question, String context, List<Map<String, String>> history) {
        if (!isAvailable()) {
            log.warn("AI provider 'openai' 未配置完整 (api-url/api-key/model 至少一项空), 交由上层走抽取式兜底");
            return null;
        }

        long t0 = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String systemPrompt = """
                    你是学院学生综合服务与党团管理平台的政策问答助手。
                    下面会给出若干以【数字】编号的政策资料，以及对话历史。
                    请优先依据这些资料回答；资料不充分时可结合常识谨慎作答，并提醒以学院正式通知为准、必要时联系辅导员核实，不要直接拒答。
                    回答要正式、简洁，涉及制度条款时保留条款编号与原文要点。
                    在回答的最后另起一行，标注你实际引用到的资料编号，格式严格为：[引用: 1,3]；若没有用到任何给定资料，则写：[引用: 无]。
                    """;

            // messages = system + 历史对话(最多保留最近若干轮) + 当前问题(带最新检索上下文)
            List<Map<String, Object>> messages = new java.util.ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            int historyUsed = 0;
            if (history != null) {
                for (Map<String, String> h : history) {
                    String role = h == null ? null : h.get("role");
                    String content = h == null ? null : h.get("content");
                    if (StringUtils.hasText(role) && StringUtils.hasText(content)
                            && ("user".equals(role) || "assistant".equals(role))) {
                        messages.add(Map.of("role", role, "content", content));
                        historyUsed++;
                    }
                }
            }
            messages.add(Map.of("role", "user", "content",
                    "【政策上下文】\n" + (context == null ? "" : context)
                            + "\n\n【学生问题】\n" + question));

            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "messages", messages
            );

            log.info("AI chat -> {} model={} qLen={} ctxLen={} historyMsgs={}",
                    apiUrl, model, question == null ? 0 : question.length(),
                    context == null ? 0 : context.length(), historyUsed);

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
            return null;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - t0;
            log.error("AI chat 调用失败 url={} model={} cost={}ms err={}",
                    apiUrl, model, cost, e.toString(), e);
            return null;
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
}
