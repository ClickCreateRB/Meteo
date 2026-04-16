package com.weatherapp.exception;

public class CityNotFoundException extends RuntimeException {

    public CityNotFoundException(String city) {
        super("Città non trovata: " + city);
    }
}
