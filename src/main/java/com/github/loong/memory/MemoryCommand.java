package com.github.loong.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * /memory 命令的解析和输出渲染。
 */
public class MemoryCommand {

    private final MemoryService memoryService;

    public MemoryCommand(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public List<String> execute(String commandLine, boolean clearConfirmed) {
        if (memoryService == null) {
            return List.of("Memory service is not available.");
        }
        String args = commandLine.substring("/memory".length()).trim();
        if (args.isEmpty() || "status".equals(args)) {
            return renderStatus(memoryService.status());
        }
        if (args.startsWith("search ")) {
            String query = args.substring("search ".length()).trim();
            if (query.isEmpty()) {
                return List.of("Usage: /memory search <query>");
            }
            return renderSearchResults(memoryService.search(query));
        }
        if ("clear".equals(args)) {
            if (!clearConfirmed) {
                return List.of("Run /memory clear again after confirming in the prompt to clear current workspace memory.");
            }
            try {
                memoryService.clear();
                return List.of("Memory cleared for current workspace.");
            } catch (Exception e) {
                return List.of("Failed to clear memory: " + e.getMessage());
            }
        }
        return List.of("Usage: /memory status | /memory search <query> | /memory clear");
    }

    public static List<String> renderStatus(MemoryStatus status) {
        List<String> lines = new ArrayList<>();
        lines.add("Memory status:");
        lines.add("  Workspace ID: " + status.workspaceId());
        lines.add("  Short-term turns: " + status.shortTermTurns());
        lines.add("  Short-term bytes: " + status.shortTermBytes());
        lines.add("  Has summary: " + status.hasSummary());
        lines.add("  Long-term enabled: " + status.longTermEnabled());
        if (status.lastCompressionError() != null && !status.lastCompressionError().isBlank()) {
            lines.add("  Last compression error: " + status.lastCompressionError());
        }
        if (status.lastRetrievalError() != null && !status.lastRetrievalError().isBlank()) {
            lines.add("  Last retrieval error: " + status.lastRetrievalError());
        }
        return lines;
    }

    public static List<String> renderSearchResults(List<RetrievedMemory> results) {
        if (results == null || results.isEmpty()) {
            return List.of("No memory found.");
        }
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (RetrievedMemory result : results) {
            lines.add(String.format("%d. [%s] score=%.2f similarity=%.2f",
                    index++, result.entry().type(), result.finalScore(), result.similarityScore()));
            lines.add("   " + result.entry().content());
        }
        return lines;
    }
}
