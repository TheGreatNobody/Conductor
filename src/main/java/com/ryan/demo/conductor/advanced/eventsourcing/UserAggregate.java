package com.ryan.demo.conductor.advanced.eventsourcing;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 使用者聚合根 - UserAggregate
 * 事件溯源模式中的使用者聚合根
 */
public class UserAggregate {

    private String userId;
    private String name;
    private String email;
    private Integer age;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
    private Long version;

    // 未提交的事件列表
    private final List<UserDomainEvent> uncommittedEvents = new ArrayList<>();

    /**
     * 從事件流重建聚合根
     */
    public static UserAggregate fromEvents(List<EventSourcingCommand.DomainEvent> events) {
        UserAggregate aggregate = new UserAggregate();

        for (EventSourcingCommand.DomainEvent event : events) {
            if (event instanceof UserDomainEvent userEvent) {
                aggregate.apply(userEvent);
            }
        }

        aggregate.markEventsAsCommitted();
        return aggregate;
    }

    /**
     * 建立新使用者
     */
    public static UserAggregate createUser(String userId, String name, String email, Integer age) {
        UserAggregate aggregate = new UserAggregate();

        UserCreatedEvent event = new UserCreatedEvent(userId, name, email, age);
        aggregate.apply(event);

        return aggregate;
    }

    /**
     * 更新使用者資訊
     */
    public void updateUserInfo(String name, String email, Integer age) {
        if (status == UserStatus.DELETED) {
            throw new IllegalStateException("Cannot update deleted user");
        }

        UserInfoUpdatedEvent event = new UserInfoUpdatedEvent(userId, name, email, age, version + 1);
        apply(event);
    }

    /**
     * 啟用使用者
     */
    public void activateUser() {
        if (status == UserStatus.ACTIVE) {
            throw new IllegalStateException("User is already active");
        }

        if (status == UserStatus.DELETED) {
            throw new IllegalStateException("Cannot activate deleted user");
        }

        UserActivatedEvent event = new UserActivatedEvent(userId, version + 1);
        apply(event);
    }

    /**
     * 停用使用者
     */
    public void deactivateUser() {
        if (status == UserStatus.INACTIVE) {
            throw new IllegalStateException("User is already inactive");
        }

        if (status == UserStatus.DELETED) {
            throw new IllegalStateException("Cannot deactivate deleted user");
        }

        UserDeactivatedEvent event = new UserDeactivatedEvent(userId, version + 1);
        apply(event);
    }

    /**
     * 刪除使用者
     */
    public void deleteUser() {
        if (status == UserStatus.DELETED) {
            throw new IllegalStateException("User is already deleted");
        }

        UserDeletedEvent event = new UserDeletedEvent(userId, version + 1);
        apply(event);
    }

    /**
     * 應用事件到聚合根
     */
    private void apply(UserDomainEvent event) {
        switch (event.getEventType()) {
            case "UserCreated" -> applyUserCreatedEvent((UserCreatedEvent) event);
            case "UserInfoUpdated" -> applyUserInfoUpdatedEvent((UserInfoUpdatedEvent) event);
            case "UserActivated" -> applyUserActivatedEvent((UserActivatedEvent) event);
            case "UserDeactivated" -> applyUserDeactivatedEvent((UserDeactivatedEvent) event);
            case "UserDeleted" -> applyUserDeletedEvent((UserDeletedEvent) event);
            default -> throw new IllegalArgumentException("Unknown event type: " + event.getEventType());
        }

        // 只有新事件才加入未提交列表
        if (!event.isCommitted()) {
            uncommittedEvents.add(event);
        }

        this.version = event.getVersion();
        this.lastModifiedAt = event.getTimestamp();
    }

    private void applyUserCreatedEvent(UserCreatedEvent event) {
        this.userId = event.getUserId();
        this.name = event.getName();
        this.email = event.getEmail();
        this.age = event.getAge();
        this.status = UserStatus.ACTIVE;
        this.createdAt = event.getTimestamp();
        this.version = 1L;
    }

