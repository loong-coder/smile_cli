package com.github.loong.memory;

/**
 * 当前会话的滚动摘要记忆。
 */
public class SummaryMemory {

    private String content = "";

    public boolean hasSummary() {
        return content != null && !content.isBlank();
    }

    public String content() {
        return content == null ? "" : content;
    }

    public void update(String newSummary) {
        if (newSummary == null || newSummary.isBlank()) {
            return;
        }
        this.content = newSummary.trim();
    }

    public void clear() {
        this.content = "";
    }
}
