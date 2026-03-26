package com.sajith.payments.redesign.service.accountposting.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
