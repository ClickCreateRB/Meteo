package com.weatherapp.service;

import com.weatherapp.client.OpenMeteoClient;
import com.weatherapp.exception.CityNotFoundException;
import com.weatherapp.model.GeocodingResponse;
import com.weatherapp.model.WeatherInfo;
import com.weatherapp.model.WeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private OpenMeteoClient openMeteoClient;
    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private WeatherService weatherService;

    private GeocodingResponse.Location mockLocation;
    private WeatherResponse mockWeatherResponse;

    @BeforeEach
    void setUp() {
        // Mock della location
        mockLocation = new GeocodingResponse.Location();
        mockLocation.setName("Roma");
        mockLocation.setLatitude(41.89);
        mockLocation.setLongitude(12.48);
        mockLocation.setCountry("Italia");

        // Mock del current weather (nomi metodi aggiornati)
        WeatherResponse.CurrentWeather current = new WeatherResponse.CurrentWeather();
        current.setTemperature(22.5);
        current.setApparentTemperature(21.0);
        current.setHumidity(65);
        current.setWindSpeed(12.3);
        current.setWindDirection(180);
        current.setPressure(1013.0);
        current.setWeatherCode(0);
        current.setIsDay(1);

        // Mock del daily (richiesto dal service)
        WeatherResponse.DailyWeather daily = new WeatherResponse.DailyWeather();
        daily.setTime(List.of("2026-04-16"));
        daily.setWeatherCode(List.of(0));
        daily.setTemperatureMax(List.of(24.0));
        daily.setTemperatureMin(List.of(15.0));
        daily.setPrecipitationProbability(List.of(10));
        daily.setUvIndexMax(List.of(5.0));
        daily.setSunrise(List.of("2026-04-16T06:30"));
        daily.setSunset(List.of("2026-04-16T19:45"));

        mockWeatherResponse = new WeatherResponse();
        mockWeatherResponse.setCurrent(current);
        mockWeatherResponse.setDaily(daily);
    }

    @Test
    void getWeatherByCity_validCity_returnsWeatherInfo() {
        when(geocodingService.resolve("Roma")).thenReturn(mockLocation);
        when(openMeteoClient.fetchForecast(anyDouble(), anyDouble(), anyString()))
                .thenReturn(mockWeatherResponse);

        WeatherInfo info = weatherService.getWeatherByCity("Roma", "celsius");

        assertNotNull(info);
        assertEquals("Roma", info.getCity());
        assertEquals("Italia", info.getCountry());

        // I dati current sono dentro info.getCurrent()
        assertNotNull(info.getCurrent());
        assertEquals(22.5, info.getCurrent().getTemperature());
        assertEquals(65, info.getCurrent().getHumidity());
        assertEquals("Cielo sereno", info.getCurrent().getDescription());

        // Controllo suggerimento generato
        assertNotNull(info.getCurrent().getSuggestion());

        // Controllo forecast
        assertNotNull(info.getForecast());
        assertEquals(1, info.getForecast().size());
    }

    @Test
    void getWeatherByCity_unknownCity_throwsCityNotFoundException() {
        when(geocodingService.resolve("XyzNonEsiste"))
                .thenThrow(new CityNotFoundException("XyzNonEsiste"));

        assertThrows(CityNotFoundException.class,
                () -> weatherService.getWeatherByCity("XyzNonEsiste", "celsius"));
    }

    @Test
    void getWeatherByCoords_validCoords_returnsWeatherInfo() {
        when(openMeteoClient.fetchForecast(anyDouble(), anyDouble(), anyString()))
                .thenReturn(mockWeatherResponse);

        WeatherInfo info = weatherService.getWeatherByCoords(41.89, 12.48, "celsius");

        assertNotNull(info);
        assertEquals("La tua posizione", info.getCity());
        assertEquals(22.5, info.getCurrent().getTemperature());
    }

    @Test
    void getWeatherByCity_externalApiFails_propagatesException() {
        // Scenario: geocoding OK, ma l'API forecast è down/irraggiungibile.
        // Verifichiamo che ExternalApiException non venga inghiottita dal service.
        when(geocodingService.resolve("Roma")).thenReturn(mockLocation);
        when(openMeteoClient.fetchForecast(anyDouble(), anyDouble(), anyString()))
                .thenThrow(new com.weatherapp.exception.ExternalApiException(
                        "Impossibile raggiungere il servizio meteo"));

        assertThrows(com.weatherapp.exception.ExternalApiException.class,
                () -> weatherService.getWeatherByCity("Roma", "celsius"));
    }

    @Test
    void getWeatherByCoords_externalApiFails_propagatesException() {
        // Stesso scenario ma via coordinate: non c'è geocoding ma l'API forecast può fallire.
        when(openMeteoClient.fetchForecast(anyDouble(), anyDouble(), anyString()))
                .thenThrow(new com.weatherapp.exception.ExternalApiException(
                        "Errore HTTP dall'API Forecast"));

        assertThrows(com.weatherapp.exception.ExternalApiException.class,
                () -> weatherService.getWeatherByCoords(41.89, 12.48, "celsius"));
    }
}
