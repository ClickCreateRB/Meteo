/* =============================================================================
   Weather App · Frontend v2
   Sezioni:
     1. Config & state
     2. DOM helpers
     3. URL state (condivisione)
     4. Fetch API (meteo, autocomplete)
     5. Rendering principale
     6. Grafico temperatura (SVG)
     7. Bussola vento
     8. Arco sole sunrise/sunset
     9. Icone meteo SVG
    10. Cronologia + preferiti
    11. Modalità confronto preferiti
    12. Modale dettaglio giorno
    13. Particelle animate (canvas)
    14. Autocomplete con tastiera
    15. Geolocalizzazione
    16. Toggle unità/tema
    17. Scorciatoie tastiera
    18. Share
    19. Init
============================================================================= */

// ============================================================================
// 1. CONFIG & STATE
// ============================================================================
const API_BASE = '/api/weather';
const STORAGE = {
    history: 'weather-history',
    favorites: 'weather-favorites',
    unit: 'weather-unit',
    theme: 'weather-theme'
};
const HISTORY_MAX = 5;
const DEBOUNCE_MS = 300;

const state = {
    unit: localStorage.getItem(STORAGE.unit) || 'celsius',
    theme: localStorage.getItem(STORAGE.theme) || 'light',
    currentData: null,           // ultimo WeatherInfo ricevuto
    lastQuery: null,             // per ricaricare dopo cambio unità
    selectedAutocomplete: -1,    // indice navigazione tastiera
    particles: [],               // array particelle per canvas
    fetchToken: 0,               // token incrementale per invalidare risposte "vecchie"
    lastFocusedElement: null     // elemento da rifocalizzare alla chiusura del modale
};

// ============================================================================
// 2. DOM HELPERS
// ============================================================================
const $ = (id) => document.getElementById(id);

const el = {
    body: document.body,
    cityInput: $('cityInput'),
    geolocateBtn: $('geolocateBtn'),
    autocompleteList: $('autocompleteList'),
    historySection: $('historySection'),
    historyChips: $('historyChips'),
    favoritesSection: $('favoritesSection'),
    favoritesChips: $('favoritesChips'),
    alertBanner: $('alertBanner'),
    alertMessage: $('alertMessage'),
    alertCloseBtn: $('alertCloseBtn'),
    errorBanner: $('errorBanner'),
    errorMessage: $('errorMessage'),
    retryBtn: $('retryBtn'),
    loadingState: $('loadingState'),
    weatherSection: $('weatherSection'),
    emptyState: $('emptyState'),
    cityName: $('cityName'),
    cityMeta: $('cityMeta'),
    weatherIcon: $('weatherIcon'),
    temperature: $('temperature'),
    tempUnit: $('tempUnit'),
    description: $('description'),
    feelsLike: $('feelsLike'),
    humidity: $('humidity'),
    windSpeed: $('windSpeed'),
    pressure: $('pressure'),
    uvIndex: $('uvIndex'),
    suggestion: $('suggestion'),
    windCompass: $('windCompass'),
    windDirectionText: $('windDirectionText'),
    windSpeedCompass: $('windSpeedCompass'),
    sunArc: $('sunArc'),
    sunriseTime: $('sunriseTime'),
    sunsetTime: $('sunsetTime'),
    temperatureChart: $('temperatureChart'),
    forecastList: $('forecastList'),
    unitToggle: $('unitToggle'),
    themeToggle: $('themeToggle'),
    favoriteBtn: $('favoriteBtn'),
    shareBtn: $('shareBtn'),
    compareBtn: $('compareBtn'),
    closeCompareBtn: $('closeCompareBtn'),
    compareSection: $('compareSection'),
    compareGrid: $('compareGrid'),
    dayModal: $('dayModal'),
    closeModalBtn: $('closeModalBtn'),
    modalBody: $('modalBody'),
    greeting: $('greeting'),
    particlesCanvas: $('particlesCanvas')
};

// ============================================================================
// 3. URL STATE (città condivisibile)
// ============================================================================
function updateUrl(city) {
    const url = new URL(window.location);
    if (city && city !== 'La tua posizione') {
        url.searchParams.set('city', city);
    } else {
        url.searchParams.delete('city');
    }
    window.history.replaceState({}, '', url);
}

function getCityFromUrl() {
    return new URLSearchParams(window.location.search).get('city');
}

// ============================================================================
// 4. FETCH API
// ============================================================================
async function fetchWeatherByCity(city) {
    state.lastQuery = { type: 'city', value: city };
    await fetchWeather(`${API_BASE}?city=${encodeURIComponent(city)}&unit=${state.unit}`);
}

async function fetchWeatherByCoords(lat, lon) {
    state.lastQuery = { type: 'coords', lat, lon };
    await fetchWeather(`${API_BASE}/coords?lat=${lat}&lon=${lon}&unit=${state.unit}`);
}

async function fetchWeather(url) {
    showLoading();

    // Race-condition guard: se l'utente fa 3 ricerche in rapida successione,
    // le risposte possono arrivare fuori ordine (rete + cache) e l'ultima
    // in ordine temporale di render potrebbe NON essere l'ultima che l'utente
    // ha chiesto. Ogni chiamata incrementa un token; solo la risposta che
    // matcha il token corrente può rendere l'UI.
    const myToken = ++state.fetchToken;

    try {
        const res = await fetch(url);

        if (myToken !== state.fetchToken) return; // risposta obsoleta, ignora

        if (!res.ok) {
            // Se il server risponde con un errore, proviamo a estrarre il messaggio JSON
            // del GlobalExceptionHandler. Ma se risponde HTML (es. pagina di errore dal container
            // o da un filtro di sicurezza), res.json() lancia un SyntaxError: non vogliamo
            // mostrare all'utente un messaggio criptico tipo "Unexpected token '<'".
            let message = `Errore ${res.status}`;
            try {
                const err = await res.json();
                message = err.error || message;
            } catch {
                // risposta non-JSON: teniamo il fallback con il solo status code
            }
            throw new Error(message);
        }
        const data = await res.json();

        if (myToken !== state.fetchToken) return; // idem, check anche dopo res.json()

        state.currentData = data;
        renderWeather(data);
        addToHistory(data.city);
        updateUrl(data.city);
    } catch (err) {
        if (myToken !== state.fetchToken) return;
        hideLoading();
        showError(err.message);
    }
}

