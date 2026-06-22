package com.github.loong.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Qdrant HTTP 实现，负责长期向量记忆的写入、检索和清理。
 */
public class QdrantLongTermMemoryStore implements LongTermMemoryStore {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final MemoryConfig config;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QdrantLongTermMemoryStore(MemoryConfig config) {
        this(config, resolveApiKey(config));
    }

    QdrantLongTermMemoryStore(MemoryConfig config, String apiKey) {
        this.config = config;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private static String resolveApiKey(MemoryConfig config) {
        String apiKeyEnv = config.qdrantApiKeyEnv();
        if (apiKeyEnv == null || apiKeyEnv.isBlank()) {
            // 本地 Qdrant 常见部署没有鉴权，此时不发送 api-key 请求头。
            return null;
        }
        return System.getenv(apiKeyEnv);
    }

    @Override
    public void upsert(String workspaceId, MemoryEntry entry, List<Float> vector) throws MemoryException {
        ensureCollection(workspaceId, vector.size());
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", entry.id());
        point.put("vector", vector);
        point.put("payload", payload(entry));
        Map<String, Object> body = Map.of("points", List.of(point));
        request("PUT", collectionPath(workspaceId) + "/points?wait=true", body, true);
    }

    @Override
    public List<RetrievedMemory> search(String workspaceId, List<Float> queryVector, int limit) throws MemoryException {
        ensureCollection(workspaceId, queryVector.size());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", queryVector);
        body.put("limit", limit);
        body.put("with_payload", true);
        String response = request("POST", collectionPath(workspaceId) + "/points/search", body, true);
        try {
            return parseSearch(response);
        } catch (Exception e) {
            throw new MemoryException("解析 Qdrant 检索响应失败", e);
        }
    }

    @Override
    public void markAccessed(String workspaceId, List<MemoryEntry> entries, Instant now) throws MemoryException {
        for (MemoryEntry entry : entries) {
            MemoryEntry accessed = entry.withAccessed(now);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payload", payload(accessed));
            body.put("points", List.of(entry.id()));
            request("POST", collectionPath(workspaceId) + "/points/payload?wait=true", body, true);
        }
    }

    @Override
    public void clearWorkspace(String workspaceId) throws MemoryException {
        request("DELETE", collectionPath(workspaceId), null, false);
    }

    private void ensureCollection(String workspaceId, int vectorSize) throws MemoryException {
        String path = collectionPath(workspaceId);
        int code = requestCode("GET", path, null);
        if (code == 200) {
            return;
        }
        if (code != 404) {
            throw new MemoryException("检查 Qdrant collection 失败: HTTP " + code);
        }
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", vectorSize);
        vectors.put("distance", "Cosine");
        request("PUT", path, Map.of("vectors", vectors), true);
    }

    private String request(String method, String path, Object body, boolean requireSuccess) throws MemoryException {
        try {
            Request request = buildRequest(method, path, body);
            try (Response response = httpClient.newCall(request).execute()) {
                String text = readBody(response);
                if (requireSuccess && !response.isSuccessful()) {
                    throw new MemoryException("Qdrant 请求失败: HTTP " + response.code() + " " + text);
                }
                return text;
            }
        } catch (MemoryException e) {
            throw e;
        } catch (Exception e) {
            throw new MemoryException("Qdrant 请求异常", e);
        }
    }

    private int requestCode(String method, String path, Object body) throws MemoryException {
        try {
            Request request = buildRequest(method, path, body);
            try (Response response = httpClient.newCall(request).execute()) {
                return response.code();
            }
        } catch (Exception e) {
            throw new MemoryException("Qdrant 请求异常", e);
        }
    }

    private Request buildRequest(String method, String path, Object body) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl() + path)
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("api-key", "Bearer " + apiKey);
        }
        RequestBody requestBody = body == null ? null : RequestBody.create(objectMapper.writeValueAsString(body), JSON);
        return builder.method(method, requestBody).build();
    }

    private String baseUrl() {
        String baseUrl = config.qdrantBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String readBody(Response response) throws Exception {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private String collectionPath(String workspaceId) {
        return "/collections/" + WorkspaceId.collectionName(config.qdrantCollectionPrefix(), workspaceId);
    }

    private Map<String, Object> payload(MemoryEntry entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entry.id());
        payload.put("workspaceId", entry.workspaceId());
        payload.put("type", entry.type().name());
        payload.put("role", entry.role().name());
        payload.put("content", entry.content());
        payload.put("metadata", entry.metadata());
        payload.put("createdAt", entry.createdAt().toString());
        payload.put("updatedAt", entry.updatedAt().toString());
        payload.put("lastAccessedAt", entry.lastAccessedAt() == null ? null : entry.lastAccessedAt().toString());
        payload.put("accessCount", entry.accessCount());
        payload.put("importance", entry.importance());
        payload.put("tokenCount", entry.tokenCount());
        payload.put("byteCount", entry.byteCount());
        return payload;
    }

    private List<RetrievedMemory> parseSearch(String json) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) root.get("result");
        if (result == null) {
            return List.of();
        }
        List<RetrievedMemory> memories = new ArrayList<>();
        for (Map<String, Object> item : result) {
            double score = ((Number) item.getOrDefault("score", 0.0d)).doubleValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) item.get("payload");
            if (payload != null) {
                memories.add(new RetrievedMemory(entryFromPayload(payload), score, 0.0d));
            }
        }
        return memories;
    }

    private MemoryEntry entryFromPayload(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = payload.get("metadata") instanceof Map<?, ?> map
                ? (Map<String, String>) map
                : Map.of();
        Object lastAccessed = payload.get("lastAccessedAt");
        return new MemoryEntry(
                payload.get("id").toString(),
                payload.get("workspaceId").toString(),
                MemoryRole.valueOf(payload.get("role").toString()),
                MemoryType.valueOf(payload.get("type").toString()),
                payload.get("content").toString(),
                metadata,
                Instant.parse(payload.get("createdAt").toString()),
                Instant.parse(payload.get("updatedAt").toString()),
                lastAccessed == null ? null : Instant.parse(lastAccessed.toString()),
                ((Number) payload.getOrDefault("accessCount", 0)).intValue(),
                ((Number) payload.getOrDefault("importance", 0.5d)).doubleValue(),
                ((Number) payload.getOrDefault("tokenCount", 0)).intValue(),
                ((Number) payload.getOrDefault("byteCount", 0)).intValue());
    }
}
