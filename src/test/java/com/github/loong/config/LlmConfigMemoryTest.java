package com.github.loong.config;

import com.github.loong.memory.MemoryConfig;
import junit.framework.TestCase;

/**
 * 验证记忆系统配置默认值和派生配置对象。
 */
public class LlmConfigMemoryTest extends TestCase {

    public void testDefaultMemoryConfigValues() {
        LlmConfig config = new LlmConfig();

        MemoryConfig memoryConfig = config.getMemoryConfig();

        assertTrue(memoryConfig.enabled());
        assertEquals(10, memoryConfig.shortTermMaxRounds());
        assertEquals(131072, memoryConfig.shortTermMaxBytes());
        assertEquals(10, memoryConfig.longTermTopK());
        assertEquals(30, memoryConfig.longTermCandidateK());
        assertEquals(16384, memoryConfig.longTermInjectMaxBytes());
        assertEquals(30, memoryConfig.timeDecayHalfLifeDays());
        assertEquals(0.30d, memoryConfig.minScore());
        assertEquals("http://localhost:6333", memoryConfig.qdrantBaseUrl());
        assertEquals("smile_cli_memory", memoryConfig.qdrantCollectionPrefix());
        assertEquals("", memoryConfig.qdrantApiKeyEnv());
        assertEquals("aliyun", memoryConfig.embeddingProvider());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings", memoryConfig.embeddingBaseUrl());
        assertEquals("text-embedding-v4", memoryConfig.embeddingModel());
        assertEquals("DASHSCOPE_API_KEY", memoryConfig.embeddingApiKeyEnv());
        assertEquals(1024, memoryConfig.embeddingDimensions());
    }

    public void testMemoryConfigAllowsLocalQdrantWithoutApiKeyEnv() {
        MemoryConfig memoryConfig = new MemoryConfig(
                true,
                10,
                131072,
                10,
                30,
                16384,
                30,
                0.30d,
                "http://localhost:6333",
                "smile_cli_memory",
                "",
                "aliyun",
                "http://embedding",
                "text-embedding-v4",
                "DASHSCOPE_API_KEY",
                1024);

        assertTrue(memoryConfig.hasLongTermConfig());
    }

    public void testMemoryConfigReportsMissingLongTermConfiguration() {
        MemoryConfig memoryConfig = new MemoryConfig(
                true,
                10,
                131072,
                10,
                30,
                16384,
                30,
                0.30d,
                "",
                "smile_cli_memory",
                "QDRANT_API_KEY",
                "aliyun",
                "",
                "text-embedding-v4",
                "ALIYUN_API_KEY",
                1024);

        assertFalse(memoryConfig.hasLongTermConfig());
    }
}
