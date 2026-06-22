package com.github.loong.memory;

import junit.framework.TestCase;

import java.time.Instant;
import java.util.Map;

/**
 * 验证长期记忆综合评分和时间衰减。
 */
public class MemoryScorerTest extends TestCase {

    public void testTimeDecayUsesHalfLife() {
        MemoryScorer scorer = new MemoryScorer(30);
        Instant now = Instant.parse("2026-06-22T00:00:00Z");

        assertEquals(1.0d, scorer.timeDecayScore(now, now));
        assertEquals(0.5d, scorer.timeDecayScore(now.minusSeconds(30L * 24 * 3600), now), 0.01d);
    }

    public void testFactScoresHigherThanToolResultWhenOtherInputsMatch() {
        MemoryScorer scorer = new MemoryScorer(30);
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        MemoryEntry fact = MemoryEntry.create("ws1", MemoryRole.USER, MemoryType.FACT, "事实", Map.of(), 0.8d, now);
        MemoryEntry tool = MemoryEntry.create("ws1", MemoryRole.TOOL, MemoryType.TOOL_RESULT, "工具", Map.of(), 0.3d, now);

        double factScore = scorer.score(fact, 0.8d, now);
        double toolScore = scorer.score(tool, 0.8d, now);

        assertTrue(factScore > toolScore);
    }

    public void testAccessScoreHasCap() {
        MemoryScorer scorer = new MemoryScorer(30);

        assertEquals(1.0d, scorer.accessScore(1000), 0.001d);
        assertTrue(scorer.accessScore(3) < 1.0d);
    }
}
