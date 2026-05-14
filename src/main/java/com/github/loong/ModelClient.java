package com.github.loong;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ModelClient implements AutoCloseable {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final ConfigManager config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile EventSource activeEventSource;

    public ModelClient(ConfigManager config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void chat(List<Map<String, String>> messages,
                     Consumer<String> onToken,
                     Consumer<String> onError) throws Exception {

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY environment variable is not set");
        }

        Map<String, Object> body = Map.of(
                "model", config.getModelName(),
                "messages", messages,
                "stream", true
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        CountDownLatch latch = new CountDownLatch(1);

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if ("[DONE]".equals(data.trim())) {
                    latch.countDown();
                    return;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                        if (delta != null) {
                            Object content = delta.get("content");
                            if (content != null) {
                                onToken.accept(content.toString());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: failed to parse SSE chunk: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                if (t != null) {
                    onError.accept(t.getMessage());
                } else if (response != null) {
                    onError.accept("HTTP " + response.code() + " " + response.message());
                } else {
                    onError.accept("Stream connection failed");
                }
                latch.countDown();
            }

            @Override
            public void onClosed(EventSource eventSource) {
                latch.countDown();
            }
        };

        EventSource.Factory factory = EventSources.createFactory(httpClient);
        activeEventSource = factory.newEventSource(request, listener);
        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                throw new RuntimeException("Chat stream timed out after 5 minutes");
            }
        } finally {
            activeEventSource.cancel();
        }
    }

    public void cancel() {
        if (activeEventSource != null) {
            activeEventSource.cancel();
        }
    }

    @Override
    public void close() {
        cancel();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
