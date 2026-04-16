package com.weatherapp.service;

import com.weatherapp.client.OpenMeteoClient;
import com.weatherapp.exception.CityNotFoundException;
import com.weatherapp.model.AutocompleteResult;
import com.weatherapp.model.GeocodingResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class GeocodingService {

    private final OpenMeteoClient client;

    public GeocodingService(OpenMeteoClient client) {
        this.client = client;
    }

    /**
     * Risolve una città in coordinate (la prima best-match).
     */
    public GeocodingResponse.Location resolve(String city) {
        GeocodingResponse response = client.geocode(city);
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new CityNotFoundException(city);
        }
        return response.getResults().get(0);
    }

    /**
     * Cerca più città matching per l'autocomplete.
     * Restituisce una lista vuota (non eccezione) se non c'è nulla:
     * per l'autocomplete non è un errore "non trovato".
     */
    public List<AutocompleteResult> search(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return Collections.emptyList();
        }

        GeocodingResponse response = client.search(query.trim(), limit);
        if (response == null || response.getResults() == null) {
            return Collections.emptyList();
        }

        return response.getResults().stream()
                .map(AutocompleteResult::new)
                .toList();
    }
}
