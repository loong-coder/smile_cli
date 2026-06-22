package com.github.loong.memory;

import junit.framework.TestCase;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.util.List;

/**
 * 验证阿里云 embedding 请求格式和响应解析。
 */
public class AliyunEmbeddingProviderTest extends TestCase {

    private MockWebServer server;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new MockWebServer();
        server.start();
    }

    @Override
    protected void tearDown() throws Exception {
        server.shutdown();
        super.tearDown();
    }

    public void testEmbedSendsCompatibleRequestAndParsesVector() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}"));
        MemoryConfig config = new MemoryConfig(true, 10, 131072, 10, 30, 16384, 30, 0.30d,
                "http://localhost:6333", "smile_cli_memory", "QDRANT_API_KEY",
                "aliyun", server.url("/").toString(), "text-embedding-v4", "ALIYUN_API_KEY", 3);
        AliyunEmbeddingProvider provider = new AliyunEmbeddingProvider(config, "test-key");

        List<Float> vector = provider.embed("hello");

        assertEquals(List.of(0.1f, 0.2f, 0.3f), vector);
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("Bearer test-key", request.getHeader("Authorization"));
        assertEquals("/embeddings", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"text-embedding-v4\""));
        assertTrue(body.contains("\"input\":\"hello\""));
    }

    public void testEmbedThrowsMemoryExceptionOnHttpFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("server error"));
        MemoryConfig config = new MemoryConfig(true, 10, 131072, 10, 30, 16384, 30, 0.30d,
                "http://localhost:6333", "smile_cli_memory", "QDRANT_API_KEY",
                "aliyun", server.url("/").toString(), "text-embedding-v4", "ALIYUN_API_KEY", 3);
        AliyunEmbeddingProvider provider = new AliyunEmbeddingProvider(config, "test-key");

        try {
            provider.embed("hello");
            fail("HTTP 500 应抛出 MemoryException");
        } catch (MemoryException e) {
            assertTrue(e.getMessage().contains("阿里云 embedding 请求失败"));
        }
    }
}