async function fetchAutocomplete(query) {
    try {
        const res = await fetch(`${API_BASE}/autocomplete?q=${encodeURIComponent(query)}`);
        if (!res.ok) return;
        return await res.json();
    } catch (err) {
        // L'autocomplete non è critico: non mostriamo un errore all'utente,
        // ma logghiamo in console per aiutare il debug se l'endpoint è rotto.
        console.warn('Autocomplete fallito:', err);
        return null;
    }
}

// ============================================================================
// 5. RENDERING PRINCIPALE
// ============================================================================
function renderWeather(data) {
    hideLoading();
    hideError();
    el.emptyState.classList.add('hidden');
    el.compareSection.classList.add('hidden');
    el.weatherSection.classList.remove('hidden');

    // Location
    el.cityName.textContent = data.city;
    const metaParts = [data.admin1, data.country].filter(Boolean);
    el.cityMeta.textContent = metaParts.join(', ');

    // Temperature con animazione
    animateNumber(el.temperature, Math.round(data.current.temperature));
    el.tempUnit.textContent = state.unit === 'celsius' ? '°C' : '°F';

    el.description.textContent = data.current.description;
    el.feelsLike.textContent = `${Math.round(data.current.feelsLike)}°`;
    el.humidity.textContent = `${data.current.humidity}%`;
    el.windSpeed.textContent = `${Math.round(data.current.windSpeed)} ${state.unit === 'celsius' ? 'km/h' : 'mph'}`;
    el.pressure.textContent = `${Math.round(data.current.pressure)} hPa`;
    el.uvIndex.innerHTML = renderUvIndex(data.current.weatherCode && data.today ? data.today.uvIndex : 0);

    // Icon grande
    el.weatherIcon.innerHTML = getWeatherIcon(data.current.icon);

    // Suggerimento
    if (data.current.suggestion) {
        el.suggestion.textContent = data.current.suggestion;
        el.suggestion.classList.remove('hidden');
    } else {
        el.suggestion.classList.add('hidden');
    }

    // Background
    el.body.dataset.weather = data.current.icon;

    // Componenti speciali
    renderWindCompass(data.current.windDirection, data.current.windSpeed);
    renderSunArc(data.today);
    renderTemperatureChart(data.forecast);
    renderForecast(data.forecast);

    // Stato preferito
    updateFavoriteButton(data.city);

    // Alert se condizioni estreme
    maybeShowAlert(data);

    // Particelle animate
    startParticles(data.current.icon);
}

function renderForecast(forecast) {
    if (!forecast || forecast.length === 0) { el.forecastList.innerHTML = ''; return; }

    const tempUnit = '°';
    const today = new Date().toDateString();

    el.forecastList.innerHTML = forecast.map((day, i) => {
        const dayDate = new Date(day.date);
        // Guardia contro date invalide (es. se il backend tornasse una stringa malformata):
        // new Date('xxx') è un oggetto Date valido ma con timestamp NaN.
        // toLocaleDateString su questo mostra "Invalid Date" all'utente.
        const validDate = !isNaN(dayDate.getTime());
        const isToday = validDate && dayDate.toDateString() === today;
        const dayName = !validDate ? '—'
            : isToday ? 'Oggi'
            : dayDate.toLocaleDateString('it-IT', { weekday: 'short' });

        const precip = day.precipitationProbability || 0;

        return `
            <div class="forecast-day" data-index="${i}" title="Clic per dettagli">
                <div class="forecast-day-name">${dayName}</div>
                <div class="forecast-icon">${getWeatherIcon(day.icon)}</div>
                <div class="forecast-temps">
                    <span class="forecast-temp-max">${Math.round(day.temperatureMax)}${tempUnit}</span>
                    <span class="forecast-temp-min">${Math.round(day.temperatureMin)}${tempUnit}</span>
                </div>
                ${precip > 0 ? `
                    <div class="forecast-precipitation-bar">
                        <div class="forecast-precipitation-fill" style="width: ${precip}%"></div>
                    </div>
                    ${precip > 20 ? `<div class="forecast-precipitation-label">💧 ${precip}%</div>` : ''}
                ` : ''}
            </div>
        `;
    }).join('');

    el.forecastList.querySelectorAll('.forecast-day').forEach(day => {
        day.addEventListener('click', () => openDayModal(Number(day.dataset.index)));
    });
}

function renderUvIndex(uv) {
    let label = 'Basso', cls = 'uv-low';
    if (uv >= 11) { label = 'Estremo'; cls = 'uv-extreme'; }
    else if (uv >= 8) { label = 'Molto alto'; cls = 'uv-very-high'; }
    else if (uv >= 6) { label = 'Alto'; cls = 'uv-high'; }
    else if (uv >= 3) { label = 'Moderato'; cls = 'uv-moderate'; }
    return `<span class="${cls}">${Math.round(uv)} · ${label}</span>`;
}

function animateNumber(element, target) {
    const current = parseInt(element.textContent) || 0;
    if (current === target) return;

    element.classList.add('changing');
    setTimeout(() => {
        element.textContent = target;
        element.classList.remove('changing');
    }, 150);
}

function maybeShowAlert(data) {
    const c = data.current;
    let msg = null;
    if (c.weatherCode === 95 || c.weatherCode === 96 || c.weatherCode === 99) {
        msg = '⛈️ Temporale previsto nella zona';
    } else if (c.temperature >= 35) {
        msg = '🔥 Ondata di calore · idratati spesso';
    } else if (c.temperature <= -5) {
        msg = '🥶 Gelo intenso · attenzione al ghiaccio';
    } else if (data.forecast && data.forecast[0] && data.forecast[0].precipitationProbability >= 80) {
        msg = '☔ Alta probabilità di pioggia nelle prossime ore';
    }

    if (msg) {
        showAlert(msg);
    } else {
        hideAlert();
    }
}

