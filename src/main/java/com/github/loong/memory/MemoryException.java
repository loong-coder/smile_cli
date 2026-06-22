package com.github.loong.memory;

/**
 * 记忆系统受控异常，上层捕获后应降级而不是中断聊天。
 */
public class MemoryException extends Exception {

    public MemoryException(String message) {
        super(message);
    }

    public MemoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
