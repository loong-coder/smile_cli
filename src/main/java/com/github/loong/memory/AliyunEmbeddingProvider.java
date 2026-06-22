package com.github.loong.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云 DashScope 兼容模式 embedding 客户端。
 */
public class AliyunEmbeddingProvider implements EmbeddingProvider {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final MemoryConfig config;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AliyunEmbeddingProvider(MemoryConfig config) {
        this(config, System.getenv(config.embeddingApiKeyEnv()));
    }

    AliyunEmbeddingProvider(MemoryConfig config, String apiKey) {
        this.config = config;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public List<Float> embed(String text) throws MemoryException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MemoryException("缺少阿里云 embedding API Key 环境变量: " + config.embeddingApiKeyEnv());
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.embeddingModel());
            body.put("input", text == null ? "" : text);
            String json = objectMapper.writeValueAsString(body);
            Request request = new Request.Builder()
                    .url(endpointUrl())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseText = readBody(response);
                if (!response.isSuccessful()) {
                    throw new MemoryException("阿里云 embedding 请求失败: HTTP " + response.code() + " " + responseText);
                }
                return parseVector(responseText);
            }
        } catch (MemoryException e) {
            throw e;
        } catch (Exception e) {
            throw new MemoryException("阿里云 embedding 请求异常", e);
        }
    }

    private String endpointUrl() {
        String baseUrl = config.embeddingBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/embeddings";
    }

    private String readBody(Response response) throws Exception {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private List<Float> parseVector(String json) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) root.get("data");
        if (data == null || data.isEmpty()) {
            throw new MemoryException("阿里云 embedding 响应缺少 data");
        }
        @SuppressWarnings("unchecked")
        List<Number> embedding = (List<Number>) data.get(0).get("embedding");
        if (embedding == null || embedding.isEmpty()) {
            throw new MemoryException("阿里云 embedding 响应缺少 embedding");
        }
        List<Float> vector = new ArrayList<>();
        for (Number number : embedding) {
            vector.add(number.floatValue());
        }
        return vector;
    }
}
