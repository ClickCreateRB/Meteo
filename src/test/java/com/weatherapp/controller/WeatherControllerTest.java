package com.weatherapp.controller;

import com.weatherapp.exception.CityNotFoundException;
import com.weatherapp.exception.GlobalExceptionHandler;
import com.weatherapp.model.WeatherInfo;
import com.weatherapp.service.GeocodingService;
import com.weatherapp.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WeatherControllerTest {

    private MockMvc mockMvc;

    @Mock private WeatherService weatherService;
    @Mock private GeocodingService geocodingService;

    @InjectMocks private WeatherController weatherController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(weatherController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getWeather_validCity_returns200() throws Exception {
        WeatherInfo info = WeatherInfo.builder()
                .city("Milano")
                .country("Italia")
                .current(WeatherInfo.Current.builder()
                        .temperature(18.5)
                        .description("Parzialmente nuvoloso")
                        .icon("partly-cloudy")
                        .build())
                .forecast(List.of())
                .build();

        when(weatherService.getWeatherByCity(anyString(), anyString())).thenReturn(info);

        mockMvc.perform(get("/api/weather").param("city", "Milano"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Milano"))
                .andExpect(jsonPath("$.current.temperature").value(18.5));
    }

    @Test
    void getWeather_unknownCity_returns404() throws Exception {
        when(weatherService.getWeatherByCity(anyString(), anyString()))
                .thenThrow(new CityNotFoundException("XyzNonEsiste"));

        mockMvc.perform(get("/api/weather").param("city", "XyzNonEsiste"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getWeather_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autocomplete_returnsList() throws Exception {
        when(geocodingService.search(anyString(), any(Integer.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/weather/autocomplete").param("q", "Mil"))
                .andExpect(status().isOk());
    }
}
