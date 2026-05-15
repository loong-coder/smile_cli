package com.github.loong.tools.util;

import com.github.loong.tools.ToolDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import junit.framework.TestCase;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class SwaggerToolDescriptionParserTest extends TestCase {

    public void testParseMethodToToolDescription() throws Exception {
        Method method = TestTools.class.getDeclaredMethod("getWeatherData", String.class);

        ToolDefinition tool = SwaggerToolDescriptionParser.parse(method);

        assertEquals("get_weather_data", tool.name());
        assertEquals("Weather Data Retriever", tool.title());
        assertEquals("Get current weather data for a location", tool.description());

        Map<String, Object> inputSchema = tool.inputSchema();
        assertEquals("object", inputSchema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputProperties = (Map<String, Object>) inputSchema.get("properties");
        assertJsonType("string", property(inputProperties, "location").get("type"));
        assertEquals("City name or zip code", property(inputProperties, "location").get("description"));
        assertEquals(List.of("location"), inputSchema.get("required"));

        Map<String, Object> outputSchema = tool.outputSchema();
        assertEquals("object", outputSchema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> outputProperties = (Map<String, Object>) outputSchema.get("properties");
        assertJsonType("number", property(outputProperties, "temperature").get("type"));
        assertEquals("Temperature in celsius", property(outputProperties, "temperature").get("description"));
        assertJsonType("string", property(outputProperties, "conditions").get("type"));
        assertEquals("Weather conditions description", property(outputProperties, "conditions").get("description"));
        assertJsonType("number", property(outputProperties, "humidity").get("type"));
        assertEquals("Humidity percentage", property(outputProperties, "humidity").get("description"));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) outputSchema.get("required");
        assertEquals(3, required.size());
        assertTrue(required.contains("temperature"));
        assertTrue(required.contains("conditions"));
        assertTrue(required.contains("humidity"));
    }

    public void testToMapUsesDirectToolDescriptionShape() throws Exception {
        Method method = TestTools.class.getDeclaredMethod("getWeatherData", String.class);

        Map<String, Object> map = SwaggerToolDescriptionParser.parse(method).toMap();

        assertEquals("get_weather_data", map.get("name"));
        assertEquals("Weather Data Retriever", map.get("title"));
        assertEquals("Get current weather data for a location", map.get("description"));
        assertTrue(map.containsKey("inputSchema"));
        assertTrue(map.containsKey("outputSchema"));
        assertFalse(map.containsKey("type"));
        assertFalse(map.containsKey("function"));
    }

    public void testVoidMethodHasNoOutputSchema() throws Exception {
        Method method = TestTools.class.getDeclaredMethod("search", String.class, int.class, boolean.class, Priority.class);

        ToolDefinition tool = SwaggerToolDescriptionParser.parse(method);
        Map<String, Object> map = tool.toMap();

        assertNull(tool.outputSchema());
        assertFalse(map.containsKey("outputSchema"));
    }

    public void testBuildArrayAndCollectionSchemas() throws Exception {
        Method method = TestTools.class.getDeclaredMethod("batch", String[].class, List.class);

        ToolDefinition tool = SwaggerToolDescriptionParser.parse(method);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");

        Map<String, Object> ids = property(properties, "ids");
        assertJsonType("array", ids.get("type"));
        assertJsonType("string", property(ids, "items").get("type"));

        Map<String, Object> scores = property(properties, "scores");
        assertJsonType("array", scores.get("type"));
        assertJsonType("integer", property(scores, "items").get("type"));
    }

    public void testBuildObjectSchemaWithSwaggerFieldMetadata() throws Exception {
        Method method = TestTools.class.getDeclaredMethod("configure", SearchOptions.class);

        ToolDefinition tool = SwaggerToolDescriptionParser.parse(method);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");
        Map<String, Object> options = property(properties, "options");

        assertJsonType("object", options.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> optionProperties = (Map<String, Object>) options.get("properties");
        assertJsonType("string", property(optionProperties, "keyword").get("type"));
        assertEquals("Filter keyword", property(optionProperties, "keyword").get("description"));
        assertJsonType("integer", property(optionProperties, "limit").get("type"));
    }

    public void testParseClassPublicDeclaredMethods() {
        List<ToolDefinition> tools = SwaggerToolDescriptionParser.parse(TestTools.class);

        assertEquals(4, tools.size());
        assertEquals("batch", tools.get(0).name());
        assertEquals("configure", tools.get(1).name());
        assertEquals("get_weather_data", tools.get(2).name());
        assertEquals("search_docs", tools.get(3).name());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> property(Map<String, Object> properties, String name) {
        return (Map<String, Object>) properties.get(name);
    }

    private void assertJsonType(String expected, Object actual) {
        if (actual instanceof List<?>) {
            assertTrue(((List<?>) actual).contains(expected));
        } else {
            assertEquals(expected, actual);
        }
    }

    private enum Priority {
        LOW,
        HIGH
    }

    private static class TestTools {

        @Schema(name = "get_weather_data", title = "Weather Data Retriever", description = "Get current weather data for a location")
        public WeatherData getWeatherData(
                @Schema(name = "location", description = "City name or zip code", requiredMode = Schema.RequiredMode.REQUIRED) String location) {
            return new WeatherData();
        }

        @Schema(name = "search_docs", description = "Search documents")
        public void search(
                @Schema(name = "query", description = "Search query", requiredMode = Schema.RequiredMode.REQUIRED) String query,
                @Schema(name = "limit") int limit,
                @Schema(name = "strict") boolean strict,
                @Schema(name = "priority") Priority priority) {
        }

        public void batch(@Schema(name = "ids") String[] ids,
                          @Schema(name = "scores") List<Integer> scores) {
        }

        public void configure(@Schema(name = "options") SearchOptions options) {
        }

        void hidden() {
        }
    }

    private static class WeatherData {

        @Schema(description = "Temperature in celsius", requiredMode = Schema.RequiredMode.REQUIRED)
        public double temperature;

        @Schema(description = "Weather conditions description", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        public String conditions;

        @Schema(description = "Humidity percentage", requiredMode = Schema.RequiredMode.REQUIRED)
        public double humidity;
    }

    private static class SearchOptions {

        @Schema(description = "Filter keyword")
        public String keyword;

        public int limit;
    }
}
