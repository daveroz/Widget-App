# Worldly (standalone Android app: Currency, SBB Clock and Calendar widgets)

A native replacement for the KWGT preset "Currency Exchange". No KWGT, no Sun Surveyor, no API
keys and no accounts. Install the APK, add the widget, done.

## Get the APK
The newest build is always at
**https://github.com/daveroz/Worldly/releases/download/latest/Worldly.apk**
(GitHub Actions builds it on every push to `main`). Each run's **Actions** page also has
`Worldly.apk` to download directly, not inside a zip.

(Or open the folder in Android Studio and use Build > Build APK.)

### Signing, so updates install over each other
Android only installs an update that is signed with the same key as the version already on the
phone. Builds are signed with one fixed key, kept in the repository secret
**`SIGNING_KEYSTORE_BASE64`** (Settings → Secrets and variables → Actions): the keystore file,
base64-encoded. The key password and alias default to `android` and `androiddebugkey`; if yours
differ, add them as `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.
Without the secret, builds are signed with a throwaway key, can't install over an existing copy,
and aren't published to the `latest` release.

If the phone says **"package conflicts with an existing package"**, the copy on it was signed with
another key (builds from before the fixed key all had different ones). Uninstall Worldly once and
install the new APK; after that every update installs over the last one. Uninstalling removes
the widgets and settings, so add them again afterwards.

## Install & set up
1. Install the APK (allow "install unknown apps").
2. Open **Worldly**, check cities / currencies / fee, tap **Save & refresh**.
3. Tap **Use my location for weather** (or type a home city).
4. Tap **Add widget to home screen**.
The status text at the bottom of the settings screen shows what worked and what failed
(cities, Visa, weather).

## Widgets (4)

| Widget | What it is |
|---|---|
| **Currency Exchange** | World clocks, market + Visa rates (the original KWGT widget). The top bar shows, for where you are: the weather now (condition icon and temperature), the chance of rain over the next 12 hours, sunrise and sunset, in four equal sections. Tap a currency row to open the converter over the home screen: type an amount (say 30) and it shows the market and Visa value; the swap button converts the other way and the chips switch currency. Behind each conversion row, the last 30 days of the market rate (one point a day) as a faint amber area chart (only the shape, no axis or numbers) |
| **SBB Clock** | Swiss railway station clock. Second hand: one lap in 58.5 s, then waits at 12; on the minute impulse the minute hand (and the hour hand with it) moves one minute with SBB's elastic swing, exactly when the second hand starts its next lap. Geometry and motion follow SBB's own digital clock (sbbUhr 1.3, dark-background face without frame) |
| **Sound** | The Deco launcher's sound light show on the home screen. A wall of square lamps from margin to margin, in strips three wide, lit in one colour: the album cover's primary colour for music, the app's own colour for videos and apps without a cover. On top: "Now playing · app", the title and the artist on the left and, for music, the cover as a vinyl LP on the right, the record sliding out and spinning at 33⅓ rpm, slowing to a stop on pause. Below, the wall of lamps across the whole widget, its top a little behind the LP: while something plays the lamp columns follow its sound, bass on the left and treble on the right, filling up from the bottom; paused or nothing playing, the panels run their light patterns (filling, chasing bands, pixels, flashes), in Worldly's amber when nothing plays. Along the bottom, while something plays, the controls: previous, play / pause (lit in the light), next, the time so far, the progress line and the length; a tap on the progress line jumps the song to that point (it's split into 24 zones, as widgets can't be dragged). Small widgets leave out the times. Any size works: on a taller widget the wall gets more rows (the lamps stay the same size, and the sound fills its whole height), and the LP and the words grow with it. A tap anywhere else opens the playing app. Needs **notification access** (to see what's playing; nothing is read from notifications) and, to follow the real sound, **sound levels** (Android's "record audio" permission: it reads the phone's own sound through the audio visualizer, never the microphone). Without sound levels, or on phones that keep their sound to themselves, the lights move with the music on their own |
| **Calendar** | Agenda layout: big day number, week row (Sunday to Saturday, today lit in amber), then your upcoming events with colour bar, time range and "in N minutes"; "+" adds an event. Reads the device calendar (READ_CALENDAR), no account or key. A taller widget lists more events, a wider one gives titles more room |

Live updates: world clocks and calendar run on exact per-minute alarms with no notification; only the moving SBB second hand and the Sound widget's lights use a small foreground service (SBB: "Smooth" about 10 pictures a second, "Every second", or "Minute only"; Sound: about 20 pictures a second while something plays, 12 while it plays its patterns). With the screen off it does nothing. Alarms stay as a backup. Location is always where you are (last known device location, refreshed whenever you open the app). Fonts: Carbon+ (Currency) and Carbon+ (all widgets), both from the original KWGT presets.

## Data sources (all free, no key, no account)
| Data | Source |
|---|---|
| City -> coordinates, timezone, country | Open-Meteo geocoding |
| Weather now (condition, temperature) + hourly chance of rain | Open-Meteo forecast |
| Sunrise / sunset, day/night strip | Computed on the phone (no network) |
| Market exchange rates | fawazahmed0 currency-api (CDN + mirror), fallback open.er-api.com |
| Visa rate | Visa's public FX endpoint, same call as the original preset (fee=2, date MM/dd/yyyy) |
| Visa rate if Visa refuses | Estimate = market rate + your fee, shown with a `~` |
| 30-day trend behind each row | fawazahmed0 currency-api dated files (CDN + mirror): one request per day covers every row, each day is fetched once and cached |
| Flags | google/region-flags on GitHub, fallback flagcdn.com (downloaded once, then offline) |

Rates and weather inherently need an online source; these are the free ones that need no key.
Everything is cached, so the widget keeps showing the last values when offline.

## Visual language: light in fog (shared with the Deco launcher and SAB Monitor)
- **A night stage in haze.** The settings screen sits on a dark stage (`#05070b`): two slow washes of
  amber light, fog banks drifting through, a row of small lamps along the top running a slow chase,
  and a fine film grain (`StageView`).
