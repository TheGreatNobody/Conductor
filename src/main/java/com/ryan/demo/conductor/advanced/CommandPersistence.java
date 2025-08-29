package com.ryan.demo.conductor.advanced;

import com.ryan.demo.conductor.basic.Command;
import com.ryan.demo.conductor.basic.CommandResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 命令持久化 - CommandPersistence
 * 負責命令的持久化存儲和回放
 */
@Component
public class CommandPersistence {

    private final ObjectMapper objectMapper;
    private final Map<String, CommandRecord> commandStore = new HashMap<>();

    public CommandPersistence() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 儲存命令執行記錄
     */
    public void saveCommandExecution(Command command, CommandResult result) {
        try {
            CommandRecord record = new CommandRecord();
            record.setId(command.getCommandId());
            record.setCommandType(command.getClass().getSimpleName());
            record.setDescription(command.getDescription());
            record.setCommandData(serializeCommand(command));
            record.setResult(serializeResult(result));
            record.setSuccess(result.isSuccess());
            record.setExecutedAt(LocalDateTime.now());

            commandStore.put(record.getId(), record);

        } catch (Exception e) {
            throw new RuntimeException("Failed to save command execution: " + e.getMessage(), e);
        }
    }

    /**
     * 獲取命令執行記錄
     */
    public Optional<CommandRecord> getCommandRecord(String commandId) {
        return Optional.ofNullable(commandStore.get(commandId));
    }

    /**
     * 獲取所有命令記錄
     */
    public List<CommandRecord> getAllCommandRecords() {
        return new ArrayList<>(commandStore.values());
    }

    /**
     * 根據時間範圍查詢命令記錄
     */
    public List<CommandRecord> getCommandRecordsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return commandStore.values().stream()
                .filter(record -> {
                    LocalDateTime executedAt = record.getExecutedAt();
                    return (start == null || executedAt.isAfter(start)) &&
                            (end == null || executedAt.isBefore(end));
                })
                .sorted(Comparator.comparing(CommandRecord::getExecutedAt))
                .toList();
    }

    /**
     * 根據成功狀態查詢命令記錄
     */
    public List<CommandRecord> getCommandRecordsByStatus(boolean success) {
        return commandStore.values().stream()
                .filter(record -> record.isSuccess() == success)
                .sorted(Comparator.comparing(CommandRecord::getExecutedAt))
                .toList();
    }

    /**
     * 獲取命令統計資訊
     */
    public CommandStatistics getCommandStatistics() {
        List<CommandRecord> allRecords = getAllCommandRecords();

        long totalCommands = allRecords.size();
        long successfulCommands = allRecords.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        long failedCommands = totalCommands - successfulCommands;

        Map<String, Long> commandTypeStats = allRecords.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CommandRecord::getCommandType,
                        java.util.stream.Collectors.counting()
                ));

        OptionalDouble avgExecutionTime = allRecords.stream()
                .filter(r -> r.getExecutionTimeMs() != null)
                .mapToLong(CommandRecord::getExecutionTimeMs)
                .average();

        return new CommandStatistics(
                totalCommands,
                successfulCommands,
                failedCommands,
                commandTypeStats,
                avgExecutionTime.orElse(0.0)
        );
    }

    /**
     * 刪除過期的命令記錄
     */
    public int cleanupExpiredRecords(LocalDateTime before) {
        List<String> expiredIds = commandStore.values().stream()
                .filter(record -> record.getExecutedAt().isBefore(before))
                .map(CommandRecord::getId)
                .toList();

        for (String id : expiredIds) {
            commandStore.remove(id);
        }

        return expiredIds.size();
    }

    /**
     * 清空所有命令記錄
     */
    public void clearAllRecords() {
        commandStore.clear();
    }

    /**
     * 序列化命令
     */
    private String serializeCommand(Command command) throws JsonProcessingException {
        Map<String, Object> commandData = new HashMap<>();
        commandData.put("commandId", command.getCommandId());
        commandData.put("description", command.getDescription());
        commandData.put("className", command.getClass().getName());

        // 可以根據具體命令類型添加更多資料
        return objectMapper.writeValueAsString(commandData);
    }

    /**
     * 序列化執行結果
     */
    private String serializeResult(CommandResult result) throws JsonProcessingException {
        return objectMapper.writeValueAsString(result);
    }

    /**
     * 反序列化執行結果
     */
    private CommandResult deserializeResult(String resultJson) throws JsonProcessingException {
        return objectMapper.readValue(resultJson, CommandResult.class);
    }

    /**
     * 命令記錄實體
     */
    @Entity
    @Table(name = "command_records")
    public static class CommandRecord {

        @Id
        private String id;

        @Column(name = "command_type", nullable = false)
        private String commandType;

        @Column(name = "description")
        private String description;

        @Lob
        @Column(name = "command_data")
        private String commandData;

        @Lob
        @Column(name = "result")
        private String result;

        @Column(name = "success", nullable = false)
        private boolean success;

        @Column(name = "executed_at", nullable = false)
        private LocalDateTime executedAt;

        @Column(name = "execution_time_ms")
        private Long executionTimeMs;

        // Constructors
        public CommandRecord() {}

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getCommandType() { return commandType; }
        public void setCommandType(String commandType) { this.commandType = commandType; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getCommandData() { return commandData; }
        public void setCommandData(String commandData) { this.commandData = commandData; }

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public LocalDateTime getExecutedAt() { return executedAt; }
        public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

        public Long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

        @Override
        public String toString() {
            return "CommandRecord{" +
                    "id='" + id + '\'' +
                    ", commandType='" + commandType + '\'' +
                    ", description='" + description + '\'' +
                    ", success=" + success +
                    ", executedAt=" + executedAt +
                    '}';
        }
    }

    /**
     * 命令統計資訊
     */
    public static class CommandStatistics {
        private final long totalCommands;
        private final long successfulCommands;
        private final long failedCommands;
        private final Map<String, Long> commandTypeStats;
        private final double averageExecutionTimeMs;

        public CommandStatistics(long totalCommands, long successfulCommands, long failedCommands,
                                 Map<String, Long> commandTypeStats, double averageExecutionTimeMs) {
            this.totalCommands = totalCommands;
            this.successfulCommands = successfulCommands;
            this.failedCommands = failedCommands;
            this.commandTypeStats = new HashMap<>(commandTypeStats);
            this.averageExecutionTimeMs = averageExecutionTimeMs;
        }

        public long getTotalCommands() { return totalCommands; }
        public long getSuccessfulCommands() { return successfulCommands; }
        public long getFailedCommands() { return failedCommands; }
        public Map<String, Long> getCommandTypeStats() { return commandTypeStats; }
        public double getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
        public double getSuccessRate() {
            return totalCommands > 0 ? (double) successfulCommands / totalCommands * 100 : 0;
        }

        @Override
        public String toString() {
            return "CommandStatistics{" +
                    "totalCommands=" + totalCommands +
                    ", successfulCommands=" + successfulCommands +
                    ", failedCommands=" + failedCommands +
                    ", successRate=" + String.format("%.2f%%", getSuccessRate()) +
                    ", averageExecutionTimeMs=" + averageExecutionTimeMs +
                    '}';
        }
    }
}