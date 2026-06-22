package com.github.loong.memory;

import com.github.loong.message.SystemMessage;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 将短期、摘要和长期记忆格式化为可注入 LLM 的系统消息。
 */
public final class MemoryContextBuilder {

    private MemoryContextBuilder() {
    }

    public static SystemMessage build(ShortTermMemory shortTermMemory,
                                      SummaryMemory summaryMemory,
                                      List<RetrievedMemory> longTermMemories,
                                      int longTermMaxBytes) {
        StringBuilder builder = new StringBuilder();
        builder.append("以下是可参考的记忆上下文。\n\n");
        builder.append("<context>");
//        appendShortTerm(builder, shortTermMemory);
        appendSummary(builder, summaryMemory);
        appendLongTerm(builder, longTermMemories, longTermMaxBytes);
        builder.append("</context>");
        return new SystemMessage(builder.toString());
    }

    private static void appendShortTerm(StringBuilder builder, ShortTermMemory shortTermMemory) {
        builder.append("[短期记忆]\n");
        if (shortTermMemory.turns().isEmpty()) {
            builder.append("(无)\n\n");
            return;
        }
        for (ConversationTurn turn : shortTermMemory.turns()) {
            builder.append("第 ").append(turn.turnNumber()).append(" 轮\n");
            for (MemoryEntry entry : turn.entries()) {
                builder.append("- ").append(entry.role().apiRole()).append(": ").append(entry.content()).append("\n");
            }
        }
        builder.append("\n");
    }

    private static void appendSummary(StringBuilder builder, SummaryMemory summaryMemory) {
        builder.append("[摘要记忆]\n");
        if (summaryMemory.hasSummary()) {
            builder.append(summaryMemory.content()).append("\n\n");
        } else {
            builder.append("(无)\n\n");
        }
    }

    private static void appendLongTerm(StringBuilder builder, List<RetrievedMemory> memories, int maxBytes) {
        builder.append("[相关长期记忆]\n");
        if (memories == null || memories.isEmpty()) {
            builder.append("(无)\n");
            return;
        }
        int used = 0;
        int index = 1;
        for (RetrievedMemory memory : memories) {
            MemoryEntry entry = memory.entry();
            if (entry.byteCount() > maxBytes || used + entry.byteCount() > maxBytes) {
                continue;
            }
            used += entry.byteCount();
            builder.append(index++).append(". 类型: ").append(entry.type())
                    .append(", 时间: ").append(DateTimeFormatter.ISO_INSTANT.format(entry.createdAt()))
                    .append(", 综合分: ").append(String.format("%.2f", memory.finalScore()))
                    .append("\n   内容: ").append(entry.content()).append("\n");
        }
        if (index == 1) {
            builder.append("(无)\n");
        }
    }
}
