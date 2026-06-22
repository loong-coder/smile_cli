package com.github.loong.agent;

import com.github.loong.chat.ChatContext;
import com.github.loong.llm.ChatResult;
import com.github.loong.llm.LLmClient;
import com.github.loong.memory.MemoryConfig;
import com.github.loong.memory.MemoryService;
import com.github.loong.message.Message;
import com.github.loong.message.SystemMessage;
import com.github.loong.tools.ToolDefinition;
import junit.framework.TestCase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 验证 Agent 在调用 LLM 前注入记忆上下文，并记录 assistant 回复。
 */
public class AgentMemoryIntegrationTest extends TestCase {

    public void testAgentInjectsMemoryContextBeforeMessages() {
        FakeLlmClient llmClient = new FakeLlmClient();
        MemoryConfig config = new MemoryConfig(true, 10, 131072, 10, 30, 16384, 30, 0.30d,
                "", "smile_cli_memory", "QDRANT_API_KEY", "aliyun", "", "text-embedding-v4", "ALIYUN_API_KEY", 3);
        MemoryService memoryService = MemoryService.localOnly("ws1", config, llmClient,
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC));
        ChatContext context = ChatContext.builder()
                .terminalManager(null)
                .llmClient(llmClient)
                .toolCallExecutor(null)
                .toolDefinitions(List.of())
                .messages(new ArrayList<>())
                .agentSystemPrompts(new ConcurrentHashMap<>())
                .memoryService(memoryService)
                .build();

        new Agent(context, "MainAgent").chat("你好");

        assertNotNull(llmClient.lastMessages);
        assertEquals("system", llmClient.lastMessages.get(0).getRole());
        assertEquals("system", llmClient.lastMessages.get(1).getRole());
        SystemMessage memoryMessage = (SystemMessage) llmClient.lastMessages.get(1);
        assertTrue(memoryMessage.getContent().contains("[短期记忆]"));
        assertTrue(memoryMessage.getContent().contains("你好"));
        assertEquals(1, memoryService.status().shortTermTurns());
    }

    private static class FakeLlmClient implements LLmClient {
        private List<Message> lastMessages;

        @Override
        public ChatResult chat(List<Message> messages, List<ToolDefinition> tools,
                               Consumer<String> onToken, Consumer<String> onReasoning,
                               Consumer<String> onError) {
            lastMessages = messages;
            return new ChatResult("回复", "", List.of(), "stop");
        }

        @Override
        public void cancel() {
        }

        @Override
        public void close() {
        }
    }
}
