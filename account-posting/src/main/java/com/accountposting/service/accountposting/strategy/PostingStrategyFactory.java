package com.accountposting.service.accountposting.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the correct {@link PostingStrategy} by posting flow key.
 * All {@code PostingStrategy} beans are auto-collected by Spring and indexed
 * by {@link PostingStrategy#getPostingFlow()} (e.g. {@code CBS_POSTING}),
 * which must match {@code target_system + "_" + operation} in the {@code posting_config} table.
 */
@Slf4j
@Component
public class PostingStrategyFactory {

    private final Map<String, PostingStrategy> strategies;

    public PostingStrategyFactory(List<PostingStrategy> strategyList) {
        strategies = strategyList.stream()
                .collect(Collectors.toMap(PostingStrategy::getPostingFlow, Function.identity()));
        log.info("Registered posting strategies: {}", strategies.keySet());
    }

    public PostingStrategy resolve(String targetSystem) {
        PostingStrategy strategy = strategies.get(targetSystem);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No posting strategy found for target system: " + targetSystem);
        }
        return strategy;
    }
}
