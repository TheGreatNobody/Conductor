package com.ryan.demo.conductor.advanced.eventsourcing;

import com.ryan.demo.conductor.basic.Command;
import com.ryan.demo.conductor.basic.CommandResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 事件溯源命令 - EventSourcingCommand
 * 支援事件溯源模式的命令基類
 */
public abstract class EventSourcingCommand implements Command {

    private final String commandId;
    private final String aggregateId;
    private final LocalDateTime timestamp;
    private final String userId;
    private final Long expectedVersion;

    protected EventSourcingCommand(String aggregateId) {
        this(aggregateId, null, null);
    }

    protected EventSourcingCommand(String aggregateId, String userId, Long expectedVersion) {
        this.commandId = UUID.randomUUID().toString();
        this.aggregateId = aggregateId;
        this.timestamp = LocalDateTime.now();
        this.userId = userId;
        this.expectedVersion = expectedVersion;
    }

    @Override
    public final CommandResult execute() {
        try {
            // 驗證命令
            ValidationResult validation = validate();
            if (!validation.isValid()) {
                return CommandResult.failure("Command validation failed: " + validation.getErrorMessage(),
                                "VALIDATION_ERROR")
                        .build();
            }

            // 執行命令並產生事件
            List<DomainEvent> events = executeCommand();

            if (events == null || events.isEmpty()) {
                return CommandResult.failure("No events generated from command execution", "NO_EVENTS")
                        .build();
            }

            // 儲存事件到事件庫
            EventStore eventStore = getEventStore();
            eventStore.saveEvents(aggregateId, events, expectedVersion);
            return CommandResult
                    .success("Command executed successfully, " + events.size() + " events generated")
                    .data(events)
                    .metadata("aggregateId", aggregateId)
                    .metadata("eventCount", events.size())
                    .metadata("version", eventStore.getAggregateVersion(aggregateId))
                    .build();

        } catch (ConcurrencyException e) {
            return CommandResult.failure("Concurrency conflict: " + e.getMessage(), "CONCURRENCY_ERROR")
                    .build();
        } catch (AggregateNotFoundException e) {
            return CommandResult.failure("Aggregate not found: " + e.getMessage(), "AGGREGATE_NOT_FOUND")
                    .build();
        } catch (Exception e) {
            return CommandResult.failure("Command execution failed: " + e.getMessage(), "EXECUTION_ERROR")
                    .build();
        }
    }

    @Override
    public final CommandResult undo() {
        // 事件溯源模式下，撤銷通常是通過產生補償事件來實現
        try {
            List<DomainEvent> compensatingEvents = generateCompensatingEvents();

            if (compensatingEvents == null || compensatingEvents.isEmpty()) {
                return CommandResult.failure("No compensating events available for undo", "NO_COMPENSATION")
                        .build();
            }

            EventStore eventStore = getEventStore();
            eventStore.saveEvents(aggregateId, compensatingEvents, null);

            return CommandResult.success("Command undone successfully via compensating events")
                    .data(compensatingEvents)
                    .metadata("compensatingEventCount", compensatingEvents.size())
                    .build();

        } catch (Exception e) {
            return CommandResult.failure("Undo failed: " + e.getMessage(), "UNDO_ERROR")
                    .build();
        }
    }

    /**
     * 驗證命令有效性
     */
    protected abstract ValidationResult validate();

    /**
     * 執行具體的命令邏輯並產生領域事件
     */
    protected abstract List<DomainEvent> executeCommand() throws Exception;

    /**
     * 產生補償事件用於撤銷操作
     */
    protected abstract List<DomainEvent> generateCompensatingEvents();

    /**
     * 獲取事件庫實例
     */
    protected abstract EventStore getEventStore();

    // Getters
    @Override
    public String getCommandId() {
        return commandId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    /**
     * 驗證結果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 領域事件介面
     */
    public interface DomainEvent {
        String getEventId();
        String getAggregateId();
        String getEventType();
        LocalDateTime getTimestamp();
        Object getEventData();
        Long getVersion();
    }

    /**
     * 事件庫介面
     */
    public interface EventStore {
        void saveEvents(String aggregateId, List<DomainEvent> events, Long expectedVersion)
                throws ConcurrencyException;

        List<DomainEvent> getEvents(String aggregateId);
        List<DomainEvent> getEvents(String aggregateId, Long fromVersion);
        Long getAggregateVersion(String aggregateId);
        boolean aggregateExists(String aggregateId);
    }

    /**
     * 並發異常
     */
    public static class ConcurrencyException extends Exception {
        public ConcurrencyException(String message) {
            super(message);
        }
    }

    /**
     * 聚合根未找到異常
     */
    public static class AggregateNotFoundException extends Exception {
        public AggregateNotFoundException(String message) {
            super(message);
        }
    }
}