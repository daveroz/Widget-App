# Worldly (standalone Android app: Currency, SBB Clock and Calendar widgets)

A native replacement for the KWGT preset "Currency Exchange". No KWGT, no Sun Surveyor, no API
keys and no accounts. Install the APK, add the widget, done.

## Get the APK
1. Create a private GitHub repo and upload the contents of this folder (keep `.github/workflows/build.yml`
   at the repo root).
2. Open the **Actions** tab, run **Build APK** (also runs on every push to `main`).
3. Download the artifact **Worldly**; the APK inside is `Worldly.apk`.

(Or open the folder in Android Studio and use Build > Build APK.)

## Install & set up
1. Install the APK (allow "install unknown apps").
2. Open **Worldly**, check cities / currencies / fee, tap **Save & refresh**.
3. Tap **Use my location for weather** (or type a home city).
4. Tap **Add widget to home screen**.
The status text at the bottom of the settings screen shows what worked and what failed
(cities, Visa, weather).

## Widgets (3)

| Widget | What it is |
|---|---|
| **Currency Exchange** | World clocks, weather, market + Visa rates (the original KWGT widget). Behind each conversion row, the last 12 months of the market rate (one point a week) as a faint amber area chart (only the shape, no axis or numbers) |
| **SBB Clock** | Swiss railway station clock. Second hand: one lap in 58.5 s, then waits at 12; on the minute impulse the minute hand (and the hour hand with it) moves one minute with SBB's elastic swing, exactly when the second hand starts its next lap. Geometry and motion follow SBB's own digital clock (sbbUhr 1.3, dark-background face without frame) |
| **Calendar** | Agenda layout: big day number, week row (Sunday to Saturday, today in an amber circle), then your upcoming events with colour bar, time range and "in N minutes"; "+" adds an event. Reads the device calendar (READ_CALENDAR), no account or key. A taller widget lists more events, a wider one gives titles more room |

Live updates: world clocks and calendar run on exact per-minute alarms with no notification; only the moving SBB second hand uses a small foreground service ("Smooth" about 10 pictures a second, "Every second", or "Minute only"). With the screen off it does nothing. Alarms stay as a backup. Location is always where you are (last known device location, refreshed whenever you open the app). Fonts: Carbon+ (Currency) and Carbon+ (all widgets), both from the original KWGT presets.

## Data sources (all free, no key, no account)
| Data | Source |
|---|---|
| City -> coordinates, timezone, country | Open-Meteo geocoding |
| Feels-like + humidity | Open-Meteo forecast |
| Sunrise / sunset, day/night strip | Computed on the phone (no network) |
| Market exchange rates | fawazahmed0 currency-api (CDN + mirror), fallback open.er-api.com |
| Visa rate | Visa's public FX endpoint, same call as the original preset (fee=2, date MM/dd/yyyy) |
| Visa rate if Visa refuses | Estimate = market rate + your fee, shown with a `~` |
| 12-month trend behind each row | fawazahmed0 currency-api dated files (CDN + mirror): one request per sampled week covers every row, each is fetched once and cached |
| Flags | google/region-flags on GitHub, fallback flagcdn.com (downloaded once, then offline) |

Rates and weather inherently need an online source; these are the free ones that need no key.
Everything is cached, so the widget keeps showing the last values when offline.

## Visual language (shared with SAB Monitor)
Taken from `daverodd/SAB-App`: graphite surfaces (`#0e111a`, `#11141e`, `#151922`), thin slate borders
(`#1f2636`, `#252c3c`), a single amber accent (`#df8b35`, used as a gradient on primary buttons and for
arrows / day strip) and 16/12 corner radii. The type stays the original Carbon+ font from the KWGT
preset (`assets/fonts`). All of it lives in `Theme.java`; both the settings screen and the widget
read from there, so one edit restyles both.

## How it updates
- Clocks and day/night: redrawn every minute with an exact alarm.
- Rates / weather / Visa: refreshed about every 30 minutes (weather at most every 10 minutes).
- Trend history: filled once (about 52 small requests, one per week of the past year), then one new request a week, fetched after the rates so they never wait for it.

## Code map (`app/src/main/java/app/currencyexchange/widget/`)
`Renderer` draws everything onto one bitmap; `Trend` draws the rate chart behind each currency row; `Fetcher` does all network calls; `Store` holds settings
and cache; `SunCalc` offline sun times; `WidgetProvider` + `RefreshJobService` handle updates;
`MainActivity` is the settings screen; `Util`, `Net`, `Flags` are helpers.
Layout units are the 750-wide grid of the original preset.

Layout note: the Calendar and Currency widgets are native views with weighted rows/columns (fonts bundled in res/font), so they follow the widget's real width and height; only flags, the Visa logo and clock backgrounds are small bitmaps.
