package com.weatherapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeocodingResponse {

    private List<Location> results;

    public List<Location> getResults() { return results; }
    public void setResults(List<Location> results) { this.results = results; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        private String name;
        private double latitude;
        private double longitude;
        private String country;
        private String admin1;      // regione/stato (es. "Lazio")
        private String countryCode; // es. "IT"

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }

        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }

        public String getAdmin1() { return admin1; }
        public void setAdmin1(String admin1) { this.admin1 = admin1; }

        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    }
}
