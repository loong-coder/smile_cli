package com.github.loong.memory;

import java.util.List;

/**
 * 文本向量化接口，第一版由阿里云实现。
 */
public interface EmbeddingProvider {
    List<Float> embed(String text) throws MemoryException;
}
