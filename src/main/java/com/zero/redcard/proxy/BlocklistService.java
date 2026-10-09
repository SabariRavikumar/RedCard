package com.zero.redcard.proxy;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import com.zero.redcard.records.BlocklistEntry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class BlocklistService {

    private final static String BLOCKED_HOSTS_SET_KEY = "blocked:sites";

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(BlocklistService.class);

    public BlocklistService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isBlocked(String host) {
        String normalizedHost = host.toLowerCase();
        String unlockedHost = "unlocked:" + normalizedHost;

        return redisTemplate.opsForHash().hasKey(BLOCKED_HOSTS_SET_KEY, normalizedHost)
                .flatMap(onBlockedList -> {
                    if (!onBlockedList) {
                        return Mono.just(false);
                    }

                    return redisTemplate.hasKey(unlockedHost).map(unlocked -> !unlocked);
                });

    }
    
    public Mono<Boolean> addSite(String host, long unlockSeconds) {
        String normalizedHost = host.toLowerCase();
        if (unlockSeconds <= 0) {
            return Mono.error(new IllegalArgumentException("unlockSeconds must be greater than 0"));
        }

        return redisTemplate.opsForHash().put(BLOCKED_HOSTS_SET_KEY, normalizedHost, String.valueOf(unlockSeconds));
    }


    public Mono<Boolean> removeSite(String host) {
        String normalizedHost = host.toLowerCase();
        return redisTemplate.opsForHash().remove(BLOCKED_HOSTS_SET_KEY, normalizedHost)
                .map(removedCount -> removedCount > 0)
                .flatMap(removed -> {
                    String unlockKey = "unlocked:" + normalizedHost;
                    return redisTemplate.delete(unlockKey).thenReturn(removed);
                });
    }

    
    public Flux<BlocklistEntry> listAllBlockedSites() {
        return redisTemplate.opsForHash().entries(BLOCKED_HOSTS_SET_KEY)
                .flatMap(entry -> {
                    String host = (String) entry.getKey();
                    String rawSeconds = (String) entry.getValue();
                    try {
                        return Mono.just(new BlocklistEntry(host, Long.parseLong(rawSeconds)));
                    } catch (NumberFormatException e) {
                        logger.warn("Skipping blocklist entry '{}': invalid unlockSeconds '{}'", host, rawSeconds);
                        return Mono.empty();
                    }
                });

    }
    
    public Mono<Boolean> unlockSite(String host) {
        String normalizedHost = host.toLowerCase();
        String unlockKey = "unlocked:" + normalizedHost;

        return redisTemplate.opsForHash().get(BLOCKED_HOSTS_SET_KEY,normalizedHost)
                .flatMap(unlockStr -> {
                    long unlockSeconds;
                    try {
                        unlockSeconds = Long.parseLong((String)unlockStr);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid unlockSeconds for host '{}': {}", normalizedHost, unlockStr);
                        return Mono.just(false);
                    }

                    return redisTemplate.opsForValue().set(unlockKey, "true", java.time.Duration.ofSeconds(unlockSeconds))
                            .thenReturn(true);
                }).defaultIfEmpty(false);
    }
}