// ============================================================================
// 6. GRAFICO TEMPERATURA (SVG puro)
// ============================================================================
function renderTemperatureChart(forecast) {
    if (!forecast || forecast.length === 0) return;

    const width = 800;
    const height = 180;
    const padding = { top: 30, right: 30, bottom: 40, left: 30 };

    const maxTemps = forecast.map(d => d.temperatureMax);
    const minTemps = forecast.map(d => d.temperatureMin);
    const allTemps = [...maxTemps, ...minTemps];
    const maxT = Math.max(...allTemps);
    const minT = Math.min(...allTemps);
    const range = maxT - minT || 1;

    const chartWidth = width - padding.left - padding.right;
    const chartHeight = height - padding.top - padding.bottom;
    const stepX = chartWidth / (forecast.length - 1);

    const scaleY = (t) => padding.top + chartHeight - ((t - minT) / range) * chartHeight;
    const scaleX = (i) => padding.left + i * stepX;

    const pathMax = forecast.map((d, i) => {
        const x = scaleX(i);
        const y = scaleY(d.temperatureMax);
        return i === 0 ? `M ${x},${y}` : `L ${x},${y}`;
    }).join(' ');

    const pathArea = pathMax +
        ` L ${scaleX(forecast.length - 1)},${padding.top + chartHeight}` +
        ` L ${scaleX(0)},${padding.top + chartHeight} Z`;

    const dots = forecast.map((d, i) => `
        <circle class="chart-dot" cx="${scaleX(i)}" cy="${scaleY(d.temperatureMax)}" r="4"/>
        <text class="chart-value" x="${scaleX(i)}" y="${scaleY(d.temperatureMax) - 12}"
              text-anchor="middle">${Math.round(d.temperatureMax)}°</text>
    `).join('');

    const labels = forecast.map((d, i) => {
        const date = new Date(d.date);
        const dayName = i === 0 ? 'Oggi' : date.toLocaleDateString('it-IT', { weekday: 'short' });
        return `<text class="chart-label" x="${scaleX(i)}" y="${height - 10}"
                      text-anchor="middle">${dayName}</text>`;
    }).join('');

    el.temperatureChart.innerHTML = `
        <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none">
            <defs>
                <linearGradient id="tempGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stop-color="#5b6cff"/>
                    <stop offset="100%" stop-color="#ff6b9d"/>
                </linearGradient>
                <linearGradient id="tempAreaGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" stop-color="#5b6cff" stop-opacity="0.4"/>
                    <stop offset="100%" stop-color="#5b6cff" stop-opacity="0"/>
                </linearGradient>
            </defs>
            <path class="chart-area" d="${pathArea}"/>
            <path class="chart-line" d="${pathMax}"/>
            ${dots}
            ${labels}
        </svg>
    `;
}

// ============================================================================
// 7. BUSSOLA VENTO
// ============================================================================
function renderWindCompass(direction, speed) {
    const dirText = degreesToCompass(direction);
    el.windDirectionText.textContent = dirText;
    el.windSpeedCompass.textContent = `${Math.round(speed)} ${state.unit === 'celsius' ? 'km/h' : 'mph'}`;

    el.windCompass.innerHTML = `
        <svg viewBox="0 0 100 100">
            <circle cx="50" cy="50" r="44" fill="var(--surface)" stroke="var(--surface-border)" stroke-width="1"/>
            <circle cx="50" cy="50" r="38" fill="none" stroke="var(--surface-border)" stroke-width="0.5" stroke-dasharray="2 2"/>

            <text x="50" y="14" text-anchor="middle" font-size="9" font-weight="700" fill="var(--accent)" font-family="Inter">N</text>
            <text x="88" y="53" text-anchor="middle" font-size="8" fill="var(--text-tertiary)" font-family="Inter">E</text>
            <text x="50" y="92" text-anchor="middle" font-size="8" fill="var(--text-tertiary)" font-family="Inter">S</text>
            <text x="12" y="53" text-anchor="middle" font-size="8" fill="var(--text-tertiary)" font-family="Inter">O</text>

            <g class="compass-arrow" style="transform: rotate(${direction}deg)">
                <path d="M 50,22 L 44,52 L 50,48 L 56,52 Z" fill="var(--accent)"/>
                <path d="M 50,78 L 44,50 L 50,52 L 56,50 Z" fill="var(--text-tertiary)" opacity="0.4"/>
                <circle cx="50" cy="50" r="3" fill="var(--accent)"/>
            </g>
        </svg>
    `;
}

function degreesToCompass(deg) {
    const directions = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'];
    const idx = Math.round(((deg % 360) / 45)) % 8;
    return directions[idx];
}

// ============================================================================
// 8. ARCO SOLE (sunrise/sunset)
// ============================================================================
function renderSunArc(today) {
    if (!today || !today.sunrise || !today.sunset) {
        el.sunArc.innerHTML = '';
        return;
    }

    const sunrise = new Date(today.sunrise);
    const sunset = new Date(today.sunset);
    const now = new Date();

    const timeFmt = (d) => d.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' });
    el.sunriseTime.textContent = timeFmt(sunrise);
    el.sunsetTime.textContent = timeFmt(sunset);

    // Posizione del sole nell'arco (0 = sunrise, 1 = sunset)
    const totalDayMs = sunset - sunrise;
    const elapsedMs = now - sunrise;
    let progress = totalDayMs > 0 ? elapsedMs / totalDayMs : 0;
    progress = Math.max(0, Math.min(1, progress));
    const isNight = now < sunrise || now > sunset;

    // Arco SVG
    const width = 200, height = 120;
    const cx = width / 2, cy = height - 10, r = 80;

    // Posizione del sole: arco da sinistra (180°) a destra (0°)
    const angle = Math.PI * (1 - progress);
    const sunX = cx + r * Math.cos(angle);
    const sunY = cy - r * Math.sin(angle);

    // Percorso arco
    const arcPath = `M ${cx - r},${cy} A ${r},${r} 0 0 1 ${cx + r},${cy}`;
    const arcPassed = `M ${cx - r},${cy} A ${r},${r} 0 0 1 ${sunX},${sunY}`;

    el.sunArc.innerHTML = `
        <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="xMidYMid meet">
            <defs>
                <linearGradient id="sunArcGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stop-color="#fbbf24"/>
                    <stop offset="50%" stop-color="#f97316"/>
                    <stop offset="100%" stop-color="#ef4444"/>
                </linearGradient>
            </defs>
            <path d="${arcPath}" fill="none" stroke="var(--surface-border)"
                  stroke-width="2" stroke-dasharray="3 3"/>
            ${!isNight ? `
                <path d="${arcPassed}" fill="none" stroke="url(#sunArcGrad)" stroke-width="3" stroke-linecap="round"/>
                <circle cx="${sunX}" cy="${sunY}" r="8" fill="#fbbf24" filter="drop-shadow(0 0 8px #fbbf24)"/>
                <circle cx="${sunX}" cy="${sunY}" r="4" fill="#fff"/>
            ` : `
                <circle cx="${cx}" cy="${cy - 30}" r="10" fill="#e0e7ff"/>
                <circle cx="${cx + 3}" cy="${cy - 33}" r="8" fill="var(--bg-base)"/>
            `}
            <line x1="${cx - r}" y1="${cy}" x2="${cx + r}" y2="${cy}" stroke="var(--surface-border)" stroke-width="1"/>
        </svg>
    `;
}

