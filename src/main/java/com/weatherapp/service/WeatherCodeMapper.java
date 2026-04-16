package com.weatherapp.service;

public final class WeatherCodeMapper {

    private WeatherCodeMapper() {}

    public static String describe(int code) {
        return switch (code) {
            case 0 -> "Cielo sereno";
            case 1 -> "Prevalentemente sereno";
            case 2 -> "Parzialmente nuvoloso";
            case 3 -> "Coperto";
            case 45, 48 -> "Nebbia";
            case 51, 53, 55 -> "Pioviggine";
            case 56, 57 -> "Pioviggine gelata";
            case 61, 63, 65 -> "Pioggia";
            case 66, 67 -> "Pioggia gelata";
            case 71, 73, 75 -> "Neve";
            case 77 -> "Granelli di neve";
            case 80, 81, 82 -> "Rovesci";
            case 85, 86 -> "Rovesci di neve";
            case 95 -> "Temporale";
            case 96, 99 -> "Temporale con grandine";
            default -> "Condizioni sconosciute";
        };
    }

    public static String iconFor(int code, boolean isDay) {
        return switch (code) {
            case 0 -> isDay ? "sunny" : "clear-night";
            case 1, 2 -> isDay ? "partly-cloudy" : "partly-cloudy-night";
            case 3 -> "cloudy";
            case 45, 48 -> "fog";
            case 51, 53, 55, 56, 57 -> "drizzle";
            case 61, 63, 65, 66, 67 -> "rain";
            case 71, 73, 75, 77, 85, 86 -> "snow";
            case 80, 81, 82 -> "showers";
            case 95, 96, 99 -> "thunderstorm";
            default -> "cloudy";
        };
    }

    /**
     * Suggerimento intelligente in base a meteo + temperatura + UV.
     * Priorità alle condizioni più pericolose.
     */
    public static String suggestion(int code, double temperature, double uvIndex) {
        // Condizioni estreme prima
        if (code == 95 || code == 96 || code == 99) return "⚡ Temporale in corso, resta al coperto";
        if (code == 71 || code == 73 || code == 75 || code == 85 || code == 86) return "❄️ Attenzione al ghiaccio, guida con prudenza";
        if (code == 45 || code == 48) return "🌫️ Visibilità ridotta, occhio in strada";
        if (code >= 61 && code <= 67) return "☔ Porta l'ombrello, oggi piove";
        if (code >= 80 && code <= 82) return "🌂 Rovesci in arrivo, prepara l'ombrello";
        if (code >= 51 && code <= 57) return "💧 Pioviggine leggera, una giacca può bastare";

        // Temperatura
        if (temperature >= 30) return "🥵 Fa molto caldo, bevi spesso e cerca l'ombra";
        if (temperature <= 0) return "🧊 Temperature sotto zero, copriti bene";
        if (temperature <= 5) return "🧥 Freddo intenso, cappotto e sciarpa";

        // UV (solo se cielo sereno o quasi)
        if (uvIndex >= 8) return "☀️ UV molto alto, usa crema solare";
        if (uvIndex >= 6) return "😎 UV elevato, meglio occhiali e cappello";

        // Bel tempo
        if (code == 0 || code == 1) {
            if (temperature >= 18 && temperature <= 26) return "🌞 Giornata perfetta per stare all'aperto";
            return "✨ Cielo sereno, goditela";
        }

        return "🌤️ Tempo gradevole, nessuna accortezza particolare";
    }
}
