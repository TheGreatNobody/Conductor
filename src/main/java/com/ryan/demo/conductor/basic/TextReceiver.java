package com.ryan.demo.conductor.basic;

import org.springframework.stereotype.Component;
import java.util.Stack;

/**
 * 文字接收者 - Receiver
 * 知道如何執行與文字相關的操作
 */
@Component
public class TextReceiver {

    private StringBuilder content = new StringBuilder();
    private final Stack<String> history = new Stack<>();

    /**
     * 寫入文字
     */
    public void write(String text) {
        history.push(content.toString());
        content.append(text);
    }

    /**
     * 刪除文字
     */
    public String delete(int length) {
        history.push(content.toString());

        if (length > content.length()) {
            String deleted = content.toString();
            content.setLength(0);
            return deleted;
        }

        int start = content.length() - length;
        String deleted = content.substring(start);
        content.delete(start, content.length());
        return deleted;
    }

    /**
     * 插入文字
     */
    public void insert(int position, String text) {
        history.push(content.toString());

        if (position < 0) position = 0;
        if (position > content.length()) position = content.length();

        content.insert(position, text);
    }

    /**
     * 替換文字
     */
    public String replace(int start, int end, String newText) {
        history.push(content.toString());

        if (start < 0) start = 0;
        if (end > content.length()) end = content.length();
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }

        String oldText = content.substring(start, end);
        content.replace(start, end, newText);
        return oldText;
    }

    /**
     * 清空內容
     */
    public String clear() {
        history.push(content.toString());
        String oldContent = content.toString();
        content.setLength(0);
        return oldContent;
    }

    /**
     * 回復到上一個狀態
     */
    public boolean restore() {
        if (!history.isEmpty()) {
            content = new StringBuilder(history.pop());
            return true;
        }
        return false;
    }

    /**
     * 獲取當前內容
     */
    public String getContent() {
        return content.toString();
    }

    /**
     * 獲取內容長度
     */
    public int getLength() {
        return content.length();
    }

    /**
     * 重置接收者狀態
     */
    public void reset() {
        content.setLength(0);
        history.clear();
    }

    @Override
    public String toString() {
        return "TextReceiver{" +
                "content='" + content + '\'' +
                ", historySize=" + history.size() +
                '}';
    }
}