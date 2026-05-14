package com.github.loong;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigManager {

    private final Properties props;

    private static final String DEFAULT_MODEL_NAME = "deepseek-v4-pro";
    private static final String DEFAULT_DISPLAY_VERSION = "deepseek v4 pro";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    public ConfigManager() {
        this.props = new Properties();
        props.setProperty("model.name", DEFAULT_MODEL_NAME);
        props.setProperty("model.displayVersion", DEFAULT_DISPLAY_VERSION);
        props.setProperty("api.baseUrl", DEFAULT_BASE_URL);

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
}
