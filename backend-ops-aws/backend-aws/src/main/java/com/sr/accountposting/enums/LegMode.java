package com.sr.accountposting.enums;

public enum LegMode {
    NORM,   // Normal first-time processing
    RETRY,  // Automated retry via SQS
    MANUAL  // Manual override via dashboard PATCH endpoint
}
