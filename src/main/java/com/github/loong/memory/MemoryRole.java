package com.github.loong.memory;

/**
 * 记忆来源角色，对应聊天消息角色。
 */
public enum MemoryRole {
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
    SYSTEM("system");

    private final String apiRole;

    MemoryRole(String apiRole) {
        this.apiRole = apiRole;
    }

    public String apiRole() {
        return apiRole;
    }
}
