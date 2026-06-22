package com.github.loong.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 执行长期记忆 RAG 检索，并在 Java 侧进行综合评分和截断。
 */
public class MemoryRetriever {

    private final MemoryConfig config;
    private final EmbeddingProvider embeddingProvider;
    private final LongTermMemoryStore store;
    private final MemoryScorer scorer;

    public MemoryRetriever(MemoryConfig config,
                           EmbeddingProvider embeddingProvider,
                           LongTermMemoryStore store,
                           MemoryScorer scorer) {
        this.config = config;
        this.embeddingProvider = embeddingProvider;
        this.store = store;
        this.scorer = scorer;
    }

    public List<RetrievedMemory> retrieve(String workspaceId, String query, Instant now) throws MemoryException {
        List<Float> queryVector = embeddingProvider.embed(query == null ? "" : query);
        List<RetrievedMemory> candidates = store.search(workspaceId, queryVector, config.longTermCandidateK());
        List<RetrievedMemory> scored = new ArrayList<>();
        for (RetrievedMemory candidate : candidates) {
            double finalScore = scorer.score(candidate.entry(), candidate.similarityScore(), now);
            if (finalScore >= config.minScore()) {
                scored.add(new RetrievedMemory(candidate.entry(), candidate.similarityScore(), finalScore));
            }
        }
        scored.sort(Comparator.comparingDouble(RetrievedMemory::finalScore).reversed());
        if (scored.size() > config.longTermTopK()) {
            return List.copyOf(scored.subList(0, config.longTermTopK()));
        }
        return List.copyOf(scored);
    }
}
