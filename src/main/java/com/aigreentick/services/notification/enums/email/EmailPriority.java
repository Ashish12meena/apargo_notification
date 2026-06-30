package com.aigreentick.services.notification.enums.email;

public enum EmailPriority {
    HIGHEST(1),
    HIGH(2),
    NORMAL(3),
    LOW(4),
    LOWEST(5);

    private final int value;

    EmailPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}  