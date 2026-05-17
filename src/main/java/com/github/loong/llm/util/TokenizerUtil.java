package com.github.loong.llm.util;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 估算token
 *
 */
public class TokenizerUtil {


    private static final String DEEPSEEK_TOKENIZER_PATH = "tokenizer/deepseek/v4pro/tokenizer.json";

    /**
     * 估算deepseek 的token使用量
     * @param text
     * @return
     * @throws IOException
     */
    public static int estimateDeekSeekTokens(String text)  {
        // 从classpath读取资源，避免打包成JAR后相对路径失效
        try (InputStream inputStream = TokenizerUtil.class.getClassLoader().getResourceAsStream(DEEPSEEK_TOKENIZER_PATH)) {
            if (inputStream == null) {
                throw new IOException("Tokenizer resource not found: " + DEEPSEEK_TOKENIZER_PATH);
            }
            try (var tokenizer = HuggingFaceTokenizer.newInstance(inputStream, Map.of())) {
                var encoding = tokenizer.encode(text);
                return encoding.getIds().length;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
