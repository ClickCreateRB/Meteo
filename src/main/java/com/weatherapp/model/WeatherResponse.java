package com.weatherapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherResponse {

    @JsonProperty("current") private CurrentWeather current;
    @JsonProperty("daily") private DailyWeather daily;

    public CurrentWeather getCurrent() { return current; }
    public void setCurrent(CurrentWeather v) { this.current = v; }
    public DailyWeather getDaily() { return daily; }
    public void setDaily(DailyWeather v) { this.daily = v; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrentWeather {
        @JsonProperty("temperature_2m") private double temperature;
        @JsonProperty("relative_humidity_2m") private int humidity;
        @JsonProperty("wind_speed_10m") private double windSpeed;
        @JsonProperty("wind_direction_10m") private int windDirection;
        @JsonProperty("weather_code") private int weatherCode;
        @JsonProperty("apparent_temperature") private double apparentTemperature;
        @JsonProperty("is_day") private int isDay;
        @JsonProperty("pressure_msl") private double pressure;

        public double getTemperature() { return temperature; }
        public void setTemperature(double v) { this.temperature = v; }
        public int getHumidity() { return humidity; }
        public void setHumidity(int v) { this.humidity = v; }
        public double getWindSpeed() { return windSpeed; }
        public void setWindSpeed(double v) { this.windSpeed = v; }
        public int getWindDirection() { return windDirection; }
        public void setWindDirection(int v) { this.windDirection = v; }
        public int getWeatherCode() { return weatherCode; }
        public void setWeatherCode(int v) { this.weatherCode = v; }
        public double getApparentTemperature() { return apparentTemperature; }
        public void setApparentTemperature(double v) { this.apparentTemperature = v; }
        public int getIsDay() { return isDay; }
        public void setIsDay(int v) { this.isDay = v; }
        public double getPressure() { return pressure; }
        public void setPressure(double v) { this.pressure = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyWeather {
        @JsonProperty("time") private List<String> time;
        @JsonProperty("weather_code") private List<Integer> weatherCode;
        @JsonProperty("temperature_2m_max") private List<Double> temperatureMax;
        @JsonProperty("temperature_2m_min") private List<Double> temperatureMin;
        @JsonProperty("precipitation_probability_max") private List<Integer> precipitationProbability;
        @JsonProperty("uv_index_max") private List<Double> uvIndexMax;
        @JsonProperty("sunrise") private List<String> sunrise;
        @JsonProperty("sunset") private List<String> sunset;

        public List<String> getTime() { return time; }
        public void setTime(List<String> v) { this.time = v; }
        public List<Integer> getWeatherCode() { return weatherCode; }
        public void setWeatherCode(List<Integer> v) { this.weatherCode = v; }
        public List<Double> getTemperatureMax() { return temperatureMax; }
        public void setTemperatureMax(List<Double> v) { this.temperatureMax = v; }
        public List<Double> getTemperatureMin() { return temperatureMin; }
        public void setTemperatureMin(List<Double> v) { this.temperatureMin = v; }
        public List<Integer> getPrecipitationProbability() { return precipitationProbability; }
        public void setPrecipitationProbability(List<Integer> v) { this.precipitationProbability = v; }
        public List<Double> getUvIndexMax() { return uvIndexMax; }
        public void setUvIndexMax(List<Double> v) { this.uvIndexMax = v; }
        public List<String> getSunrise() { return sunrise; }
        public void setSunrise(List<String> v) { this.sunrise = v; }
        public List<String> getSunset() { return sunset; }
        public void setSunset(List<String> v) { this.sunset = v; }
    }
}
