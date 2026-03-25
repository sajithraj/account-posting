package com.accountposting.controller;

import com.accountposting.config.StubSimulatorConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Test-only endpoint to configure which external systems the stub should simulate as failing.
 * Excluded from prod via @Profile("!prod") — never deployed to production.
 * <p>
 * Supported system keys (must match posting_config.target_system + "_" + operation):
 * CBS_POSTING, GL_POSTING, OBPM_POSTING
 */
@Profile("!prod")
@RestController
@RequestMapping("/v2/payment/test/stub")
@RequiredArgsConstructor
public class StubSimulatorControllerV2 {

    private final StubSimulatorConfig simulatorConfig;

    /**
     * Configure which systems should simulate failure.
     * POST /v2/payment/test/stub/configure
     * Body: { "fail_systems": ["CBS_POSTING", "GL_POSTING"] }
     */
    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configure(@RequestBody Map<String, List<String>> body) {
        List<String> systems = body.getOrDefault("fail_systems", List.of());
        simulatorConfig.configure(systems);
        return ResponseEntity.ok(Map.of(
                "message", "Stub simulator configured",
                "failing_systems", simulatorConfig.getFailingSystems()
        ));
    }

    /**
     * Clear all failure simulations — all stubs return SUCCESS again.
     * POST /v2/payment/test/stub/clear
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear() {
        simulatorConfig.clear();
        return ResponseEntity.ok(Map.of(
                "message", "Stub simulator cleared",
                "failing_systems", List.of()
        ));
    }

    /**
     * Check current simulator state.
     * GET /v2/payment/test/stub/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "failing_systems", simulatorConfig.getFailingSystems()
        ));
    }
}
