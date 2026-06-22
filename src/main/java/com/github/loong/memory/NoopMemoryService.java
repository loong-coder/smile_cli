package com.github.loong.memory;

import com.github.loong.llm.LLmClient;
import com.github.loong.message.SystemMessage;

import java.time.Clock;
import java.util.List;

/**
 * 记忆关闭时使用的空实现，保持 Agent 调用路径简单。
 */
public class NoopMemoryService extends MemoryService {

    public NoopMemoryService(String workspaceId, MemoryConfig config, LLmClient llmClient) {
        super(workspaceId, config, llmClient, Clock.systemUTC(), null, null, null);
    }

    @Override
    public void recordUserMessage(String content) {
    }

    @Override
    public void recordAssistantMessage(String content) {
    }

    @Override
    public void recordAssistantToolCall(String content) {
    }

    @Override
    public void recordToolResult(String toolCallId, String content) {
    }

    @Override
    public SystemMessage buildMemoryContext(String query) {
        return null;
    }

    @Override
    public List<RetrievedMemory> search(String query) {
        return List.of();
    }

    @Override
    public void clear() {
    }
}
