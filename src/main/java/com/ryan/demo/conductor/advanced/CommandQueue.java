package com.ryan.demo.conductor.advanced;

import com.ryan.demo.conductor.basic.Command;
import com.ryan.demo.conductor.basic.CommandResult;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

/**
 * 命令佇列 - CommandQueue
 * 支援非同步命令執行和批次處理
 */
@Component
public class CommandQueue {

    private final BlockingQueue<QueuedCommand> commandQueue = new LinkedBlockingQueue<>();
    private final Map<String, CompletableFuture<CommandResult>> pendingCommands = new ConcurrentHashMap<>();
    private final List<CommandQueueListener> listeners = new ArrayList<>();
    private final ExecutorService executorService;
    private volatile boolean isRunning = false;

    public CommandQueue() {
        this.executorService = Executors.newFixedThreadPool(5);
        startProcessing();
    }

    /**
     * 提交命令到佇列
     */
    public CompletableFuture<CommandResult> submitCommand(Command command) {
        return submitCommand(command, CommandPriority.NORMAL);
    }

    /**
     * 提交命令到佇列（指定優先級）
     */
    public CompletableFuture<CommandResult> submitCommand(Command command, CommandPriority priority) {
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        QueuedCommand queuedCommand = new QueuedCommand(command, priority, future);

        try {
            commandQueue.offer(queuedCommand);
            pendingCommands.put(command.getCommandId(), future);

            notifyCommandQueued(command, priority);

            return future;

        } catch (Exception e) {
            future.complete(CommandResult.failure("Failed to queue command: " + e.getMessage(), "QUEUE_ERROR").build());
            return future;
        }
    }

    /**
     * 批次提交命令
     */
    public List<CompletableFuture<CommandResult>> submitCommands(List<Command> commands) {
        return submitCommands(commands, CommandPriority.NORMAL);
    }

    /**
     * 批次提交命令（指定優先級）
     */
    public List<CompletableFuture<CommandResult>> submitCommands(List<Command> commands, CommandPriority priority) {
        List<CompletableFuture<CommandResult>> futures = new ArrayList<>();

        for (Command command : commands) {
            futures.add(submitCommand(command, priority));
        }

        return futures;
    }

    /**
     * 等待所有佇列中的命令完成
     */
    public CompletableFuture<List<CommandResult>> waitForAll(List<CompletableFuture<CommandResult>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * 取消待執行的命令
     */
    public boolean cancelCommand(String commandId) {
        CompletableFuture<CommandResult> future = pendingCommands.remove(commandId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            return true;
        }
        return false;
    }

    /**
     * 獲取佇列狀態
     */
    public QueueStatus getQueueStatus() {
        return new QueueStatus(
                commandQueue.size(),
                pendingCommands.size(),
                isRunning,
                ((ThreadPoolExecutor) executorService).getActiveCount(),
                ((ThreadPoolExecutor) executorService).getCompletedTaskCount()
        );
    }

    /**
     * 清空佇列
     */
    public void clearQueue() {
        commandQueue.clear();

        // 取消所有待執行的命令
        for (CompletableFuture<CommandResult> future : pendingCommands.values()) {
            if (!future.isDone()) {
                future.cancel(false);
            }
        }
        pendingCommands.clear();
    }

    /**
     * 暫停佇列處理
     */
    public void pauseProcessing() {
        isRunning = false;
    }

    /**
     * 恢復佇列處理
     */
    public void resumeProcessing() {
        if (!isRunning) {
            isRunning = true;
            startProcessing();
        }
    }

    /**
     * 關閉佇列
     */
    public void shutdown() {
        isRunning = false;
        clearQueue();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 開始處理佇列
     */
    private void startProcessing() {
        isRunning = true;

        executorService.submit(() -> {
            while (isRunning) {
                try {
                    QueuedCommand queuedCommand = commandQueue.poll(1, TimeUnit.SECONDS);
                    if (queuedCommand != null) {
                        processCommand(queuedCommand);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // 記錄錯誤但繼續處理
                    System.err.println("Error processing command queue: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 處理單個命令
     */
    private void processCommand(QueuedCommand queuedCommand) {
        Command command = queuedCommand.getCommand();
        CompletableFuture<CommandResult> future = queuedCommand.getFuture();

        try {
            notifyCommandStarted(command);

            CommandResult result = command.execute();

            pendingCommands.remove(command.getCommandId());
            future.complete(result);

            notifyCommandCompleted(command, result);

        } catch (Exception e) {
            CommandResult errorResult = CommandResult.failure("Command execution failed: " + e.getMessage(), "EXECUTION_ERROR").build();

            pendingCommands.remove(command.getCommandId());
            future.complete(errorResult);

            notifyCommandCompleted(command, errorResult);
        }
    }

    // 監聽器相關方法
    public void addQueueListener(CommandQueueListener listener) {
        listeners.add(listener);
    }

    public void removeQueueListener(CommandQueueListener listener) {
        listeners.remove(listener);
    }

    private void notifyCommandQueued(Command command, CommandPriority priority) {
        for (CommandQueueListener listener : listeners) {
            try {
                listener.onCommandQueued(command, priority);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    private void notifyCommandStarted(Command command) {
        for (CommandQueueListener listener : listeners) {
            try {
                listener.onCommandStarted(command);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    private void notifyCommandCompleted(Command command, CommandResult result) {
        for (CommandQueueListener listener : listeners) {
            try {
                listener.onCommandCompleted(command, result);
            } catch (Exception e) {
                // 忽略監聽器異常
            }
        }
    }

    /**
     * 命令優先級
     */
    public enum CommandPriority {
        LOW(1), NORMAL(2), HIGH(3), URGENT(4);

        private final int level;

        CommandPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * 佇列中的命令包裝
     */
    private static class QueuedCommand {
        private final Command command;
        private final CommandPriority priority;
        private final CompletableFuture<CommandResult> future;
        private final long timestamp;

        public QueuedCommand(Command command, CommandPriority priority, CompletableFuture<CommandResult> future) {
            this.command = command;
            this.priority = priority;
            this.future = future;
            this.timestamp = System.currentTimeMillis();
        }

        public Command getCommand() { return command; }
        public CommandPriority getPriority() { return priority; }
        public CompletableFuture<CommandResult> getFuture() { return future; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 佇列狀態
     */
    public static class QueueStatus {
        private final int queueSize;
        private final int pendingCount;
        private final boolean running;
        private final int activeThreads;
        private final long completedTasks;

        public QueueStatus(int queueSize, int pendingCount, boolean running, int activeThreads, long completedTasks) {
            this.queueSize = queueSize;
            this.pendingCount = pendingCount;
            this.running = running;
            this.activeThreads = activeThreads;
            this.completedTasks = completedTasks;
        }

        public int getQueueSize() { return queueSize; }
        public int getPendingCount() { return pendingCount; }
        public boolean isRunning() { return running; }
        public int getActiveThreads() { return activeThreads; }
        public long getCompletedTasks() { return completedTasks; }

        @Override
        public String toString() {
            return "QueueStatus{" +
                    "queueSize=" + queueSize +
                    ", pendingCount=" + pendingCount +
                    ", running=" + running +
                    ", activeThreads=" + activeThreads +
                    ", completedTasks=" + completedTasks +
                    '}';
        }
    }

    /**
     * 命令佇列監聽器
     */
    public interface CommandQueueListener {
        default void onCommandQueued(Command command, CommandPriority priority) {}
        default void onCommandStarted(Command command) {}
        default void onCommandCompleted(Command command, CommandResult result) {}
    }
}