package com.ryan.demo.conductor.controller;

import com.ryan.demo.conductor.advanced.CommandQueue;
import com.ryan.demo.conductor.advanced.CommandPersistence;
import com.ryan.demo.conductor.advanced.MacroCommand;
import com.ryan.demo.conductor.advanced.eventsourcing.*;
import com.ryan.demo.conductor.basic.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 命令模式演示控制器
 * 展示命令模式的各種應用層級
 */
@RestController
@RequestMapping("/api/command-demo")
public class CommandDemoController {

    @Autowired
    private TextReceiver textReceiver;

    @Autowired
    private CommandInvoker commandInvoker;

    @Autowired
    private CommandQueue commandQueue;

    @Autowired
    private CommandPersistence commandPersistence;

    @Autowired
    private InMemoryEventStore eventStore;

    /**
     * 初階演示 - 基本命令執行
     */
    @PostMapping("/basic/write")
    public ResponseEntity<Map<String, Object>> basicWriteCommand(@RequestBody Map<String, String> request) {
        String text = request.get("text");

        WriteCommand command = new WriteCommand(textReceiver, text);
        CommandResult result = commandInvoker.executeCommand(command);

        Map<String, Object> response = new HashMap<>();
        response.put("commandId", command.getCommandId());
        response.put("result", result);
        response.put("currentContent", textReceiver.getContent());
        response.put("canUndo", commandInvoker.canUndo());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/basic/delete")
    public ResponseEntity<Map<String, Object>> basicDeleteCommand(@RequestBody Map<String, Integer> request) {
        Integer length = request.get("length");

        DeleteCommand command = new DeleteCommand(textReceiver, length);
        CommandResult result = commandInvoker.executeCommand(command);

        Map<String, Object> response = new HashMap<>();
        response.put("commandId", command.getCommandId());
        response.put("result", result);
        response.put("currentContent", textReceiver.getContent());
        response.put("deletedText", command.getDeletedText());
        response.put("canUndo", commandInvoker.canUndo());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/basic/clear")
    public ResponseEntity<Map<String, Object>> basicClearCommand() {
        ClearCommand command = new ClearCommand(textReceiver);
        CommandResult result = commandInvoker.executeCommand(command);

        Map<String, Object> response = new HashMap<>();
        response.put("commandId", command.getCommandId());
        response.put("result", result);
        response.put("currentContent", textReceiver.getContent());
        response.put("clearedContent", command.getClearedContent());
        response.put("canUndo", commandInvoker.canUndo());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/basic/undo")
    public ResponseEntity<Map<String, Object>> basicUndoCommand() {
        CommandResult result = commandInvoker.undoLastCommand();

        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        response.put("currentContent", textReceiver.getContent());
        response.put("canUndo", commandInvoker.canUndo());
        response.put("canRedo", commandInvoker.canRedo());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/basic/redo")
    public ResponseEntity<Map<String, Object>> basicRedoCommand() {
        CommandResult result = commandInvoker.redoLastCommand();

        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        response.put("currentContent", textReceiver.getContent());
        response.put("canUndo", commandInvoker.canUndo());
        response.put("canRedo", commandInvoker.canRedo());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/basic/status")
    public ResponseEntity<Map<String, Object>> getBasicStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("currentContent", textReceiver.getContent());
        response.put("contentLength", textReceiver.getLength());
        response.put("historyStats", commandInvoker.getHistoryStats());
        response.put("commandHistory", commandInvoker.getCommandHistory().stream()
                .map(cmd -> Map.of("id", cmd.getCommandId(), "description", cmd.getDescription()))
                .toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 進階演示 - 巨集命令
     */
    @PostMapping("/advanced/macro")
    public ResponseEntity<Map<String, Object>> executeMacroCommand(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commandDefs = (List<Map<String, Object>>) request.get("commands");
        String macroName = (String) request.getOrDefault("name", "Demo Macro");

        List<Command> commands = new ArrayList<>();

        for (Map<String, Object> cmdDef : commandDefs) {
            String type = (String) cmdDef.get("type");
            Command command = switch (type.toLowerCase()) {
                case "write" -> new WriteCommand(textReceiver, (String) cmdDef.get("text"));
                case "delete" -> new DeleteCommand(textReceiver, (Integer) cmdDef.get("length"));
                case "clear" -> new ClearCommand(textReceiver);
                default -> throw new IllegalArgumentException("Unknown command type: " + type);
            };
            commands.add(command);
        }

        MacroCommand macroCommand = new MacroCommand.Builder()
                .name(macroName)
                .addCommands(commands)
                .build();

        CommandResult result = commandInvoker.executeCommand(macroCommand);

        Map<String, Object> response = new HashMap<>();
        response.put("macroId", macroCommand.getCommandId());
        response.put("result", result);
        response.put("currentContent", textReceiver.getContent());
        response.put("executedCommands", macroCommand.getCommands().size());
        response.put("executionResults", macroCommand.getExecutionResults());

        return ResponseEntity.ok(response);
    }

    /**
     * 進階演示 - 命令佇列
     */
    @PostMapping("/advanced/queue/submit")
    public ResponseEntity<Map<String, Object>> submitToQueue(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commandDefs = (List<Map<String, Object>>) request.get("commands");
        String priorityStr = (String) request.getOrDefault("priority", "NORMAL");

        CommandQueue.CommandPriority priority = CommandQueue.CommandPriority.valueOf(priorityStr.toUpperCase());

        List<CompletableFuture<CommandResult>> futures = new ArrayList<>();
        List<String> commandIds = new ArrayList<>();

        for (Map<String, Object> cmdDef : commandDefs) {
            String type = (String) cmdDef.get("type");
            Command command = switch (type.toLowerCase()) {
                case "write" -> new WriteCommand(textReceiver, (String) cmdDef.get("text"));
                case "delete" -> new DeleteCommand(textReceiver, (Integer) cmdDef.get("length"));
                case "clear" -> new ClearCommand(textReceiver);
                default -> throw new IllegalArgumentException("Unknown command type: " + type);
            };

            CompletableFuture<CommandResult> future = commandQueue.submitCommand(command, priority);
            futures.add(future);
            commandIds.add(command.getCommandId());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("submittedCommands", commandIds.size());
        response.put("commandIds", commandIds);
        response.put("queueStatus", commandQueue.getQueueStatus());
        response.put("priority", priority);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/advanced/queue/status")
    public ResponseEntity<CommandQueue.QueueStatus> getQueueStatus() {
        return ResponseEntity.ok(commandQueue.getQueueStatus());
    }

    /**
     * 高階演示 - 事件溯源
     */
    @PostMapping("/advanced/eventsourcing/create-user")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> request) {
        String userId = UUID.randomUUID().toString();
        String name = (String) request.get("name");
        String email = (String) request.get("email");
        Integer age = (Integer) request.get("age");

        CreateUserCommand command = new CreateUserCommand(userId, name, email, age);
        CommandResult result = command.execute();

        // 持久化命令執行記錄
        commandPersistence.saveCommandExecution(command, result);

        Map<String, Object> response = new HashMap<>();
        response.put("commandId", command.getCommandId());
        response.put("userId", userId);
        response.put("result", result);
        response.put("eventStoreStats", eventStore.getStatistics());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/advanced/eventsourcing/update-user/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {

        String name = (String) request.get("name");
        String email = (String) request.get("email");
        Integer age = (Integer) request.get("age");
        Long expectedVersion = request.containsKey("expectedVersion") ?
                ((Number) request.get("expectedVersion")).longValue() : null;

        UpdateUserCommand command = new UpdateUserCommand(userId, name, email, age, "demo-user", expectedVersion);
        CommandResult result = command.execute();

        // 持久化命令執行記錄
        commandPersistence.saveCommandExecution(command, result);

        Map<String, Object> response = new HashMap<>();
        response.put("commandId", command.getCommandId());
        response.put("userId", userId);
        response.put("result", result);
        response.put("currentVersion", eventStore.getAggregateVersion(userId));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/advanced/eventsourcing/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String userId) {
        if (!eventStore.aggregateExists(userId)) {
            return ResponseEntity.notFound().build();
        }

        List<EventSourcingCommand.DomainEvent> events = eventStore.getEvents(userId);
        UserAggregate userAggregate = UserAggregate.fromEvents(events);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userAggregate.getUserId());
        response.put("name", userAggregate.getName());
        response.put("email", userAggregate.getEmail());
        response.put("age", userAggregate.getAge());
        response.put("status", userAggregate.getStatus());
        response.put("version", userAggregate.getVersion());
        response.put("createdAt", userAggregate.getCreatedAt());
        response.put("lastModifiedAt", userAggregate.getLastModifiedAt());
        response.put("eventHistory", events.stream()
                .map(event -> Map.of(
                        "eventId", event.getEventId(),
                        "eventType", event.getEventType(),
                        "version", event.getVersion(),
                        "timestamp", event.getTimestamp(),
                        "data", event.getEventData()
                ))
                .toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/advanced/eventsourcing/statistics")
    public ResponseEntity<Map<String, Object>> getEventSourcingStatistics() {
        Map<String, Object> response = new HashMap<>();
        response.put("eventStore", eventStore.getStatistics());
        response.put("commandStats", commandPersistence.getCommandStatistics());
        response.put("allAggregates", eventStore.getAllAggregateIds());

        return ResponseEntity.ok(response);
    }

    /**
     * 綜合演示 - 複雜工作流程
     */
    @PostMapping("/comprehensive/workflow")
    public ResponseEntity<Map<String, Object>> executeWorkflow(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            // 1. 基本命令序列
            WriteCommand writeCmd = new WriteCommand(textReceiver, "Hello ");
            results.add(executeAndRecord(writeCmd, "基本寫入命令"));

            WriteCommand writeCmd2 = new WriteCommand(textReceiver, "World!");
            results.add(executeAndRecord(writeCmd2, "續寫命令"));

            // 2. 巨集命令
            MacroCommand macroCmd = new MacroCommand.Builder()
                    .name("Format Text")
                    .addCommand(new WriteCommand(textReceiver, " - "))
                    .addCommand(new WriteCommand(textReceiver, "Formatted"))
                    .build();
            results.add(executeAndRecord(macroCmd, "巨集命令"));

            // 3. 事件溯源命令
            String userId = UUID.randomUUID().toString();
            CreateUserCommand createUserCmd = new CreateUserCommand(userId, "Demo User", "demo@example.com", 25);
            CommandResult createResult = createUserCmd.execute();
            commandPersistence.saveCommandExecution(createUserCmd, createResult);
            results.add(Map.of(
                    "step", "事件溯源 - 建立使用者",
                    "result", createResult,
                    "userId", userId
            ));

            // 4. 非同步命令處理
            CompletableFuture<CommandResult> asyncResult = commandQueue.submitCommand(
                    new WriteCommand(textReceiver, " [Async]"),
                    CommandQueue.CommandPriority.HIGH
            );

            // 等待非同步結果
            CommandResult asyncCommandResult = asyncResult.get(5, TimeUnit.SECONDS);
            results.add(Map.of(
                    "step", "非同步命令處理",
                    "result", asyncCommandResult
            ));

            response.put("success", true);
            response.put("workflow", "Complex Command Pattern Workflow");
            response.put("steps", results);
            response.put("finalContent", textReceiver.getContent());
            response.put("queueStatus", commandQueue.getQueueStatus());
            response.put("eventStoreStats", eventStore.getStatistics());

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("completedSteps", results);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 重置演示環境
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetDemo() {
        // 重置文字接收器
        textReceiver.reset();

        // 清空命令歷史
        commandInvoker.clearHistory();

        // 清空佇列
        commandQueue.clearQueue();

        // 清空事件庫
        eventStore.clearAll();

        // 清空命令記錄
        commandPersistence.clearAllRecords();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Demo environment reset successfully");
        response.put("textContent", textReceiver.getContent());
        response.put("queueStatus", commandQueue.getQueueStatus());
        response.put("eventStoreStats", eventStore.getStatistics());

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> executeAndRecord(Command command, String step) {
        CommandResult result = commandInvoker.executeCommand(command);
        commandPersistence.saveCommandExecution(command, result);

        return Map.of(
                "step", step,
                "commandId", command.getCommandId(),
                "result", result,
                "description", command.getDescription()
        );
    }
}