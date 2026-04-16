package com.weatherapp.controller;

import com.weatherapp.exception.ExternalApiException;
import com.weatherapp.service.GeocodingService;
import com.weatherapp.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test dedicati alla VALIDAZIONE degli input del controller.
 *
 * Nota importante: qui usiamo @WebMvcTest invece di MockMvcBuilders.standaloneSetup
 * perché @Validated (a livello classe, per validare i @RequestParam) richiede AOP
 * di Spring. In standalone l'AOP non viene attivata e i test sui vincoli
 * @Pattern / @Size / @DecimalMin scivolerebbero silenziosamente.
 */
@WebMvcTest(WeatherController.class)
class WeatherControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherService weatherService;

    @MockitoBean
    private GeocodingService geocodingService;

    // --- Validazione parametro "city" ---

    @Test
    void getWeather_cityTooShort_returns400() throws Exception {
        mockMvc.perform(get("/api/weather").param("city", "A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getWeather_cityOnlyWhitespace_returns400() throws Exception {
        // @NotBlank rifiuta stringhe composte solo di spazi
        mockMvc.perform(get("/api/weather").param("city", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getWeather_cityWithInvalidChars_returns400() throws Exception {
        // Il pattern rifiuta caratteri come <, >, numeri isolati, simboli
        mockMvc.perform(get("/api/weather").param("city", "<script>"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getWeather_cityWithApostrophe_returns200() throws Exception {
        // Verifichiamo che città tipo "L'Aquila" siano ACCETTATE dal pattern
        when(weatherService.getWeatherByCity(anyString(), anyString()))
                .thenReturn(com.weatherapp.model.WeatherInfo.builder()
                        .city("L'Aquila")
                        .forecast(java.util.List.of())
                        .build());

        mockMvc.perform(get("/api/weather").param("city", "L'Aquila"))
                .andExpect(status().isOk());
    }

    @Test
    void getWeather_cityWithAccents_returns200() throws Exception {
        // Le lettere Unicode (São, Zürich, Köln) devono passare
        when(weatherService.getWeatherByCity(anyString(), anyString()))
                .thenReturn(com.weatherapp.model.WeatherInfo.builder()
                        .city("São Paulo")
                        .forecast(java.util.List.of())
                        .build());

        mockMvc.perform(get("/api/weather").param("city", "São Paulo"))
                .andExpect(status().isOk());
    }

    // --- Validazione parametro "unit" ---

    @Test
    void getWeather_invalidUnit_returns400() throws Exception {
        mockMvc.perform(get("/api/weather")
                        .param("city", "Milano")
                        .param("unit", "kelvin"))
                .andExpect(status().isBadRequest());
    }

    // --- Validazione parametri "lat" / "lon" ---

    @Test
    void getByCoords_latOutOfRange_returns400() throws Exception {
        mockMvc.perform(get("/api/weather/coords")
                        .param("lat", "91.0")
                        .param("lon", "0.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByCoords_lonOutOfRange_returns400() throws Exception {
        mockMvc.perform(get("/api/weather/coords")
                        .param("lat", "0.0")
                        .param("lon", "-181.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByCoords_latNotNumeric_returns400() throws Exception {
        // Spring risponde 400 (type mismatch) quando il parametro non è convertibile in double
        mockMvc.perform(get("/api/weather/coords")
                        .param("lat", "abc")
                        .param("lon", "0.0"))
                .andExpect(status().isBadRequest());
    }

    // --- Validazione parametro "q" (autocomplete) ---

    @Test
    void autocomplete_queryTooShort_returns400() throws Exception {
        mockMvc.perform(get("/api/weather/autocomplete").param("q", "M"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autocomplete_missingQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/weather/autocomplete"))
                .andExpect(status().isBadRequest());
    }

    // --- Errori dell'API esterna → 502 ---

    @Test
    void getWeather_externalApiFails_returns502() throws Exception {
        when(weatherService.getWeatherByCity(anyString(), anyString()))
                .thenThrow(new ExternalApiException(
                        "Impossibile raggiungere il servizio meteo"));

        mockMvc.perform(get("/api/weather").param("city", "Milano"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getByCoords_externalApiFails_returns502() throws Exception {
        when(weatherService.getWeatherByCoords(anyDouble(), anyDouble(), anyString()))
                .thenThrow(new ExternalApiException(
                        "Il servizio meteo ha restituito un errore (503)"));

        mockMvc.perform(get("/api/weather/coords")
                        .param("lat", "41.89")
                        .param("lon", "12.48"))
                .andExpect(status().isBadGateway());
    }
}
