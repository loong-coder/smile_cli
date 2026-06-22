package com.github.loong.memory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * 根据工作区路径生成稳定短 ID，用于隔离不同项目的长期记忆。
 */
public final class WorkspaceId {

    private WorkspaceId() {
    }

    public static String fromPath(Path workspacePath) {
        try {
            String normalized = workspacePath.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成工作区记忆 ID", e);
        }
    }

    public static String collectionName(String prefix, String workspaceId) {
        return prefix + "_" + workspaceId;
    }
}
