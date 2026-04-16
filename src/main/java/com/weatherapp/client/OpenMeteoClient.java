package com.weatherapp.client;

import com.weatherapp.config.CacheConfig;
import com.weatherapp.exception.ExternalApiException;
import com.weatherapp.model.GeocodingResponse;
import com.weatherapp.model.WeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Client HTTP verso le API Open-Meteo.
 * Tutte le risposte vengono mantenute in cache per ridurre le chiamate
 * all'API esterna e velocizzare le richieste ripetute.
 *
 * Strategia di resilienza:
 * - Timeout configurabile su connect/read/write (vedi AppConfig).
 * - Retry automatico con backoff esponenziale SOLO su errori di rete/timeout.
 *   Non ritentiamo mai su 4xx/5xx: una 404 è una 404, e ripeterla spreca quota.
 * - Dopo i tentativi esauriti, sollevo ExternalApiException con messaggio user-friendly
 *   che l'utente vedrà come 502 dal GlobalExceptionHandler.
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    // Retry policy: 2 tentativi totali dopo il primo, con backoff che parte da 300ms
    // e raddoppia (300, 600). Un utente percepisce fino a ~1s come accettabile.
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_INITIAL_BACKOFF = Duration.ofMillis(300);

    private final WebClient forecastWebClient;
    private final WebClient geocodingWebClient;

    public OpenMeteoClient(
            @Qualifier("forecastWebClient") WebClient forecastWebClient,
            @Qualifier("geocodingWebClient") WebClient geocodingWebClient) {
        this.forecastWebClient = forecastWebClient;
        this.geocodingWebClient = geocodingWebClient;
    }

    /**
     * Cerca una singola città → coordinate. Risultato in cache per 24h.
     */
    @Cacheable(value = CacheConfig.CACHE_GEOCODING, key = "'single:' + #city.toLowerCase()")
    public GeocodingResponse geocode(String city) {
        log.info("Geocoding (MISS cache): {}", city);
        return callGeocoding(city, 1);
    }

    /**
     * Cerca più città per autocomplete. Risultato in cache per 24h.
     */
    @Cacheable(value = CacheConfig.CACHE_GEOCODING, key = "'multi:' + #query.toLowerCase() + ':' + #limit")
    public GeocodingResponse search(String query, int limit) {
        log.info("Autocomplete (MISS cache): {} (limit={})", query, limit);
        return callGeocoding(query, limit);
    }

    /**
     * Recupera meteo corrente + previsioni per 7 giorni.
     * Cache 30 minuti, chiave = lat/lon + unità.
     */
    @Cacheable(value = CacheConfig.CACHE_FORECAST,
               key = "#latitude + ',' + #longitude + ':' + #unit")
    public WeatherResponse fetchForecast(double latitude, double longitude, String unit) {
        log.info("Forecast (MISS cache): lat={}, lon={}, unit={}", latitude, longitude, unit);

        // Deriviamo una sola volta il "sistema" di unità: metrico se celsius, imperiale se fahrenheit.
        // In questo modo temperatura e velocità del vento restano coerenti tra loro,
        // e aggiungere una nuova unità in futuro non introduce accoppiamenti nascosti.
        boolean metric = "celsius".equals(unit);
        String temperatureUnit = metric ? "celsius" : "fahrenheit";
        String windSpeedUnit = metric ? "kmh" : "mph";

        try {
            return forecastWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current",
                                    "temperature_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code,apparent_temperature,is_day,pressure_msl")
                            .queryParam("daily",
                                    "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,sunrise,sunset")
                            .queryParam("temperature_unit", temperatureUnit)
                            .queryParam("wind_speed_unit", windSpeedUnit)
                            .queryParam("timezone", "auto")
                            .queryParam("forecast_days", 7)
                            .build())
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .retryWhen(transientErrorRetrySpec("Forecast"))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Errore HTTP {} dall'API Forecast: {}", e.getStatusCode(), e.getMessage());
            throw new ExternalApiException(
                    "Il servizio meteo ha restituito un errore (" + e.getStatusCode() + ")", e);
        } catch (Exception e) {
            // Dopo retry esauriti o errori non retryabili dal WebClient.
            // Messaggio user-friendly: l'utente non sa cosa sia un "ReadTimeoutException".
            if (isTransientError(unwrap(e))) {
                log.error("API Forecast irraggiungibile dopo {} tentativi: {}",
                        MAX_RETRIES + 1, e.getMessage());
                throw new ExternalApiException(
                        "Il servizio meteo è momentaneamente lento. Riprova tra qualche istante.", e);
            }
            log.error("Errore inatteso durante il recupero meteo", e);
            throw new ExternalApiException("Errore inatteso durante il recupero dei dati meteo", e);
        }
    }

    // --- metodi privati ---

    private GeocodingResponse callGeocoding(String query, int limit) {
        try {
            return geocodingWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("name", query)
                            .queryParam("count", limit)
                            .queryParam("language", "it")
                            .build())
                    .retrieve()
                    .bodyToMono(GeocodingResponse.class)
                    .retryWhen(transientErrorRetrySpec("Geocoding"))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Errore HTTP {} dall'API Geocoding: {}", e.getStatusCode(), e.getMessage());
            throw new ExternalApiException(
                    "Il servizio di geocoding ha restituito un errore (" + e.getStatusCode() + ")", e);
        } catch (Exception e) {
            if (isTransientError(unwrap(e))) {
                log.error("API Geocoding irraggiungibile dopo {} tentativi: {}",
                        MAX_RETRIES + 1, e.getMessage());
                throw new ExternalApiException(
                        "Il servizio di geocoding è momentaneamente lento. Riprova tra qualche istante.", e);
            }
            log.error("Errore inatteso durante il geocoding", e);
            throw new ExternalApiException("Errore inatteso durante la ricerca della città", e);
        }
    }

    /**
     * Crea una Retry spec condivisa tra Forecast e Geocoding.
     * Filter cruciale: ritenta SOLO per errori transitori (rete, timeout).
     * Non ritenta mai per 4xx/5xx: una città che non esiste continuerà a non
     * esistere al secondo tentativo, e ripetere spreca quota verso Open-Meteo.
     */
    private Retry transientErrorRetrySpec(String apiName) {
        return Retry.backoff(MAX_RETRIES, RETRY_INITIAL_BACKOFF)
                .filter(t -> isTransientError(unwrap(t)))
                .doBeforeRetry(sig -> log.warn(
                        "Retry {} #{} dopo errore transitorio: {}",
                        apiName,
                        sig.totalRetries() + 1,
                        sig.failure().getClass().getSimpleName()));
    }

    /**
     * Identifica gli errori che vale la pena ritentare: quelli che un secondo
     * tentativo potrebbe risolvere (glitch di rete, timeout, connessione resettata).
     * NON includiamo WebClientResponseException: una 404/500 dal server non
     * cambierà al tentativo successivo.
     */
    private static boolean isTransientError(Throwable t) {
        if (t == null) return false;
        return t instanceof WebClientRequestException
                || t instanceof TimeoutException
                || t instanceof io.netty.handler.timeout.ReadTimeoutException
                || t instanceof io.netty.handler.timeout.WriteTimeoutException
                || t instanceof java.net.ConnectException;
    }

    /**
     * Scava tra le cause di un'eccezione per trovare la "vera" radice.
     * WebClient e Reactor spesso wrappano le eccezioni originali più volte.
     */
    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
