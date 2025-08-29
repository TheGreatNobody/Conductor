package com.ryan.demo.conductor.advanced;

import com.ryan.demo.conductor.basic.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 進階命令模式測試
 */
class AdvancedCommandTest {

    private TextReceiver textReceiver;
    private CommandQueue commandQueue;
    private CommandPersistence commandPersistence;

    @BeforeEach
    void setUp() {
        textReceiver = new TextReceiver();
        commandQueue = new CommandQueue();
        commandPersistence = new CommandPersistence();
    }

    @Test
    @DisplayName("測試MacroCommand - 成功執行")
    void testMacroCommandSuccess() {
        // Given
        WriteCommand write1 = new WriteCommand(textReceiver, "Hello");
        WriteCommand write2 = new WriteCommand(textReceiver, " ");
        WriteCommand write3 = new WriteCommand(textReceiver, "World");

        MacroCommand macroCommand = new MacroCommand.Builder()
                .name("Write Hello World")
                .addCommand(write1)
                .addCommand(write2)
                .addCommand(write3)
                .build();

        // When
        CommandResult result = macroCommand.execute();

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Hello World", textReceiver.getContent());
        assertEquals("Macro: Write Hello World (3 commands)", macroCommand.getDescription());
        assertEquals(3, macroCommand.getCommands().size());
        assertEquals(3, macroCommand.getExecutionResults().size());
        assertTrue(macroCommand.getExecutionResults().stream().allMatch(CommandResult::isSuccess));
    }

    @Test
    @DisplayName("測試MacroCommand - 部分失敗且停止執行")
    void testMacroCommandPartialFailureStopOnFailure() {
        // Given
        WriteCommand writeCommand = new WriteCommand(textReceiver, "Hello");
        DeleteCommand deleteCommand = new DeleteCommand(textReceiver, -1); // 會失敗
        WriteCommand writeCommand2 = new WriteCommand(textReceiver, " World");

        MacroCommand macroCommand = new MacroCommand.Builder()
                .name("Test Partial Failure")
                .addCommand(writeCommand)
                .addCommand(deleteCommand)
                .addCommand(writeCommand2)
                .stopOnFailure(true)
                .build();

        // When
        CommandResult result = macroCommand.execute();

        // Then
        assertFalse(result.isSuccess());
        assertEquals("Hello", textReceiver.getContent()); // 只有第一個命令執行
        assertEquals(2, macroCommand.getExecutionResults().size()); // 只執行了兩個命令
        assertTrue(macroCommand.getExecutionResults().get(0).isSuccess());
        assertFalse(macroCommand.getExecutionResults().get(1).isSuccess());
    }

    @Test
    @DisplayName("測試MacroCommand - 部分失敗但繼續執行")
    void testMacroCommandPartialFailureContinueOnFailure() {
        // Given
        WriteCommand writeCommand = new WriteCommand(textReceiver, "Hello");
        DeleteCommand deleteCommand = new DeleteCommand(textReceiver, -1); // 會失敗
        WriteCommand writeCommand2 = new WriteCommand(textReceiver, " World");

        MacroCommand macroCommand = new MacroCommand.Builder()
                .name("Test Continue On Failure")
                .addCommand(writeCommand)
                .addCommand(deleteCommand)
                .addCommand(writeCommand2)
                .stopOnFailure(false)
                .build();

        // When
        CommandResult result = macroCommand.execute();

        // Then
        assertFalse(result.isSuccess());
        assertEquals("Hello World", textReceiver.getContent()); // 所有成功的命令都執行了
        assertEquals(3, macroCommand.getExecutionResults().size()); // 所有命令都嘗試執行
        assertTrue(macroCommand.getExecutionResults().get(0).isSuccess());
        assertFalse(macroCommand.getExecutionResults().get(1).isSuccess());
        assertTrue(macroCommand.getExecutionResults().get(2).isSuccess());
    }

    @Test
    @DisplayName("測試MacroCommand - 撤銷操作")
    void testMacroCommandUndo() {
        // Given
        textReceiver.write("Initial ");
        WriteCommand writeCommand = new WriteCommand(textReceiver, "Hello");
        WriteCommand writeCommand2 = new WriteCommand(textReceiver, " World");

        MacroCommand macroCommand = new MacroCommand.Builder()
                .name("Test Undo")
                .addCommand(writeCommand)
                .addCommand(writeCommand2)
                .build();

        macroCommand.execute();
        assertEquals("Initial Hello World", textReceiver.getContent());

        // When
        CommandResult undoResult = macroCommand.undo();

        // Then
        assertTrue(undoResult.isSuccess());
        assertEquals("Initial ", textReceiver.getContent());
    }

