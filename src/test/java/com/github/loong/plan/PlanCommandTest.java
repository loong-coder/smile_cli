package com.github.loong.plan;

import junit.framework.TestCase;

import java.util.*;

/**
 * PlanCommand 单元测试。
 *
 * <p>覆盖 JSON 解析、依赖后处理、循环依赖检测、执行计划分层构建等核心逻辑。</p>
 */
public class PlanCommandTest extends TestCase {

    private PlanCommand planCommand;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // PlanCommand 需要 LLmClient 和 TerminalManager，此处仅测试不涉及 LLM 调用的纯逻辑方法。
        // 通过构造一个不完整实例来访问包级/公开方法。实际测试中只调用不依赖 LLM 的方法。
        planCommand = new PlanCommand(null);
    }

    // ---------- extractJson 测试 ----------

    /** 测试提取带 ```json 代码块的 JSON */
    public void testExtractJsonWithJsonFence() {
        String input = "```json\n{\"summary\":\"test\",\"tasks\":[]}\n```";
        String json = planCommand.extractJson(input);
        assertNotNull(json);
        assertTrue(json.contains("\"summary\""));
        assertTrue(json.contains("\"tasks\""));
    }

    /** 测试提取带 ``` 无语言标记的代码块 */
    public void testExtractJsonWithPlainFence() {
        String input = "```\n{\"summary\":\"test\",\"tasks\":[]}\n```";
        String json = planCommand.extractJson(input);
        assertNotNull(json);
        assertTrue(json.contains("\"summary\""));
    }

    /** 测试提取直接 JSON 文本（无代码块） */
    public void testExtractJsonDirectJson() {
        String input = "{\"summary\":\"直接JSON\",\"tasks\":[]}";
        String json = planCommand.extractJson(input);
        assertNotNull(json);
        assertEquals("{\"summary\":\"直接JSON\",\"tasks\":[]}", json);
    }

    /** 测试无效输入返回 null */
    public void testExtractJsonInvalidInput() {
        String input = "这是一个普通的文本回复，不是 JSON 格式";
        String json = planCommand.extractJson(input);
        assertNull(json);
    }

    /** 测试提取前面有说明文字的 JSON 代码块 */
    public void testExtractJsonWithPrecedingText() {
        String input = "好的，这是执行计划：\n```json\n{\"summary\":\"计划\",\"tasks\":[]}\n```\n请审阅。";
        String json = planCommand.extractJson(input);
        assertNotNull(json);
        assertTrue(json.contains("\"summary\""));
    }

    // ---------- parseResponse 测试 ----------

    /** 测试解析标准格式的计划 JSON */
    public void testParseResponseValid() {
        String json = "{\"summary\":\"实现登录功能\",\"tasks\":["
                + "{\"id\":\"task_1\",\"description\":\"创建数据库表\",\"type\":\"COMMAND\",\"dependencies\":[]},"
                + "{\"id\":\"task_2\",\"description\":\"实现登录接口\",\"type\":\"FILE_WRITE\",\"dependencies\":[\"task_1\"]}"
                + "]}";
        PlanParseResult result = planCommand.parseResponse(json);
        assertNotNull(result);
        assertEquals("实现登录功能", result.summary());
        assertEquals(2, result.tasks().size());
        assertEquals("task_1", result.tasks().get(0).getId());
        assertEquals("创建数据库表", result.tasks().get(0).getDescription());
        assertEquals("COMMAND", result.tasks().get(0).getType());
        assertEquals(0, result.tasks().get(0).getDependencies().size());
        assertEquals(List.of("task_1"), result.tasks().get(1).getDependencies());
    }

    /** 测试解析带 markdown 代码块的响应 */
    public void testParseResponseWithFence() {
        String raw = "```json\n{\"summary\":\"测试\",\"tasks\":["
                + "{\"id\":\"task_1\",\"description\":\"读取文件\",\"type\":\"FILE_READ\",\"dependencies\":[]}"
                + "]}\n```";
        PlanParseResult result = planCommand.parseResponse(raw);
        assertNotNull(result);
        assertEquals("测试", result.summary());
        assertEquals(1, result.tasks().size());
    }

    /** 测试缺少 tasks 字段时返回 null */
    public void testParseResponseMissingTasks() {
        String json = "{\"summary\":\"没有tasks\"}";
        PlanParseResult result = planCommand.parseResponse(json);
        assertNull(result);
    }

    /** 测试任务缺少必填字段时返回 null */
    public void testParseResponseMissingRequiredField() {
        String json = "{\"summary\":\"测试\",\"tasks\":["
                + "{\"id\":\"task_1\",\"type\":\"COMMAND\"}" // 缺少 description
                + "]}";
        PlanParseResult result = planCommand.parseResponse(json);
        assertNull(result);
    }

    // ---------- computeDependents 测试 ----------

    /** 测试线性依赖链 A → B → C 的后继计算 */
    public void testComputeDependentsLinearChain() {
        // task_3 依赖 task_2, task_2 依赖 task_1, task_1 无依赖
        List<PlanTask> tasks = Arrays.asList(
                new PlanTask("task_1", "第一步", "COMMAND", List.of()),
                new PlanTask("task_2", "第二步", "FILE_WRITE", List.of("task_1")),
                new PlanTask("task_3", "第三步", "VERIFICATION", List.of("task_2"))
        );

        planCommand.computeDependents(tasks);

        // task_1 被 task_2 依赖
        assertEquals(List.of("task_2"), tasks.get(0).getDependents());
        // task_2 被 task_3 依赖
        assertEquals(List.of("task_3"), tasks.get(1).getDependents());
        // task_3 无人依赖
        assertEquals(List.of(), tasks.get(2).getDependents());
    }

    /** 测试菱形依赖：task_1 无依赖，task_2(t1), task_3(t1), task_4(t2, t3) */
    public void testComputeDependentsDiamond() {
        List<PlanTask> tasks = Arrays.asList(
                new PlanTask("task_1", "基础", "COMMAND", List.of()),
                new PlanTask("task_2", "分支A", "FILE_WRITE", List.of("task_1")),
                new PlanTask("task_3", "分支B", "FILE_WRITE", List.of("task_1")),
                new PlanTask("task_4", "合并", "VERIFICATION", List.of("task_2", "task_3"))
        );

        planCommand.computeDependents(tasks);

        // task_1 被 task_2 和 task_3 依赖
        List<String> t1Dependents = tasks.get(0).getDependents();
        assertEquals(2, t1Dependents.size());
        assertTrue(t1Dependents.contains("task_2"));
        assertTrue(t1Dependents.contains("task_3"));

        // task_2 被 task_4 依赖
        assertEquals(List.of("task_4"), tasks.get(1).getDependents());
        // task_3 被 task_4 依赖
        assertEquals(List.of("task_4"), tasks.get(2).getDependents());
        // task_4 无人依赖
        assertEquals(List.of(), tasks.get(3).getDependents());
    }

    /** 测试无依赖关系的任务 */
    public void testComputeDependentsNoDependencies() {
        List<PlanTask> tasks = Arrays.asList(
                new PlanTask("task_1", "独立任务A", "COMMAND", List.of()),
                new PlanTask("task_2", "独立任务B", "COMMAND", List.of())
        );

        planCommand.computeDependents(tasks);

        assertEquals(List.of(), tasks.get(0).getDependents());
        assertEquals(List.of(), tasks.get(1).getDependents());
    }

    // ---------- detectCircularDependencies 测试 ----------

    /** 测试无环 DAG 返回空列表 */
    public void testDetectNoCycle() {
        List<PlanTask> tasks = Arrays.asList(
                new PlanTask("task_1", "第一步", "COMMAND", List.of()),
                new PlanTask("task_2", "第二步", "FILE_WRITE", List.of("task_1")),
                new PlanTask("task_3", "第三步", "VERIFICATION", List.of("task_2"))
        );

        List<List<String>> cycles = planCommand.detectCircularDependencies(tasks);
        assertTrue("无环 DAG 应返回空列表", cycles.isEmpty());
    }

    /** 测试简单环: A → B → A */
    public void testDetectSimpleCycle() {
        List<PlanTask> tasks = Arrays.asList(
                new PlanTask("task_1", "A", "COMMAND", List.of("task_2")),
                new PlanTask("task_2", "B", "COMMAND", List.of("task_1"))
        );

        List<List<String>> cycles = planCommand.detectCircularDependencies(tasks);
        assertEquals(1, cycles.size());
        // 环应包含 task_1 和 task_2
        List<String> cycle = cycles.get(0);
        assertTrue(cycle.contains("task_1"));
        assertTrue(cycle.contains("task_2"));
    }

    /** 测试三节点环: A → B → C → A */
    public void testDetectThreeNodeCycle() {
        List<PlanTask> tasks = Arrays.asList(
                new PlanTask("task_1", "A", "COMMAND", List.of("task_2")),
                new PlanTask("task_2", "B", "COMMAND", List.of("task_3")),
                new PlanTask("task_3", "C", "COMMAND", List.of("task_1"))
        );

        List<List<String>> cycles = planCommand.detectCircularDependencies(tasks);
        assertEquals(1, cycles.size());
        List<String> cycle = cycles.get(0);
        assertTrue(cycle.contains("task_1"));
        assertTrue(cycle.contains("task_2"));
        assertTrue(cycle.contains("task_3"));
    }

    /** 测试自环: A → A */
    public void testDetectSelfLoop() {
        List<PlanTask> tasks = List.of(
                new PlanTask("task_1", "自己依赖自己", "COMMAND", List.of("task_1"))
        );

        List<List<String>> cycles = planCommand.detectCircularDependencies(tasks);
        assertEquals(1, cycles.size());
    }

    /** 测试包含环和非环部分的混合图 */
    public void testDetectCycleInMixedGraph() {
        List<PlanTask> tasks = Arrays.asList(
                new PlanTask("task_1", "正常任务", "COMMAND", List.of()),
                new PlanTask("task_2", "环A", "COMMAND", List.of("task_3")),
                new PlanTask("task_3", "环B", "COMMAND", List.of("task_2")),
                new PlanTask("task_4", "依赖正常任务", "FILE_WRITE", List.of("task_1"))
        );

        List<List<String>> cycles = planCommand.detectCircularDependencies(tasks);
        assertEquals(1, cycles.size());
    }

    /** 测试多个环的独立节点 */
    public void testDetectIndependentTaskNoCycle() {
        List<PlanTask> tasks = List.of(
                new PlanTask("task_1", "独立任务", "COMMAND", List.of())
        );

        List<List<String>> cycles = planCommand.detectCircularDependencies(tasks);
        assertTrue(cycles.isEmpty());
    }

    // ---------- buildExecutionPlan 测试 ----------

    /** 测试线性依赖的执行计划分层 */
    public void testBuildExecutionPlanLinear() {
        // task_1 → task_2 → task_3（task_3 依赖 task_2，task_2 依赖 task_1）
        PlanTask t1 = new PlanTask("task_1", "第一步", "COMMAND", List.of());
        PlanTask t2 = new PlanTask("task_2", "第二步", "FILE_WRITE", List.of("task_1"));
        PlanTask t3 = new PlanTask("task_3", "第三步", "VERIFICATION", List.of("task_2"));
        List<PlanTask> tasks = Arrays.asList(t1, t2, t3);

        // 后处理：计算后继
        planCommand.computeDependents(tasks);

        List<Set<PlanTask>> plan = planCommand.buildExecutionPlan(tasks);

        assertEquals("线性依赖应有 3 层", 3, plan.size());
        // 第 0 层：只有 task_1
        assertEquals(1, plan.get(0).size());
        assertTrue(plan.get(0).contains(t1));
        // 第 1 层：只有 task_2
        assertEquals(1, plan.get(1).size());
        assertTrue(plan.get(1).contains(t2));
        // 第 2 层：只有 task_3
        assertEquals(1, plan.get(2).size());
        assertTrue(plan.get(2).contains(t3));
    }

    /** 测试并行任务：两个无依赖任务可在同一层执行 */
    public void testBuildExecutionPlanParallel() {
        PlanTask t1 = new PlanTask("task_1", "任务A", "COMMAND", List.of());
        PlanTask t2 = new PlanTask("task_2", "任务B", "COMMAND", List.of());
        PlanTask t3 = new PlanTask("task_3", "任务C（依赖A和B）", "VERIFICATION", List.of("task_1", "task_2"));
        List<PlanTask> tasks = Arrays.asList(t1, t2, t3);

        planCommand.computeDependents(tasks);

        List<Set<PlanTask>> plan = planCommand.buildExecutionPlan(tasks);

        assertEquals("应有 2 层", 2, plan.size());
        // 第 0 层：t1 和 t2 可并行
        assertEquals(2, plan.get(0).size());
        assertTrue(plan.get(0).contains(t1));
        assertTrue(plan.get(0).contains(t2));
        // 第 1 层：t3
        assertEquals(1, plan.get(1).size());
        assertTrue(plan.get(1).contains(t3));
    }

    /** 测试复杂 DAG 的分层 */
    public void testBuildExecutionPlanComplexDAG() {
        // t0(无依赖), t1(无依赖)
        // t2(依赖 t0), t3(依赖 t0, t1)
        // t4(依赖 t2, t3)
        PlanTask t0 = new PlanTask("task_0", "基础A", "COMMAND", List.of());
        PlanTask t1 = new PlanTask("task_1", "基础B", "COMMAND", List.of());
        PlanTask t2 = new PlanTask("task_2", "基于A", "FILE_WRITE", List.of("task_0"));
        PlanTask t3 = new PlanTask("task_3", "基于A和B", "FILE_WRITE", List.of("task_0", "task_1"));
        PlanTask t4 = new PlanTask("task_4", "合并结果", "VERIFICATION", List.of("task_2", "task_3"));
        List<PlanTask> tasks = Arrays.asList(t0, t1, t2, t3, t4);

        planCommand.computeDependents(tasks);

        List<Set<PlanTask>> plan = planCommand.buildExecutionPlan(tasks);

        assertEquals("应有 3 层", 3, plan.size());
        // 第 0 层：t0, t1 可并行
        assertEquals(2, plan.get(0).size());
        assertTrue(plan.get(0).contains(t0));
        assertTrue(plan.get(0).contains(t1));
        // 第 1 层：t2, t3 可并行
        assertEquals(2, plan.get(1).size());
        assertTrue(plan.get(1).contains(t2));
        assertTrue(plan.get(1).contains(t3));
        // 第 2 层：t4
        assertEquals(1, plan.get(2).size());
        assertTrue(plan.get(2).contains(t4));
    }

    /** 测试全部任务无依赖时全部归入第 0 层 */
    public void testBuildExecutionPlanAllIndependent() {
        PlanTask t1 = new PlanTask("task_1", "A", "COMMAND", List.of());
        PlanTask t2 = new PlanTask("task_2", "B", "COMMAND", List.of());
        PlanTask t3 = new PlanTask("task_3", "C", "COMMAND", List.of());
        List<PlanTask> tasks = Arrays.asList(t1, t2, t3);

        planCommand.computeDependents(tasks);

        List<Set<PlanTask>> plan = planCommand.buildExecutionPlan(tasks);

        assertEquals("全部独立任务应在 1 层内完成", 1, plan.size());
        assertEquals(3, plan.get(0).size());
        assertTrue(plan.get(0).contains(t1));
        assertTrue(plan.get(0).contains(t2));
        assertTrue(plan.get(0).contains(t3));
    }

    /** 测试空任务列表 */
    public void testBuildExecutionPlanEmpty() {
        List<PlanTask> tasks = List.of();
        List<Set<PlanTask>> plan = planCommand.buildExecutionPlan(tasks);
        assertTrue("空任务列表应返回空计划", plan.isEmpty());
    }

    // ---------- buildCycleFeedback 测试 ----------

    /** 测试循环依赖反馈消息包含环信息和任务参考 */
    public void testBuildCycleFeedback() {
        List<PlanTask> tasks = Arrays.asList(
                new PlanTask("task_1", "A任务", "COMMAND", List.of("task_2")),
                new PlanTask("task_2", "B任务", "COMMAND", List.of("task_1"))
        );
        PlanParseResult lastResult = new PlanParseResult("测试摘要", tasks);
        List<List<String>> cycles = List.of(List.of("task_1", "task_2", "task_1"));

        String feedback = planCommand.buildCycleFeedback(cycles, lastResult);

        assertNotNull(feedback);
        // 应包含环的描述
        assertTrue(feedback.contains("task_1"));
        assertTrue(feedback.contains("task_2"));
        // 应包含任务参考信息
        assertTrue(feedback.contains("A任务"));
        assertTrue(feedback.contains("B任务"));
        // 应提示消除循环
        assertTrue(feedback.contains("循环") || feedback.contains("loop") || feedback.contains("依赖"));
    }

    // ---------- loadPlanPrompt 测试 ----------

    /** 测试能从 classpath 加载 plan.md */
    public void testLoadPlanPrompt() {
        String prompt = planCommand.loadPlanPrompt();
        assertNotNull("plan.md 应从 classpath 加载成功", prompt);
        assertTrue("plan.md 应包含 Mode: Plan Builder", prompt.contains("Plan Builder"));
        assertTrue("plan.md 应包含 JSON 格式说明", prompt.contains("JSON"));
    }

    // ---------- 端到端流程测试（模拟无 LLM 调用的纯逻辑链路） ----------

    /** 模拟完整流程：解析 → 后处理 → 校验 → 分层 */
    public void testFullPipelineWithValidData() {
        // 模拟 LLM 返回的 JSON
        String rawResponse = """
                ```json
                {
                  "summary": "实现用户注册功能",
                  "tasks": [
                    {"id": "task_1", "description": "设计数据库表结构", "type": "ANALYSIS", "dependencies": []},
                    {"id": "task_2", "description": "创建用户表SQL", "type": "COMMAND", "dependencies": ["task_1"]},
                    {"id": "task_3", "description": "实现注册接口", "type": "FILE_WRITE", "dependencies": ["task_1"]},
                    {"id": "task_4", "description": "编写单元测试", "type": "FILE_WRITE", "dependencies": ["task_2", "task_3"]},
                    {"id": "task_5", "description": "验证注册流程", "type": "VERIFICATION", "dependencies": ["task_4"]}
                  ]
                }
                ```""";

        // 解析
        PlanParseResult result = planCommand.parseResponse(rawResponse);
        assertNotNull("解析应成功", result);
        assertEquals("实现用户注册功能", result.summary());
        assertEquals(5, result.tasks().size());

        // 后处理
        planCommand.computeDependents(result.tasks());

        // 校验：无循环依赖
        List<List<String>> cycles = planCommand.detectCircularDependencies(result.tasks());
        assertTrue("合法 DAG 不应有循环依赖", cycles.isEmpty());

        // 分层
        List<Set<PlanTask>> plan = planCommand.buildExecutionPlan(result.tasks());

        assertEquals("应有 4 层", 4, plan.size());
        // 第 0 层：task_1
        assertEquals(1, plan.get(0).size());
        assertTrue(plan.get(0).stream().anyMatch(t -> t.getId().equals("task_1")));
        // 第 1 层：task_2, task_3 并行（都只依赖 task_1）
        assertEquals(2, plan.get(1).size());
        assertTrue(plan.get(1).stream().anyMatch(t -> t.getId().equals("task_2")));
        assertTrue(plan.get(1).stream().anyMatch(t -> t.getId().equals("task_3")));
        // 第 2 层：task_4（依赖 task_2 和 task_3）
        assertEquals(1, plan.get(2).size());
        assertTrue(plan.get(2).stream().anyMatch(t -> t.getId().equals("task_4")));
        // 第 3 层：task_5
        assertEquals(1, plan.get(3).size());
        assertTrue(plan.get(3).stream().anyMatch(t -> t.getId().equals("task_5")));
    }

    /** 模拟循环依赖场景：解析成功但校验失败 */
    public void testFullPipelineWithCycle() {
        String rawResponse = """
                ```json
                {
                  "summary": "存在环的任务",
                  "tasks": [
                    {"id": "task_1", "description": "任务A", "type": "COMMAND", "dependencies": ["task_3"]},
                    {"id": "task_2", "description": "任务B", "type": "COMMAND", "dependencies": ["task_1"]},
                    {"id": "task_3", "description": "任务C", "type": "COMMAND", "dependencies": ["task_2"]}
                  ]
                }
                ```""";

        PlanParseResult result = planCommand.parseResponse(rawResponse);
        assertNotNull(result);
        assertEquals(3, result.tasks().size());

        planCommand.computeDependents(result.tasks());

        List<List<String>> cycles = planCommand.detectCircularDependencies(result.tasks());
        assertFalse("应检测到循环依赖", cycles.isEmpty());
    }
}
