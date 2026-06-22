package com.github.loong.memory;

import java.time.Instant;
import java.util.List;

/**
 * 长期向量记忆存储接口，屏蔽 Qdrant 细节。
 */
public interface LongTermMemoryStore {

    void upsert(String workspaceId, MemoryEntry entry, List<Float> vector) throws MemoryException;

    List<RetrievedMemory> search(String workspaceId, List<Float> queryVector, int limit) throws MemoryException;

    void markAccessed(String workspaceId, List<MemoryEntry> entries, Instant now) throws MemoryException;

    void clearWorkspace(String workspaceId) throws MemoryException;
}
