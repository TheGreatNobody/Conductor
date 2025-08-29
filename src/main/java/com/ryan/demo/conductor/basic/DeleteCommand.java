package com.ryan.demo.conductor.basic;

import java.util.UUID;

/**
 * 刪除命令 - ConcreteCommand
 * 實作文字刪除的具體命令
 */
public class DeleteCommand implements Command {

    private final String commandId;
    private final TextReceiver receiver;
    private final int length;
    private String deletedText;

    public DeleteCommand(TextReceiver receiver, int length) {
        this.commandId = UUID.randomUUID().toString();
        this.receiver = receiver;
        this.length = length;
    }

    @Override
    public CommandResult execute() {
        try {
            if (length <= 0) {
                return CommandResult.failure("Invalid delete length: " + length, "INVALID_PARAMETER")
                        .build();
            }

            if (receiver.getLength() == 0) {
                return CommandResult.failure("Cannot delete from empty content", "EMPTY_CONTENT")
                        .build();
            }

            deletedText = receiver.delete(length);

            return CommandResult.success("Text deleted successfully")
                    .data(receiver.getContent())
                    .metadata("deletedText", deletedText)
                    .metadata("deletedLength", deletedText.length())
                    .metadata("remainingLength", receiver.getLength())
                    .build();

        } catch (Exception e) {
            return CommandResult.failure("Failed to delete text: " + e.getMessage(), "DELETE_ERROR")
                    .build();
        }
    }

    @Override
    public CommandResult undo() {
        try {
            if (receiver.restore()) {
                return CommandResult.success("Delete operation undone successfully")
                        .data(receiver.getContent())
                        .metadata("restoredText", deletedText)
                        .metadata("currentLength", receiver.getLength())
                        .build();
            } else {
                return CommandResult.failure("Cannot undo delete operation: no history available", "UNDO_ERROR")
                        .build();
            }
        } catch (Exception e) {
            return CommandResult.failure("Failed to undo delete operation: " + e.getMessage(), "UNDO_ERROR")
                    .build();
        }
    }

    @Override
    public String getDescription() {
        return "Delete " + length + " characters";
    }

    @Override
    public String getCommandId() {
        return commandId;
    }

    public int getLength() {
        return length;
    }

    public String getDeletedText() {
        return deletedText;
    }

    @Override
    public String toString() {
        return "DeleteCommand{" +
                "commandId='" + commandId + '\'' +
                ", length=" + length +
                ", deletedText='" + deletedText + '\'' +
                '}';
    }
}