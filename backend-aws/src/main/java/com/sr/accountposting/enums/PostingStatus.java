package com.sr.accountposting.enums;

public enum PostingStatus {
    RCVD,   // Async: validated + queued to SQS, awaiting processing
    PNDG,       // Processed but one or more legs failed — needs retry
    ACSP,       // All legs succeeded
    RJCT        // Sync processing rejected by core system
}
