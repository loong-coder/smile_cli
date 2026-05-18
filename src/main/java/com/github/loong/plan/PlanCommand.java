package com.github.loong.plan;

import cn.hutool.core.io.IoUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.loong.agent.Agent;
import com.github.loong.chat.ChatContext;
import com.github.loong.llm.LLmClient;
import com.github.loong.message.Message;
import com.github.loong.message.SystemMessage;
import com.github.loong.message.UserMessage;
import com.github.loong.tools.executor.ToolCallExecutor;
import com.github.loong.ui.TerminalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * /plan 命令处理器。
 *
 * <p>工作流程：
 * <ol>
 *   <li>加载 plan.md 系统提示词</li>
 *   <li>调用 LLM 生成任务列表（JSON 格式）</li>
 *   <li>解析 JSON → 构建 PlanTask 列表</li>
 *   <li>后处理：计算每个任务的后继依赖（dependents）</li>
 *   <li>DFS 检测循环依赖</li>
 *   <li>若存在循环依赖，将错误信息反馈给 LLM 重新生成（最多重试 3 次）</li>
 *   <li>通过拓扑分层生成执行计划（集合的集合，同一集合内的任务可并行执行）</li>
 * </ol>
 */
public class PlanCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanCommand.class);

    /**
     * plan.md 在 classpath 中的路径
     */
    private static final String PLAN_PROMPT_PATH = "prompts/plan/plan.md";
    /**
     * 最大重试次数
     */
    private static final int MAX_RETRIES = 3;

    private final ChatContext context;

    private final TerminalManager terminalManager;

    private final ObjectMapper objectMapper;

    /**
     * 循环依赖检测中用于标记节点状态的颜色
     */
    private enum Color {WHITE, GRAY, BLACK}

    /**
     * 构造 PlanCommand，context 为 null 时仅支持纯逻辑方法（JSON 解析、依赖分析等），
     * 适用于测试场景；需要 LLM 调用或终端输出时必须提供有效 context。
     */
    public PlanCommand(ChatContext context) {
        this.context = context;
        this.terminalManager = context != null ? context.terminalManager() : null;
        this.objectMapper = new ObjectMapper();
    }

    // ==================== 公开入口 ====================

    /**
     * 执行 /plan 命令的完整流程。
     *
     * @param userInput 用户输入的任务描述（不含 /plan 前缀）
     */
    public void execute(String userInput) {
        // 1. 加载系统提示词
        String systemPrompt = loadPlanPrompt();
        if (systemPrompt == null) {
            terminalManager.printError("无法加载 plan.md 系统提示词");
            return;
        }

        // 2. 构建初始消息
        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage("请为以下任务生成执行计划：\n\n" + userInput)
        );

        PlanParseResult lastResult = null;
        List<Set<PlanTask>> executionPlan = null;

        // 3. 循环调用 LLM，直至通过循环依赖校验或达到最大重试次数
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            terminalManager.printInfo("正在生成执行计划..." + (attempt > 0 ? " (第 " + (attempt + 1) + " 次尝试)" : ""));

            String rawResponse = callLlm(messages);
            if (rawResponse == null || rawResponse.isBlank()) {
                terminalManager.printError("LLM 未返回有效内容");
                return;
            }

            // 4. 解析 JSON 响应
            PlanParseResult parseResult = parseResponse(rawResponse);
            if (parseResult == null) {
                terminalManager.printError("无法解析 LLM 返回的计划格式，请重试");
                terminalManager.printInfo("LLM 原始输出（前500字符）：" + rawResponse.substring(0, Math.min(500, rawResponse.length())));
                return;
            }

            // 5. 后处理：计算每个任务的后继依赖
            computeDependents(parseResult.tasks());

            // 6. 检测循环依赖
            List<List<String>> cycles = detectCircularDependencies(parseResult.tasks());

            if (cycles.isEmpty()) {
                // 校验通过，生成并展示执行计划
                lastResult = parseResult;
                executionPlan = buildExecutionPlan(parseResult.tasks());
                displayPlan(parseResult, executionPlan);
                break;
            }

            // 循环依赖存在，构建错误反馈重新请求
            terminalManager.printError("检测到循环依赖，要求 LLM 重新生成...");
            String cycleFeedback = buildCycleFeedback(cycles, parseResult);
            messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage("请为以下任务生成执行计划：\n\n" + userInput),
                    new UserMessage("上一次生成的计划存在以下循环依赖，请修正后重新生成：\n" + cycleFeedback)
            );
        }

        if (executionPlan == null || lastResult == null) {
            terminalManager.printError("经过 " + MAX_RETRIES + " 次重试后仍存在循环依赖，请手动调整任务描述后重试");
            return;
        }

        // 7. 执行任务：将原始目标和执行计划交给 PlanExecutor
        terminalManager.printInfo("");
        terminalManager.printInfo("开始执行任务...");
        new PlanExecutor(context, userInput).execute(executionPlan, lastResult.tasks());
    }

    // ==================== 系统提示词加载 ====================

    /**
     * 从 classpath 加载 plan.md 内容。
     *
     * @return plan.md 文本内容，加载失败返回 null
     */
    String loadPlanPrompt() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(PLAN_PROMPT_PATH)) {
            if (is == null) {
                LOGGER.error("plan.md 文件未找到: {}", PLAN_PROMPT_PATH);
                return null;
            }
            return IoUtil.read(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("加载 plan.md 失败", e);
            return null;
        }
    }

    // ==================== LLM 调用 ====================

    /**
     * 调用 LLM 并返回文本响应。
     *
     * @param messages 消息列表
     * @return LLM 返回的文本内容，失败返回 null
     */
    String callLlm(List<Message> messages) {
        try {
            var result = context.llmClient().chat(
                    messages,
                    List.of(),  // 计划生成阶段不提供工具
                    token -> {
                    },  // 静默，不显示 token 流
                    thinking -> {
                    },
                    error -> terminalManager.printError("LLM 错误: " + error),
                    "json_object"  // 使用结构化输出，确保 LLM 返回合法 JSON
            );
            return result.content();
        } catch (Exception e) {
            LOGGER.error("LLM 调用失败", e);
            return null;
        }
    }

    // ==================== JSON 解析 ====================

    /**
     * 从 JSON 中提取 markdown 代码块中的内容匹配模式。
     * 支持 ```json ... ``` 或直接的 JSON 文本。
     */
    private static final Pattern JSON_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 解析 LLM 返回的计划 JSON。
     *
     * @param rawResponse LLM 原始响应
     * @return 解析后的 PlanParseResult，失败返回 null
     */
    PlanParseResult parseResponse(String rawResponse) {
        String json = extractJson(rawResponse);
        if (json == null) {
            return null;
        }

        try {
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });

            String summary = (String) map.getOrDefault("summary", "");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> taskMaps = (List<Map<String, Object>>) map.get("tasks");
            if (taskMaps == null) {
                LOGGER.error("JSON 中缺少 tasks 字段");
                return null;
            }

            List<PlanTask> tasks = new ArrayList<>();
            for (Map<String, Object> taskMap : taskMaps) {
                String id = (String) taskMap.get("id");
                String description = (String) taskMap.get("description");
                String type = (String) taskMap.get("type");

                @SuppressWarnings("unchecked")
                List<String> dependencies = (List<String>) taskMap.get("dependencies");

                if (id == null || description == null || type == null) {
                    LOGGER.error("任务缺少必填字段: id={}, description={}, type={}", id, description, type);
                    return null;
                }
                tasks.add(new PlanTask(id, description, type, dependencies));
            }

            return new PlanParseResult(summary, tasks);
        } catch (Exception e) {
            LOGGER.error("JSON 解析失败", e);
            return null;
        }
    }

    /**
     * 从 LLM 原始响应中提取 JSON 文本。
     * 优先匹配 ```json ... ``` 代码块，否则尝试整段解析。
     */
    String extractJson(String rawResponse) {
        Matcher matcher = JSON_FENCE_PATTERN.matcher(rawResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 没有代码块，尝试直接作为 JSON 使用
        String trimmed = rawResponse.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        LOGGER.error("无法从 LLM 响应中提取 JSON: {}", rawResponse.substring(0, Math.min(200, rawResponse.length())));
        return null;
    }

    // ==================== 依赖后处理 ====================

    /**
     * 计算每个任务的后继依赖（dependents）。
     * 遍历所有任务，将当前任务 id 添加到其依赖任务的 dependents 列表中。
     *
     * @param tasks 任务列表
     */
    void computeDependents(List<PlanTask> tasks) {
        // 构建 id → PlanTask 映射
        Map<String, PlanTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(PlanTask::getId, t -> t));

        for (PlanTask task : tasks) {
            for (String depId : task.getDependencies()) {
                PlanTask depTask = taskMap.get(depId);
                if (depTask != null) {
                    depTask.addDependent(task.getId());
                }
            }
        }
    }

    // ==================== 循环依赖检测 ====================

    /**
     * 使用 DFS 三色标记法检测循环依赖。
     *
     * <p>检测到多个环时全部返回，方便 LLM 一次性修正。</p>
     *
     * @param tasks 任务列表
     * @return 检测到的循环依赖列表，每个元素为一个环上的任务 id 序列
     */
    List<List<String>> detectCircularDependencies(List<PlanTask> tasks) {
        Map<String, PlanTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(PlanTask::getId, t -> t));
        Map<String, Color> colors = new HashMap<>();
        Map<String, String> parent = new HashMap<>();  // childId → parentId
        List<List<String>> allCycles = new ArrayList<>();

        // 初始化所有节点为白色
        for (PlanTask task : tasks) {
            colors.put(task.getId(), Color.WHITE);
        }

        // 对每个白色节点启动 DFS
        for (PlanTask task : tasks) {
            if (colors.get(task.getId()) == Color.WHITE) {
                dfsDetectCycle(task.getId(), taskMap, colors, parent, allCycles);
            }
        }

        return allCycles;
    }

    /**
     * 递归 DFS，三色标记检测环。
     */
    private void dfsDetectCycle(String nodeId,
                                Map<String, PlanTask> taskMap,
                                Map<String, Color> colors,
                                Map<String, String> parent,
                                List<List<String>> allCycles) {
        colors.put(nodeId, Color.GRAY);

        PlanTask task = taskMap.get(nodeId);
        if (task == null) {
            colors.put(nodeId, Color.BLACK);
            return; // 依赖了不存在的任务 id，跳过（解析时已校验）
        }

        for (String depId : task.getDependencies()) {
            Color depColor = colors.getOrDefault(depId, Color.BLACK);
            if (depColor == Color.GRAY) {
                // 发现环，回溯构建环路径
                List<String> cycle = new ArrayList<>();
                cycle.add(depId);
                String current = nodeId;
                while (!current.equals(depId)) {
                    cycle.add(current);
                    current = parent.get(current);
                }
                cycle.add(depId); // 闭合环
                Collections.reverse(cycle);
                // 去重：只保留之前未发现的环
                if (allCycles.stream().noneMatch(existing -> sameCycle(existing, cycle))) {
                    allCycles.add(cycle);
                }
            } else if (depColor == Color.WHITE) {
                parent.put(depId, nodeId);
                dfsDetectCycle(depId, taskMap, colors, parent, allCycles);
            }
            // BLACK 不做处理
        }

        colors.put(nodeId, Color.BLACK);
    }

    /**
     * 判断两个环是否等价（旋转后内容匹配）
     */
    private boolean sameCycle(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        // 简化比较：排序后比较内容
        Set<String> setA = new LinkedHashSet<>(a);
        Set<String> setB = new LinkedHashSet<>(b);
        return setA.equals(setB);
    }

    // ==================== 循环依赖反馈构建 ====================

    /**
     * 构建循环依赖的错误反馈消息，帮助 LLM 理解并修正。
     */
    String buildCycleFeedback(List<List<String>> cycles, PlanParseResult lastResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下循环依赖被检测到，这些是不允许的：\n\n");
        for (int i = 0; i < cycles.size(); i++) {
            sb.append("循环 ").append(i + 1).append(": ");
            sb.append(String.join(" → ", cycles.get(i)));
            sb.append("\n");
        }
        sb.append("\n上一次生成的任务列表（供参考）：\n");
        for (PlanTask task : lastResult.tasks()) {
            sb.append("- ").append(task.getId()).append(": ")
                    .append(task.getDescription())
                    .append(" (依赖: ").append(task.getDependencies()).append(")\n");
        }
        sb.append("\n请调整任务之间的依赖关系以消除循环，重新生成 JSON 格式的执行计划。");
        return sb.toString();
    }

    // ==================== 执行计划构建（集合的集合） ====================

    /**
     * 基于拓扑分层构建执行计划。
     *
     * <p>返回 {@code List<Set<PlanTask>>}，其中下标 i 对应的集合内的所有任务
     * 可以同时执行（它们的依赖已在前 i-1 层全部完成）。</p>
     *
     * @param tasks 任务列表（已通过循环依赖校验）
     * @return 分层执行计划
     */
    List<Set<PlanTask>> buildExecutionPlan(List<PlanTask> tasks) {
        Map<String, PlanTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(PlanTask::getId, t -> t));
        // 记录每个任务剩余的未完成依赖数
        Map<String, Integer> remainingDeps = new LinkedHashMap<>();
        // 记录每个任务依赖的前置任务所在的层级（用于计算当前任务层级）
        Map<String, Set<String>> depTaskIds = new LinkedHashMap<>();

        for (PlanTask task : tasks) {
            remainingDeps.put(task.getId(), task.getDependencies().size());
            depTaskIds.put(task.getId(), new LinkedHashSet<>(task.getDependencies()));
        }

        List<Set<PlanTask>> levels = new ArrayList<>();
        Set<String> completed = new LinkedHashSet<>();

        // Kahn 算法变体：按层推进
        while (true) {
            Set<PlanTask> currentLevel = new LinkedHashSet<>();

            for (PlanTask task : tasks) {
                int remaining = remainingDeps.get(task.getId());
                if (remaining == 0 && !completed.contains(task.getId())) {
                    currentLevel.add(task);
                }
            }

            if (currentLevel.isEmpty()) {
                break; // 所有任务都已分配，或存在无法满足依赖的遗留任务
            }

            levels.add(currentLevel);

            // 标记当前层任务为已完成，并更新依赖它们的任务的剩余计数
            for (PlanTask task : currentLevel) {
                completed.add(task.getId());
                for (String dependentId : task.getDependents()) {
                    remainingDeps.merge(dependentId, -1, Integer::sum);
                }
            }
        }

        return levels;
    }

    // ==================== 结果展示 ====================

    /**
     * 将执行计划展示到终端。
     */
    void displayPlan(PlanParseResult parseResult, List<Set<PlanTask>> executionPlan) {
        terminalManager.printInfo("");
        terminalManager.printInfo("══════════════════════════════════════════");
        terminalManager.printInfo("  执行计划: " + parseResult.summary());
        terminalManager.printInfo("══════════════════════════════════════════");
        terminalManager.printInfo("");

        if (executionPlan.isEmpty()) {
            terminalManager.printInfo("  无可执行任务");
            return;
        }

        for (int i = 0; i < executionPlan.size(); i++) {
            Set<PlanTask> level = executionPlan.get(i);
            terminalManager.printInfo("── 第 " + (i + 1) + " 步（可并行执行 " + level.size() + " 个任务）──");

            for (PlanTask task : level) {
                String depStr = task.getDependencies().isEmpty()
                        ? ""
                        : " [依赖: " + String.join(", ", task.getDependencies()) + "]";
                String dependentStr = task.getDependents().isEmpty()
                        ? ""
                        : " [被依赖: " + String.join(", ", task.getDependents()) + "]";
                terminalManager.printInfo(String.format("  • [%s] %s (%s)%s%s",
                        task.getId(), task.getDescription(), task.getType(), depStr, dependentStr));
            }
            terminalManager.printInfo("");
        }

        terminalManager.printInfo("══════════════════════════════════════════");
        terminalManager.printInfo("  共 " + executionPlan.stream().mapToInt(Set::size).sum()
                + " 个任务，" + executionPlan.size() + " 个执行步骤");
        terminalManager.printInfo("══════════════════════════════════════════");
    }
}
