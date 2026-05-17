package com.github.loong.llm;

import com.github.loong.message.Message;
import com.github.loong.tools.ToolDefinition;

import java.util.List;
import java.util.function.Consumer;

public interface LLmClient extends AutoCloseable {

    default void chat(List<Message> messages,
                      Consumer<String> onToken,
                      Consumer<String> onReasoning,
                      Consumer<String> onError) throws Exception {
        chat(messages, List.of(), onToken, onReasoning, onError);
    }

    ChatResult chat(List<Message> messages,
                    List<ToolDefinition> tools,
                    Consumer<String> onToken,
                    Consumer<String> onReasoning,
                    Consumer<String> onError) throws Exception;

    /**
     * 带响应格式控制的 chat 重载。
     *
     * @param responseFormat 响应格式，如 "text"（默认）或 "json_object"（结构化输出）
     */
    default ChatResult chat(List<Message> messages,
                    List<ToolDefinition> tools,
                    Consumer<String> onToken,
                    Consumer<String> onReasoning,
                    Consumer<String> onError,
                    String responseFormat) throws Exception {
        // 默认实现忽略 responseFormat，委托给原方法
        return chat(messages, tools, onToken, onReasoning, onError);
    }

    void cancel();

}