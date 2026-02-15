package com.flashsale.worker;

import com.flashsale.flashsale.service.FlashSaleCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CacheRefreshScheduler {

    private final FlashSaleCacheService flashSaleCacheService;

    @Scheduled(fixedRate = 30_000)
    public void refreshCache() {
        flashSaleCacheService.refreshCache();
    }
}
