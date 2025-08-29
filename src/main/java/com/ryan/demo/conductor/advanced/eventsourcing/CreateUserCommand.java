package com.ryan.demo.conductor.advanced.eventsourcing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 建立使用者命令 - CreateUserCommand
 * 使用事件溯源模式的具體命令實作
 */
@Component
public class CreateUserCommand extends EventSourcingCommand {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String name;
    private final String email;
    private final Integer age;

    @Autowired
    private EventStore eventStore;

    public CreateUserCommand(String userId, String name, String email, Integer age) {
        super(userId);
        this.name = name;
        this.email = email;
        this.age = age;
    }

    // Spring需要的無參構造函數
    public CreateUserCommand() {
        super("");
        this.name = "";
        this.email = "";
        this.age = 0;
    }

    @Override
    protected ValidationResult validate() {
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

        // 檢查使用者是否已存在
        if (eventStore.aggregateExists(getAggregateId())) {
            return ValidationResult.invalid("User already exists with ID: " + getAggregateId());
        }

        return ValidationResult.valid();
    }

    @Override
    protected List<DomainEvent> executeCommand() throws Exception {
        // 建立使用者聚合根
        UserAggregate userAggregate = UserAggregate.createUser(getAggregateId(), name, email, age);

        // 獲取產生的事件
        List<UserAggregate.UserDomainEvent> userEvents = userAggregate.getUncommittedEvents();

        // 轉換為DomainEvent列表
        return userEvents.stream()
                .map(event -> (DomainEvent) event)
                .toList();
    }

    @Override
    protected List<DomainEvent> generateCompensatingEvents() {
        // 對於建立使用者命令，補償事件是刪除使用者
        try {
            // 載入當前聚合狀態
            List<DomainEvent> events = eventStore.getEvents(getAggregateId());
            UserAggregate userAggregate = UserAggregate.fromEvents(events);

            // 產生刪除事件
            userAggregate.deleteUser();

            return userAggregate.getUncommittedEvents().stream()
                    .map(event -> (DomainEvent) event)
                    .toList();

        } catch (Exception e) {
            // 如果無法產生補償事件，返回空列表
            return List.of();
        }
    }

    @Override
    protected EventStore getEventStore() {
        return eventStore;
    }

    @Override
    public String getDescription() {
        return String.format("Create user: %s (%s)", name, email);
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

    @Override
    public String toString() {
        return "CreateUserCommand{" +
                "aggregateId='" + getAggregateId() + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                '}';
    }
}