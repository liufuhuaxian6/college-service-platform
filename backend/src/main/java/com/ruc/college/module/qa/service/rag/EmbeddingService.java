package com.ruc.college.module.qa.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9]{2,}");

    private final ObjectMapper objectMapper;

    @Value("${rag.embedding.provider:local-hash}")
    private String provider;

    @Value("${rag.embedding.api-url:}")
    private String apiUrl;

    @Value("${rag.embedding.api-key:}")
    private String apiKey;

    @Value("${rag.embedding.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${rag.embedding.query-prefix:}")
    private String queryPrefix;

    @Value("${rag.embedding-dim:512}")
    private int dimension;

    private RestTemplate restTemplate;

    @PostConstruct
    void initRestTemplate() {
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    /**
     * 向量化文档片段（不加 query 前缀）。
     */
    public double[] embed(String text) {
        return embedInternal(text, false);
    }

    /**
     * 向量化用户查询（BGE 系列建议加 query 前缀以提升召回率）。
     */
    public double[] embedQuery(String text) {
        return embedInternal(text, true);
    }

    private double[] embedInternal(String text, boolean isQuery) {
        if ("http".equalsIgnoreCase(provider) && StringUtils.hasText(apiUrl)) {
            String payload = (isQuery && StringUtils.hasText(queryPrefix))
                    ? queryPrefix + (text == null ? "" : text)
                    : (text == null ? "" : text);
            double[] remote = embedByHttp(payload);
            if (remote != null && remote.length == dimension) {
                return normalize(remote);
            }
            if (remote != null) {
                log.warn("Embedding dimension mismatch: expected {} but got {}; falling back to local-hash",
                        dimension, remote.length);
            }
        }
        return embedByLocalHash(text);
    }

    public String toVectorLiteral(double[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(java.util.Locale.US, "%.8f", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    private double[] embedByHttp(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(apiKey)) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            Map<String, Object> body = new HashMap<>();
            String input = text == null ? "" : text;
            body.put("input", input);
            body.put("inputs", input);   // TEI /embed 端点字段名
            body.put("text", input);     // 兼容旧自建服务
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, new HttpEntity<>(body, headers), String.class);
            String raw = response.getBody();
            if (!StringUtils.hasText(raw)) return null;

            JsonNode root = objectMapper.readTree(raw);
            JsonNode embedding = extractEmbeddingArray(root);
            if (embedding == null || !embedding.isArray()) {
                log.warn("Embedding response did not contain a vector array: {}", truncate(raw, 200));
                return null;
            }

            double[] result = new double[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).asDouble();
            }
            return result;
        } catch (Exception e) {
            log.warn("Embedding HTTP call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 兼容多种向量服务响应格式：
     *   OpenAI / TEI /v1/embeddings:  {"data":[{"embedding":[...]}]}
     *   自建简单服务:                  {"embedding":[...]}
     *   TEI 原生 /embed:               [[...]] （顶层数组）
     *   TEI 原生 /embed 单条:          [...]
     */
    private static JsonNode extractEmbeddingArray(JsonNode root) {
        JsonNode candidate = root.path("embedding");
        if (candidate.isArray()) return candidate;

        candidate = root.path("data").path(0).path("embedding");
        if (candidate.isArray()) return candidate;

        candidate = root.path("embeddings").path(0);
        if (candidate.isArray()) return candidate;

        if (root.isArray() && root.size() > 0) {
            JsonNode first = root.get(0);
            if (first.isArray()) return first;       // [[...]]
            if (first.isNumber()) return root;       // [...]
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private double[] embedByLocalHash(String text) {
        double[] vector = new double[dimension];
        if (!StringUtils.hasText(text)) {
            return vector;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            addTokenFeatures(vector, matcher.group());
        }
        if (!matched) {
            addToken(vector, text.trim());
        }
        return normalize(vector);
    }

    private void addTokenFeatures(double[] vector, String token) {
        if (!StringUtils.hasText(token)) return;
        String normalized = token.trim().toLowerCase();
        if (containsHan(normalized)) {
            addChineseNgrams(vector, normalized);
        } else {
            addToken(vector, normalized);
        }
    }

    private void addChineseNgrams(double[] vector, String text) {
        String compact = text.replaceAll("\\s+", "");
        if (compact.length() <= 4) {
            addToken(vector, compact);
            return;
        }
        for (int n = 2; n <= 4; n++) {
            for (int i = 0; i <= compact.length() - n; i++) {
                addToken(vector, compact.substring(i, i + n));
            }
        }
    }

    private static boolean containsHan(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private void addToken(double[] vector, String token) {
        if (!StringUtils.hasText(token)) return;
        byte[] digest = digest(token.toLowerCase());
        int hash = 0;
        for (int i = 0; i < 4; i++) {
            hash = (hash << 8) | (digest[i] & 0xff);
        }
        int index = Math.floorMod(hash, vector.length);
        vector[index] += 1.0;
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return token.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static double[] normalize(double[] vector) {
        double norm = Math.sqrt(Arrays.stream(vector).map(v -> v * v).sum());
        if (norm == 0) return vector;
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
        return vector;
    }
}
