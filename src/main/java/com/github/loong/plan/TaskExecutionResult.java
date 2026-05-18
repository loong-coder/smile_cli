package com.github.loong.plan;

/**
 * 单个任务的执行结果，同时携带成功/失败状态和内容描述。
 * 失败时 message 为错误原因，供重规划时 LLM 参考。
 */
public record TaskExecutionResult(boolean success, String message) {

    /** 创建成功结果 */
    public static TaskExecutionResult success(String message) {
        return new TaskExecutionResult(true, message);
    }

    /** 创建失败结果，reason 描述失败原因 */
    public static TaskExecutionResult failure(String reason) {
        return new TaskExecutionResult(false, reason);
    }
}
