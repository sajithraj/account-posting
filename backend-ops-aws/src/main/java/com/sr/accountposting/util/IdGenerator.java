package com.sr.accountposting.util;

import java.util.UUID;

public class IdGenerator {

    private IdGenerator() {
    }

    public static String nextId() {
        return UUID.randomUUID().toString();
    }

    public static long ttlEpochSeconds(int days) {
        return (System.currentTimeMillis() / 1000L) + ((long) days * 86400L);
    }
}
