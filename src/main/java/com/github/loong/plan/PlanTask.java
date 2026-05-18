package com.github.loong.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 执行计划中的单个任务。
 *
 * <p>每个任务记录自身依赖的前置任务（dependencies），
 * 并在后处理阶段计算出依赖它的后继任务（dependents）。</p>
 *
 * <p>任务执行过程中会跟踪状态和执行结果，用于偏离检测和重规划。</p>
 */
public class PlanTask {

    /**
     * 任务执行状态。
     */
    public enum TaskStatus {
        /** 等待执行 */ PENDING,
        /** 正在执行 */ IN_PROGRESS,
        /** 执行成功 */ COMPLETED,
        /** 执行失败 */ FAILED
    }

    /** 任务唯一标识 */
    private final String id;
    /** 任务描述 */
    private final String description;
    /** 任务类型，如 FILE_READ、FILE_WRITE、COMMAND、ANALYSIS、VERIFICATION */
    private final String type;
    /** 当前任务依赖的前置任务 id 列表 */
    private final List<String> dependencies;
    /** 依赖当前任务的后继任务 id 列表（后处理计算得出） */
    private final List<String> dependents;
    /** 任务执行状态 */
    private TaskStatus status;
    /** 任务执行结果摘要（LLM 返回的最终文本） */
    private String result;

    public PlanTask(String id, String description, String type, List<String> dependencies) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.description = Objects.requireNonNull(description, "description 不能为 null");
        this.type = Objects.requireNonNull(type, "type 不能为 null");
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        this.dependents = new ArrayList<>();
        this.status = TaskStatus.PENDING;
    }

    // --- 基础 getter ---

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    /** 返回不可变的依赖列表 */
    public List<String> getDependencies() {
        return dependencies;
    }

    /** 返回不可变的后继任务列表 */
    public List<String> getDependents() {
        return Collections.unmodifiableList(dependents);
    }

    // --- 执行状态相关 getter/setter ---

    /** 获取任务当前执行状态 */
    public TaskStatus getStatus() {
        return status;
    }

    /** 设置任务执行状态 */
    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    /** 获取任务执行结果摘要 */
    public String getResult() {
        return result;
    }

    /** 设置任务执行结果摘要 */
    public void setResult(String result) {
        this.result = result;
    }

    // --- 包级可见方法：供 PlanCommand 后处理使用 ---

    /** 添加一个后继任务 id */
    void addDependent(String taskId) {
        dependents.add(taskId);
    }

    @Override
    public String toString() {
        return "PlanTask{id='" + id + "', description='" + description + "', type='" + type
                + "', dependencies=" + dependencies + ", dependents=" + dependents + '}';
    }
}
