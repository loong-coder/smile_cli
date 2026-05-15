package com.github.loong.model;

import com.github.loong.config.LlmConfig;
import junit.framework.TestCase;

public class LLmClientBuilderTest extends TestCase {

    public void testBuildReturnsModelClient() throws Exception {
        LLmClient client = LLmClientFactoryBuilder.fromConfig(new LlmConfig()).build();
        try {
            assertNotNull(client);
        } finally {
            client.close();
        }
    }
}