// ============================================================================
// 9. ICONE METEO SVG
// ============================================================================
function getWeatherIcon(type) {
    const icons = {
        'sunny': `<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="20" fill="#ffb703"/>
            <g stroke="#ffb703" stroke-width="4" stroke-linecap="round">
                <line x1="50" y1="10" x2="50" y2="20"/><line x1="50" y1="80" x2="50" y2="90"/>
                <line x1="10" y1="50" x2="20" y2="50"/><line x1="80" y1="50" x2="90" y2="50"/>
                <line x1="22" y1="22" x2="29" y2="29"/><line x1="71" y1="71" x2="78" y2="78"/>
                <line x1="22" y1="78" x2="29" y2="71"/><line x1="71" y1="29" x2="78" y2="22"/>
            </g></svg>`,
        'clear-night': `<svg viewBox="0 0 100 100">
            <path d="M60 20 Q40 25 40 50 Q40 75 65 75 Q75 75 80 70 Q65 70 55 55 Q50 45 55 35 Q60 25 70 22 Q65 20 60 20 Z" fill="#e0e7ff"/>
            <circle cx="75" cy="25" r="1.5" fill="#fff"/><circle cx="85" cy="40" r="1" fill="#fff"/><circle cx="78" cy="55" r="1" fill="#fff"/>
        </svg>`,
        'partly-cloudy': `<svg viewBox="0 0 100 100">
            <circle cx="35" cy="40" r="15" fill="#ffb703"/>
            <g stroke="#ffb703" stroke-width="3" stroke-linecap="round">
                <line x1="35" y1="15" x2="35" y2="20"/><line x1="15" y1="40" x2="20" y2="40"/><line x1="18" y1="22" x2="22" y2="26"/>
            </g>
            <path d="M45 60 Q45 50 55 50 Q65 42 75 50 Q85 50 85 60 Q85 70 75 70 L55 70 Q45 70 45 60 Z" fill="#cbd5e0"/>
        </svg>`,
        'partly-cloudy-night': `<svg viewBox="0 0 100 100">
            <path d="M40 25 Q28 30 30 45 Q33 55 45 55 Q50 55 53 52 Q45 50 40 43 Q38 35 42 28 Q41 26 40 25 Z" fill="#e0e7ff"/>
            <path d="M45 65 Q45 55 55 55 Q65 48 75 55 Q85 55 85 65 Q85 75 75 75 L55 75 Q45 75 45 65 Z" fill="#94a3b8"/>
        </svg>`,
        'cloudy': `<svg viewBox="0 0 100 100">
            <path d="M25 55 Q25 42 40 42 Q50 30 65 40 Q82 38 82 55 Q82 70 65 70 L40 70 Q25 70 25 55 Z" fill="#94a3b8"/>
            <path d="M35 35 Q35 25 50 25 Q60 18 72 28 Q85 28 85 40 L85 42 Q82 38 75 38 Q65 30 55 38 Q45 38 42 45 Q35 45 35 35 Z" fill="#cbd5e0" opacity="0.7"/>
        </svg>`,
        'rain': `<svg viewBox="0 0 100 100">
            <path d="M25 45 Q25 32 40 32 Q50 20 65 30 Q82 28 82 45 Q82 60 65 60 L40 60 Q25 60 25 45 Z" fill="#64748b"/>
            <g fill="#3b82f6">
                <ellipse cx="35" cy="75" rx="2" ry="5"/><ellipse cx="50" cy="80" rx="2" ry="5"/>
                <ellipse cx="65" cy="75" rx="2" ry="5"/><ellipse cx="42" cy="85" rx="2" ry="4"/>
                <ellipse cx="58" cy="85" rx="2" ry="4"/>
            </g></svg>`,
        'drizzle': `<svg viewBox="0 0 100 100">
            <path d="M25 45 Q25 32 40 32 Q50 20 65 30 Q82 28 82 45 Q82 60 65 60 L40 60 Q25 60 25 45 Z" fill="#94a3b8"/>
            <g fill="#60a5fa">
                <circle cx="38" cy="75" r="2"/><circle cx="52" cy="78" r="2"/>
                <circle cx="66" cy="75" r="2"/><circle cx="45" cy="85" r="2"/><circle cx="60" cy="85" r="2"/>
            </g></svg>`,
        'showers': `<svg viewBox="0 0 100 100">
            <path d="M25 45 Q25 32 40 32 Q50 20 65 30 Q82 28 82 45 Q82 60 65 60 L40 60 Q25 60 25 45 Z" fill="#475569"/>
            <g fill="#2563eb">
                <path d="M35 68 L33 78 L37 78 Z"/><path d="M50 72 L48 82 L52 82 Z"/><path d="M65 68 L63 78 L67 78 Z"/>
            </g></svg>`,
        'snow': `<svg viewBox="0 0 100 100">
            <path d="M25 45 Q25 32 40 32 Q50 20 65 30 Q82 28 82 45 Q82 60 65 60 L40 60 Q25 60 25 45 Z" fill="#cbd5e0"/>
            <g fill="#e0e7ff" stroke="#94a3b8" stroke-width="1">
                <text x="35" y="80" font-size="14">❄</text><text x="50" y="85" font-size="14">❄</text><text x="65" y="80" font-size="14">❄</text>
            </g></svg>`,
        'thunderstorm': `<svg viewBox="0 0 100 100">
            <path d="M25 45 Q25 32 40 32 Q50 20 65 30 Q82 28 82 45 Q82 60 65 60 L40 60 Q25 60 25 45 Z" fill="#1e293b"/>
            <path d="M50 60 L40 80 L48 80 L44 92 L58 72 L50 72 L54 60 Z" fill="#fbbf24"/>
        </svg>`,
        'fog': `<svg viewBox="0 0 100 100">
            <g stroke="#94a3b8" stroke-width="6" stroke-linecap="round">
                <line x1="20" y1="35" x2="75" y2="35"/><line x1="25" y1="50" x2="85" y2="50"/>
                <line x1="15" y1="65" x2="70" y2="65"/><line x1="30" y1="80" x2="80" y2="80"/>
            </g></svg>`
    };
    return icons[type] || icons['cloudy'];
}