    private void applyUserInfoUpdatedEvent(UserInfoUpdatedEvent event) {
        this.name = event.getName();
        this.email = event.getEmail();
        this.age = event.getAge();
    }

    private void applyUserActivatedEvent(UserActivatedEvent event) {
        this.status = UserStatus.ACTIVE;
    }

    private void applyUserDeactivatedEvent(UserDeactivatedEvent event) {
        this.status = UserStatus.INACTIVE;
    }

    private void applyUserDeletedEvent(UserDeletedEvent event) {
        this.status = UserStatus.DELETED;
    }

    /**
     * 獲取未提交的事件
     */
    public List<UserDomainEvent> getUncommittedEvents() {
        return new ArrayList<>(uncommittedEvents);
    }

    /**
     * 標記事件已提交
     */
    public void markEventsAsCommitted() {
        for (UserDomainEvent event : uncommittedEvents) {
            event.markAsCommitted();
        }
        uncommittedEvents.clear();
    }

    // Getters
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Integer getAge() { return age; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }
    public Long getVersion() { return version; }

    public enum UserStatus {
        ACTIVE, INACTIVE, DELETED
    }

    // 事件類別定義
    public abstract static class UserDomainEvent implements EventSourcingCommand.DomainEvent {
        private final String eventId;
        private final String userId;
        private final LocalDateTime timestamp;
        private final Long version;
        private boolean committed = false;

        protected UserDomainEvent(String userId, Long version) {
            this.eventId = UUID.randomUUID().toString();
            this.userId = userId;
            this.timestamp = LocalDateTime.now();
            this.version = version;
        }

        @Override
        public String getEventId() { return eventId; }

        @Override
        public String getAggregateId() { return userId; }

        public String getUserId() { return userId; }

        @Override
        public LocalDateTime getTimestamp() { return timestamp; }

        @Override
        public Long getVersion() { return version; }

        public boolean isCommitted() { return committed; }

        public void markAsCommitted() { this.committed = true; }
    }

    public static class UserCreatedEvent extends UserDomainEvent {
        private final String name;
        private final String email;
        private final Integer age;

        public UserCreatedEvent(String userId, String name, String email, Integer age) {
            super(userId, 1L);
            this.name = name;
            this.email = email;
            this.age = age;
        }

        @Override
        public String getEventType() { return "UserCreated"; }

        @Override
        public Object getEventData() {
            return Map.of("name", name, "email", email, "age", age);
        }

        public String getName() { return name; }
        public String getEmail() { return email; }
        public Integer getAge() { return age; }
    }

    public static class UserInfoUpdatedEvent extends UserDomainEvent {
        private final String name;
        private final String email;
        private final Integer age;

        public UserInfoUpdatedEvent(String userId, String name, String email, Integer age, Long version) {
            super(userId, version);
            this.name = name;
            this.email = email;
            this.age = age;
        }

        @Override
        public String getEventType() { return "UserInfoUpdated"; }

        @Override
        public Object getEventData() {
            return Map.of("name", name, "email", email, "age", age);
        }

        public String getName() { return name; }
        public String getEmail() { return email; }
        public Integer getAge() { return age; }
    }

    public static class UserActivatedEvent extends UserDomainEvent {
        public UserActivatedEvent(String userId, Long version) {
            super(userId, version);
        }

        @Override
        public String getEventType() { return "UserActivated"; }

        @Override
        public Object getEventData() {
            return Map.of("status", "ACTIVE");
        }
    }

    public static class UserDeactivatedEvent extends UserDomainEvent {
        public UserDeactivatedEvent(String userId, Long version) {
            super(userId, version);
        }

        @Override
        public String getEventType() { return "UserDeactivated"; }

        @Override
        public Object getEventData() {
            return Map.of("status", "INACTIVE");
        }
    }

    public static class UserDeletedEvent extends UserDomainEvent {
        public UserDeletedEvent(String userId, Long version) {
            super(userId, version);
        }

        @Override
        public String getEventType() { return "UserDeleted"; }

        @Override
        public Object getEventData() {
            return Map.of("status", "DELETED");
        }
    }
}