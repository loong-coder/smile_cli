package com.github.loong.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Map;

// 将 Java 方法适配为工具执行器，避免注册表直接承载工具调用细节。
class ReflectiveToolExecutor implements ToolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectiveToolExecutor.class);

    private final Object target;
    private final Method method;
    private final ObjectMapper objectMapper;

    ReflectiveToolExecutor(Object target, Method method) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }
        if (method == null) {
            throw new IllegalArgumentException("method cannot be null");
        }
        this.target = target;
        this.method = method;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Object execute(Map<String, Object> arguments) throws Exception {
        Parameter[] parameters = method.getParameters();
        Type[] parameterTypes = method.getGenericParameterTypes();
        Object[] values = new Object[parameters.length];
        TypeFactory typeFactory = objectMapper.getTypeFactory();

        for (int i = 0; i < parameters.length; i++) {
            String name = resolveParameterName(parameters[i], i);
            Object value = arguments.get(name);
            values[i] = objectMapper.convertValue(value, typeFactory.constructType(parameterTypes[i]));
        }

        method.setAccessible(true);
        try {
            return method.invoke(target, values);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            logInvocationFailure(arguments, cause);
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        } catch (Exception e) {
            logInvocationFailure(arguments, e);
            throw e;
        }
    }

    private void logInvocationFailure(Map<String, Object> arguments, Throwable e) {
        // 记录反射调用上下文，方便从日志定位具体工具方法和入参。
        LOGGER.error("Tool method invocation failed: methodName={}, arguments={}, errorType={}, errorMessage={}",
                method.getName(), arguments, e.getClass().getName(), e.getMessage(), e);
    }

    private String resolveParameterName(Parameter parameter, int index) {
        Schema schema = parameter.getAnnotation(Schema.class);
        if (schema != null && schema.name() != null && !schema.name().isBlank()) {
            return schema.name();
        }
        if (parameter.isNamePresent() && parameter.getName() != null && !parameter.getName().isBlank()) {
            return parameter.getName();
        }
        return "arg" + index;
    }
}