package com.github.loong.tools.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.loong.tools.ToolDefinition;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SwaggerToolDescriptionParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final SchemaGenerator TYPE_SCHEMA_GENERATOR;

    static {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new Swagger2Module())
                .with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
                .with(Option.STANDARD_FORMATS)
                .with(Option.PLAIN_DEFINITION_KEYS)
                .without(Option.SCHEMA_VERSION_INDICATOR);
        SchemaGeneratorConfig config = configBuilder.build();
        TYPE_SCHEMA_GENERATOR = new SchemaGenerator(config);
    }

    private SwaggerToolDescriptionParser() {
    }

    public static ToolDefinition parse(Method method) {
        if (method == null) {
            throw new IllegalArgumentException("method cannot be null");
        }

        Schema methodSchema = method.getAnnotation(Schema.class);
        String name = firstText(methodSchema == null ? null : methodSchema.name(), method.getName());
        String title = firstText(methodSchema == null ? null : methodSchema.title(), name);
        String description = firstText(methodSchema == null ? null : methodSchema.description(), method.getName());

        return new ToolDefinition(
                name,
                title,
                description,
                buildInputSchema(method),
                buildOutputSchema(method));
    }

    public static List<ToolDefinition> parse(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz cannot be null");
        }

        List<ToolDefinition> tools = new ArrayList<>();
        Method[] methods = clazz.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isBridge() && !method.isSynthetic()) {
                tools.add(parse(method));
            }
        }
        return tools;
    }

    private static Map<String, Object> buildInputSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        Parameter[] parameters = method.getParameters();
        Type[] parameterTypes = method.getGenericParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Schema parameterSchema = parameter.getAnnotation(Schema.class);
            String parameterName = resolveParameterName(parameter, parameterSchema, i);

            Map<String, Object> property = jsonNodeToMap(TYPE_SCHEMA_GENERATOR.generateSchema(parameterTypes[i]));
            String parameterDescription = parameterSchema == null ? null : parameterSchema.description();
            if (hasText(parameterDescription)) {
                property.put("description", parameterDescription);
            }
            properties.put(parameterName, property);

            if (isRequired(parameterSchema)) {
                required.add(parameterName);
            }
        }

        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static Map<String, Object> buildOutputSchema(Method method) {
        if (method.getReturnType() == void.class) {
            return null;
        }
        return jsonNodeToMap(TYPE_SCHEMA_GENERATOR.generateSchema(method.getGenericReturnType()));
    }

    private static String resolveParameterName(Parameter parameter, Schema schema, int index) {
        if (schema != null && hasText(schema.name())) {
            return schema.name();
        }
        if (parameter.isNamePresent() && hasText(parameter.getName())) {
            return parameter.getName();
        }
        return "arg" + index;
    }

    @SuppressWarnings("deprecation")
    private static boolean isRequired(Schema schema) {
        if (schema == null) {
            return false;
        }
        return schema.requiredMode() == Schema.RequiredMode.REQUIRED || schema.required();
    }

    private static Map<String, Object> jsonNodeToMap(ObjectNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            map.put(field.getKey(), jsonNodeToObject(field.getValue()));
        }
        return map;
    }

    private static Object jsonNodeToObject(JsonNode node) {
        return OBJECT_MAPPER.convertValue(node, Object.class);
    }

    private static String firstText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
