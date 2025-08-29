package com.ryan.demo.conductor.basic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 基本命令模式測試
 */
class BasicCommandTest {

    private TextReceiver textReceiver;
    private CommandInvoker commandInvoker;

    @BeforeEach
    void setUp() {
        textReceiver = new TextReceiver();
        commandInvoker = new CommandInvoker();
    }

    @Test
    @DisplayName("測試ClearCommand - 撤銷操作")
    void testClearCommandUndo() {
        // Given
        textReceiver.write("Original content");
        ClearCommand command = new ClearCommand(textReceiver);
        command.execute();
        assertEquals("", textReceiver.getContent());

        // When
        CommandResult undoResult = command.undo();

        // Then
        assertTrue(undoResult.isSuccess());
        assertEquals("Original content", textReceiver.getContent());
    }

    @Test
    @DisplayName("測試CommandInvoker - 基本執行")
    void testCommandInvokerBasicExecution() {
        // Given
        WriteCommand command = new WriteCommand(textReceiver, "Test");

        // When
        CommandResult result = commandInvoker.executeCommand(command);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Test", textReceiver.getContent());
        assertTrue(commandInvoker.canUndo());
        assertFalse(commandInvoker.canRedo());
        assertEquals(1, commandInvoker.getCommandHistory().size());
    }

    @Test
    @DisplayName("測試CommandInvoker - 撤銷和重做")
    void testCommandInvokerUndoRedo() {
        // Given
        WriteCommand command1 = new WriteCommand(textReceiver, "Hello");
        WriteCommand command2 = new WriteCommand(textReceiver, " World");

        commandInvoker.executeCommand(command1);
        commandInvoker.executeCommand(command2);
        assertEquals("Hello World", textReceiver.getContent());

        // When - 撤銷最後一個命令
        CommandResult undoResult = commandInvoker.undoLastCommand();

        // Then
        assertTrue(undoResult.isSuccess());
        assertEquals("Hello", textReceiver.getContent());
        assertTrue(commandInvoker.canUndo());
        assertTrue(commandInvoker.canRedo());

        // When - 重做命令
        CommandResult redoResult = commandInvoker.redoLastCommand();

        // Then
        assertTrue(redoResult.isSuccess());
        assertEquals("Hello World", textReceiver.getContent());
        assertTrue(commandInvoker.canUndo());
        assertFalse(commandInvoker.canRedo());
    }

    @Test
    @DisplayName("測試CommandInvoker - 無法撤銷")
    void testCommandInvokerCannotUndo() {
        // When
        CommandResult result = commandInvoker.undoLastCommand();

        // Then
        assertFalse(result.isSuccess());
        assertEquals("NO_HISTORY", result.getErrorCode());
        assertEquals("No commands to undo", result.getMessage());
    }

    @Test
    @DisplayName("測試CommandInvoker - 無法重做")
    void testCommandInvokerCannotRedo() {
        // When
        CommandResult result = commandInvoker.redoLastCommand();

        // Then
        assertFalse(result.isSuccess());
        assertEquals("NO_REDO_HISTORY", result.getErrorCode());
        assertEquals("No commands to redo", result.getMessage());
    }

    @Test
    @DisplayName("測試CommandInvoker - 執行新命令清空重做歷史")
    void testCommandInvokerNewCommandClearsRedoHistory() {
        // Given
        WriteCommand command1 = new WriteCommand(textReceiver, "First");
        WriteCommand command2 = new WriteCommand(textReceiver, " Second");
        WriteCommand command3 = new WriteCommand(textReceiver, " Third");

        commandInvoker.executeCommand(command1);
        commandInvoker.executeCommand(command2);
        commandInvoker.undoLastCommand();
        assertTrue(commandInvoker.canRedo());

        // When - 執行新命令
        commandInvoker.executeCommand(command3);

        // Then - 重做歷史被清空
        assertFalse(commandInvoker.canRedo());
        assertEquals("First Third", textReceiver.getContent());
    }

