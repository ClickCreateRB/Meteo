package com.weatherapp.service;

import com.weatherapp.client.OpenMeteoClient;
import com.weatherapp.model.GeocodingResponse;
import com.weatherapp.model.WeatherInfo;
import com.weatherapp.model.WeatherResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WeatherService {

    private final GeocodingService geocodingService;
    private final OpenMeteoClient openMeteoClient;

    public WeatherService(GeocodingService geocodingService, OpenMeteoClient openMeteoClient) {
        this.geocodingService = geocodingService;
        this.openMeteoClient = openMeteoClient;
    }

    public WeatherInfo getWeatherByCity(String city, String unit) {
        GeocodingResponse.Location location = geocodingService.resolve(city);
        WeatherResponse weather = openMeteoClient.fetchForecast(
                location.getLatitude(), location.getLongitude(), unit);
        return buildWeatherInfo(location, weather);
    }

    public WeatherInfo getWeatherByCoords(double latitude, double longitude, String unit) {
        WeatherResponse weather = openMeteoClient.fetchForecast(latitude, longitude, unit);
        GeocodingResponse.Location pseudoLocation = new GeocodingResponse.Location();
        pseudoLocation.setName("La tua posizione");
        pseudoLocation.setCountry("");
        pseudoLocation.setLatitude(latitude);
        pseudoLocation.setLongitude(longitude);
        return buildWeatherInfo(pseudoLocation, weather);
    }

    // --- Helpers privati ---

    private WeatherInfo buildWeatherInfo(GeocodingResponse.Location location, WeatherResponse weather) {
        double todayUv = safeDouble(weather.getDaily() != null ? weather.getDaily().getUvIndexMax() : null, 0);

        return WeatherInfo.builder()
                .city(location.getName())
                .country(location.getCountry())
                .countryCode(location.getCountryCode())
                .admin1(location.getAdmin1())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .current(mapCurrent(weather.getCurrent(), todayUv))
                .today(mapToday(weather.getDaily()))
                .forecast(mapForecast(weather.getDaily()))
                .build();
    }

    private WeatherInfo.Current mapCurrent(WeatherResponse.CurrentWeather c, double todayUv) {
        if (c == null) return null;
        boolean isDay = c.getIsDay() == 1;
        return WeatherInfo.Current.builder()
                .temperature(round(c.getTemperature()))
                .feelsLike(round(c.getApparentTemperature()))
                .humidity(c.getHumidity())
                .windSpeed(round(c.getWindSpeed()))
                .windDirection(c.getWindDirection())
                .pressure(round(c.getPressure()))
                .weatherCode(c.getWeatherCode())
                .description(WeatherCodeMapper.describe(c.getWeatherCode()))
                .icon(WeatherCodeMapper.iconFor(c.getWeatherCode(), isDay))
                .isDay(isDay)
                .suggestion(WeatherCodeMapper.suggestion(c.getWeatherCode(), c.getTemperature(), todayUv))
                .build();
    }

    private WeatherInfo.DayInfo mapToday(WeatherResponse.DailyWeather d) {
        if (d == null) return null;
        return WeatherInfo.DayInfo.builder()
                .sunrise(firstOrNull(d.getSunrise()))
                .sunset(firstOrNull(d.getSunset()))
                .uvIndex(round(safeDouble(d.getUvIndexMax(), 0)))
                .build();
    }

    private List<WeatherInfo.DailyForecast> mapForecast(WeatherResponse.DailyWeather d) {
        if (d == null || d.getTime() == null) return List.of();

        List<WeatherInfo.DailyForecast> list = new ArrayList<>();
        int size = d.getTime().size();

        for (int i = 0; i < size; i++) {
            int code = safeInt(d.getWeatherCode(), i);
            list.add(WeatherInfo.DailyForecast.builder()
                    .date(d.getTime().get(i))
                    .temperatureMax(round(safeDouble(d.getTemperatureMax(), i)))
                    .temperatureMin(round(safeDouble(d.getTemperatureMin(), i)))
                    .weatherCode(code)
                    .description(WeatherCodeMapper.describe(code))
                    .icon(WeatherCodeMapper.iconFor(code, true))
                    .precipitationProbability(safeInt(d.getPrecipitationProbability(), i))
                    .uvIndex(round(safeDouble(d.getUvIndexMax(), i)))
                    .build());
        }
        return list;
    }

    private double round(double v) { return Math.round(v * 10.0) / 10.0; }

    private int safeInt(List<Integer> list, int i) {
        return (list != null && i < list.size() && list.get(i) != null) ? list.get(i) : 0;
    }

    private double safeDouble(List<Double> list, int i) {
        return (list != null && i < list.size() && list.get(i) != null) ? list.get(i) : 0.0;
    }

    private String firstOrNull(List<String> list) {
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }
}
