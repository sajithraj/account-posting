package com.sajith.payments.redesign.controller;

import com.sajith.payments.redesign.dto.config.PostingConfigRequestV2;
import com.sajith.payments.redesign.dto.config.PostingConfigResponseV2;
import com.sajith.payments.redesign.service.config.PostingConfigServiceV2;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v2/payment/account-posting/config")
@RequiredArgsConstructor
public class PostingConfigControllerV2 {

    private final PostingConfigServiceV2 postingConfigService;

    @GetMapping
    public ResponseEntity<List<PostingConfigResponseV2>> getAll() {
        return ResponseEntity.ok(postingConfigService.getAll());
    }

    @GetMapping("/{requestType}")
    public ResponseEntity<List<PostingConfigResponseV2>> getByRequestType(
            @PathVariable String requestType) {
        return ResponseEntity.ok(postingConfigService.getByRequestType(requestType));
    }

    @PostMapping
    public ResponseEntity<PostingConfigResponseV2> create(
            @Valid @RequestBody PostingConfigRequestV2 request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postingConfigService.create(request));
    }

    @PutMapping("/{configId}")
    public ResponseEntity<PostingConfigResponseV2> update(
            @PathVariable Long configId,
            @Valid @RequestBody PostingConfigRequestV2 request) {
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
