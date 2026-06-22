package com.github.loong.memory;

/**
 * 记忆系统状态快照，用于 /memory status 展示。
 */
public record MemoryStatus(String workspaceId,
                           int shortTermTurns,
                           int shortTermBytes,
                           boolean hasSummary,
                           boolean longTermEnabled,
                           String lastCompressionError,
                           String lastRetrievalError) {
}
