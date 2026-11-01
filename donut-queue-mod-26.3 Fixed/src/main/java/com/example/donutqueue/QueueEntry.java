package com.example.donutqueue;

/**
 * A single person waiting in line, created the moment a payment message
 * from them is detected in chat.
 */
public class QueueEntry {
    public String playerName;
    public String amount;
    public long timestamp;

    // No-arg constructor kept for Gson.
    public QueueEntry() {
    }

    public QueueEntry(String playerName, String amount, long timestamp) {
        this.playerName = playerName;
        this.amount = amount;
        this.timestamp = timestamp;
    }
}