    @Test
    @DisplayName("測試CommandQueue - 基本提交和執行")
    void testCommandQueueBasicSubmission() throws Exception {
        // Given
        WriteCommand command = new WriteCommand(textReceiver, "Async Test");

        // When
        CompletableFuture<CommandResult> future = commandQueue.submitCommand(command);
        CommandResult result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Async Test", textReceiver.getContent());
    }

    @Test
    @DisplayName("測試CommandQueue - 批次提交")
    void testCommandQueueBatchSubmission() throws Exception {
        // Given
        List<Command> commands = Arrays.asList(
                new WriteCommand(textReceiver, "First"),
                new WriteCommand(textReceiver, " Second"),
                new WriteCommand(textReceiver, " Third")
        );

        // When
        List<CompletableFuture<CommandResult>> futures = commandQueue.submitCommands(commands);
        List<CommandResult> results = commandQueue.waitForAll(futures).get(10, TimeUnit.SECONDS);

        // Then
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(CommandResult::isSuccess));
        assertEquals("First Second Third", textReceiver.getContent());
    }

    @Test
    @DisplayName("測試CommandQueue - 優先級處理")
    void testCommandQueuePriority() throws Exception {
        // Given
        WriteCommand normalCommand = new WriteCommand(textReceiver, "Normal");
        WriteCommand highCommand = new WriteCommand(textReceiver, " High");
        WriteCommand urgentCommand = new WriteCommand(textReceiver, " Urgent");

        // When - 以不同優先級提交
        CompletableFuture<CommandResult> normalFuture =
                commandQueue.submitCommand(normalCommand, CommandQueue.CommandPriority.NORMAL);
        CompletableFuture<CommandResult> highFuture =
                commandQueue.submitCommand(highCommand, CommandQueue.CommandPriority.HIGH);
        CompletableFuture<CommandResult> urgentFuture =
                commandQueue.submitCommand(urgentCommand, CommandQueue.CommandPriority.URGENT);

        // Then - 等待所有完成
        normalFuture.get(5, TimeUnit.SECONDS);
        highFuture.get(5, TimeUnit.SECONDS);
        urgentFuture.get(5, TimeUnit.SECONDS);

        // 驗證所有命令都執行成功
        assertTrue(textReceiver.getContent().contains("Normal"));
        assertTrue(textReceiver.getContent().contains("High"));
        assertTrue(textReceiver.getContent().contains("Urgent"));
    }

    @Test
    @DisplayName("測試CommandQueue - 狀態查詢")
    void testCommandQueueStatus() {
        // Given
        CommandQueue.QueueStatus initialStatus = commandQueue.getQueueStatus();

        // When - 提交一些命令
        commandQueue.submitCommand(new WriteCommand(textReceiver, "Test1"));
        commandQueue.submitCommand(new WriteCommand(textReceiver, "Test2"));

        CommandQueue.QueueStatus afterSubmissionStatus = commandQueue.getQueueStatus();

        // Then
        assertTrue(afterSubmissionStatus.isRunning());
        assertTrue(afterSubmissionStatus.getQueueSize() >= 0);
        assertTrue(afterSubmissionStatus.getPendingCount() >= 0);
    }

    @Test
    @DisplayName("測試CommandQueue - 取消命令")
    void testCommandQueueCancelCommand() throws Exception {
        // Given
        WriteCommand command = new WriteCommand(textReceiver, "To be cancelled");
        CompletableFuture<CommandResult> future = commandQueue.submitCommand(command);

        // When
        boolean cancelled = commandQueue.cancelCommand(command.getCommandId());

        // Then
        assertTrue(cancelled || future.isDone()); // 命令可能已經執行完成
    }

    @Test
    @DisplayName("測試CommandPersistence - 基本儲存和查詢")
    void testCommandPersistenceBasicOperations() {
        // Given
        WriteCommand command = new WriteCommand(textReceiver, "Persistence Test");
        CommandResult result = command.execute();

        // When
        commandPersistence.saveCommandExecution(command, result);

        // Then
        var record = commandPersistence.getCommandRecord(command.getCommandId());
        assertTrue(record.isPresent());
        assertEquals(command.getCommandId(), record.get().getId());
        assertEquals("WriteCommand", record.get().getCommandType());
        assertTrue(record.get().isSuccess());
        assertNotNull(record.get().getExecutedAt());
    }

    @Test
    @DisplayName("測試CommandPersistence - 統計資訊")
    void testCommandPersistenceStatistics() {
        // Given
        WriteCommand successCommand = new WriteCommand(textReceiver, "Success");
        DeleteCommand failCommand = new DeleteCommand(textReceiver, -1);

        CommandResult successResult = successCommand.execute();
        CommandResult failResult = failCommand.execute();

        commandPersistence.saveCommandExecution(successCommand, successResult);
        commandPersistence.saveCommandExecution(failCommand, failResult);

        // When
        CommandPersistence.CommandStatistics stats = commandPersistence.getCommandStatistics();

        // Then
        assertEquals(2, stats.getTotalCommands());
        assertEquals(1, stats.getSuccessfulCommands());
        assertEquals(1, stats.getFailedCommands());
        assertEquals(50.0, stats.getSuccessRate(), 0.001);
        assertTrue(stats.getCommandTypeStats().containsKey("WriteCommand"));
        assertTrue(stats.getCommandTypeStats().containsKey("DeleteCommand"));
    }

    @Test
    @DisplayName("測試CommandPersistence - 時間範圍查詢")
    void testCommandPersistenceTimeRangeQuery() throws InterruptedException {
        // Given
        WriteCommand command1 = new WriteCommand(textReceiver, "First");
        CommandResult result1 = command1.execute();
        commandPersistence.saveCommandExecution(command1, result1);

        java.time.LocalDateTime middle = java.time.LocalDateTime.now();
        Thread.sleep(10); // 確保時間差異

        WriteCommand command2 = new WriteCommand(textReceiver, "Second");
        CommandResult result2 = command2.execute();
        commandPersistence.saveCommandExecution(command2, result2);

        // When
        List<CommandPersistence.CommandRecord> recordsAfterMiddle =
                commandPersistence.getCommandRecordsByTimeRange(middle, null);

        // Then
        assertEquals(1, recordsAfterMiddle.size());
        assertEquals(command2.getCommandId(), recordsAfterMiddle.get(0).getId());
    }

    @Test
    @DisplayName("測試CommandPersistence - 狀態過濾查詢")
    void testCommandPersistenceStatusFilter() {
        // Given
        WriteCommand successCommand = new WriteCommand(textReceiver, "Success");
        DeleteCommand failCommand = new DeleteCommand(textReceiver, -1);

        CommandResult successResult = successCommand.execute();
        CommandResult failResult = failCommand.execute();

        commandPersistence.saveCommandExecution(successCommand, successResult);
        commandPersistence.saveCommandExecution(failCommand, failResult);

        // When
        List<CommandPersistence.CommandRecord> successRecords =
                commandPersistence.getCommandRecordsByStatus(true);
        List<CommandPersistence.CommandRecord> failRecords =
                commandPersistence.getCommandRecordsByStatus(false);

        // Then
        assertEquals(1, successRecords.size());
        assertEquals(1, failRecords.size());
        assertEquals(successCommand.getCommandId(), successRecords.get(0).getId());
        assertEquals(failCommand.getCommandId(), failRecords.get(0).getId());
    }

    @Test
    @DisplayName("測試Builder模式驗證")
    void testMacroCommandBuilderValidation() {
        // Test empty name
        assertThrows(IllegalArgumentException.class, () -> {
            new MacroCommand.Builder()
                    .name("")
                    .addCommand(new WriteCommand(textReceiver, "test"))
                    .build();
        });

        // Test no commands
        assertThrows(IllegalArgumentException.class, () -> {
            new MacroCommand.Builder()
                    .name("Test")
                    .build();
        });

        // Test valid build
        assertDoesNotThrow(() -> {
            new MacroCommand.Builder()
                    .name("Valid Macro")
                    .addCommand(new WriteCommand(textReceiver, "test"))
                    .build();
        });
    }
}