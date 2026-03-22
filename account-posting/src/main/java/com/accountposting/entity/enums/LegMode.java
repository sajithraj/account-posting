package com.accountposting.entity.enums;

public enum LegMode {
    /**
     * Initial posting call from the create flow.
     */
    NORM,
    /**
     * Leg was re-executed via the retry flow.
     */
    RETRY,
    /**
     * Status was manually updated from the UI.
     */
    MANUAL
}
