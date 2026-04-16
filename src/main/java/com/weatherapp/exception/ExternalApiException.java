package com.weatherapp.exception;

/**
 * Eccezione lanciata quando l'API esterna (Open-Meteo) fallisce:
 * errori HTTP 4xx/5xx, timeout, problemi di rete.
 */
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalApiException(String message) {
        super(message);
    }
}
