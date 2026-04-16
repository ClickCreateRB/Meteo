package com.weatherapp.model;

import java.util.List;

public class WeatherInfo {

    private String city;
    private String country;
    private String countryCode;
    private String admin1;
    private double latitude;
    private double longitude;

    private Current current;
    private DayInfo today;
    private List<DailyForecast> forecast;

    public static Builder builder() { return new Builder(); }

    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getCountryCode() { return countryCode; }
    public String getAdmin1() { return admin1; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Current getCurrent() { return current; }
    public DayInfo getToday() { return today; }
    public List<DailyForecast> getForecast() { return forecast; }

    public static class Current {
        private double temperature;
        private double feelsLike;
        private int humidity;
        private double windSpeed;
        private int windDirection;
        private double pressure;
        private int weatherCode;
        private String description;
        private String icon;
        private boolean isDay;
        private String suggestion;

        public double getTemperature() { return temperature; }
        public double getFeelsLike() { return feelsLike; }
        public int getHumidity() { return humidity; }
        public double getWindSpeed() { return windSpeed; }
        public int getWindDirection() { return windDirection; }
        public double getPressure() { return pressure; }
        public int getWeatherCode() { return weatherCode; }
        public String getDescription() { return description; }
        public String getIcon() { return icon; }
        public boolean isDay() { return isDay; }
        public String getSuggestion() { return suggestion; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final Current c = new Current();
            public Builder temperature(double v) { c.temperature = v; return this; }
            public Builder feelsLike(double v) { c.feelsLike = v; return this; }
            public Builder humidity(int v) { c.humidity = v; return this; }
            public Builder windSpeed(double v) { c.windSpeed = v; return this; }
            public Builder windDirection(int v) { c.windDirection = v; return this; }
            public Builder pressure(double v) { c.pressure = v; return this; }
            public Builder weatherCode(int v) { c.weatherCode = v; return this; }
            public Builder description(String v) { c.description = v; return this; }
            public Builder icon(String v) { c.icon = v; return this; }
            public Builder isDay(boolean v) { c.isDay = v; return this; }
            public Builder suggestion(String v) { c.suggestion = v; return this; }
            public Current build() { return c; }
        }
    }

    /** Info aggiuntive del giorno corrente (sunrise, sunset, UV) */
    public static class DayInfo {
        private String sunrise;
        private String sunset;
        private double uvIndex;

        public String getSunrise() { return sunrise; }
        public String getSunset() { return sunset; }
        public double getUvIndex() { return uvIndex; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final DayInfo d = new DayInfo();
            public Builder sunrise(String v) { d.sunrise = v; return this; }
            public Builder sunset(String v) { d.sunset = v; return this; }
            public Builder uvIndex(double v) { d.uvIndex = v; return this; }
            public DayInfo build() { return d; }
        }
    }

    public static class DailyForecast {
        private String date;
        private double temperatureMax;
        private double temperatureMin;
        private int weatherCode;
        private String description;
        private String icon;
        private int precipitationProbability;
        private double uvIndex;

        public String getDate() { return date; }
        public double getTemperatureMax() { return temperatureMax; }
        public double getTemperatureMin() { return temperatureMin; }
        public int getWeatherCode() { return weatherCode; }
        public String getDescription() { return description; }
        public String getIcon() { return icon; }
        public int getPrecipitationProbability() { return precipitationProbability; }
        public double getUvIndex() { return uvIndex; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final DailyForecast d = new DailyForecast();
            public Builder date(String v) { d.date = v; return this; }
            public Builder temperatureMax(double v) { d.temperatureMax = v; return this; }
            public Builder temperatureMin(double v) { d.temperatureMin = v; return this; }
            public Builder weatherCode(int v) { d.weatherCode = v; return this; }
            public Builder description(String v) { d.description = v; return this; }
            public Builder icon(String v) { d.icon = v; return this; }
            public Builder precipitationProbability(int v) { d.precipitationProbability = v; return this; }
            public Builder uvIndex(double v) { d.uvIndex = v; return this; }
            public DailyForecast build() { return d; }
        }
    }

    public static class Builder {
        private final WeatherInfo info = new WeatherInfo();
        public Builder city(String v) { info.city = v; return this; }
        public Builder country(String v) { info.country = v; return this; }
        public Builder countryCode(String v) { info.countryCode = v; return this; }
        public Builder admin1(String v) { info.admin1 = v; return this; }
        public Builder latitude(double v) { info.latitude = v; return this; }
        public Builder longitude(double v) { info.longitude = v; return this; }
        public Builder current(Current v) { info.current = v; return this; }
        public Builder today(DayInfo v) { info.today = v; return this; }
        public Builder forecast(List<DailyForecast> v) { info.forecast = v; return this; }
        public WeatherInfo build() { return info; }
    }
}
