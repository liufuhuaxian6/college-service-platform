package com.ruc.college.module.qa.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * 验证 EmbeddingService HTTP 调用对各种 embedding 服务响应格式的兼容性。
 * 覆盖 TEI v1/embeddings、TEI 原生 /embed、自建简单服务、查询前缀。
 */
class EmbeddingServiceTest {

    private static final String FAKE_URL = "http://embedding.test/v1/embeddings";
    private static final int DIM = 4;

    private EmbeddingService service;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        service = new EmbeddingService(new ObjectMapper());
        RestTemplate restTemplate = new RestTemplate();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "provider", "http");
        ReflectionTestUtils.setField(service, "apiUrl", FAKE_URL);
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "timeoutMs", 5000);
        ReflectionTestUtils.setField(service, "queryPrefix", "");
        ReflectionTestUtils.setField(service, "dimension", DIM);
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void parsesOpenAiCompatibleResponse() {
        // TEI /v1/embeddings 格式
        mockServer.expect(requestTo(FAKE_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.5,0.5,0.5,0.5]}]}"));

        double[] result = service.embed("入党流程");
        assertNotNull(result);
        assertEquals(DIM, result.length);
        // 归一化后每维 = 0.5/sqrt(1.0) = 0.5
        assertEquals(0.5, result[0], 1e-6);
        mockServer.verify();
    }

    @Test
    void parsesSimpleEmbeddingFieldResponse() {
        mockServer.expect(requestTo(FAKE_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"embedding\":[1.0,0.0,0.0,0.0]}"));

        double[] result = service.embed("test");
        assertNotNull(result);
        assertEquals(DIM, result.length);
        assertEquals(1.0, result[0], 1e-6);
        assertEquals(0.0, result[1], 1e-6);
    }

    @Test
    void parsesTeiNativeNestedArrayResponse() {
        // TEI /embed 原生格式：[[...]]
        mockServer.expect(requestTo(FAKE_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[[0.1,0.2,0.3,0.4]]"));

        double[] result = service.embed("test");
        assertNotNull(result);
        assertEquals(DIM, result.length);
        // 归一化前: [0.1, 0.2, 0.3, 0.4], norm = sqrt(0.3)
        double norm = Math.sqrt(0.01 + 0.04 + 0.09 + 0.16);
        assertEquals(0.1 / norm, result[0], 1e-6);
        assertEquals(0.4 / norm, result[3], 1e-6);
    }

    @Test
    void parsesEmbeddingsArrayFieldResponse() {
        // 某些服务返回 {"embeddings":[[...]]}
        mockServer.expect(requestTo(FAKE_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"embeddings\":[[0.0,1.0,0.0,0.0]]}"));

        double[] result = service.embed("test");
        assertNotNull(result);
        assertEquals(1.0, result[1], 1e-6);
    }

    @Test
    void fallsBackToLocalHashOnDimensionMismatch() {
        // 服务返回了非 DIM 维度的向量，应回退到 local-hash 而不是抛错
        mockServer.expect(requestTo(FAKE_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"embedding\":[0.1,0.2]}"));   // 只有 2 维, 不是 DIM=4

        double[] result = service.embed("入党");
        assertNotNull(result);
        assertEquals(DIM, result.length);   // 应是 local-hash 兜底, DIM 维
    }

    @Test
    void fallsBackToLocalHashOnHttpError() {
        mockServer.expect(requestTo(FAKE_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        double[] result = service.embed("test");
        assertNotNull(result);
        assertEquals(DIM, result.length);
    }

    @Test
    void embedQueryAppendsConfiguredPrefix() {
        ReflectionTestUtils.setField(service, "queryPrefix", "为这个句子生成表示以用于检索相关文章：");

        mockServer.expect(requestTo(FAKE_URL))
                .andExpect(request -> {
                    String body = new String(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                            .getBodyAsString().getBytes());
                    assertTrue(body.contains("为这个句子生成表示以用于检索相关文章：入党流程"),
                            "Query prefix should be prepended for embedQuery; body=" + body);
                })
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"embedding\":[0.5,0.5,0.5,0.5]}"));

        double[] result = service.embedQuery("入党流程");
        assertNotNull(result);
        mockServer.verify();
    }

    @Test
    void embedDocumentDoesNotApplyQueryPrefix() {
        ReflectionTestUtils.setField(service, "queryPrefix", "QUERY_PREFIX:");

        mockServer.expect(requestTo(FAKE_URL))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertFalse(body.contains("QUERY_PREFIX:"),
                            "Document embedding should NOT have query prefix; body=" + body);
                })
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"embedding\":[1,0,0,0]}"));

        double[] result = service.embed("第一条 党员资格...");
        assertNotNull(result);
        mockServer.verify();
    }

    @Test
    void toVectorLiteralFormatsCorrectly() {
        double[] vec = new double[]{0.123456789, -0.5, 0.0};
        String literal = service.toVectorLiteral(vec);
        assertTrue(literal.startsWith("[") && literal.endsWith("]"));
        assertTrue(literal.contains("0.12345679"));
        assertTrue(literal.contains("-0.50000000"));
    }

    @Test
    void localHashEmbeddingProducesUnitVector() {
        ReflectionTestUtils.setField(service, "provider", "local-hash");
        double[] result = service.embed("入党积极分子");
        assertEquals(DIM, result.length);
        double norm = 0;
        for (double v : result) norm += v * v;
        assertEquals(1.0, Math.sqrt(norm), 1e-6, "local-hash vector must be L2 normalized");
    }
}
