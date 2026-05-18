package com.github.loong.plan;

import java.util.List;
import java.util.Objects;

/**
 * LLM 返回的计划解析结果。
 *
 * @param summary 任务摘要
 * @param tasks   任务列表（已按执行顺序排列）
 */
public record PlanParseResult(String summary, List<PlanTask> tasks) {

    public PlanParseResult {
        summary = summary == null ? "" : summary;
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
