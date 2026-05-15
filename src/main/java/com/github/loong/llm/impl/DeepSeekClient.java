package com.github.loong.llm.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.loong.config.LlmConfig;
import com.github.loong.llm.ChatResult;
import com.github.loong.llm.LLmClient;
import com.github.loong.message.AssistantMessage;
import com.github.loong.message.Message;
import com.github.loong.tool.ToolDefinition;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DeepSeekClient implements LLmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepSeekClient.class);
    private static final MediaType JSON = MediaType.parse("application/json");

    private final LlmConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile EventSource activeEventSource;

    public DeepSeekClient(LlmConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ChatResult chat(List<Message> messages,
                           List<ToolDefinition> tools,
                           Consumer<String> onToken,
                           Consumer<String> onError) throws Exception {

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY environment variable is not set");
        }

        Map<String, Object> body = buildRequestBody(config.getModelName(), messages, tools);
        String jsonBody = objectMapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        StreamAccumulator accumulator = new StreamAccumulator();

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if ("[DONE]".equals(data.trim())) {
                    latch.countDown();
                    return;
                }
                try {
                    String token = accumulator.accept(data);
                    if (token != null && !token.isEmpty()) {
                        onToken.accept(token);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to parse DeepSeek SSE chunk", e);
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                if (t != null) {
                    LOGGER.error("DeepSeek stream connection failed", t);
                    onError.accept(t.getMessage());
                } else if (response != null) {
                    String responseBody = responseBodyText(response);
                    LOGGER.error("DeepSeek stream failed: HTTP {} {} {}", response.code(), response.message(), responseBody);
                    onError.accept("HTTP " + response.code() + " " + response.message());
                } else {
                    LOGGER.error("DeepSeek stream connection failed");
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
        return accumulator.result();
    }

    static String responseBodyText(Response response) {
        // 记录 DeepSeek 返回体，方便定位 HTTP 400 等接口错误。
        ResponseBody body = response.body();
        if (body == null) {
            return "";
        }
        try {
            return body.string();
        } catch (Exception e) {
            LOGGER.error("Failed to read DeepSeek error response body", e);
            return "";
        }
    }

    static Map<String, Object> buildRequestBody(String model, List<Message> messages, List<ToolDefinition> tools) {
        List<Map<String, Object>> msgMaps = new ArrayList<>();
        for (Message msg : messages) {
            msgMaps.add(msg.toMap());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", msgMaps);
        body.put("stream", true);

        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolMaps = new ArrayList<>();
            for (ToolDefinition tool : tools) {
                toolMaps.add(toDeepSeekToolMap(tool));
            }
            body.put("tools", toolMaps);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    static Map<String, Object> toDeepSeekToolMap(ToolDefinition tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", tool.inputSchema());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "function");
        map.put("function", function);
        return map;
    }

    static ChatResult parseChunks(List<String> chunks) throws Exception {
        StreamAccumulator accumulator = new StreamAccumulator();
        for (String chunk : chunks) {
            accumulator.accept(chunk);
        }
        return accumulator.result();
    }

    @Override
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

    private static class StreamAccumulator {

        private final ObjectMapper objectMapper = new ObjectMapper();
        private final StringBuilder content = new StringBuilder();
        private final Map<Integer, ToolCallBuilder> toolCalls = new LinkedHashMap<>();
        private String finishReason;

        String accept(String data) throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }

            Map<String, Object> choice = choices.get(0);
            Object finishReasonValue = choice.get("finish_reason");
            if (finishReasonValue != null) {
                finishReason = finishReasonValue.toString();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
            if (delta == null) {
                return null;
            }

            String token = null;
            Object contentValue = delta.get("content");
            if (contentValue != null) {
                token = contentValue.toString();
                content.append(token);
            }
            collectToolCalls(delta);
            return token;
        }

        private void collectToolCalls(Map<String, Object> delta) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> calls = (List<Map<String, Object>>) delta.get("tool_calls");
            if (calls == null) {
                return;
            }

            for (Map<String, Object> call : calls) {
                int index = ((Number) call.getOrDefault("index", 0)).intValue();
                ToolCallBuilder builder = toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder());

                Object id = call.get("id");
                if (id != null) {
                    builder.id = id.toString();
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) call.get("function");
                if (function != null) {
                    Object name = function.get("name");
                    if (name != null) {
                        builder.name = name.toString();
                    }
                    Object arguments = function.get("arguments");
                    if (arguments != null) {
                        builder.arguments.append(arguments);
                    }
                }
            }
        }

        ChatResult result() {
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            for (ToolCallBuilder builder : toolCalls.values()) {
                calls.add(new AssistantMessage.ToolCall(builder.id, builder.name, builder.arguments.toString()));
            }
            return new ChatResult(content.toString(), calls, finishReason);
        }
    }

    private static class ToolCallBuilder {

        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}