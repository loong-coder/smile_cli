package com.github.loong.memory;

import java.util.List;

/**
 * 短期记忆超限时被选出的旧轮次集合。
 */
public record ShortTermOverflow(List<ConversationTurn> turns) {

    public ShortTermOverflow {
        turns = turns == null ? List.of() : List.copyOf(turns);
    }

    public boolean empty() {
        return turns.isEmpty();
    }

    public String renderForSummary() {
        StringBuilder builder = new StringBuilder();
        for (ConversationTurn turn : turns) {
            builder.append("第 ").append(turn.turnNumber()).append(" 轮:\n");
            for (MemoryEntry entry : turn.entries()) {
                builder.append(entry.role().apiRole()).append(": ").append(entry.content()).append("\n");
            }
        }
        return builder.toString();
    }
}
