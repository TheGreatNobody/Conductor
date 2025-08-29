package com.ryan.demo.conductor.basic;

/**
 * 命令介面 - Command
 * 定義執行操作的標準介面
 */
public interface Command {

    /**
     * 執行命令
     * @return 命令執行結果
     */
    CommandResult execute();

    /**
     * 撤銷命令
     * @return 撤銷執行結果
     */
    CommandResult undo();

    /**
     * 獲取命令描述
     * @return 命令描述
     */
    String getDescription();

    /**
     * 獲取命令ID
     * @return 命令ID
     */
    String getCommandId();
}