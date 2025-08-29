package com.ryan.demo.conductor.advanced.eventsourcing;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 記憶體事件庫 - InMemoryEventStore
 * 事件溯源模式的記憶體實作
 */
@Component
public class InMemoryEventStore implements EventSourcingCommand.EventStore {

    private final Map<String, List<StoredEvent>> eventStreams = new ConcurrentHashMap<>();
    private final Map<String, Long> aggregateVersions = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void saveEvents(String aggregateId, List<EventSourcingCommand.DomainEvent> events, Long expectedVersion)
            throws EventSourcingCommand.ConcurrencyException {

        lock.lock();
        try {
            // 檢查並發控制
            if (expectedVersion != null) {
                Long currentVersion = aggregateVersions.get(aggregateId);
                if (currentVersion != null && !currentVersion.equals(expectedVersion)) {
                    throw new EventSourcingCommand.ConcurrencyException(
                            String.format("Concurrency conflict for aggregate %s: expected version %d, actual version %d",
                                    aggregateId, expectedVersion, currentVersion));
                }
            }

            // 獲取或建立事件流
            List<StoredEvent> eventStream = eventStreams.computeIfAbsent(aggregateId, k -> new ArrayList<>());

            // 儲存事件
            for (EventSourcingCommand.DomainEvent event : events) {
                StoredEvent storedEvent = new StoredEvent(
                        event.getEventId(),
                        event.getAggregateId(),
                        event.getEventType(),
                        event.getEventData(),
                        event.getVersion(),
                        event.getTimestamp()
                );

                eventStream.add(storedEvent);
                aggregateVersions.put(aggregateId, event.getVersion());
            }

        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<EventSourcingCommand.DomainEvent> getEvents(String aggregateId) {
        return getEvents(aggregateId, null);
    }

    @Override
    public List<EventSourcingCommand.DomainEvent> getEvents(String aggregateId, Long fromVersion) {
        List<StoredEvent> eventStream = eventStreams.get(aggregateId);
        if (eventStream == null) {
            return new ArrayList<>();
        }

        return eventStream.stream()
                .filter(event -> fromVersion == null || event.getVersion() > fromVersion)
                .map(event -> (EventSourcingCommand.DomainEvent) event)
                .toList();
    }

    @Override
    public Long getAggregateVersion(String aggregateId) {
        return aggregateVersions.getOrDefault(aggregateId, 0L);
    }

    @Override
    public boolean aggregateExists(String aggregateId) {
        return eventStreams.containsKey(aggregateId) && !eventStreams.get(aggregateId).isEmpty();
    }

    /**
     * 獲取所有聚合ID
     */
    public Set<String> getAllAggregateIds() {
        return new HashSet<>(eventStreams.keySet());
    }

    /**
     * 獲取事件統計資訊
     */
    public EventStoreStatistics getStatistics() {
        lock.lock();
        try {
            int totalAggregates = eventStreams.size();
            int totalEvents = eventStreams.values().stream()
                    .mapToInt(List::size)
                    .sum();

            Map<String, Integer> eventTypeStats = new HashMap<>();
            for (List<StoredEvent> eventStream : eventStreams.values()) {
                for (StoredEvent event : eventStream) {
                    eventTypeStats.merge(event.getEventType(), 1, Integer::sum);
                }
            }

            return new EventStoreStatistics(totalAggregates, totalEvents, eventTypeStats);

        } finally {
            lock.unlock();
        }
    }

    /**
     * 清空所有事件
     */
    public void clearAll() {
        lock.lock();
        try {
            eventStreams.clear();
            aggregateVersions.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 刪除指定聚合的所有事件
     */
    public boolean deleteAggregate(String aggregateId) {
        lock.lock();
        try {
            boolean existed = eventStreams.containsKey(aggregateId);
            eventStreams.remove(aggregateId);
            aggregateVersions.remove(aggregateId);
            return existed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 儲存的事件
     */
    private static class StoredEvent implements EventSourcingCommand.DomainEvent {
        private final String eventId;
        private final String aggregateId;
        private final String eventType;
        private final Object eventData;
        private final Long version;
        private final java.time.LocalDateTime timestamp;

        public StoredEvent(String eventId, String aggregateId, String eventType,
                           Object eventData, Long version, java.time.LocalDateTime timestamp) {
            this.eventId = eventId;
            this.aggregateId = aggregateId;
            this.eventType = eventType;
            this.eventData = eventData;
            this.version = version;
            this.timestamp = timestamp;
        }

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public String getAggregateId() {
            return aggregateId;
        }

        @Override
        public String getEventType() {
            return eventType;
        }

        @Override
        public java.time.LocalDateTime getTimestamp() {
            return timestamp;
        }

        @Override
        public Object getEventData() {
            return eventData;
        }

        @Override
        public Long getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return "StoredEvent{" +
                    "eventId='" + eventId + '\'' +
                    ", aggregateId='" + aggregateId + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", version=" + version +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * 事件庫統計資訊
     */
    public static class EventStoreStatistics {
        private final int totalAggregates;
        private final int totalEvents;
        private final Map<String, Integer> eventTypeStats;

        public EventStoreStatistics(int totalAggregates, int totalEvents, Map<String, Integer> eventTypeStats) {
            this.totalAggregates = totalAggregates;
            this.totalEvents = totalEvents;
            this.eventTypeStats = new HashMap<>(eventTypeStats);
        }

        public int getTotalAggregates() {
            return totalAggregates;
        }

        public int getTotalEvents() {
            return totalEvents;
        }

        public Map<String, Integer> getEventTypeStats() {
            return eventTypeStats;
        }

        public double getAverageEventsPerAggregate() {
            return totalAggregates > 0 ? (double) totalEvents / totalAggregates : 0.0;
        }

        @Override
        public String toString() {
            return "EventStoreStatistics{" +
                    "totalAggregates=" + totalAggregates +
                    ", totalEvents=" + totalEvents +
                    ", averageEventsPerAggregate=" + String.format("%.2f", getAverageEventsPerAggregate()) +
                    ", eventTypeStats=" + eventTypeStats +
                    '}';
        }
    }
}