package com.sr.accountposting.enums;

public enum RequestMode {
    NORM,   // New posting — create legs then process
    RETRY   // Retry — fetch non-SUCCESS legs and reprocess
}
