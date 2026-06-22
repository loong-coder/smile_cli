package com.github.loong.memory;

/**
 * 长期记忆检索结果，包含原始相似度和综合得分。
 */
public record RetrievedMemory(MemoryEntry entry, double similarityScore, double finalScore) {
}
