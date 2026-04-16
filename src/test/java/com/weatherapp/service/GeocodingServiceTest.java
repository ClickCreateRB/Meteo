package com.weatherapp.service;

import com.weatherapp.client.OpenMeteoClient;
import com.weatherapp.exception.CityNotFoundException;
import com.weatherapp.model.AutocompleteResult;
import com.weatherapp.model.GeocodingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test per GeocodingService focalizzati sui casi limite:
 * - risposte vuote dall'API esterna (→ CityNotFoundException per resolve, lista vuota per search)
 * - query di autocomplete troppo corte (→ lista vuota, niente chiamata al client)
 * - risposta malformata / null dal client.
 */
@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock
    private OpenMeteoClient client;

    @InjectMocks
    private GeocodingService geocodingService;

    // --- resolve(city) ---

    @Test
    void resolve_validCity_returnsFirstLocation() {
        GeocodingResponse.Location loc = new GeocodingResponse.Location();
        loc.setName("Milano");
        loc.setLatitude(45.46);
        loc.setLongitude(9.19);

        GeocodingResponse response = new GeocodingResponse();
        response.setResults(List.of(loc));

        when(client.geocode("Milano")).thenReturn(response);

        GeocodingResponse.Location result = geocodingService.resolve("Milano");

        assertEquals("Milano", result.getName());
        assertEquals(45.46, result.getLatitude());
    }

    @Test
    void resolve_emptyResults_throwsCityNotFound() {
        GeocodingResponse response = new GeocodingResponse();
        response.setResults(List.of()); // API 200 OK ma nessun match

        when(client.geocode("XyzNonEsiste")).thenReturn(response);

        assertThrows(CityNotFoundException.class,
                () -> geocodingService.resolve("XyzNonEsiste"));
    }

    @Test
    void resolve_nullResults_throwsCityNotFound() {
        // Caso limite: l'API risponde ma il campo "results" è proprio null
        GeocodingResponse response = new GeocodingResponse();
        response.setResults(null);

        when(client.geocode("XyzNonEsiste")).thenReturn(response);

        assertThrows(CityNotFoundException.class,
                () -> geocodingService.resolve("XyzNonEsiste"));
    }

    @Test
    void resolve_nullResponse_throwsCityNotFound() {
        // Caso estremo: il client restituisce proprio null (improbabile ma difendiamoci)
        when(client.geocode(anyString())).thenReturn(null);

        assertThrows(CityNotFoundException.class,
                () -> geocodingService.resolve("Qualcosa"));
    }

    // --- search(query, limit) ---

    @Test
    void search_queryTooShort_returnsEmptyListWithoutCallingClient() {
        // La query di 1 carattere non deve nemmeno raggiungere l'API esterna:
        // è un filtro difensivo per evitare richieste inutili a Open-Meteo.
        List<AutocompleteResult> result = geocodingService.search("A", 5);

        assertTrue(result.isEmpty());
        verify(client, never()).search(anyString(), anyInt());
    }

    @Test
    void search_nullQuery_returnsEmptyList() {
        List<AutocompleteResult> result = geocodingService.search(null, 5);

        assertTrue(result.isEmpty());
        verify(client, never()).search(anyString(), anyInt());
    }

    @Test
    void search_whitespaceQuery_returnsEmptyList() {
        // Dopo trim la stringa è vuota → stessa logica del "too short"
        List<AutocompleteResult> result = geocodingService.search("   ", 5);

        assertTrue(result.isEmpty());
        verify(client, never()).search(anyString(), anyInt());
    }

    @Test
    void search_noResults_returnsEmptyList() {
        // A differenza di resolve(), l'autocomplete non lancia eccezione se non trova nulla:
        // è normale che l'utente stia ancora scrivendo e non ci siano match.
        GeocodingResponse response = new GeocodingResponse();
        response.setResults(null);

        when(client.search("Xyz", 5)).thenReturn(response);

        List<AutocompleteResult> result = geocodingService.search("Xyz", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void search_validQuery_returnsMappedResults() {
        GeocodingResponse.Location loc1 = new GeocodingResponse.Location();
        loc1.setName("Milano");
        loc1.setCountry("Italia");

        GeocodingResponse.Location loc2 = new GeocodingResponse.Location();
        loc2.setName("Milazzo");
        loc2.setCountry("Italia");

        GeocodingResponse response = new GeocodingResponse();
        response.setResults(List.of(loc1, loc2));

        when(client.search("Mil", 5)).thenReturn(response);

        List<AutocompleteResult> result = geocodingService.search("Mil", 5);

        assertEquals(2, result.size());
    }
}
