package com.github.loong.memory;

import com.github.loong.llm.util.TokenizerUtil;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 单条记忆对象，统一承载短期、摘要和长期记忆所需字段。
 */
public record MemoryEntry(String id,
                          String workspaceId,
                          MemoryRole role,
                          MemoryType type,
                          String content,
                          Map<String, String> metadata,
                          Instant createdAt,
                          Instant updatedAt,
                          Instant lastAccessedAt,
                          int accessCount,
                          double importance,
                          int tokenCount,
                          int byteCount) {

    public MemoryEntry {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        content = content == null ? "" : content;
    }

    public static MemoryEntry create(String workspaceId,
                                     MemoryRole role,
                                     MemoryType type,
                                     String content,
                                     Map<String, String> metadata,
                                     double importance,
                                     Instant now) {
        String safeContent = content == null ? "" : content;
        return new MemoryEntry(
                UUID.randomUUID().toString(),
                workspaceId,
                role,
                type,
                safeContent,
                metadata,
                now,
                now,
                null,
                0,
                importance,
                estimateTokens(safeContent),
                safeContent.getBytes(StandardCharsets.UTF_8).length);
    }

    public MemoryEntry withAccessed(Instant now) {
        return new MemoryEntry(id, workspaceId, role, type, content, metadata, createdAt, now, now,
                accessCount + 1, importance, tokenCount, byteCount);
    }

    private static int estimateTokens(String content) {
        try {
            return TokenizerUtil.estimateDeekSeekTokens(content);
        } catch (RuntimeException e) {
            // tokenizer 资源不可用时使用保守估算，避免记忆流程中断。
            return Math.max(1, content.length() / 2);
        }
    }
}
