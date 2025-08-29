package com.ryan.demo.conductor.advanced.eventsourcing;

import com.ryan.demo.conductor.basic.CommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 事件溯源模式測試
 */
class EventSourcingTest {

    private InMemoryEventStore eventStore;
    private String testUserId;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        testUserId = UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("測試UserAggregate - 建立使用者")
    void testUserAggregateCreateUser() {
        // When
        UserAggregate aggregate = UserAggregate.createUser(testUserId, "John Doe", "john@example.com", 30);

        // Then
        assertEquals(testUserId, aggregate.getUserId());
        assertEquals("John Doe", aggregate.getName());
        assertEquals("john@example.com", aggregate.getEmail());
        assertEquals(30, aggregate.getAge());
        assertEquals(UserAggregate.UserStatus.ACTIVE, aggregate.getStatus());
        assertEquals(1L, aggregate.getVersion());
        assertNotNull(aggregate.getCreatedAt());

        // 檢查產生的事件
        List<UserAggregate.UserDomainEvent> events = aggregate.getUncommittedEvents();
        assertEquals(1, events.size());
        assertEquals("UserCreated", events.get(0).getEventType());
    }

    @Test
    @DisplayName("測試UserAggregate - 更新使用者資訊")
    void testUserAggregateUpdateUserInfo() {
        // Given
        UserAggregate aggregate = UserAggregate.createUser(testUserId, "John Doe", "john@example.com", 30);
        aggregate.markEventsAsCommitted();

        // When
        aggregate.updateUserInfo("Jane Doe", "jane@example.com", 25);

        // Then
        assertEquals("Jane Doe", aggregate.getName());
        assertEquals("jane@example.com", aggregate.getEmail());
        assertEquals(25, aggregate.getAge());
        assertEquals(2L, aggregate.getVersion());

        // 檢查產生的事件
        List<UserAggregate.UserDomainEvent> events = aggregate.getUncommittedEvents();
        assertEquals(1, events.size());
        assertEquals("UserInfoUpdated", events.get(0).getEventType());
        assertEquals(2L, events.get(0).getVersion());
    }

    @Test
    @DisplayName("測試UserAggregate - 使用者狀態變更")
    void testUserAggregateStatusChanges() {
        // Given
        UserAggregate aggregate = UserAggregate.createUser(testUserId, "John Doe", "john@example.com", 30);
        aggregate.markEventsAsCommitted();

        // When - 停用使用者
        aggregate.deactivateUser();

        // Then
        assertEquals(UserAggregate.UserStatus.INACTIVE, aggregate.getStatus());
        assertEquals("UserDeactivated", aggregate.getUncommittedEvents().get(0).getEventType());
        aggregate.markEventsAsCommitted();

        // When - 重新啟用使用者
        aggregate.activateUser();

        // Then
        assertEquals(UserAggregate.UserStatus.ACTIVE, aggregate.getStatus());
        assertEquals("UserActivated", aggregate.getUncommittedEvents().get(0).getEventType());
        aggregate.markEventsAsCommitted();

        // When - 刪除使用者
        aggregate.deleteUser();

        // Then
        assertEquals(UserAggregate.UserStatus.DELETED, aggregate.getStatus());
        assertEquals("UserDeleted", aggregate.getUncommittedEvents().get(0).getEventType());
    }

    @Test
    @DisplayName("測試UserAggregate - 無效狀態轉換")
    void testUserAggregateInvalidStateTransitions() {
        // Given - 已啟用的使用者
        UserAggregate aggregate = UserAggregate.createUser(testUserId, "John Doe", "john@example.com", 30);

        // Then - 不能再次啟用
        assertThrows(IllegalStateException.class, () -> aggregate.activateUser());

        // Given - 停用使用者
        aggregate.deactivateUser();

        // Then - 不能再次停用
        assertThrows(IllegalStateException.class, () -> aggregate.deactivateUser());

        // Given - 刪除使用者
        aggregate.deleteUser();

        // Then - 不能對已刪除用戶進行操作
        assertThrows(IllegalStateException.class, () -> aggregate.activateUser());
        assertThrows(IllegalStateException.class, () -> aggregate.deactivateUser());
        assertThrows(IllegalStateException.class, () ->
                aggregate.updateUserInfo("New Name", "new@example.com", 35));
    }

