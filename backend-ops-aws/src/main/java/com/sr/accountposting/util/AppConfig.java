package com.sr.accountposting.util;

public final class AppConfig {

    public static final String AWS_REGION = env("AWS_ACCOUNT_REGION", "ap-southeast-1");
    public static final String QUEUE_URL = env("PROCESSING_QUEUE_URL", null);
    public static final String POSTING_TABLE = env("POSTING_TABLE_NAME", null);
    public static final String LEG_TABLE = env("LEG_TABLE_NAME", null);
    public static final String CONFIG_TABLE = env("CONFIG_TABLE_NAME", null);
    public static final int TTL_DAYS = Integer.parseInt(env("DYNAMO_TTL_DAYS", "60"));
    public static final long RETRY_LOCK_TTL_MS = 5 * 60 * 1000L;

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v != null) return v;
        v = System.getProperty(key);
        return v != null ? v : defaultValue;
    }

    private AppConfig() {
    }
}
