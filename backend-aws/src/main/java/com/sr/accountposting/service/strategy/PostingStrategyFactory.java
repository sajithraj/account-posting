package com.sr.accountposting.service.strategy;

import com.sr.accountposting.service.strategy.impl.CBSAddHoldStrategy;
import com.sr.accountposting.service.strategy.impl.CBSPostingStrategy;
import com.sr.accountposting.service.strategy.impl.CBSRemoveHoldStrategy;
import com.sr.accountposting.service.strategy.impl.GLPostingStrategy;
import com.sr.accountposting.service.strategy.impl.OBPMPostingStrategy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class PostingStrategyFactory {

    private final Map<String, PostingStrategy> registry = new HashMap<>();

    @Inject
    public PostingStrategyFactory(CBSPostingStrategy cbs,
                                  GLPostingStrategy gl,
                                  OBPMPostingStrategy obpm,
                                  CBSAddHoldStrategy cbsAddHold,
                                  CBSRemoveHoldStrategy cbsRemoveHold) {
        List.of(cbs, gl, obpm, cbsAddHold, cbsRemoveHold)
                .forEach(s -> registry.put(s.getFlowKey(), s));
    }

    public PostingStrategy get(String targetSystem, String operation) {
        String key = targetSystem + "_" + operation;
        PostingStrategy strategy = registry.get(key);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No strategy registered for flow key: " + key);
        }
        return strategy;
    }
}
