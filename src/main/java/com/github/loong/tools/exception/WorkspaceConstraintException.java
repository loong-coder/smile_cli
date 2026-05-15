package com.github.loong.tools.exception;

import java.io.IOException;

/**
 * 工作区边界约束异常，表示请求的路径超出了允许的工作区范围。
 * 与普通 IO 异常区分，便于调用方识别越界状态而非系统错误。
 */
public class WorkspaceConstraintException extends IOException {

    private final String requestedPath;

    public WorkspaceConstraintException(String requestedPath) {
        super("path is outside workspace: " + requestedPath);
        this.requestedPath = requestedPath;
    }

    /**
     * 触发越界的路径原始值。
     */
    public String requestedPath() {
        return requestedPath;
    }
}
