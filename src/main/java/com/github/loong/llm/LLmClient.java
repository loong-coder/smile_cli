package com.github.loong.llm;

import com.github.loong.message.Message;
import com.github.loong.tools.ToolDefinition;

import java.util.List;
import java.util.function.Consumer;

public interface LLmClient extends AutoCloseable {

    default void chat(List<Message> messages,
                      Consumer<String> onToken,
                      Consumer<String> onError) throws Exception {
        chat(messages, List.of(), onToken, onError);
    }

    ChatResult chat(List<Message> messages,
                    List<ToolDefinition> tools,
                    Consumer<String> onToken,
                    Consumer<String> onError) throws Exception;

    void cancel();

}