    @Test
    @DisplayName("測試UserAggregate - 從事件流重建")
    void testUserAggregateFromEvents() throws Exception {
        // Given - 建立一系列事件
        UserAggregate originalAggregate = UserAggregate.createUser(testUserId, "John Doe", "john@example.com", 30);
        originalAggregate.updateUserInfo("Jane Doe", "jane@example.com", 25);
        originalAggregate.deactivateUser();

        List<UserAggregate.UserDomainEvent> events = originalAggregate.getUncommittedEvents();

        // 將事件轉換為DomainEvent列表
        List<EventSourcingCommand.DomainEvent> domainEvents = events.stream()
                .map(event -> (EventSourcingCommand.DomainEvent) event)
                .toList();

        // When - 從事件流重建聚合根
        UserAggregate rebuiltAggregate = UserAggregate.fromEvents(domainEvents);

        // Then
        assertEquals(testUserId, rebuiltAggregate.getUserId());
        assertEquals("Jane Doe", rebuiltAggregate.getName());
        assertEquals("jane@example.com", rebuiltAggregate.getEmail());
        assertEquals(25, rebuiltAggregate.getAge());
        assertEquals(UserAggregate.UserStatus.INACTIVE, rebuiltAggregate.getStatus());
        assertEquals(3L, rebuiltAggregate.getVersion());
        assertTrue(rebuiltAggregate.getUncommittedEvents().isEmpty()); // 事件已提交
    }

    @Test
    @DisplayName("測試CreateUserCommand - 成功建立")
    void testCreateUserCommandSuccess() {
        // Given
        CreateUserCommand command = new CreateUserCommand(testUserId, "John Doe", "john@example.com", 30);
        // 手動設定EventStore，因為沒有Spring容器
        java.lang.reflect.Field eventStoreField;
        try {
            eventStoreField = CreateUserCommand.class.getDeclaredField("eventStore");
            eventStoreField.setAccessible(true);
            eventStoreField.set(command, eventStore);
        } catch (Exception e) {
            fail("無法設定eventStore: " + e.getMessage());
        }

        // When
        CommandResult result = command.execute();

        // Then
        assertTrue(result.isSuccess());
        assertTrue(eventStore.aggregateExists(testUserId));
        assertEquals(1L, eventStore.getAggregateVersion(testUserId));

        // 驗證事件
        List<EventSourcingCommand.DomainEvent> events = eventStore.getEvents(testUserId);
        assertEquals(1, events.size());
        assertEquals("UserCreated", events.get(0).getEventType());
    }

    @Test
    @DisplayName("測試CreateUserCommand - 驗證失敗")
    void testCreateUserCommandValidationFailure() {
        // Test invalid email
        CreateUserCommand invalidEmailCommand = new CreateUserCommand(testUserId, "John Doe", "invalid-email", 30);
        try {
            java.lang.reflect.Field field = CreateUserCommand.class.getDeclaredField("eventStore");
            field.setAccessible(true);
            field.set(invalidEmailCommand, eventStore);
        } catch (Exception e) {
            fail("無法設定eventStore");
        }

        CommandResult result = invalidEmailCommand.execute();
        assertFalse(result.isSuccess());
        assertEquals("VALIDATION_ERROR", result.getErrorCode());
        assertTrue(result.getMessage().contains("Invalid email format"));

        // Test invalid age
        CreateUserCommand invalidAgeCommand = new CreateUserCommand(testUserId, "John Doe", "john@example.com", -1);
        try {
            java.lang.reflect.Field field = CreateUserCommand.class.getDeclaredField("eventStore");
            field.setAccessible(true);
            field.set(invalidAgeCommand, eventStore);
        } catch (Exception e) {
            fail("無法設定eventStore");
        }

        result = invalidAgeCommand.execute();
        assertFalse(result.isSuccess());
        assertEquals("VALIDATION_ERROR", result.getErrorCode());
    }

    @Test
    @DisplayName("測試UpdateUserCommand - 成功更新")
    void testUpdateUserCommandSuccess() throws Exception {
        // Given - 先建立使用者
        CreateUserCommand createCommand = new CreateUserCommand(testUserId, "John Doe", "john@example.com", 30);
        setEventStore(createCommand, eventStore);
        createCommand.execute();

        // When - 更新使用者
        UpdateUserCommand updateCommand = new UpdateUserCommand(testUserId, "Jane Doe", "jane@example.com", 25, "user1", 1L);
        setEventStore(updateCommand, eventStore);
        CommandResult result = updateCommand.execute();

        // Then
        assertTrue(result.isSuccess());
        assertEquals(2L, eventStore.getAggregateVersion(testUserId));

        // 驗證聚合根狀態
        List<EventSourcingCommand.DomainEvent> events = eventStore.getEvents(testUserId);
        UserAggregate aggregate = UserAggregate.fromEvents(events);
        assertEquals("Jane Doe", aggregate.getName());
        assertEquals("jane@example.com", aggregate.getEmail());
        assertEquals(25, aggregate.getAge());
    }

