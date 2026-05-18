package com.github.loong.plan;

import com.github.loong.chat.ChatContext;
import com.github.loong.llm.ChatResult;
import com.github.loong.message.AssistantMessage;
import com.github.loong.message.Message;
import com.github.loong.message.SystemMessage;
import com.github.loong.message.ToolMessage;
import com.github.loong.message.UserMessage;
import com.github.loong.ui.TerminalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 执行计划中任务的执行器，按拓扑顺序逐个执行任务。
 *
 * <p>执行流程：
 * <ol>
 *   <li>将分层执行计划展平为线性顺序</li>
 *   <li>逐个执行任务：构建执行上下文 → LLM + 工具循环 → 收集结果</li>
 *   <li>每完成 5 个任务（一批）后触发目标偏离校验</li>
 *   <li>若偏离，将已完成任务、未完成任务、原始目标发送给 LLM 重新规划</li>
 *   <li>重新规划后递归执行新任务列表</li>
 * </ol>
 */
public class PlanExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanExecutor.class);

    /** 单任务最大工具调用轮次 */
    static final int MAX_TOOL_ROUNDS = 8;
    /** 重新规划最大次数，避免无限循环 */
    static final int MAX_REPLAN_ATTEMPTS = 3;
    /** 目标偏离校验间隔（每 N 个任务检测一次） */
    static final int VERIFICATION_INTERVAL = 5;

    private final ChatContext context;
    private final TerminalManager terminalManager;
    private final String originalGoal;

    /**
     * 构造任务执行器，context 为 null 时仅支持纯逻辑方法测试，
     * 正式执行任务需要提供有效的 ChatContext。
     */
    public PlanExecutor(ChatContext context, String originalGoal) {
        this.context = context;
        this.terminalManager = context != null ? context.terminalManager() : null;
        this.originalGoal = originalGoal;
    }

    // ==================== 公开入口 ====================

    /**
     * 执行拓扑分层后的执行计划。
     *
     * @param executionPlan 分层执行计划（每层内的任务原本可并行，此处逐个执行方便调试）
     * @param allTasks      全部任务列表
     */
    public void execute(List<Set<PlanTask>> executionPlan, List<PlanTask> allTasks) {
        List<PlanTask> sequentialTasks = flattenPlan(executionPlan);
        executeSequential(sequentialTasks, 0);
    }

    // ==================== 核心执行流程 ====================

    /**
     * 递归执行任务序列，支持偏离后重新规划继续执行。
     *
     * @param tasks       待执行任务列表
     * @param replanCount 已重新规划次数
     */
    private void executeSequential(List<PlanTask> tasks, int replanCount) {
        if (replanCount >= MAX_REPLAN_ATTEMPTS) {
            terminalManager.printError("重新规划次数已达上限（" + MAX_REPLAN_ATTEMPTS + " 次），执行中止");
            return;
        }

        // 已处理任务（含成功和失败），用于重规划时告知 LLM 完整上下文
        List<PlanTask> processedTasks = new ArrayList<>();
        List<PlanTask> pendingTasks = new ArrayList<>(tasks);

        for (int i = 0; i < tasks.size(); i++) {
            PlanTask task = tasks.get(i);
            pendingTasks.remove(0);

            // 检查前置依赖是否完成
            if (!dependenciesSatisfied(task, processedTasks)) {
                task.setResult("前置依赖任务未完成");
                task.setStatus(PlanTask.TaskStatus.FAILED);
                terminalManager.printError("  ✗ 任务 " + task.getId() + " 的前置依赖未完成，标记为失败");
                processedTasks.add(task);
                continue;
            }

            // 执行单个任务
            terminalManager.printInfo("");
            terminalManager.printInfo("── 执行 [" + task.getId() + "] " + task.getDescription() + " ──");
            task.setStatus(PlanTask.TaskStatus.IN_PROGRESS);

            TaskExecutionResult execResult = executeSingleTask(task, processedTasks, i + 1, tasks.size());
            // 始终记录执行结果（含失败原因），供重规划时 LLM 参考
            task.setResult(execResult.message());

            if (execResult.success()) {
                task.setStatus(PlanTask.TaskStatus.COMPLETED);
                terminalManager.printInfo("  ✓ [" + task.getId() + "] 完成");
            } else {
                task.setStatus(PlanTask.TaskStatus.FAILED);
                terminalManager.printError("  ✗ [" + task.getId() + "] 失败: " + execResult.message());
            }
            processedTasks.add(task);

            // 每 VERIFICATION_INTERVAL 个任务或最后一批时触发目标偏离校验
            boolean batchEnd = (i + 1) % VERIFICATION_INTERVAL == 0;
            boolean lastTask = (i == tasks.size() - 1);
            if ((batchEnd || lastTask) && !pendingTasks.isEmpty()) {
                int batchNum = (i + 1) / VERIFICATION_INTERVAL + ((i + 1) % VERIFICATION_INTERVAL > 0 ? 1 : 0);
                terminalManager.printInfo("── 第 " + batchNum + " 批任务完成，触发目标偏离校验...");
                boolean aligned = verifyGoalAlignment(processedTasks, pendingTasks);
                if (!aligned) {
                    terminalManager.printWarning("⚠ 检测到执行偏离，重新规划（含已完成任务中错误操作的回滚）...");
                    // 展示将被废弃的旧任务
                    terminalManager.printInfo("  已废弃的旧计划剩余任务:");
                    for (PlanTask pt : pendingTasks) {
                        terminalManager.printInfo("    - [" + pt.getId() + "] " + pt.getDescription());
                    }
                    terminalManager.printInfo("");

                    List<PlanTask> newTasks = replan(processedTasks, pendingTasks);
                    if (newTasks != null && !newTasks.isEmpty()) {
                        terminalManager.printInfo("重新规划成功，使用新计划继续执行...");
                        executeSequential(newTasks, replanCount + 1);
                    } else {
                        terminalManager.printError("重新规划未产生有效任务，执行中止");
                    }
                    return;
                }
            }
        }

        terminalManager.printInfo("");
        terminalManager.printInfo("══════════════════════════════════════════");
        long successCount = processedTasks.stream()
                .filter(t -> t.getStatus() == PlanTask.TaskStatus.COMPLETED).count();
        terminalManager.printInfo("  执行结束: " + successCount + "/" + tasks.size() + " 个任务成功");
        terminalManager.printInfo("══════════════════════════════════════════");
    }

    // ==================== 单任务执行 ====================

    /**
     * 通过 LLM + 工具循环执行单个任务。
     *
     * @param task           当前任务
     * @param processedTasks 已处理的任务列表（用于提供上下文）
     * @return 执行结果对象，含成功/失败状态和内容描述，失败原因可传递到重规划阶段
     */
    TaskExecutionResult executeSingleTask(PlanTask task, List<PlanTask> processedTasks, int taskIndex, int totalTasks) {
        List<Message> messages = buildExecutionMessages(task, processedTasks, taskIndex, totalTasks);

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            try {
                ChatResult result = context.llmClient().chat(
                        messages,
                        context.toolDefinitions(),
                        token -> terminalManager.printToken(token),
                        thinking -> terminalManager.printThinking(thinking),
                        error -> terminalManager.printError("LLM 错误: " + error));

                if (result == null) {
                    return TaskExecutionResult.failure("LLM 调用未返回结果");
                }

                if (result.hasToolCalls()) {
                    messages.add(new AssistantMessage(
                            result.content(), result.reasoningContent(), result.toolCalls()));
                    for (AssistantMessage.ToolCall call : result.toolCalls()) {
                        String toolResult = context.toolCallExecutor().execute(call);
                        messages.add(new ToolMessage(call.id(), toolResult));
                    }
                    continue;
                }

                // 无工具调用：任务执行完毕
                if (!result.content().isBlank()) {
                    return TaskExecutionResult.success(result.content());
                }
                return TaskExecutionResult.failure("LLM 未返回文本内容");
            } catch (Exception e) {
                LOGGER.error("任务 {} 执行异常", task.getId(), e);
                String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                return TaskExecutionResult.failure("执行异常: " + cause);
            }
        }

        // 超过最大工具轮次
        return TaskExecutionResult.failure("超过最大工具调用轮次（" + MAX_TOOL_ROUNDS + "）");
    }

    /**
     * 构建任务执行的初始消息列表。
     */
    /**
     * 构建任务执行的初始消息列表。
     *
     * <p>关键设计：用户原始目标仅放入系统提示词作为背景上下文，并明确标注"不是你的任务"，
     * 当前任务描述是唯一的用户消息指令。避免模型把大目标当成当前要完成的事。</p>
     */
    private List<Message> buildExecutionMessages(PlanTask task, List<PlanTask> processedTasks, int taskIndex, int totalTasks) {
        List<Message> messages = new ArrayList<>();

        // 系统提示词：设置角色 + 背景上下文（用户原始目标仅供理解，不是指令）
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("""
                你是任务执行器，正在执行一个多步骤计划中的第""")
                .append(taskIndex).append("步（共").append(totalTasks).append("步）。\n\n")
                .append("""
                        你的唯一职责是执行用户消息中描述的【当前任务】，不得越权处理其他步骤。

                        背景上下文（仅供理解当前任务的意义，不是你的任务）:
                        - 用户原始目标: """)
                .append(originalGoal).append("\n");

        if (!processedTasks.isEmpty()) {
            systemPrompt.append("- 已处理步骤:\n");
            for (PlanTask pt : processedTasks) {
                String statusLabel = pt.getStatus() == PlanTask.TaskStatus.COMPLETED ? "✓" : "✗";
                systemPrompt.append("  ").append(statusLabel).append(" [").append(pt.getId()).append("] ")
                        .append(pt.getDescription()).append("\n");
                if (pt.getResult() != null && !pt.getResult().isBlank()) {
                    String truncated = pt.getResult().length() > 300
                            ? pt.getResult().substring(0, 300) + "..."
                            : pt.getResult();
                    systemPrompt.append("    结果: ").append(truncated).append("\n");
                }
            }
        }

        systemPrompt.append("""

                执行规则:
                1. 只执行用户消息中当前任务描述明确要求的操作
                2. 不要尝试完成用户的原始目标 — 那是整个计划的事，后续步骤会逐步完成
                3. 不要跳过当前步骤去提前执行后续任务
                4. 可以使用文件读取、文件写入、命令执行、grep 搜索等工具
                5. 完成后用中文简明总结执行结果""");

        messages.add(new SystemMessage(systemPrompt.toString()));

        // 用户消息：只有当前任务，不含原始目标
        messages.add(new UserMessage("当前任务: " + task.getDescription()));

        return messages;
    }

    // ==================== 目标偏离校验 ====================

    /**
     * 校验当前执行是否偏离原始目标。
     *
     * @param completedTasks 已完成任务列表
     * @param pendingTasks   待执行任务列表
     * @return true 表示对齐，可继续；false 表示偏离
     */
    boolean verifyGoalAlignment(List<PlanTask> processedTasks, List<PlanTask> pendingTasks) {
        try {
            String prompt = buildVerificationPrompt(processedTasks, pendingTasks);
            List<Message> messages = List.of(
                    new SystemMessage("""
                            你是任务监控器。请判断当前任务执行是否偏离了原始目标。
                            仅回复一个词: ALIGNED 或 DEVIATED。
                            如果 DEVIATED，在第二行用中文简述偏离原因。"""),
                    new UserMessage(prompt));

            ChatResult result = context.llmClient().chat(
                    messages,
                    List.of(),  // 校验阶段不使用工具
                    token -> {
                    },
                    thinking -> {
                    },
                    error -> {
                    });

            if (result == null || result.content() == null) {
                // LLM 调用失败，保守处理：视为对齐继续执行
                return true;
            }

            String content = result.content().trim().toUpperCase();
            boolean aligned = content.startsWith("ALIGNED");

            if (!aligned && content.startsWith("DEVIATED")) {
                // 提取偏离原因
                String reason = result.content().length() > 200
                        ? result.content().substring(0, 200)
                        : result.content();
                LOGGER.warn("目标偏离检测: {}", reason);
                terminalManager.printWarning("  偏离原因: " + reason);
            }

            return aligned;
        } catch (Exception e) {
            LOGGER.error("目标偏离校验异常", e);
            // 校验失败时保守处理：视为对齐继续执行
            return true;
        }
    }

    /**
     * 构建校验提示词。
     */
    private String buildVerificationPrompt(List<PlanTask> processedTasks, List<PlanTask> pendingTasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("原始目标: ").append(originalGoal).append("\n\n");

        sb.append("已处理任务:\n");
        for (PlanTask t : processedTasks) {
            String statusLabel = t.getStatus() == PlanTask.TaskStatus.COMPLETED ? "✓" : "✗";
            sb.append("- ").append(statusLabel).append(" [").append(t.getId()).append("] ").append(t.getDescription());
            if (t.getResult() != null && !t.getResult().isBlank()) {
                String truncated = t.getResult().length() > 200
                        ? t.getResult().substring(0, 200) + "..."
                        : t.getResult();
                sb.append(" → ").append(truncated);
            }
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("待执行任务:\n");
        for (PlanTask t : pendingTasks) {
            sb.append("- [").append(t.getId()).append("] ").append(t.getDescription()).append("\n");
        }

        return sb.toString();
    }

    // ==================== 重新规划 ====================

    /**
     * 检测到偏离时，将已完成任务、未完成任务、原始目标组装后发送给 LLM 重新生成计划。
     *
     * @param completedTasks 已完成任务列表
     * @param pendingTasks   原计划中未完成的任务列表
     * @return 新生成的任务列表，失败返回 null
     */
    private List<PlanTask> replan(List<PlanTask> processedTasks, List<PlanTask> pendingTasks) {
        try {
            String replanPrompt = buildReplanUserMessage(processedTasks, pendingTasks);
            List<Message> messages = List.of(
                    new SystemMessage("""
                            你是任务规划专家。当前执行计划发生了偏离，需要你重新生成完整计划。

                            重要：已处理任务中如果存在错误操作（如写入了错误文件、执行了不当命令），
                            必须首先生成回滚任务来撤销这些错误操作，然后再生成正确的前进任务。

                            可用任务类型：FILE_READ、FILE_WRITE、COMMAND、ANALYSIS、VERIFICATION

                            规则：
                            1. 审视每个已处理任务，如果其操作导致偏离原始目标，需要生成回滚任务
                            2. 回滚任务放在新计划的最前面（如恢复被错误修改的文件、删除错误创建的内容等）
                            3. 回滚完成后，再生成从干净状态到原始目标的正确前进任务
                            4. 正确完成的任务不需要回滚，也不需要重新生成
                            5. 新任务 ID 使用 task_rollback_N 或 task_forward_N 前缀便于区分
                            6. 回滚任务的 type 应根据实际操作用 FILE_WRITE 或 COMMAND
                            7. 任务描述要具体明确，回滚任务需写明要撤销的具体操作
                            8. 保持所有任务都朝向原始目标"""),
                    new UserMessage(replanPrompt));

            ChatResult result = context.llmClient().chat(
                    messages,
                    List.of(),  // 规划阶段不使用工具
                    token -> {
                    },
                    thinking -> {
                    },
                    error -> {
                    },
                    "json_object");

            if (result == null || result.content() == null) {
                return null;
            }

            // 复用 PlanCommand 的解析能力
            PlanCommand parser = new PlanCommand(context);
            PlanParseResult parseResult = parser.parseResponse(result.content());
            if (parseResult == null) {
                LOGGER.error("重新规划 JSON 解析失败: {}",
                        result.content().substring(0, Math.min(200, result.content().length())));
                return null;
            }

            // 后处理
            parser.computeDependents(parseResult.tasks());
            List<List<String>> cycles = parser.detectCircularDependencies(parseResult.tasks());
            if (!cycles.isEmpty()) {
                LOGGER.error("重新规划产生了循环依赖");
                return null;
            }

            terminalManager.printInfo("新计划: " + parseResult.summary());
            for (PlanTask t : parseResult.tasks()) {
                terminalManager.printInfo("  • [" + t.getId() + "] " + t.getDescription()
                        + " (" + t.getType() + ")");
            }

            return parseResult.tasks();
        } catch (Exception e) {
            LOGGER.error("重新规划异常", e);
            return null;
        }
    }

    /**
     * 构建重新规划的用户提示词。
     */
    private String buildReplanUserMessage(List<PlanTask> processedTasks, List<PlanTask> pendingTasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("原始目标: ").append(originalGoal).append("\n\n");

        sb.append("已处理任务（审视其中哪些导致了偏离，需要生成回滚任务）:\n");
        for (PlanTask t : processedTasks) {
            String statusLabel = t.getStatus() == PlanTask.TaskStatus.COMPLETED ? "成功" : "失败";
            sb.append("- [").append(statusLabel).append("] [").append(t.getId()).append("] ")
                    .append(t.getType()).append(": ").append(t.getDescription());
            if (t.getResult() != null && !t.getResult().isBlank()) {
                String truncated = t.getResult().length() > 300
                        ? t.getResult().substring(0, 300) + "..."
                        : t.getResult();
                sb.append("\n  执行结果: ").append(truncated);
            }
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("原计划中未执行的任务（已废弃，仅供理解原始意图）:\n");
        for (PlanTask t : pendingTasks) {
            sb.append("- [").append(t.getId()).append("] ").append(t.getDescription())
                    .append(" (").append(t.getType()).append(")")
                    .append(" 依赖: ").append(t.getDependencies()).append("\n");
        }
        sb.append("\n");

        sb.append("请按以下步骤生成新计划：\n");
        sb.append("1. 判断哪些已处理任务导致了偏离（标记为\"失败\"的优先审视）\n");
        sb.append("2. 为导致偏离的任务生成回滚任务（放在新计划最前面，type 用 FILE_WRITE 或 COMMAND）\n");
        sb.append("3. 回滚完成后，生成纠正后的前进任务以达成原始目标\n");
        sb.append("4. 正确完成的任务不需要回滚\n");
        sb.append("5. 回滚任务 ID 用 task_rollback_N，前进任务 ID 用 task_forward_N\n");

        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    /**
     * 将分层执行计划展平为线性任务序列（层内按原始顺序逐个排列）。
     */
    List<PlanTask> flattenPlan(List<Set<PlanTask>> executionPlan) {
        List<PlanTask> flat = new ArrayList<>();
        for (Set<PlanTask> level : executionPlan) {
            flat.addAll(level);
        }
        return flat;
    }

    /**
     * 检查任务的所有前置依赖是否都已完成。
     */
    boolean dependenciesSatisfied(PlanTask task, List<PlanTask> processedTasks) {
        if (task.getDependencies().isEmpty()) {
            return true;
        }
        Set<String> completedIds = processedTasks.stream()
                .filter(t -> t.getStatus() == PlanTask.TaskStatus.COMPLETED)
                .map(PlanTask::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String depId : task.getDependencies()) {
            if (!completedIds.contains(depId)) {
                return false;
            }
        }
        return true;
    }
}
