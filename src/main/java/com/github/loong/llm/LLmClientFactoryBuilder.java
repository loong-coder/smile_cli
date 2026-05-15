package com.github.loong.llm;

import com.github.loong.config.LlmConfig;
import com.github.loong.llm.impl.DeepSeekClient;

public class LLmClientFactoryBuilder {

    private final LlmConfig config;

    private LLmClientFactoryBuilder(LlmConfig config) {
        this.config = config;
    }

    public static LLmClientFactoryBuilder fromConfig(LlmConfig config) {
        return new LLmClientFactoryBuilder(config);
    }

    public LLmClient build() {
        return new DeepSeekClient(config);
    }
}