    @Test
    @DisplayName("測試CommandInvoker - 歷史統計")
    void testCommandInvokerHistoryStats() {
        // Given
        WriteCommand command1 = new WriteCommand(textReceiver, "Test1");
        WriteCommand command2 = new WriteCommand(textReceiver, "Test2");

        commandInvoker.executeCommand(command1);
        commandInvoker.executeCommand(command2);
        commandInvoker.undoLastCommand();

        // When
        var stats = commandInvoker.getHistoryStats();

        // Then
        assertEquals(1, stats.get("totalExecuted"));
        assertTrue((Boolean) stats.get("canUndo"));
        assertTrue((Boolean) stats.get("canRedo"));
        assertEquals(1, stats.get("undoHistorySize"));
    }

    @Test
    @DisplayName("測試CommandInvoker - 清空歷史")
    void testCommandInvokerClearHistory() {
        // Given
        WriteCommand command = new WriteCommand(textReceiver, "Test");
        commandInvoker.executeCommand(command);
        assertTrue(commandInvoker.canUndo());

        // When
        commandInvoker.clearHistory();

        // Then
        assertFalse(commandInvoker.canUndo());
        assertFalse(commandInvoker.canRedo());
        assertEquals(0, commandInvoker.getCommandHistory().size());
    }

    @Test
    @DisplayName("測試CommandResult建構")
    void testCommandResultBuilder() {
        // Test success result
        CommandResult successResult = CommandResult.success("Operation completed")
                .data("test data")
                .metadata("key", "value")
                .build();

        assertTrue(successResult.isSuccess());
        assertEquals("Operation completed", successResult.getMessage());
        assertEquals("test data", successResult.getData());
        assertEquals("value", successResult.getMetadata().get("key"));
        assertNotNull(successResult.getTimestamp());

        // Test failure result
        CommandResult failureResult = CommandResult.failure("Operation failed", "ERROR_CODE")
                .metadata("error", true)
                .build();

        assertFalse(failureResult.isSuccess());
        assertEquals("Operation failed", failureResult.getMessage());
        assertEquals("ERROR_CODE", failureResult.getErrorCode());
        assertEquals(true, failureResult.getMetadata().get("error"));
    }

    @Test
    @DisplayName("測試TextReceiver - 基本操作")
    void testTextReceiverBasicOperations() {
        // Test write
        textReceiver.write("Hello");
        assertEquals("Hello", textReceiver.getContent());
        assertEquals(5, textReceiver.getLength());

        // Test insert
        textReceiver.insert(5, " World");
        assertEquals("Hello World", textReceiver.getContent());

        // Test replace
        String replaced = textReceiver.replace(6, 11, "Universe");
        assertEquals("World", replaced);
        assertEquals("Hello Universe", textReceiver.getContent());

        // Test restore
        assertTrue(textReceiver.restore());
        assertEquals("Hello World", textReceiver.getContent());
    }

    @Test
    @DisplayName("測試TextReceiver - 邊界條件")
    void testTextReceiverBoundaryConditions() {
        textReceiver.write("Test");

        // Insert at negative position
        textReceiver.insert(-1, "Start");
        assertEquals("StartTest", textReceiver.getContent());

        // Insert beyond content length
        textReceiver.insert(100, "End");
        assertEquals("StartTestEnd", textReceiver.getContent());

        // Replace with invalid range
        String replaced = textReceiver.replace(5, 4, "X");
        assertEquals("t", replaced);
        assertTrue(textReceiver.getContent().contains("X"));
    }

    @Test
    @DisplayName("測試命令執行監聽器")
    void testCommandExecutionListener() {
        // Given
        TestCommandExecutionListener listener = new TestCommandExecutionListener();
        commandInvoker.addExecutionListener(listener);

        WriteCommand command = new WriteCommand(textReceiver, "Test");

        // When
        commandInvoker.executeCommand(command);
        commandInvoker.undoLastCommand();
        commandInvoker.redoLastCommand();

        // Then
        assertEquals(1, listener.beforeExecutionCount);
        assertEquals(1, listener.afterExecutionCount);
        assertEquals(1, listener.beforeUndoCount);
        assertEquals(1, listener.afterUndoCount);
        assertEquals(1, listener.beforeRedoCount);
        assertEquals(1, listener.afterRedoCount);
    }

