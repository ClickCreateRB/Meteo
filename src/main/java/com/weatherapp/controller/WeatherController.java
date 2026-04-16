package com.weatherapp.controller;

import com.weatherapp.model.AutocompleteResult;
import com.weatherapp.model.WeatherInfo;
import com.weatherapp.service.GeocodingService;
import com.weatherapp.service.WeatherService;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * REST API endpoints:
 *   GET /api/weather?city=Roma&unit=celsius        → meteo per città
 *   GET /api/weather/coords?lat=X&lon=Y&unit=...   → meteo per coordinate (geolocalizzazione)
 *   GET /api/weather/autocomplete?q=rom            → suggerimenti per autocomplete
 */
@RestController
@RequestMapping("/api/weather")
@Validated
public class WeatherController {

    // Pattern che ammette: lettere Unicode (\p{L}), accenti combinanti (\p{M}),
    // spazi, apostrofi, punti e trattini. Richiede che il PRIMO carattere non-spazio
    // sia una lettera e che seguano almeno 1 altro carattere valido: così "R" o "  "
    // vengono rifiutati, mentre "L'Aquila", "Sant'Agata", "São Paulo", "Aix-en-Provence" passano.
    // Spazi iniziali/finali ammessi: vengono puliti col trim() prima di passare al service.
    private static final String CITY_NAME_REGEX =
            "^\\s*[\\p{L}][\\p{L}\\p{M}\\s'.\\-]{1,99}\\s*$";

    private final WeatherService weatherService;
    private final GeocodingService geocodingService;

    public WeatherController(WeatherService weatherService, GeocodingService geocodingService) {
        this.weatherService = Objects.requireNonNull(weatherService, "weatherService non può essere null");
        this.geocodingService = Objects.requireNonNull(geocodingService, "geocodingService non può essere null");
    }

    @GetMapping
    public ResponseEntity<WeatherInfo> getByCity(
            @RequestParam
            @NotBlank(message = "Il nome della città non può essere vuoto")
            @Pattern(regexp = CITY_NAME_REGEX,
                    message = "Il nome della città non è valido: sono ammesse lettere, spazi, apostrofi, punti e trattini (min 2 caratteri)")
            String city,

            @RequestParam(defaultValue = "celsius")
            @Pattern(regexp = "celsius|fahrenheit", message = "unit deve essere 'celsius' o 'fahrenheit'")
            String unit) {

        return ResponseEntity.ok(weatherService.getWeatherByCity(city.trim(), unit));
    }

    @GetMapping("/coords")
    public ResponseEntity<WeatherInfo> getByCoords(
            @RequestParam
            @DecimalMin(value = "-90.0", message = "Latitudine non valida")
            @DecimalMax(value = "90.0", message = "Latitudine non valida")
            double lat,

            @RequestParam
            @DecimalMin(value = "-180.0", message = "Longitudine non valida")
            @DecimalMax(value = "180.0", message = "Longitudine non valida")
            double lon,

            @RequestParam(defaultValue = "celsius")
            @Pattern(regexp = "celsius|fahrenheit", message = "unit deve essere 'celsius' o 'fahrenheit'")
            String unit) {

        return ResponseEntity.ok(weatherService.getWeatherByCoords(lat, lon, unit));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<AutocompleteResult>> autocomplete(
            @RequestParam
            @NotBlank(message = "La query non può essere vuota")
            @Size(min = 2, max = 50, message = "La query deve avere tra 2 e 50 caratteri")
            String q) {

        return ResponseEntity.ok(geocodingService.search(q.trim(), 5));
    }
}
