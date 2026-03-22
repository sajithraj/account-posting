package com.accountposting.controller;

import com.accountposting.dto.config.PostingConfigRequest;
import com.accountposting.dto.config.PostingConfigResponse;
import com.accountposting.service.PostingConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/account-posting/config")
@RequiredArgsConstructor
public class PostingConfigController {

    private final PostingConfigService postingConfigService;

    @GetMapping
    public ResponseEntity<List<PostingConfigResponse>> getAll() {
        return ResponseEntity.ok(postingConfigService.getAll());
    }

    @GetMapping("/{requestType}")
    public ResponseEntity<List<PostingConfigResponse>> getByRequestType(
            @PathVariable String requestType) {
        return ResponseEntity.ok(postingConfigService.getByRequestType(requestType));
    }

    @PostMapping
    public ResponseEntity<PostingConfigResponse> create(
            @Valid @RequestBody PostingConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postingConfigService.create(request));
    }

    @PutMapping("/{configId}")
    public ResponseEntity<PostingConfigResponse> update(
            @PathVariable Long configId,
            @Valid @RequestBody PostingConfigRequest request) {
        return ResponseEntity.ok(postingConfigService.update(configId, request));
    }

    @DeleteMapping("/{configId}")
    public ResponseEntity<Void> delete(@PathVariable Long configId) {
        postingConfigService.delete(configId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cache/flush")
    public ResponseEntity<Void> flushCache() {
        postingConfigService.flushCache();
        return ResponseEntity.noContent().build();
    }
}
