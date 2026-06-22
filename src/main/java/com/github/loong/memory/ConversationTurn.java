package com.github.loong.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次用户输入触发的一整轮对话，裁剪时作为不可拆分单元。
 */
public class ConversationTurn {

    private final int turnNumber;
    private final List<MemoryEntry> entries = new ArrayList<>();

    public ConversationTurn(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public int turnNumber() {
        return turnNumber;
    }

    public void add(MemoryEntry entry) {
        entries.add(entry);
    }

    public List<MemoryEntry> entries() {
        return List.copyOf(entries);
    }

    public int byteCount() {
        int total = 0;
        for (MemoryEntry entry : entries) {
            total += entry.byteCount();
        }
        return total;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