    @Test
    @DisplayName("測試UpdateUserCommand - 使用者不存在")
    void testUpdateUserCommandUserNotFound() throws Exception {
        // Given
        UpdateUserCommand command = new UpdateUserCommand("nonexistent", "Name", "email@example.com", 25, "user1", null);
        setEventStore(command, eventStore);

        // When
        CommandResult result = command.execute();

        // Then
        assertFalse(result.isSuccess());
        assertEquals("VALIDATION_ERROR", result.getErrorCode());
        assertTrue(result.getMessage().contains("User not found"));
    }

    @Test
    @DisplayName("測試InMemoryEventStore - 基本操作")
    void testInMemoryEventStoreBasicOperations() throws Exception {
        // Given
        UserAggregate aggregate = UserAggregate.createUser(testUserId, "John Doe", "john@example.com", 30);
        List<EventSourcingCommand.DomainEvent> events = aggregate.getUncommittedEvents().stream()
                .map(event -> (EventSourcingCommand.DomainEvent) event)
                .toList();

        // When
        eventStore.saveEvents(testUserId, events, null);

        // Then
        assertTrue(eventStore.aggregateExists(testUserId));
        assertEquals(1L, eventStore.getAggregateVersion(testUserId));

        List<EventSourcingCommand.DomainEvent> retrievedEvents = eventStore.getEvents(testUserId);
        assertEquals(1, retrievedEvents.size());
        assertEquals("UserCreated", retrievedEvents.get(0).getEventType());
    }

    @Test
    @DisplayName("測試InMemoryEventStore - 並發控制")
    void testInMemoryEventStoreConcurrencyControl() throws Exception {
        // Given - 先儲存一個事件
        UserAggregate aggregate = UserAggregate.createUser(testUserId, "John Doe", "john@example.com", 30);
        List<EventSourcingCommand.DomainEvent> initialEvents = aggregate.getUncommittedEvents().stream()
                .map(event -> (EventSourcingCommand.DomainEvent) event)
                .toList();
        eventStore.saveEvents(testUserId, initialEvents, null);

        // When - 嘗試用錯誤的預期版本儲存
        aggregate.updateUserInfo("Jane Doe", "jane@example.com", 25);
        List<EventSourcingCommand.DomainEvent> updateEvents = aggregate.getUncommittedEvents().stream()
                .map(event -> (EventSourcingCommand.DomainEvent) event)
                .toList();

        // Then - 應該拋出並發異常
        assertThrows(EventSourcingCommand.ConcurrencyException.class, () -> {
            eventStore.saveEvents(testUserId, updateEvents, 0L); // 錯誤的版本
        });
    }

    @Test
    @DisplayName("測試InMemoryEventStore - 統計資訊")
    void testInMemoryEventStoreStatistics() throws Exception {
        // Given - 建立多個聚合根和事件
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();

        UserAggregate aggregate1 = UserAggregate.createUser(userId1, "User1", "user1@example.com", 30);
        UserAggregate aggregate2 = UserAggregate.createUser(userId2, "User2", "user2@example.com", 25);
        aggregate2.updateUserInfo("Updated User2", "updated2@example.com", 26);

        eventStore.saveEvents(userId1, aggregate1.getUncommittedEvents().stream()
                .map(event -> (EventSourcingCommand.DomainEvent) event).toList(), null);
        eventStore.saveEvents(userId2, aggregate2.getUncommittedEvents().stream()
                .map(event -> (EventSourcingCommand.DomainEvent) event).toList(), null);

        // When
        InMemoryEventStore.EventStoreStatistics stats = eventStore.getStatistics();

        // Then
        assertEquals(2, stats.getTotalAggregates());
        assertEquals(3, stats.getTotalEvents()); // User1: 1 event, User2: 2 events
        assertEquals(1.5, stats.getAverageEventsPerAggregate(), 0.001);
        assertTrue(stats.getEventTypeStats().containsKey("UserCreated"));
        assertTrue(stats.getEventTypeStats().containsKey("UserInfoUpdated"));
        assertEquals(2, (int) stats.getEventTypeStats().get("UserCreated"));
        assertEquals(1, (int) stats.getEventTypeStats().get("UserInfoUpdated"));
    }

    private void setEventStore(Object command, InMemoryEventStore eventStore) throws Exception {
        java.lang.reflect.Field field = command.getClass().getDeclaredField("eventStore");
        field.setAccessible(true);
        field.set(command, eventStore);
    }
}