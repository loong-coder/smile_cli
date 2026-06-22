package com.github.loong.memory;

import com.github.loong.llm.ChatResult;
import com.github.loong.llm.LLmClient;
import com.github.loong.message.Message;
import com.github.loong.message.SystemMessage;
import com.github.loong.tools.ToolDefinition;
import junit.framework.TestCase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

/**
 * 验证 MemoryService 记录、压缩、摘要合并和长期降级。
 */
public class MemoryServiceTest extends TestCase {

    public void testBuildContextIncludesShortTermAndSummaryAfterCompression() {
        MemoryConfig config = new MemoryConfig(true, 1, 40, 10, 30, 16384, 30, 0.30d,
                "", "smile_cli_memory", "QDRANT_API_KEY",
                "aliyun", "", "text-embedding-v4", "ALIYUN_API_KEY", 3);
        FakeLLmClient llm = new FakeLLmClient("合并摘要");
        MemoryService service = MemoryService.localOnly("ws1", config, llm,
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC));

        service.recordUserMessage("第一轮用户内容很长");
        service.recordAssistantMessage("第一轮助手内容很长");
        service.recordUserMessage("第二轮用户内容");
        SystemMessage message = service.buildMemoryContext("当前问题");

        assertTrue(message.getContent().contains("[短期记忆]"));
        assertTrue(message.getContent().contains("第二轮用户内容"));
        assertTrue(message.getContent().contains("合并摘要"));
        assertEquals(1, llm.calls);
        assertEquals(1, service.status().shortTermTurns());
        assertTrue(service.status().hasSummary());
    }

    public void testSummaryFailureKeepsShortTermOverflow() {
        MemoryConfig config = new MemoryConfig(true, 1, 40, 10, 30, 16384, 30, 0.30d,
                "", "smile_cli_memory", "QDRANT_API_KEY",
                "aliyun", "", "text-embedding-v4", "ALIYUN_API_KEY", 3);
        FakeLLmClient llm = new FakeLLmClient(null);
        llm.fail = true;
        MemoryService service = MemoryService.localOnly("ws1", config, llm,
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC));

        service.recordUserMessage("第一轮用户内容很长");
        service.recordAssistantMessage("第一轮助手内容很长");
        service.recordUserMessage("第二轮用户内容");
        service.buildMemoryContext("当前问题");

        assertEquals(2, service.status().shortTermTurns());
        assertFalse(service.status().hasSummary());
        assertTrue(service.status().lastCompressionError().contains("summary failed"));
    }

    private static class FakeLLmClient implements LLmClient {
        private final String summary;
        private boolean fail;
        private int calls;

        FakeLLmClient(String summary) {
            this.summary = summary;
        }

        @Override
        public ChatResult chat(List<Message> messages, List<ToolDefinition> tools,
                               Consumer<String> onToken, Consumer<String> onReasoning,
                               Consumer<String> onError) throws Exception {
            calls++;
            if (fail) {
                throw new RuntimeException("summary failed");
            }
            return new ChatResult(summary, "", List.of(), "stop");
        }

        @Override
        public void cancel() {
        }

        @Override
        public void close() {
        }
    }
}
