package com.github.loong.config;

import com.github.loong.memory.MemoryConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class LlmConfig {

    private final Properties props;

    private static final String DEFAULT_MODEL_NAME = "deepseek-v4-pro";
    private static final String DEFAULT_DISPLAY_VERSION = "deepseek v4 pro";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    public LlmConfig() {
        this.props = new Properties();
        props.setProperty("model.name", DEFAULT_MODEL_NAME);
        props.setProperty("model.displayVersion", DEFAULT_DISPLAY_VERSION);
        props.setProperty("api.baseUrl", DEFAULT_BASE_URL);
        // 记忆系统默认启用短期/摘要记忆，长期记忆在配置完整时启用。
        props.setProperty("memory.enabled", "true");
        props.setProperty("memory.shortTerm.maxRounds", "10");
        props.setProperty("memory.shortTerm.maxBytes", "131072");
        props.setProperty("memory.longTerm.topK", "10");
        props.setProperty("memory.longTerm.candidateK", "30");
        props.setProperty("memory.longTerm.injectMaxBytes", "16384");
        props.setProperty("memory.longTerm.timeDecayHalfLifeDays", "30");
        props.setProperty("memory.longTerm.minScore", "0.30");
        props.setProperty("qdrant.baseUrl", "http://localhost:6333");
        props.setProperty("qdrant.collectionPrefix", "smile_cli_memory");
        props.setProperty("qdrant.apiKeyEnv", "QDRANT_API_KEY");
        props.setProperty("embedding.provider", "aliyun");
        props.setProperty("embedding.baseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        props.setProperty("embedding.model", "text-embedding-v4");
        props.setProperty("embedding.apiKeyEnv", "ALIYUN_API_KEY");
        props.setProperty("embedding.dimensions", "1024");

        loadConfigFile();
    }

    private void loadConfigFile() {
        Path configPath = Paths.get(System.getProperty("user.home"), ".smile_cli", "config");
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                Properties fileProps = new Properties();
                fileProps.load(in);
                props.putAll(fileProps);
            } catch (IOException e) {
                System.err.println("Warning: cannot read config file, using defaults");
            }
        }
    }

    public String getModelName() {
        return props.getProperty("model.name");
    }

    public String getDisplayVersion() {
        return props.getProperty("model.displayVersion");
    }

    public String getBaseUrl() {
        String url = props.getProperty("api.baseUrl");
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public String getApiKey() {
        return System.getenv("DEEPSEEK_API_KEY");
    }

    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }

    public MemoryConfig getMemoryConfig() {
        return new MemoryConfig(
                getBoolean("memory.enabled"),
                getInt("memory.shortTerm.maxRounds"),
                getInt("memory.shortTerm.maxBytes"),
                getInt("memory.longTerm.topK"),
                getInt("memory.longTerm.candidateK"),
                getInt("memory.longTerm.injectMaxBytes"),
                getInt("memory.longTerm.timeDecayHalfLifeDays"),
                getDouble("memory.longTerm.minScore"),
                trimTrailingSlash(props.getProperty("qdrant.baseUrl")),
                props.getProperty("qdrant.collectionPrefix"),
                props.getProperty("qdrant.apiKeyEnv"),
                props.getProperty("embedding.provider"),
                trimTrailingSlash(props.getProperty("embedding.baseUrl")),
                props.getProperty("embedding.model"),
                props.getProperty("embedding.apiKeyEnv"),
                getInt("embedding.dimensions"));
    }

    private boolean getBoolean(String key) {
        return Boolean.parseBoolean(props.getProperty(key));
    }

    private int getInt(String key) {
        return Integer.parseInt(props.getProperty(key));
    }

    private double getDouble(String key) {
        return Double.parseDouble(props.getProperty(key));
    }

    private String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
