package com.github.loong.memory;

import java.time.Duration;
import java.time.Instant;

/**
 * 长期记忆综合评分器，结合相似度、时间衰减、重要性和访问频率。
 */
public class MemoryScorer {

    private final int halfLifeDays;

    public MemoryScorer(int halfLifeDays) {
        this.halfLifeDays = Math.max(1, halfLifeDays);
    }

    public double score(MemoryEntry entry, double similarityScore, Instant now) {
        return 0.60d * clamp(similarityScore)
                + 0.20d * timeDecayScore(entry.createdAt(), now)
                + 0.10d * importanceScore(entry)
                + 0.10d * accessScore(entry.accessCount());
    }

    public double timeDecayScore(Instant createdAt, Instant now) {
        if (createdAt == null || now == null || !createdAt.isBefore(now)) {
            return 1.0d;
        }
        double ageDays = Duration.between(createdAt, now).toHours() / 24.0d;
        return Math.pow(0.5d, ageDays / halfLifeDays);
    }

    public double importanceScore(MemoryEntry entry) {
        if (entry.type() == MemoryType.FACT) {
            return Math.max(0.8d, clamp(entry.importance()));
        }
        if (entry.type() == MemoryType.SUMMARY) {
            return Math.max(0.7d, clamp(entry.importance()));
        }
        return clamp(entry.importance());
    }

    public double accessScore(int accessCount) {
        return Math.min(1.0d, Math.log1p(Math.max(0, accessCount)) / Math.log1p(20));
    }

    private double clamp(double value) {
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }
}
