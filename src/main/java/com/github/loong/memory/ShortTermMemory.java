package com.github.loong.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 当前会话内的短期记忆，负责维护最近轮次和容量上限。
 */
public class ShortTermMemory {

    private final String workspaceId;
    private final int maxRounds;
    private final int maxBytes;
    private final Clock clock;
    private final List<ConversationTurn> turns = new ArrayList<>();
    private int nextTurnNumber = 1;

    public ShortTermMemory(String workspaceId, int maxRounds, int maxBytes) {
        this(workspaceId, maxRounds, maxBytes, Clock.systemUTC());
    }

    public ShortTermMemory(String workspaceId, int maxRounds, int maxBytes, Clock clock) {
        this.workspaceId = workspaceId;
        this.maxRounds = maxRounds;
        this.maxBytes = maxBytes;
        this.clock = clock;
    }

    public void startTurn(String userContent) {
        ConversationTurn turn = new ConversationTurn(nextTurnNumber++);
        turn.add(entry(MemoryRole.USER, MemoryType.CONVERSATION, userContent, 0.5d));
        turns.add(turn);
    }

    public void recordAssistantMessage(String content) {
        currentTurn().add(entry(MemoryRole.ASSISTANT, MemoryType.CONVERSATION, content, 0.4d));
    }

    public void recordAssistantToolCall(String content) {
        currentTurn().add(entry(MemoryRole.ASSISTANT, MemoryType.CONVERSATION, content, 0.4d));
    }

    public void recordToolResult(String toolCallId, String content) {
        currentTurn().add(MemoryEntry.create(workspaceId, MemoryRole.TOOL, MemoryType.TOOL_RESULT, content,
                Map.of("toolCallId", toolCallId == null ? "" : toolCallId), 0.3d, Instant.now(clock)));
    }

    public ShortTermOverflow selectOverflow() {
        List<ConversationTurn> overflow = new ArrayList<>();
        int remainingTurns = turns.size();
        int remainingBytes = byteCount();
        for (ConversationTurn turn : turns) {
            if (remainingTurns <= maxRounds && remainingBytes <= maxBytes) {
                break;
            }
            overflow.add(turn);
            remainingTurns--;
            remainingBytes -= turn.byteCount();
        }
        return new ShortTermOverflow(overflow);
    }

    public void removeOverflow(ShortTermOverflow overflow) {
        if (overflow == null || overflow.empty()) {
            return;
        }
        turns.removeAll(overflow.turns());
    }

    public void clear() {
        turns.clear();
        nextTurnNumber = 1;
    }

    public List<ConversationTurn> turns() {
        return List.copyOf(turns);
    }

    public int turnCount() {
        return turns.size();
    }

    public int byteCount() {
        int total = 0;
        for (ConversationTurn turn : turns) {
            total += turn.byteCount();
        }
        return total;
    }

    public boolean overLimit() {
        return turnCount() > maxRounds || byteCount() > maxBytes;
    }

    private ConversationTurn currentTurn() {
        if (turns.isEmpty()) {
            ConversationTurn turn = new ConversationTurn(nextTurnNumber++);
            turns.add(turn);
        }
        return turns.get(turns.size() - 1);
    }

    private MemoryEntry entry(MemoryRole role, MemoryType type, String content, double importance) {
        return MemoryEntry.create(workspaceId, role, type, content, Map.of(), importance, Instant.now(clock));
    }
}
