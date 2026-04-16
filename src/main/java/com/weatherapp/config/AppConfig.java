package com.weatherapp.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {

        @Value("${openmeteo.forecast-url}")
        private String forecastUrl;

        @Value("${openmeteo.geocoding-url}")
        private String geocodingUrl;

        @Value("${openmeteo.timeout-seconds:5}")
        private int timeoutSeconds;

        @Bean
        public WebClient forecastWebClient() {
                return buildWebClient(Objects.requireNonNull(forecastUrl,
                                "openmeteo.forecast-url non configurato"));
        }

        @Bean
        public WebClient geocodingWebClient() {
                return buildWebClient(Objects.requireNonNull(geocodingUrl,
                                "openmeteo.geocoding-url non configurato"));
        }

        /**
         * Costruisce un WebClient con timeout di connessione, lettura e scrittura.
         * Questo evita che l'app si blocchi indefinitamente se Open-Meteo non risponde.
         */
        private WebClient buildWebClient(String baseUrl) {
                Objects.requireNonNull(baseUrl, "baseUrl non può essere null");

                HttpClient httpClient = HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                                .doOnConnected(conn -> conn
                                                .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds,
                                                                TimeUnit.SECONDS))
                                                .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds,
                                                                TimeUnit.SECONDS)));

                Objects.requireNonNull(httpClient, "httpClient non può essere null");

                return WebClient.builder()
                                .baseUrl(baseUrl)
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .build();
        }
}
