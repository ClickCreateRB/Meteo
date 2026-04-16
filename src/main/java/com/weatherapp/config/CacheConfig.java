package com.weatherapp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Configurazione della cache in-memory con Caffeine.
 *
 * Usiamo 3 cache separate perché hanno "shelf life" diverse:
 * - geocoding: le coordinate di una città non cambiano mai → 24h
 * - weather: il meteo corrente cambia ogni ~10 minuti → 10min
 * - forecast: le previsioni vengono aggiornate ~ogni ora → 30min
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_GEOCODING = "geocoding";
    public static final String CACHE_WEATHER = "weather";
    public static final String CACHE_FORECAST = "forecast";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();

        List<CaffeineCache> caches = List.of(
                buildCache(CACHE_GEOCODING, 24, TimeUnit.HOURS, 500),
                buildCache(CACHE_WEATHER, 10, TimeUnit.MINUTES, 500),
                buildCache(CACHE_FORECAST, 30, TimeUnit.MINUTES, 500));

        manager.setCaches(Objects.requireNonNull(caches));
        return manager;
    }

    private CaffeineCache buildCache(String name, long ttl, TimeUnit unit, int maxSize) {
        Objects.requireNonNull(name, "nome cache non può essere null");
        Objects.requireNonNull(unit, "TimeUnit non può essere null");

        var caffeineCache = Objects.requireNonNull(
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl, unit)
                        .maximumSize(maxSize)
                        .recordStats()
                        .build());

        return new CaffeineCache(name, caffeineCache);
    }
}
