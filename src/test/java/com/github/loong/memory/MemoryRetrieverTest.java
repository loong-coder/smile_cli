package com.github.loong.memory;

import junit.framework.TestCase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 验证 RAG 检索会嵌入查询、综合排序、过滤阈值并遵守 TopK。
 */
public class MemoryRetrieverTest extends TestCase {

    public void testRetrieveScoresAndFiltersCandidates() throws Exception {
        FakeEmbeddingProvider embeddingProvider = new FakeEmbeddingProvider();
        FakeLongTermMemoryStore store = new FakeLongTermMemoryStore();
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        store.candidates.add(new RetrievedMemory(
                MemoryEntry.create("ws1", MemoryRole.USER, MemoryType.FACT, "高相关事实", Map.of(), 0.9d, now),
                0.95d,
                0.0d));
        store.candidates.add(new RetrievedMemory(
                MemoryEntry.create("ws1", MemoryRole.TOOL, MemoryType.TOOL_RESULT, "低相关工具", Map.of(), 0.1d, now.minusSeconds(200L * 24 * 3600)),
                0.05d,
                0.0d));
        MemoryConfig config = new MemoryConfig(true, 10, 131072, 10, 30, 16384, 30, 0.30d,
                "http://localhost:6333", "smile_cli_memory", "QDRANT_API_KEY",
                "aliyun", "http://embedding", "text-embedding-v4", "ALIYUN_API_KEY", 3);
        MemoryRetriever retriever = new MemoryRetriever(config, embeddingProvider, store, new MemoryScorer(30));

        List<RetrievedMemory> results = retriever.retrieve("ws1", "查询", now);

        assertEquals(List.of(1.0f, 2.0f, 3.0f), embeddingProvider.lastVector);
        assertEquals("ws1", store.lastWorkspaceId);
        assertEquals(30, store.lastLimit);
        assertEquals(1, results.size());
        assertEquals("高相关事实", results.get(0).entry().content());
        assertTrue(results.get(0).finalScore() >= 0.30d);
    }

    private static class FakeEmbeddingProvider implements EmbeddingProvider {
        private List<Float> lastVector;

        @Override
        public List<Float> embed(String text) {
            lastVector = List.of(1.0f, 2.0f, 3.0f);
            return lastVector;
        }
    }

    private static class FakeLongTermMemoryStore implements LongTermMemoryStore {
        private final List<RetrievedMemory> candidates = new ArrayList<>();
        private String lastWorkspaceId;
        private int lastLimit;

        @Override
        public void upsert(String workspaceId, MemoryEntry entry, List<Float> vector) {
        }

        @Override
        public List<RetrievedMemory> search(String workspaceId, List<Float> queryVector, int limit) {
            lastWorkspaceId = workspaceId;
            lastLimit = limit;
            return candidates;
        }

        @Override
        public void markAccessed(String workspaceId, List<MemoryEntry> entries, Instant now) {
        }

        @Override
        public void clearWorkspace(String workspaceId) {
        }
    }
}
