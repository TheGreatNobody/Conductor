package com.ryan.demo.conductor.basic;

import java.util.UUID;

/**
 * 清空命令 - ConcreteCommand
 * 實作文字清空的具體命令
 */
public class ClearCommand implements Command {

    private final String commandId;
    private final TextReceiver receiver;
    private String clearedContent;

    public ClearCommand(TextReceiver receiver) {
        this.commandId = UUID.randomUUID().toString();
        this.receiver = receiver;
    }

    @Override
    public CommandResult execute() {
        try {
            int previousLength = receiver.getLength();
            clearedContent = receiver.clear();

            return CommandResult.success("Content cleared successfully")
                    .data(receiver.getContent())
                    .metadata("clearedContent", clearedContent)
                    .metadata("clearedLength", previousLength)
                    .build();

        } catch (Exception e) {
            return CommandResult.failure("Failed to clear content: " + e.getMessage(), "CLEAR_ERROR")
                    .build();
        }
    }

    @Override
    public CommandResult undo() {
        try {
            if (receiver.restore()) {
                return CommandResult.success("Clear operation undone successfully")
                        .data(receiver.getContent())
                        .metadata("restoredContent", clearedContent)
                        .metadata("restoredLength", receiver.getLength())
                        .build();
            } else {
                return CommandResult.failure("Cannot undo clear operation: no history available", "UNDO_ERROR")
                        .build();
            }
        } catch (Exception e) {
            return CommandResult.failure("Failed to undo clear operation: " + e.getMessage(), "UNDO_ERROR")
                    .build();
        }
    }

    @Override
    public String getDescription() {
        return "Clear all content";
    }

    @Override
    public String getCommandId() {
        return commandId;
    }

    public String getClearedContent() {
        return clearedContent;
    }

    @Override
    public String toString() {
        return "ClearCommand{" +
                "commandId='" + commandId + '\'' +
                ", clearedContent='" + clearedContent + '\'' +
                '}';
    }
}