// ============================================================================
// 10. CRONOLOGIA + PREFERITI
// ============================================================================
function getList(key) {
    try { return JSON.parse(localStorage.getItem(key)) || []; } catch { return []; }
}

function setList(key, list) {
    // localStorage può fallire in modalità privata Safari, con quota piena,
    // o con browser che lo disabilitano per privacy. Non lasciare che un
    // errore di storage blocchi l'app: se non possiamo persistere, ok.
    try {
        localStorage.setItem(key, JSON.stringify(list));
    } catch (err) {
        console.warn('Impossibile salvare in localStorage:', err);
        showAlert('Impossibile salvare le preferenze (storage pieno o disabilitato)');
    }
}

// Numero massimo di città confrontabili: oltre questa soglia la grid diventa
// illeggibile su mobile e la chiamata parallela pesa troppo.
const FAVORITES_MAX = 10;

function addToHistory(city) {
    if (!city || city === 'La tua posizione') return;
    let history = getList(STORAGE.history).filter(c => c.toLowerCase() !== city.toLowerCase());
    history.unshift(city);
    history = history.slice(0, HISTORY_MAX);
    setList(STORAGE.history, history);
    renderChips(el.historySection, el.historyChips, history, (c) => fetchWeatherByCity(c));
}

function toggleFavorite() {
    if (!state.currentData) return;
    const city = state.currentData.city;
    if (city === 'La tua posizione') return;

    let favs = getList(STORAGE.favorites);
    const idx = favs.findIndex(c => c.toLowerCase() === city.toLowerCase());
    if (idx >= 0) {
        favs.splice(idx, 1);
    } else {
        if (favs.length >= FAVORITES_MAX) {
            showAlert(`Massimo ${FAVORITES_MAX} preferiti · rimuovi una città per aggiungerne altre`);
            return;
        }
        favs.push(city);
    }
    setList(STORAGE.favorites, favs);
    updateFavoriteButton(city);
    renderFavorites();
}

function updateFavoriteButton(city) {
    const favs = getList(STORAGE.favorites);
    const isFav = favs.some(c => c.toLowerCase() === (city || '').toLowerCase());
    el.favoriteBtn.classList.toggle('active', isFav);
    el.favoriteBtn.title = isFav ? 'Rimuovi dai preferiti' : 'Aggiungi ai preferiti';
}

function renderFavorites() {
    const favs = getList(STORAGE.favorites);
    renderChips(el.favoritesSection, el.favoritesChips, favs, (c) => fetchWeatherByCity(c));
}

function renderHistory() {
    const h = getList(STORAGE.history);
    renderChips(el.historySection, el.historyChips, h, (c) => fetchWeatherByCity(c));
}

function renderChips(section, container, items, onClick) {
    if (!items || items.length === 0) { section.classList.add('hidden'); return; }
    section.classList.remove('hidden');
    container.innerHTML = items.map(c => `<button class="history-chip">${escapeHtml(c)}</button>`).join('');
    container.querySelectorAll('.history-chip').forEach((chip, i) => {
        chip.addEventListener('click', () => { el.cityInput.value = items[i]; onClick(items[i]); });
    });
}

// ============================================================================
// 11. MODALITA' CONFRONTO PREFERITI
// ============================================================================
async function openCompare() {
    const favs = getList(STORAGE.favorites);
    // Un "confronto" con 0 o 1 città non ha senso come funzione:
    // serve almeno una coppia per avere qualcosa da mettere a paragone.
    if (favs.length < 2) {
        const msg = favs.length === 0
            ? 'Aggiungi almeno 2 città ai preferiti per confrontarle'
            : 'Serve almeno una seconda città nei preferiti per il confronto';
        showAlert(msg);
        return;
    }

    el.weatherSection.classList.add('hidden');
    el.emptyState.classList.add('hidden');
    el.compareSection.classList.remove('hidden');
    el.compareGrid.innerHTML = '<div style="color:var(--text-tertiary)">Caricamento...</div>';

    // Fetch parallelo
    const results = await Promise.all(favs.map(async city => {
        try {
            const res = await fetch(`${API_BASE}?city=${encodeURIComponent(city)}&unit=${state.unit}`);
            if (!res.ok) return null;
            return await res.json();
        } catch { return null; }
    }));

    const valid = results.filter(Boolean);
    if (valid.length === 0) {
        el.compareGrid.innerHTML = '<div>Nessun dato caricabile</div>';
        return;
    }

    el.compareGrid.innerHTML = valid.map(d => `
        <div class="compare-card glass" data-city="${escapeHtml(d.city)}">
            <div class="compare-card-header">
                <div>
                    <div class="compare-card-city">${escapeHtml(d.city)}</div>
                    <div class="compare-card-country">${escapeHtml(d.country || '')}</div>
                </div>
                <div class="compare-card-icon">${getWeatherIcon(d.current.icon)}</div>
            </div>
            <div class="compare-card-temp">${Math.round(d.current.temperature)}°</div>
            <div class="compare-card-desc">${escapeHtml(d.current.description)}</div>
        </div>
    `).join('');

    el.compareGrid.querySelectorAll('.compare-card').forEach(card => {
        card.addEventListener('click', () => {
            el.compareSection.classList.add('hidden');
            fetchWeatherByCity(card.dataset.city);
        });
    });
}

