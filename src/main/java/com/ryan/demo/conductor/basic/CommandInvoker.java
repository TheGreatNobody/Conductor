package com.ryan.demo.conductor.basic;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 命令調用者 - Invoker
 * 負責調用命令和管理命令歷史
 */
@Component
public class CommandInvoker {

    private final Stack<Command> commandHistory = new Stack<>();
    private final Stack<Command> undoHistory = new Stack<>();
    private final List<CommandExecutionListener> listeners = new ArrayList<>();

    /**
     * 執行命令
     */
    public CommandResult executeCommand(Command command) {
        try {
            // 清空重做歷史（執行新命令時）
            undoHistory.clear();

            // 通知監聽器命令即將執行
            notifyBeforeExecution(command);

            // 執行命令
            CommandResult result = command.execute();

            // 只有成功的命令才加入歷史
            if (result.isSuccess()) {
                commandHistory.push(command);
            }

            // 通知監聽器命令執行完成
            notifyAfterExecution(command, result);

            return result;

        } catch (Exception e) {
            CommandResult errorResult = CommandResult.failure("Command execution failed: " + e.getMessage(), "EXECUTION_ERROR")
                    .build();
            notifyAfterExecution(command, errorResult);
            return errorResult;
        }
    }

    /**
     * 撤銷上一個命令
     */
    public CommandResult undoLastCommand() {
        if (commandHistory.isEmpty()) {
            return CommandResult.failure("No commands to undo", "NO_HISTORY")
                    .build();
        }

        try {
            Command lastCommand = commandHistory.pop();

            // 通知監聽器即將撤銷
            notifyBeforeUndo(lastCommand);

            CommandResult result = lastCommand.undo();

            // 撤銷成功的命令加入重做歷史
            if (result.isSuccess()) {
                undoHistory.push(lastCommand);
            }

            // 通知監聽器撤銷完成
            notifyAfterUndo(lastCommand, result);

            return result;

        } catch (Exception e) {
            return CommandResult.failure("Undo operation failed: " + e.getMessage(), "UNDO_ERROR")
                    .build();
        }
    }

    /**
     * 重做上一個撤銷的命令
     */
    public CommandResult redoLastCommand() {
        if (undoHistory.isEmpty()) {
            return CommandResult.failure("No commands to redo", "NO_REDO_HISTORY")
                    .build();
        }

        try {
            Command commandToRedo = undoHistory.pop();

            // 通知監聽器即將重做
            notifyBeforeRedo(commandToRedo);

            CommandResult result = commandToRedo.execute();

            // 重做成功的命令重新加入執行歷史
            if (result.isSuccess()) {
                commandHistory.push(commandToRedo);
            }

            // 通知監聽器重做完成
            notifyAfterRedo(commandToRedo, result);

            return result;

        } catch (Exception e) {
            return CommandResult.failure("Redo operation failed: " + e.getMessage(), "REDO_ERROR")
                    .build();
        }
    }

    /**
     * 獲取命令歷史
     */
    public List<Command> getCommandHistory() {
        return new ArrayList<>(commandHistory);
    }

    /**
     * 獲取撤銷歷史
     */
    public List<Command> getUndoHistory() {
        return new ArrayList<>(undoHistory);
    }

    /**
     * 檢查是否可以撤銷
     */
    public boolean canUndo() {
        return !commandHistory.isEmpty();
    }

    /**
     * 檢查是否可以重做
     */
    public boolean canRedo() {
        return !undoHistory.isEmpty();
    }

    /**
     * 清空所有歷史
     */
    public void clearHistory() {
        commandHistory.clear();
        undoHistory.clear();
    }

    /**
     * 獲取歷史統計資訊
     */
    public Map<String, Object> getHistoryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalExecuted", commandHistory.size());
        stats.put("canUndo", canUndo());
        stats.put("canRedo", canRedo());
        stats.put("undoHistorySize", undoHistory.size());
        return stats;
    }

    // 監聽器相關方法
    public void addExecutionListener(CommandExecutionListener listener) {
        listeners.add(listener);
    }

    public void removeExecutionListener(CommandExecutionListener listener) {
        listeners.remove(listener);
    }

    private void notifyBeforeExecution(Command command) {
        for (CommandExecutionListener listener : listeners) {
            try {
                listener.beforeExecution(command);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    private void notifyAfterExecution(Command command, CommandResult result) {
        for (CommandExecutionListener listener : listeners) {
            try {
                listener.afterExecution(command, result);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    private void notifyBeforeUndo(Command command) {
        for (CommandExecutionListener listener : listeners) {
            try {
                listener.beforeUndo(command);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    private void notifyAfterUndo(Command command, CommandResult result) {
        for (CommandExecutionListener listener : listeners) {
            try {
                listener.afterUndo(command, result);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    private void notifyBeforeRedo(Command command) {
        for (CommandExecutionListener listener : listeners) {
            try {
                listener.beforeRedo(command);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    private void notifyAfterRedo(Command command, CommandResult result) {
        for (CommandExecutionListener listener : listeners) {
            try {
                listener.afterRedo(command, result);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    /**
     * 命令執行監聽器介面
     */
    public interface CommandExecutionListener {
        default void beforeExecution(Command command) {}
        default void afterExecution(Command command, CommandResult result) {}
        default void beforeUndo(Command command) {}
        default void afterUndo(Command command, CommandResult result) {}
        default void beforeRedo(Command command) {}
        default void afterRedo(Command command, CommandResult result) {}
    }
}