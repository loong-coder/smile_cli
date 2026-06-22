package com.github.loong.memory;

/**
 * 记忆系统运行配置快照，避免业务代码直接读取 Properties。
 */
public record MemoryConfig(boolean enabled,
                           int shortTermMaxRounds,
                           int shortTermMaxBytes,
                           int longTermTopK,
                           int longTermCandidateK,
                           int longTermInjectMaxBytes,
                           int timeDecayHalfLifeDays,
                           double minScore,
                           String qdrantBaseUrl,
                           String qdrantCollectionPrefix,
                           String qdrantApiKeyEnv,
                           String embeddingProvider,
                           String embeddingBaseUrl,
                           String embeddingModel,
                           String embeddingApiKeyEnv,
                           int embeddingDimensions) {

    /**
     * 判断长期记忆所需的远端配置是否完整。
     */
    public boolean hasLongTermConfig() {
        return !isBlank(qdrantBaseUrl)
                && !isBlank(qdrantCollectionPrefix)
                && !isBlank(embeddingProvider)
                && !isBlank(embeddingBaseUrl)
                && !isBlank(embeddingModel)
                && !isBlank(embeddingApiKeyEnv)
                && embeddingDimensions > 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
