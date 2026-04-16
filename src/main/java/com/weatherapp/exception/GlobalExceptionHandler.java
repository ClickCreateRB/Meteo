package com.weatherapp.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 404 - Città non trovata dal geocoding.
     */
    @ExceptionHandler(CityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCityNotFound(CityNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * 502 - L'API esterna Open-Meteo ha fallito (errore HTTP, rete, timeout).
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<Map<String, Object>> handleExternalApi(ExternalApiException ex) {
        log.error("Errore API esterna: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    /**
     * 400 - Parametro "city" mancante nella richiesta.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Parametro obbligatorio mancante: " + ex.getParameterName());
    }

    /**
     * 400 - Parametro presente ma con tipo sbagliato (es. lat=abc dove serve un double).
     * Senza questo handler Spring lascerebbe propagare l'eccezione fino al fallback Exception
     * e l'utente riceverebbe un 500 "Errore interno del server" per un semplice errore di input.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        // Catturiamo il tipo atteso in una variabile locale: così il null-check copre
        // anche l'accesso successivo (una doppia chiamata a getRequiredType() farebbe
        // scattare un warning di potential-null-access dato che il valore di ritorno
        // non è garantito stabile tra due invocazioni).
        Class<?> requiredType = ex.getRequiredType();
        String expectedType = (requiredType != null) ? requiredType.getSimpleName() : "valore valido";
        String message = String.format(
                "Parametro '%s' non valido: atteso %s, ricevuto '%s'",
                ex.getName(), expectedType, ex.getValue());
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 400 - Validazione fallita (@NotBlank, @Size, ecc.).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 404 - Risorsa statica non trovata (es. favicon, file DevTools del browser,
     * ecc.).
     * Non logghiamo come errore: sono richieste normali del browser per risorse
     * assenti.
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Risorsa non trovata");
    }

    /**
     * 500 - Fallback per qualsiasi altra eccezione non gestita.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Errore interno non gestito", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno del server");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Objects.requireNonNull(status, "status non può essere null");
        Objects.requireNonNull(message, "message non può essere null");

        Map<String, Object> body = Map.of(
                "error", message,
                "status", status.value(),
                "timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(status).body(body);
    }
}
