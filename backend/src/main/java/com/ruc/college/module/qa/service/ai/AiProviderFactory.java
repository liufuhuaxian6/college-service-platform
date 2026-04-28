package com.ruc.college.module.qa.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 提供者工厂：根据配置选择对应的 AI 实现
 */
@Component
@RequiredArgsConstructor
public class AiProviderFactory {

    @Value("${ai.provider:none}")
    private String providerName;

    private final List<AiProvider> providers;

    public AiProvider getProvider() {
        Map<String, AiProvider> providerMap = providers.stream()
                .collect(Collectors.toMap(AiProvider::getName, Function.identity()));

        AiProvider provider = providerMap.get(providerName);
        if (provider != null && provider.isAvailable()) {
            return provider;
        }
        // 回退到 noop
        return providerMap.get("none");
    }
}