- **Glass.** Cards are the night at 72% (`#0a0f16`) with a hairline edge of cool light
  (`rgba(170,220,255,0.12)`); inputs and pickers are darker wells inside the glass.
- **One key light: amber `#eb9438`.** Something that's on (the save button, the chosen SBB mode, the
  converter's amount and chip) is *lit*: a touch of amber on the glass, an amber edge and a soft glow,
  not a solid block. Lines drawn in the light (the 30-day trend) glow; today in the calendar is lit.
- **Labels** are small bold capitals, spaced out, like markings on a piece of equipment.
- **Widgets** use the same night glass and hairlines, with a faint amber wash from the top right, and
  the clocks' sun-phase strip glows up into the picture above it.

The type stays the original Carbon+ font from the KWGT preset (`assets/fonts`). Colours and the glass,
well and lit looks live in `Theme.java`; the settings screen, the converter and the widgets all read
from there, so one edit restyles everything.

## How it updates
- Clocks and day/night: redrawn every minute with an exact alarm.
- Rates / weather / Visa: refreshed about every 30 minutes (weather at most every 10 minutes).
- Trend history: filled once (about 30 small requests), then one new day a day, fetched after the rates so they never wait for it.

## Code map (`app/src/main/java/app/currencyexchange/widget/`)
`Renderer` draws everything onto one bitmap; `Trend` draws the rate chart behind each currency row; `Fetcher` does all network calls; `Store` holds settings
and cache; `SunCalc` offline sun times; `WidgetProvider` + `RefreshJobService` handle updates;
`MainActivity` is the settings screen; `Util`, `Net`, `Flags` are helpers.
Sound widget: `NowPlaying` reads the playing media session (through `MediaListener`, the notification listener),
`SoundWave` gives the sound's levels (audio visualizer, or made-up levels that move like music), `SoundLights` is the
wall of lamps, `SoundRecord` the LP, `SoundPicture` lays out one widget's picture, `Scene` and `Colours` give its
light, and `SoundWidgetProvider` + `LiveService` send the frames.
Layout units are the 750-wide grid of the original preset.

Layout note: the Calendar and Currency widgets are native views with weighted rows/columns (fonts bundled in res/font), so they follow the widget's real width and height; only flags, the Visa logo and clock backgrounds are small bitmaps.
