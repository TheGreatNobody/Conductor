package com.ryan.demo.conductor.basic;

import java.util.UUID;

/**
 * 寫入命令 - ConcreteCommand
 * 實作文字寫入的具體命令
 */
public class WriteCommand implements Command {

    private final String commandId;
    private final TextReceiver receiver;
    private final String text;
    private int previousLength;

    public WriteCommand(TextReceiver receiver, String text) {
        this.commandId = UUID.randomUUID().toString();
        this.receiver = receiver;
        this.text = text;
    }

    @Override
    public CommandResult execute() {
        try {
            previousLength = receiver.getLength();
            receiver.write(text);

            return CommandResult.success("Text written successfully")
                    .data(receiver.getContent())
                    .metadata("textLength", text.length())
                    .metadata("totalLength", receiver.getLength())
                    .build();

        } catch (Exception e) {
            return CommandResult.failure("Failed to write text: " + e.getMessage(), "WRITE_ERROR")
                    .build();
        }
    }

    @Override
    public CommandResult undo() {
        try {
            if (receiver.restore()) {
                return CommandResult.success("Write operation undone successfully")
                        .data(receiver.getContent())
                        .metadata("restoredLength", receiver.getLength())
                        .build();
            } else {
                return CommandResult.failure("Cannot undo write operation: no history available", "UNDO_ERROR")
                        .build();
            }
        } catch (Exception e) {
            return CommandResult.failure("Failed to undo write operation: " + e.getMessage(), "UNDO_ERROR")
                    .build();
        }
    }

    @Override
    public String getDescription() {
        return "Write text: '" + text + "'";
    }

    @Override
    public String getCommandId() {
        return commandId;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "WriteCommand{" +
                "commandId='" + commandId + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}