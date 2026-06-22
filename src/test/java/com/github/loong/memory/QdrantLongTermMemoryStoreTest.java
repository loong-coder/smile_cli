package com.github.loong.memory;

import junit.framework.TestCase;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 验证 Qdrant 长期记忆 HTTP 请求和响应解析。
 */
public class QdrantLongTermMemoryStoreTest extends TestCase {

    private MockWebServer server;
    private MemoryConfig config;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new MockWebServer();
        server.start();
        config = new MemoryConfig(true, 10, 131072, 10, 30, 16384, 30, 0.30d,
                server.url("/").toString(), "smile_cli_memory", "QDRANT_API_KEY",
                "aliyun", "http://embedding", "text-embedding-v4", "ALIYUN_API_KEY", 3);
    }

    @Override
    protected void tearDown() throws Exception {
        server.shutdown();
        super.tearDown();
    }

    public void testUpsertCreatesCollectionAndWritesPoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        QdrantLongTermMemoryStore store = new QdrantLongTermMemoryStore(config, "qdrant-key");
        MemoryEntry entry = MemoryEntry.create("ws1", MemoryRole.USER, MemoryType.FACT,
                "用户偏好中文", Map.of("source", "test"), 0.9d, Instant.parse("2026-06-22T00:00:00Z"));

        store.upsert("ws1", entry, List.of(0.1f, 0.2f, 0.3f));

        RecordedRequest getCollection = server.takeRequest();
        assertEquals("GET", getCollection.getMethod());
        assertEquals("/collections/smile_cli_memory_ws1", getCollection.getPath());
        RecordedRequest createCollection = server.takeRequest();
        assertEquals("PUT", createCollection.getMethod());
        assertEquals("/collections/smile_cli_memory_ws1", createCollection.getPath());
        assertTrue(createCollection.getBody().readUtf8().contains("\"size\":3"));
        RecordedRequest upsert = server.takeRequest();
        assertEquals("PUT", upsert.getMethod());
        assertEquals("/collections/smile_cli_memory_ws1/points?wait=true", upsert.getPath());
        assertEquals("Bearer qdrant-key", upsert.getHeader("api-key"));
        String body = upsert.getBody().readUtf8();
        assertTrue(body.contains("用户偏好中文"));
        assertTrue(body.contains("\"type\":\"FACT\""));
    }

    public void testSearchParsesCandidates() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("{\"result\":[{\"score\":0.9,\"payload\":{\"id\":\"m1\",\"workspaceId\":\"ws1\",\"type\":\"FACT\",\"role\":\"USER\",\"content\":\"长期事实\",\"metadata\":{},\"createdAt\":\"2026-06-22T00:00:00Z\",\"updatedAt\":\"2026-06-22T00:00:00Z\",\"accessCount\":2,\"importance\":0.8,\"tokenCount\":4,\"byteCount\":12}}]}"));
        QdrantLongTermMemoryStore store = new QdrantLongTermMemoryStore(config, "qdrant-key");

        List<RetrievedMemory> results = store.search("ws1", List.of(0.1f, 0.2f, 0.3f), 5);

        RecordedRequest exists = server.takeRequest();
        assertEquals("GET", exists.getMethod());
        RecordedRequest search = server.takeRequest();
        assertEquals("POST", search.getMethod());
        assertEquals("/collections/smile_cli_memory_ws1/points/search", search.getPath());
        String requestBody = search.getBody().readUtf8();
        assertTrue(requestBody.contains("\"limit\":5"));
        assertEquals(1, results.size());
        assertEquals("长期事实", results.get(0).entry().content());
        assertEquals(0.9d, results.get(0).similarityScore());
    }

    public void testClearWorkspaceDeletesCollection() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        QdrantLongTermMemoryStore store = new QdrantLongTermMemoryStore(config, "qdrant-key");

        store.clearWorkspace("ws1");

        RecordedRequest request = server.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/collections/smile_cli_memory_ws1", request.getPath());
    }
}