function closeCompare() {
    el.compareSection.classList.add('hidden');
    if (state.currentData) el.weatherSection.classList.remove('hidden');
    else el.emptyState.classList.remove('hidden');
}

// ============================================================================
// 12. MODALE DETTAGLIO GIORNO
// ============================================================================
function openDayModal(index) {
    if (!state.currentData || !state.currentData.forecast[index]) return;
    const day = state.currentData.forecast[index];
    const date = new Date(day.date);
    // Stessa guardia di renderForecast: Invalid Date non deve propagarsi all'UI.
    const validDate = !isNaN(date.getTime());
    const today = new Date().toDateString();
    const isToday = validDate && date.toDateString() === today;
    const dayName = !validDate ? '—' : isToday ? 'Oggi' : date.toLocaleDateString('it-IT', { weekday: 'long' });
    const dateStr = !validDate ? '' : date.toLocaleDateString('it-IT', { day: 'numeric', month: 'long' });

    el.modalBody.innerHTML = `
        <div class="modal-day-name">${dayName}</div>
        <div class="modal-day-date">${dateStr}</div>
        <div class="modal-icon">${getWeatherIcon(day.icon)}</div>
        <div class="modal-temp-range">
            <span class="modal-temp-max">${Math.round(day.temperatureMax)}°</span>
            <span class="modal-temp-min">${Math.round(day.temperatureMin)}°</span>
        </div>
        <div class="modal-description">${escapeHtml(day.description)}</div>
        <div class="modal-details">
            <div class="modal-detail">
                <div class="modal-detail-label">Pioggia</div>
                <div class="modal-detail-value">${day.precipitationProbability || 0}%</div>
            </div>
            <div class="modal-detail">
                <div class="modal-detail-label">Indice UV</div>
                <div class="modal-detail-value">${renderUvIndex(day.uvIndex)}</div>
            </div>
        </div>
    `;
    el.dayModal.classList.remove('hidden');

    // Focus management accessibile:
    // 1. Salviamo chi aveva il focus prima, per restituirlo alla chiusura
    // 2. Muoviamo il focus sul primo elemento interattivo del modale
    // Senza questo, screen reader e utenti tastiera-only continuano a navigare
    // tra gli elementi sotto al modale come se nulla fosse aperto.
    state.lastFocusedElement = document.activeElement;
    el.closeModalBtn.focus();
}

function closeDayModal() {
    el.dayModal.classList.add('hidden');
    // Restituiamo il focus all'elemento che l'aveva prima dell'apertura,
    // tipicamente la card forecast cliccata. Comportamento atteso di un modale.
    if (state.lastFocusedElement && typeof state.lastFocusedElement.focus === 'function') {
        state.lastFocusedElement.focus();
        state.lastFocusedElement = null;
    }
}

// ============================================================================
// 13. PARTICELLE ANIMATE (canvas)
// ============================================================================
let particlesAnimationId = null;

// Media query per rilevare preferenza "movimento ridotto" del sistema operativo.
// Se attiva, saltiamo completamente le particelle animate: WCAG 2.1 best practice.
const REDUCED_MOTION_QUERY = window.matchMedia('(prefers-reduced-motion: reduce)');

function startParticles(weatherType) {
    cancelAnimationFrame(particlesAnimationId);
    state.particles = [];

    const canvas = el.particlesCanvas;
    if (!canvas) return;

    // Rispetto accessibilità: niente animazioni se l'utente lo preferisce.
    if (REDUCED_MOTION_QUERY.matches) {
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        return;
    }

    // Configurazione DPR-aware: su schermi retina (devicePixelRatio = 2 o 3) un canvas
    // dimensionato solo in CSS pixel viene scalato dal browser e appare sfocato.
    // La tecnica standard è: size interna = CSS size * DPR, poi ctx.scale(DPR, DPR)
    // così disegniamo in coordinate CSS ma renderizziamo alla risoluzione nativa.
    const dpr = window.devicePixelRatio || 1;
    const cssWidth = window.innerWidth;
    const cssHeight = window.innerHeight;
    canvas.width = cssWidth * dpr;
    canvas.height = cssHeight * dpr;
    canvas.style.width = cssWidth + 'px';
    canvas.style.height = cssHeight + 'px';
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    let count = 0, type = null;
    if (['rain', 'drizzle', 'showers', 'thunderstorm'].includes(weatherType)) { count = 80; type = 'rain'; }
    else if (weatherType === 'snow') { count = 60; type = 'snow'; }
    else if (weatherType === 'clear-night' || weatherType === 'partly-cloudy-night') { count = 50; type = 'stars'; }

    if (!type) {
        ctx.clearRect(0, 0, cssWidth, cssHeight);
        return;
    }

    for (let i = 0; i < count; i++) {
        state.particles.push(createParticle(type, cssWidth, cssHeight));
    }

    function animate() {
        ctx.clearRect(0, 0, cssWidth, cssHeight);
        state.particles.forEach(p => {
            if (type === 'rain') {
                ctx.strokeStyle = 'rgba(174, 194, 224, 0.4)';
                ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.moveTo(p.x, p.y);
                ctx.lineTo(p.x + p.dx, p.y + p.dy);
                ctx.stroke();
                p.x += p.dx; p.y += p.dy;
                if (p.y > cssHeight) { p.y = -10; p.x = Math.random() * cssWidth; }
            } else if (type === 'snow') {
                ctx.fillStyle = 'rgba(255, 255, 255, 0.8)';
                ctx.beginPath();
                ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
                ctx.fill();
                p.y += p.speed;
                p.x += Math.sin(p.y * 0.01) * 0.5;
                if (p.y > cssHeight) { p.y = -10; p.x = Math.random() * cssWidth; }
            } else if (type === 'stars') {
                ctx.fillStyle = `rgba(255, 255, 255, ${p.opacity})`;
                ctx.beginPath();
                ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
                ctx.fill();
                p.opacity += p.delta;
                if (p.opacity <= 0.2 || p.opacity >= 1) p.delta = -p.delta;
            }
        });
        particlesAnimationId = requestAnimationFrame(animate);
    }
    animate();
}