    private static class TestCommandExecutionListener implements CommandInvoker.CommandExecutionListener {
        int beforeExecutionCount = 0;
        int afterExecutionCount = 0;
        int beforeUndoCount = 0;
        int afterUndoCount = 0;
        int beforeRedoCount = 0;
        int afterRedoCount = 0;

        @Override
        public void beforeExecution(Command command) {
            beforeExecutionCount++;
        }

        @Override
        public void afterExecution(Command command, CommandResult result) {
            afterExecutionCount++;
        }

        @Override
        public void beforeUndo(Command command) {
            beforeUndoCount++;
        }

        @Override
        public void afterUndo(Command command, CommandResult result) {
            afterUndoCount++;
        }

        @Override
        public void beforeRedo(Command command) {
            beforeRedoCount++;
        }

        @Override
        public void afterRedo(Command command, CommandResult result) {
            afterRedoCount++;
        }
    }

void testWriteCommandSuccess() {
    // Given
    WriteCommand command = new WriteCommand(textReceiver, "Hello World");

    // When
    CommandResult result = command.execute();

    // Then
    assertTrue(result.isSuccess());
    assertEquals("Hello World", textReceiver.getContent());
    assertEquals("Write text: 'Hello World'", command.getDescription());
    assertNotNull(command.getCommandId());
}

@Test
@DisplayName("測試WriteCommand - 撤銷操作")
void testWriteCommandUndo() {
    // Given
    textReceiver.write("Initial");
    WriteCommand command = new WriteCommand(textReceiver, " Text");
    command.execute();
    assertEquals("Initial Text", textReceiver.getContent());

    // When
    CommandResult undoResult = command.undo();

    // Then
    assertTrue(undoResult.isSuccess());
    assertEquals("Initial", textReceiver.getContent());
}

@Test
@DisplayName("測試DeleteCommand - 成功刪除")
void testDeleteCommandSuccess() {
    // Given
    textReceiver.write("Hello World");
    DeleteCommand command = new DeleteCommand(textReceiver, 5);

    // When
    CommandResult result = command.execute();

    // Then
    assertTrue(result.isSuccess());
    assertEquals("Hello ", textReceiver.getContent());
    assertEquals("World", command.getDeletedText());
    assertEquals("Delete 5 characters", command.getDescription());
}

@Test
@DisplayName("測試DeleteCommand - 刪除長度大於內容長度")
void testDeleteCommandExceedsLength() {
    // Given
    textReceiver.write("Hi");
    DeleteCommand command = new DeleteCommand(textReceiver, 10);

    // When
    CommandResult result = command.execute();

    // Then
    assertTrue(result.isSuccess());
    assertEquals("", textReceiver.getContent());
    assertEquals("Hi", command.getDeletedText());
}

@Test
@DisplayName("測試DeleteCommand - 無效刪除長度")
void testDeleteCommandInvalidLength() {
    // Given
    DeleteCommand command = new DeleteCommand(textReceiver, -1);

    // When
    CommandResult result = command.execute();

    // Then
    assertFalse(result.isSuccess());
    assertEquals("INVALID_PARAMETER", result.getErrorCode());
}

@Test
@DisplayName("測試DeleteCommand - 空內容刪除")
void testDeleteCommandEmptyContent() {
    // Given
    DeleteCommand command = new DeleteCommand(textReceiver, 5);

    // When
    CommandResult result = command.execute();

    // Then
    assertFalse(result.isSuccess());
    assertEquals("EMPTY_CONTENT", result.getErrorCode());
}

@Test
@DisplayName("測試ClearCommand - 成功清空")
void testClearCommandSuccess() {
    // Given
    textReceiver.write("Some content to clear");
    ClearCommand command = new ClearCommand(textReceiver);

    // When
    CommandResult result = command.execute();

    // Then
    assertTrue(result.isSuccess());
    assertEquals("", textReceiver.getContent());
    assertEquals("Some content to clear", command.getClearedContent());
    assertEquals("Clear all content", command.getDescription());
}

}
