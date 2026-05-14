package com.github.loong.message;

import java.util.Map;

public abstract class Message {

    public abstract String getRole();

    public abstract Map<String, Object> toMap();
}