function createParticle(type, w, h) {
    if (type === 'rain') {
        return { x: Math.random() * w, y: Math.random() * h, dx: -1, dy: 8 + Math.random() * 4 };
    } else if (type === 'snow') {
        return { x: Math.random() * w, y: Math.random() * h, r: 1 + Math.random() * 3, speed: 0.5 + Math.random() * 1.5 };
    } else {
        return { x: Math.random() * w, y: Math.random() * h * 0.6, r: Math.random() * 1.5, opacity: Math.random(), delta: (Math.random() - 0.5) * 0.02 };
    }
}

// Resize debounced: evita di ricreare il canvas decine di volte quando l'utente
// trascina il bordo della finestra. 150ms è il sweet-spot: reattivo ma non spammoso.
let resizeDebounceId = null;
window.addEventListener('resize', () => {
    clearTimeout(resizeDebounceId);
    resizeDebounceId = setTimeout(() => {
        if (state.currentData) startParticles(state.currentData.current.icon);
    }, 150);
});

// ============================================================================
// 14. AUTOCOMPLETE
// ============================================================================
let debounceTimer = null;

function onInputChange(e) {
    const query = e.target.value.trim();
    clearTimeout(debounceTimer);
    if (query.length < 2) { hideAutocomplete(); return; }
    debounceTimer = setTimeout(async () => {
        const results = await fetchAutocomplete(query);
        renderAutocomplete(results);
    }, DEBOUNCE_MS);
}

function renderAutocomplete(results) {
    if (!results || results.length === 0) { hideAutocomplete(); return; }

    state.selectedAutocomplete = -1;

    el.autocompleteList.innerHTML = results.map((r, i) => `
        <li class="autocomplete-item" role="option" aria-selected="false" data-index="${i}"
            data-name="${escapeHtml(r.name)}">
            <span class="flag">${flagEmoji(r.countryCode)}</span>
            <div class="place">
                <div class="place-name">${escapeHtml(r.name)}</div>
                <div class="place-region">${escapeHtml(r.admin1 || '')}${r.admin1 && r.country ? ', ' : ''}${escapeHtml(r.country || '')}</div>
            </div>
        </li>
    `).join('');

    el.autocompleteList.classList.remove('hidden');
    el.autocompleteList.querySelectorAll('.autocomplete-item').forEach(item => {
        item.addEventListener('click', () => {
            const name = item.dataset.name;
            el.cityInput.value = name;
            hideAutocomplete();
            fetchWeatherByCity(name);
        });
    });
}

function hideAutocomplete() {
    el.autocompleteList.classList.add('hidden');
    el.autocompleteList.innerHTML = '';
    state.selectedAutocomplete = -1;
}

function onInputKeydown(e) {
    const items = el.autocompleteList.querySelectorAll('.autocomplete-item');

    if (e.key === 'Enter') {
        e.preventDefault();
        if (state.selectedAutocomplete >= 0 && items[state.selectedAutocomplete]) {
            items[state.selectedAutocomplete].click();
        } else {
            hideAutocomplete();
            const query = el.cityInput.value.trim();
            if (query) fetchWeatherByCity(query);
        }
    } else if (e.key === 'Escape') {
        hideAutocomplete();
        el.cityInput.blur();
    } else if (e.key === 'ArrowDown' && items.length) {
        e.preventDefault();
        state.selectedAutocomplete = (state.selectedAutocomplete + 1) % items.length;
        updateAutocompleteSelection(items);
    } else if (e.key === 'ArrowUp' && items.length) {
        e.preventDefault();
        state.selectedAutocomplete = state.selectedAutocomplete <= 0 ? items.length - 1 : state.selectedAutocomplete - 1;
        updateAutocompleteSelection(items);
    }
}

function updateAutocompleteSelection(items) {
    items.forEach((item, i) => {
        const isSelected = i === state.selectedAutocomplete;
        item.classList.toggle('active', isSelected);
        item.setAttribute('aria-selected', isSelected ? 'true' : 'false');
    });
    if (items[state.selectedAutocomplete]) items[state.selectedAutocomplete].scrollIntoView({ block: 'nearest' });
}

// ============================================================================
// 15. GEOLOCALIZZAZIONE
// ============================================================================
function useGeolocation() {
    if (!navigator.geolocation) { showError('Geolocalizzazione non supportata dal browser'); return; }
    showLoading();
    navigator.geolocation.getCurrentPosition(
        (pos) => fetchWeatherByCoords(pos.coords.latitude, pos.coords.longitude),
        (err) => {
            hideLoading();
            showError(err.code === err.PERMISSION_DENIED
                ? 'Permesso negato. Cerca una città manualmente.'
                : 'Impossibile determinare la posizione.');
        },
        { timeout: 10000 }
    );
}

// ============================================================================
// 16. TOGGLE UNITA'/TEMA
// ============================================================================
function toggleUnit() {
    state.unit = state.unit === 'celsius' ? 'fahrenheit' : 'celsius';
    localStorage.setItem(STORAGE.unit, state.unit);
    el.unitToggle.querySelector('.pill-value').textContent = state.unit === 'celsius' ? '°C' : '°F';

    if (state.lastQuery) {
        if (state.lastQuery.type === 'city') fetchWeatherByCity(state.lastQuery.value);
        else fetchWeatherByCoords(state.lastQuery.lat, state.lastQuery.lon);
    }
}

function applyUnitLabel() {
    el.unitToggle.querySelector('.pill-value').textContent = state.unit === 'celsius' ? '°C' : '°F';
}

