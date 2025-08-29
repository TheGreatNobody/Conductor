package com.ryan.demo.conductor.basic;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 命令執行結果
 * 封裝命令執行的結果資訊
 */
public class CommandResult {

    private final boolean success;
    private final String message;
    private final Object data;
    private final String errorCode;
    private final LocalDateTime timestamp;
    private final Map<String, Object> metadata;

    private CommandResult(Builder builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.data = builder.data;
        this.errorCode = builder.errorCode;
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>(builder.metadata);
    }

    public static Builder success() {
        return new Builder(true);
    }

    public static Builder success(String message) {
        return new Builder(true).message(message);
    }

    public static Builder failure() {
        return new Builder(false);
    }

    public static Builder failure(String message) {
        return new Builder(false).message(message);
    }

    public static Builder failure(String message, String errorCode) {
        return new Builder(false).message(message).errorCode(errorCode);
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(Class<T> type) {
        return (T) data;
    }

    @Override
    public String toString() {
        return "CommandResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", data=" + data +
                ", errorCode='" + errorCode + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static class Builder {
        private final boolean success;
        private String message;
        private Object data;
        private String errorCode;
        private final Map<String, Object> metadata = new HashMap<>();

        private Builder(boolean success) {
            this.success = success;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public CommandResult build() {
            return new CommandResult(this);
        }
    }
}