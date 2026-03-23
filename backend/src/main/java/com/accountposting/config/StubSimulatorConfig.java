package com.accountposting.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store for controlling which external systems the stub should simulate as failing.
 * Configured at runtime via StubSimulatorController (disabled in prod).
 */
@Component
public class StubSimulatorConfig {

    private final Set<String> failingSystems = ConcurrentHashMap.newKeySet();

    public void configure(List<String> systems) {
        failingSystems.clear();
        if (systems != null) {
            failingSystems.addAll(systems);
        }
    }

    public void clear() {
        failingSystems.clear();
    }

    public boolean shouldFail(String system) {
        return failingSystems.contains(system);
    }

    public Set<String> getFailingSystems() {
        return Set.copyOf(failingSystems);
    }
}
