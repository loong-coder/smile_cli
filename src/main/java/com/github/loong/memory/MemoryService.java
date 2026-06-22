package com.github.loong.memory;

import com.github.loong.llm.ChatResult;
import com.github.loong.llm.LLmClient;
import com.github.loong.message.Message;
import com.github.loong.message.SystemMessage;
import com.github.loong.message.UserMessage;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记忆系统统一入口，Agent 只依赖该类完成记录、压缩、检索和上下文构建。
 */
public class MemoryService {

    protected final String workspaceId;
    protected final MemoryConfig config;
    protected final LLmClient llmClient;
    protected final Clock clock;
    protected final ShortTermMemory shortTermMemory;
    protected final SummaryMemory summaryMemory;
    protected final MemoryRetriever retriever;
    protected final EmbeddingProvider embeddingProvider;
    protected final LongTermMemoryStore longTermMemoryStore;
    protected String lastCompressionError = "";
    protected String lastRetrievalError = "";

    public MemoryService(String workspaceId,
                         MemoryConfig config,
                         LLmClient llmClient,
                         Clock clock,
                         EmbeddingProvider embeddingProvider,
                         LongTermMemoryStore longTermMemoryStore,
                         MemoryRetriever retriever) {
        this.workspaceId = workspaceId;
        this.config = config;
        this.llmClient = llmClient;
        this.clock = clock;
        this.embeddingProvider = embeddingProvider;
        this.longTermMemoryStore = longTermMemoryStore;
        this.retriever = retriever;
        this.shortTermMemory = new ShortTermMemory(workspaceId, config.shortTermMaxRounds(), config.shortTermMaxBytes(), clock);
        this.summaryMemory = new SummaryMemory();
    }

    public static MemoryService localOnly(String workspaceId, MemoryConfig config, LLmClient llmClient, Clock clock) {
        return new MemoryService(workspaceId, config, llmClient, clock, null, null, null);
    }

    public void recordUserMessage(String content) {
        shortTermMemory.startTurn(content);
    }

    public void recordAssistantMessage(String content) {
        shortTermMemory.recordAssistantMessage(content);
        compressIfNeeded();
    }

    public void recordAssistantToolCall(String content) {
        shortTermMemory.recordAssistantToolCall(content);
    }

    public void recordToolResult(String toolCallId, String content) {
        shortTermMemory.recordToolResult(toolCallId, content);
        writeLongTerm(MemoryEntry.create(workspaceId, MemoryRole.TOOL, MemoryType.TOOL_RESULT,
                content, Map.of("toolCallId", toolCallId == null ? "" : toolCallId), 0.3d, Instant.now(clock)));
    }

    public SystemMessage buildMemoryContext(String query) {
        compressIfNeeded();
        List<RetrievedMemory> retrieved = retrieveLongTerm(query);
        return MemoryContextBuilder.build(shortTermMemory, summaryMemory, retrieved, config.longTermInjectMaxBytes());
    }

    public List<RetrievedMemory> search(String query) {
        return retrieveLongTerm(query);
    }

    public void clear() throws MemoryException {
        if (longTermMemoryStore != null) {
            longTermMemoryStore.clearWorkspace(workspaceId);
        }
        shortTermMemory.clear();
        summaryMemory.clear();
        lastCompressionError = "";
        lastRetrievalError = "";
    }

    public MemoryStatus status() {
        return new MemoryStatus(workspaceId, shortTermMemory.turnCount(), shortTermMemory.byteCount(),
                summaryMemory.hasSummary(), retriever != null, lastCompressionError, lastRetrievalError);
    }

    protected void compressIfNeeded() {
        ShortTermOverflow overflow = shortTermMemory.selectOverflow();
        if (overflow.empty()) {
            return;
        }
        try {
            String merged = summarize(overflow);
            if (merged == null || merged.isBlank()) {
                lastCompressionError = "摘要结果为空";
                return;
            }
            summaryMemory.update(merged);
            shortTermMemory.removeOverflow(overflow);
            lastCompressionError = "";
            MemoryEntry summaryEntry = MemoryEntry.create(workspaceId, MemoryRole.SYSTEM, MemoryType.SUMMARY,
                    summaryMemory.content(), Map.of("source", "short_term_overflow"), 0.8d, Instant.now(clock));
            writeLongTerm(summaryEntry);
        } catch (Exception e) {
            lastCompressionError = e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    private String summarize(ShortTermOverflow overflow) throws Exception {
        String prompt = "请将以下旧对话与现有摘要合并为一份中文结构化摘要。"
                + "保留用户长期偏好、项目约束、未完成任务、关键结论、重要工具结果和已做决定；删除寒暄和重复内容。\n\n"
                + "[现有摘要]\n" + (summaryMemory.hasSummary() ? summaryMemory.content() : "(无)")
                + "\n\n[新增旧对话]\n" + overflow.renderForSummary();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("你负责压缩 agent 记忆，只输出摘要正文。"));
        messages.add(new UserMessage(prompt));
        ChatResult result = llmClient.chat(messages, List.of(), ignored -> { }, ignored -> { }, ignored -> { });
        return result.content();
    }

    private List<RetrievedMemory> retrieveLongTerm(String query) {
        if (retriever == null) {
            return List.of();
        }
        try {
            List<RetrievedMemory> retrieved = retriever.retrieve(workspaceId, query, Instant.now(clock));
            if (longTermMemoryStore != null && !retrieved.isEmpty()) {
                longTermMemoryStore.markAccessed(workspaceId, retrieved.stream().map(RetrievedMemory::entry).toList(), Instant.now(clock));
            }
            lastRetrievalError = "";
            return retrieved;
        } catch (Exception e) {
            lastRetrievalError = e.getMessage() == null ? e.toString() : e.getMessage();
            return List.of();
        }
    }

    protected void writeLongTerm(MemoryEntry entry) {
        if (embeddingProvider == null || longTermMemoryStore == null) {
            return;
        }
        try {
            List<Float> vector = embeddingProvider.embed(entry.content());
            longTermMemoryStore.upsert(workspaceId, entry, vector);
        } catch (Exception e) {
            lastRetrievalError = e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }
}
