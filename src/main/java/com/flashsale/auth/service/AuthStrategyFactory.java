package com.flashsale.auth.service;

import com.flashsale.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AuthStrategyFactory {

    private final List<AuthStrategy> strategies;

    public AuthStrategyFactory(List<AuthStrategy> strategies) {
        this.strategies = strategies;
        log.info("Registered {} auth strategies: {}", strategies.size(),
                strategies.stream().map(s -> s.getClass().getSimpleName()).toList());
    }

    /**
     * Resolve the correct strategy by probing each one with the identifier.
     * First match wins — strategies are ordered by Spring's natural ordering.
     */
    public AuthStrategy resolve(String identifier) {
        return strategies.stream()
                .filter(s -> s.supports(identifier))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Unrecognized identifier format. Provide a valid email or phone number."));
    }
}
