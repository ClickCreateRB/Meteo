package com.weatherapp.model;

/**
 * DTO leggero per i suggerimenti di autocomplete.
 * Contiene solo ciò che serve al dropdown del frontend.
 */
public class AutocompleteResult {

    private String name;
    private String country;
    private String countryCode;
    private String admin1;
    private double latitude;
    private double longitude;

    public AutocompleteResult(GeocodingResponse.Location location) {
        this.name = location.getName();
        this.country = location.getCountry();
        this.countryCode = location.getCountryCode();
        this.admin1 = location.getAdmin1();
        this.latitude = location.getLatitude();
        this.longitude = location.getLongitude();
    }

    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getCountryCode() { return countryCode; }
    public String getAdmin1() { return admin1; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
