package com.sr.accountposting.util;

import java.util.concurrent.ThreadLocalRandom;

public class IdGenerator {

    private IdGenerator() {
    }

    public static long nextId() {
        return System.currentTimeMillis() * 1000L
                + ThreadLocalRandom.current().nextLong(1000L);
    }

    public static long ttlEpochSeconds(int days) {
        return (System.currentTimeMillis() / 1000L) + ((long) days * 86400L);
    }
}
