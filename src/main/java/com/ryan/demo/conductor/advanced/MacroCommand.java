package com.ryan.demo.conductor.advanced;

import com.ryan.demo.conductor.basic.Command;
import com.ryan.demo.conductor.basic.CommandResult;
import java.util.*;

/**
 * 巨集命令 - MacroCommand
 * 組合多個命令為一個複合命令
 */
public class MacroCommand implements Command {

    private final String commandId;
    private final String name;
    private final List<Command> commands;
    private final List<CommandResult> executionResults;
    private final boolean stopOnFailure;

    public MacroCommand(String name, List<Command> commands) {
        this(name, commands, true);
    }

    public MacroCommand(String name, List<Command> commands, boolean stopOnFailure) {
        this.commandId = UUID.randomUUID().toString();
        this.name = name;
        this.commands = new ArrayList<>(commands);
        this.executionResults = new ArrayList<>();
        this.stopOnFailure = stopOnFailure;
    }

    @Override
    public CommandResult execute() {
        executionResults.clear();
        List<String> successfulCommands = new ArrayList<>();
        List<String> failedCommands = new ArrayList<>();

        try {
            for (int i = 0; i < commands.size(); i++) {
                Command command = commands.get(i);
                CommandResult result = command.execute();
                executionResults.add(result);

                if (result.isSuccess()) {
                    successfulCommands.add(command.getDescription());
                } else {
                    failedCommands.add(command.getDescription() + " - " + result.getMessage());

                    // 如果設定為失敗時停止，則停止執行後續命令
                    if (stopOnFailure) {
                        break;
                    }
                }
            }

            boolean overallSuccess = failedCommands.isEmpty();
            String message = String.format("Macro command '%s' executed: %d successful, %d failed",
                    name, successfulCommands.size(), failedCommands.size());

            CommandResult.Builder resultBuilder = overallSuccess ?
                    CommandResult.success(message) :
                    CommandResult.failure(message, "MACRO_EXECUTION_FAILED");

            return resultBuilder
                    .metadata("totalCommands", commands.size())
                    .metadata("successfulCommands", successfulCommands)
                    .metadata("failedCommands", failedCommands)
                    .metadata("executionResults", executionResults)
                    .build();

        } catch (Exception e) {
            return CommandResult.failure("Macro command execution failed: " + e.getMessage(), "MACRO_ERROR")
                    .metadata("executionResults", executionResults)
                    .build();
        }
    }

    @Override
    public CommandResult undo() {
        if (executionResults.isEmpty()) {
            return CommandResult.failure("Cannot undo macro command: no execution history", "NO_HISTORY")
                    .build();
        }

        try {
            List<String> undoResults = new ArrayList<>();
            int undoCount = 0;

            // 反向撤銷已成功執行的命令
            for (int i = Math.min(executionResults.size(), commands.size()) - 1; i >= 0; i--) {
                CommandResult executionResult = executionResults.get(i);

                // 只撤銷成功執行的命令
                if (executionResult.isSuccess()) {
                    Command command = commands.get(i);
                    CommandResult undoResult = command.undo();

                    if (undoResult.isSuccess()) {
                        undoResults.add("Undid: " + command.getDescription());
                        undoCount++;
                    } else {
                        undoResults.add("Failed to undo: " + command.getDescription() + " - " + undoResult.getMessage());
                    }
                }
            }

            String message = String.format("Macro command '%s' undo completed: %d commands undone",
                    name, undoCount);

            return CommandResult.success(message)
                    .metadata("undoResults", undoResults)
                    .metadata("totalUndone", undoCount)
                    .build();

        } catch (Exception e) {
            return CommandResult.failure("Macro command undo failed: " + e.getMessage(), "MACRO_UNDO_ERROR")
                    .build();
        }
    }

    @Override
    public String getDescription() {
        return String.format("Macro: %s (%d commands)", name, commands.size());
    }

    @Override
    public String getCommandId() {
        return commandId;
    }

    public String getName() {
        return name;
    }

    public List<Command> getCommands() {
        return new ArrayList<>(commands);
    }

    public List<CommandResult> getExecutionResults() {
        return new ArrayList<>(executionResults);
    }

    public boolean isStopOnFailure() {
        return stopOnFailure;
    }

    /**
     * 建立器模式建立巨集命令
     */
    public static class Builder {
        private String name;
        private final List<Command> commands = new ArrayList<>();
        private boolean stopOnFailure = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder addCommand(Command command) {
            this.commands.add(command);
            return this;
        }

        public Builder addCommands(List<Command> commands) {
            this.commands.addAll(commands);
            return this;
        }

        public Builder stopOnFailure(boolean stopOnFailure) {
            this.stopOnFailure = stopOnFailure;
            return this;
        }

        public MacroCommand build() {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Macro command name cannot be null or empty");
            }

            if (commands.isEmpty()) {
                throw new IllegalArgumentException("Macro command must contain at least one command");
            }

            return new MacroCommand(name, commands, stopOnFailure);
        }
    }

    @Override
    public String toString() {
        return "MacroCommand{" +
                "commandId='" + commandId + '\'' +
                ", name='" + name + '\'' +
                ", commandCount=" + commands.size() +
                ", stopOnFailure=" + stopOnFailure +
                '}';
    }
}