package com.github.loong.llm;

import com.github.loong.message.AssistantMessage;

import java.util.List;

/**
 * 单轮模型响应的结果记录。
 *
 * <p>每一轮与 LLM 的交互，模型可能返回普通文本、推理过程、工具调用请求，
 * 以及一个表示本轮会话结束原因的标识。该类将这些信息统一封装为不可变记录。</p>
 *
 * <p>在紧凑构造器中，所有字段都会被做空安全处理：
 * 字符串字段默认空串，工具调用列表默认空不可变列表。</p>
 *
 * @param content          模型返回的普通文本内容，不会为 {@code null}
 * @param reasoningContent 模型的推理/思考过程文本（如 DeepSeek R1 的思维链），不会为 {@code null}
 * @param toolCalls        模型请求调用的工具列表，不会为 {@code null}
 * @param finishReason     本轮会话结束原因，如 "stop"、"tool_calls" 等
 */
public record ChatResult(String content,
                         String reasoningContent,
                         List<AssistantMessage.ToolCall> toolCalls,
                         String finishReason) {

    /**
     * 紧凑构造器，确保所有字段不为 {@code null}。
     *
     * <ul>
     *   <li>{@code content} 和 {@code reasoningContent} 为 {@code null} 时设为空字符串</li>
     *   <li>{@code toolCalls} 为 {@code null} 时设为不可变空列表，否则做防御性拷贝</li>
     * </ul>
     */
    public ChatResult {
        content = content == null ? "" : content;
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * 判断本轮响应是否包含工具调用请求。
     *
     * @return {@code true} 如果工具调用列表非空
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
