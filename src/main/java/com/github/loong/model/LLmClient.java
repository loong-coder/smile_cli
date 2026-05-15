package com.github.loong.model;

import com.github.loong.message.Message;

import java.util.List;
import java.util.function.Consumer;

public interface LLmClient extends AutoCloseable {

    void chat(List<Message> messages,
              Consumer<String> onToken,
              Consumer<String> onError) throws Exception;

    void cancel();

}