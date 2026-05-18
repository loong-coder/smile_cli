package com.github.loong.plan;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PlanExecutor 纯逻辑方法测试。
 * 不依赖 LLM 调用的方法（展平、依赖校验、提示词构建）在此验证。
 */
public class PlanExecutorTest extends TestCase {

    private PlanExecutor executor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // PlanExecutor 需要 ChatContext，此处仅测试不依赖 LLM 的纯逻辑方法。
        // 通过 null context 构造会 NPE，因此直接测试静态可验证的逻辑，
        // 需要 executor 实例的方法通过构造一个能用的 executor 来测试。
    }

    // ---------- flattenPlan 测试 ----------

    /** 验证分层计划正确展平为线性序列 */
    public void testFlattenPlan() {
        PlanExecutor exec = new PlanExecutor(null, "测试目标");

        PlanTask t1 = new PlanTask("task_1", "A", "COMMAND", List.of());
        PlanTask t2 = new PlanTask("task_2", "B", "FILE_WRITE", List.of());
        PlanTask t3 = new PlanTask("task_3", "C", "VERIFICATION", List.of("task_1", "task_2"));

        List<Set<PlanTask>> plan = new ArrayList<>();
        Set<PlanTask> level0 = new LinkedHashSet<>();
        level0.add(t1);
        level0.add(t2);
        plan.add(level0);

        Set<PlanTask> level1 = new LinkedHashSet<>();
        level1.add(t3);
        plan.add(level1);

        List<PlanTask> flat = exec.flattenPlan(plan);

        assertEquals(3, flat.size());
        assertEquals("task_1", flat.get(0).getId());
        assertEquals("task_2", flat.get(1).getId());
        assertEquals("task_3", flat.get(2).getId());
    }

    /** 空计划返回空列表 */
    public void testFlattenPlanEmpty() {
        PlanExecutor exec = new PlanExecutor(null, "测试目标");
        List<PlanTask> flat = exec.flattenPlan(List.of());
        assertTrue(flat.isEmpty());
    }

    // ---------- dependenciesSatisfied 测试 ----------

    /** 无依赖任务永远满足 */
    public void testDependenciesSatisfiedNoDeps() {
        PlanExecutor exec = new PlanExecutor(null, "测试目标");
        PlanTask task = new PlanTask("task_1", "独立任务", "COMMAND", List.of());

        assertTrue(exec.dependenciesSatisfied(task, List.of()));
    }

    /** 所有依赖都已完成时返回 true */
    public void testDependenciesSatisfiedAllDone() {
        PlanExecutor exec = new PlanExecutor(null, "测试目标");
        PlanTask t1 = new PlanTask("task_1", "A", "COMMAND", List.of());
        t1.setStatus(PlanTask.TaskStatus.COMPLETED);
        PlanTask t2 = new PlanTask("task_2", "B", "COMMAND", List.of());
        t2.setStatus(PlanTask.TaskStatus.COMPLETED);
        PlanTask t3 = new PlanTask("task_3", "C（依赖A和B）", "FILE_WRITE", List.of("task_1", "task_2"));

        assertTrue(exec.dependenciesSatisfied(t3, List.of(t1, t2)));
    }

    /** 部分依赖未完成时返回 false */
    public void testDependenciesSatisfiedPartialDone() {
        PlanExecutor exec = new PlanExecutor(null, "测试目标");
        PlanTask t1 = new PlanTask("task_1", "A", "COMMAND", List.of());
        t1.setStatus(PlanTask.TaskStatus.COMPLETED);
        PlanTask t2 = new PlanTask("task_2", "B（依赖A）", "FILE_WRITE", List.of("task_1"));
        // t1 完成但未传入 completedTasks

        assertFalse(exec.dependenciesSatisfied(t2, List.of()));
    }

    /** 依赖的任务失败时也视为未满足 */
    public void testDependenciesSatisfiedDepFailed() {
        PlanExecutor exec = new PlanExecutor(null, "测试目标");
        PlanTask t1 = new PlanTask("task_1", "A", "COMMAND", List.of());
        t1.setStatus(PlanTask.TaskStatus.FAILED);
        PlanTask t2 = new PlanTask("task_2", "B（依赖A）", "FILE_WRITE", List.of("task_1"));

        assertFalse(exec.dependenciesSatisfied(t2, List.of(t1)));
    }

    /** 依赖不存在于 completedTasks 中时返回 false */
    public void testDependenciesSatisfiedDepMissing() {
        PlanExecutor exec = new PlanExecutor(null, "测试目标");
        PlanTask t1 = new PlanTask("task_1", "A", "COMMAND", List.of());
        t1.setStatus(PlanTask.TaskStatus.COMPLETED);
        PlanTask t2 = new PlanTask("task_2", "B（依赖不存在的task_99）", "FILE_WRITE", List.of("task_99"));

        assertFalse(exec.dependenciesSatisfied(t2, List.of(t1)));
    }
}
