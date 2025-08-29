package com.ryan.demo.conductor.advanced.eventsourcing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 更新使用者命令 - UpdateUserCommand
 * 使用事件溯源模式的更新命令實作
 */
@Component
public class UpdateUserCommand extends EventSourcingCommand {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String name;
    private final String email;
    private final Integer age;
    private UserAggregate originalAggregate;

    @Autowired
    private EventStore eventStore;

    public UpdateUserCommand(String userId, String name, String email, Integer age, String currentUserId, Long expectedVersion) {
        super(userId, currentUserId, expectedVersion);
        this.name = name;
        this.email = email;
        this.age = age;
    }

    // Spring需要的無參構造函數
    public UpdateUserCommand() {
        super("");
        this.name = "";
        this.email = "";
        this.age = 0;
    }

    @Override
    protected ValidationResult validate() {
        // 檢查使用者是否存在
        if (!eventStore.aggregateExists(getAggregateId())) {
            return ValidationResult.invalid("User not found with ID: " + getAggregateId());
        }

        // 載入當前聚合狀態
        try {
            List<DomainEvent> events = eventStore.getEvents(getAggregateId());
            originalAggregate = UserAggregate.fromEvents(events);

            // 檢查使用者是否已被刪除
            if (originalAggregate.getStatus() == UserAggregate.UserStatus.DELETED) {
                return ValidationResult.invalid("Cannot update deleted user");
            }

        } catch (Exception e) {
            return ValidationResult.invalid("Failed to load user aggregate: " + e.getMessage());
        }

        // 驗證新的使用者資訊
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.invalid("User name cannot be null or empty");
        }

        if (name.length() > 100) {
            return ValidationResult.invalid("User name cannot exceed 100 characters");
        }

        if (email == null || email.trim().isEmpty()) {
            return ValidationResult.invalid("Email cannot be null or empty");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ValidationResult.invalid("Invalid email format");
        }

        if (age == null || age < 0 || age > 150) {
            return ValidationResult.invalid("Age must be between 0 and 150");
        }

        // 檢查是否有實際的變更
        if (name.equals(originalAggregate.getName()) &&
                email.equals(originalAggregate.getEmail()) &&
                age.equals(originalAggregate.getAge())) {
            return ValidationResult.invalid("No changes detected in user information");
        }

        return ValidationResult.valid();
    }

    @Override
    protected List<DomainEvent> executeCommand() throws Exception {
        // 載入使用者聚合根（如果尚未載入）
        if (originalAggregate == null) {
            List<DomainEvent> events = eventStore.getEvents(getAggregateId());
            originalAggregate = UserAggregate.fromEvents(events);
        }

        // 更新使用者資訊
        originalAggregate.updateUserInfo(name, email, age);

        // 獲取產生的事件
        List<UserAggregate.UserDomainEvent> userEvents = originalAggregate.getUncommittedEvents();

        // 轉換為DomainEvent列表
        return userEvents.stream()
                .map(event -> (DomainEvent) event)
                .toList();
    }

    @Override
    protected List<DomainEvent> generateCompensatingEvents() {
        if (originalAggregate == null) {
            return List.of();
        }

        try {
            // 載入當前狀態
            List<DomainEvent> events = eventStore.getEvents(getAggregateId());
            UserAggregate currentAggregate = UserAggregate.fromEvents(events);

            // 恢復到原始狀態
            currentAggregate.updateUserInfo(
                    originalAggregate.getName(),
                    originalAggregate.getEmail(),
                    originalAggregate.getAge()
            );

            return currentAggregate.getUncommittedEvents().stream()
                    .map(event -> (DomainEvent) event)
                    .toList();

        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    protected EventStore getEventStore() {
        return eventStore;
    }

    @Override
    public String getDescription() {
        return String.format("Update user %s: %s (%s)", getAggregateId(), name, email);
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Integer getAge() {
        return age;
    }

    public UserAggregate getOriginalAggregate() {
        return originalAggregate;
    }

    @Override
    public String toString() {
        return "UpdateUserCommand{" +
                "aggregateId='" + getAggregateId() + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                ", expectedVersion=" + getExpectedVersion() +
                '}';
    }
}