function toggleTheme() {
    state.theme = state.theme === 'light' ? 'dark' : 'light';
    localStorage.setItem(STORAGE.theme, state.theme);
    applyTheme(state.theme);
}

function applyTheme(theme) {
    el.body.dataset.theme = theme;
    el.themeToggle.querySelector('.theme-icon').textContent = theme === 'light' ? '🌙' : '☀️';
}

// ============================================================================
// 17. SCORCIATOIE TASTIERA
// ============================================================================
function bindKeyboardShortcuts() {
    document.addEventListener('keydown', (e) => {
        if (e.key === '/' && document.activeElement !== el.cityInput) {
            e.preventDefault();
            el.cityInput.focus();
            el.cityInput.select();
        } else if (e.key === 'Escape') {
            if (!el.dayModal.classList.contains('hidden')) closeDayModal();
            else if (!el.compareSection.classList.contains('hidden')) closeCompare();
        }
    });
}

// ============================================================================
// 18. SHARE
// ============================================================================
async function shareCurrent() {
    if (!state.currentData) return;
    const city = state.currentData.city;
    const c = state.currentData.current;
    const unitSymbol = state.unit === 'celsius' ? '°C' : '°F';
    const text = `🌤️ ${city}: ${Math.round(c.temperature)}${unitSymbol}, ${c.description}`;
    const url = `${window.location.origin}${window.location.pathname}?city=${encodeURIComponent(city)}`;

    if (navigator.share) {
        try { await navigator.share({ title: 'Meteo', text, url }); } catch { /* user cancel */ }
    } else {
        try {
            await navigator.clipboard.writeText(`${text}\n${url}`);
            showAlert('Link copiato negli appunti ✓');
        } catch {
            showAlert('Impossibile copiare il link');
        }
    }
}

// ============================================================================
// STATI UI + UTILITIES
// ============================================================================
function showLoading() {
    el.loadingState.classList.remove('hidden');
    el.weatherSection.classList.add('hidden');
    el.emptyState.classList.add('hidden');
    el.errorBanner.classList.add('hidden');
    el.alertBanner.classList.add('hidden');
    // Disabilita interazioni che possono lanciare un nuovo fetch mentre uno è in corso.
    // I dati finali sono già protetti dal fetchToken, ma questo dà anche un feedback
    // visivo onesto ("sto lavorando, aspetta") ed evita spam di loading spinners.
    setInteractionsDisabled(true);
}

function hideLoading() {
    el.loadingState.classList.add('hidden');
    setInteractionsDisabled(false);
}

function setInteractionsDisabled(disabled) {
    [el.geolocateBtn, el.unitToggle, el.compareBtn, el.favoriteBtn, el.shareBtn]
        .forEach(btn => { if (btn) btn.disabled = disabled; });
}

function showError(message) {
    el.errorMessage.textContent = message;
    el.errorBanner.classList.remove('hidden');
    el.loadingState.classList.add('hidden');
}

function hideError() { el.errorBanner.classList.add('hidden'); }

function showAlert(message) {
    // Target il contenuto TESTUALE (non l'intero banner), altrimenti
    // sovrascriveremmo anche il close button al suo interno.
    el.alertMessage.textContent = message;
    el.alertBanner.classList.remove('hidden');

    // Auto-hide dopo 5 secondi: evita che messaggi informativi
    // ("Aggiungi città ai preferiti", "Link copiato") restino visibili
    // all'infinito finché l'utente non ricarica nuovo meteo.
    clearTimeout(showAlert._timer);
    showAlert._timer = setTimeout(hideAlert, 5000);
}

function hideAlert() {
    el.alertBanner.classList.add('hidden');
    clearTimeout(showAlert._timer);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

function flagEmoji(countryCode) {
    if (!countryCode || countryCode.length !== 2) return '🌍';
    const cp = countryCode.toUpperCase().split('').map(c => 127397 + c.charCodeAt(0));
    return String.fromCodePoint(...cp);
}

function updateGreeting() {
    const h = new Date().getHours();
    let g;
    if (h < 6) g = 'Buonanotte 🌙';
    else if (h < 12) g = 'Buongiorno ☀️';
    else if (h < 18) g = 'Buon pomeriggio 🌤️';
    else if (h < 22) g = 'Buonasera 🌅';
    else g = 'Buonanotte 🌙';
    el.greeting.textContent = g;
}

// ============================================================================
// 19. INIT
// ============================================================================
function init() {
    applyTheme(state.theme);
    applyUnitLabel();
    updateGreeting();
    renderHistory();
    renderFavorites();

    // Event bindings
    el.cityInput.addEventListener('input', onInputChange);
    el.cityInput.addEventListener('keydown', onInputKeydown);
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.search-wrapper')) hideAutocomplete();
    });

    el.geolocateBtn.addEventListener('click', useGeolocation);
    el.unitToggle.addEventListener('click', toggleUnit);
    el.themeToggle.addEventListener('click', toggleTheme);
    el.favoriteBtn.addEventListener('click', toggleFavorite);
    el.shareBtn.addEventListener('click', shareCurrent);
    el.compareBtn.addEventListener('click', openCompare);
    el.closeCompareBtn.addEventListener('click', closeCompare);
    el.closeModalBtn.addEventListener('click', closeDayModal);
    el.dayModal.querySelector('.modal-backdrop').addEventListener('click', closeDayModal);
    el.retryBtn.addEventListener('click', () => {
        if (state.lastQuery) {
            if (state.lastQuery.type === 'city') fetchWeatherByCity(state.lastQuery.value);
            else fetchWeatherByCoords(state.lastQuery.lat, state.lastQuery.lon);
        }
    });
    el.alertCloseBtn.addEventListener('click', hideAlert);

    bindKeyboardShortcuts();

    // Aggiorna greeting ogni minuto
    setInterval(updateGreeting, 60000);

    // Carica città da URL se presente
    const cityFromUrl = getCityFromUrl();
    if (cityFromUrl) {
        el.cityInput.value = cityFromUrl;
        fetchWeatherByCity(cityFromUrl);
    }
}

init();
