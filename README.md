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
5. Optional: under Permissions, tap **Allow wallpaper glass** so each widget's glass shows your wallpaper behind it,
   blurred (Android files reading the wallpaper under "All files access"; Worldly reads nothing else). Then tap each
   widget once, so its glass lines up with where it sits.
The status text at the bottom of the settings screen shows what worked and what failed
(cities, market rate, Visa, weather).

## Widgets (5)

| Widget | What it is |
|---|---|
| **Currency Exchange** | World clocks, market + Visa rates (the original KWGT widget). The top bar shows, for where you are: the weather now (condition icon and temperature), the chance of rain over the next 12 hours, sunrise and sunset, in four equal sections. Tap a currency row to open the converter over the home screen: type an amount (say 30) and it shows the market and Visa value, fetching the live market rate as it opens; the swap button converts the other way and the chips switch currency. Behind each conversion row, the last 30 days of the market rate (one point a day) as a faint area chart in the song's light (only the shape, no axis or numbers), dimming towards the row's end so the numbers there stay clear. Under each world clock, a band in the colour of that city's sun phase: day, golden hour, blue hour or night |
| **SBB Clock** | Swiss railway station clock. Second hand: one lap in 58.5 s, then waits at 12; on the minute impulse the minute hand (and the hour hand with it) moves one minute with SBB's elastic swing, exactly when the second hand starts its next lap. Geometry and motion follow SBB's own digital clock (sbbUhr 1.3, dark-background face without frame) |
| **Sound** | The Deco launcher's sound light show on the home screen. A wall of square lamps from margin to margin, in strips three wide, lit in one colour: the album cover's primary colour for music, the app's own colour for videos and apps without a cover. On top: "Now playing · app", the title and the artist on the left and, for music, the cover as a vinyl LP on the right, the record sliding out and spinning at 33⅓ rpm, slowing to a stop on pause. Below, the wall of lamps across the whole widget, its top a little behind the LP: while something plays the lamp columns follow its sound, bass on the left and treble on the right, filling up from the bottom; paused or nothing playing, an airstream with turbulence crosses the wall: two streams of air flowing in from the left on a slowly gusting wind, bent and rolled by large eddies, lighting the lamps they pass (each as bright as when the music kicks in) and leaving trails that fade over about two seconds, in Worldly's amber when nothing plays. It was tuned in the Sound Wall Simulator, an interactive copy of the wall. It's lit as light in a dark, hazy room rather than a brighter picture: the lamps have warm cores (white-hot only at their brightest) with soft edges, their light blooming into the gaps between them, scattering in a haze over each lit strip and in a faint fog over the whole widget; "Now playing" and the title glow; the record is luminous coloured vinyl with warm light caught in its grooves and a soft halo of light round its edge; and the light comes from inside the sleeve: a line of light runs along the sleeve's mouth and falls on the record as it comes out, brightest nearest the sleeve, and around the sleeve there's only a soft glow. The album art itself is left almost exactly as it is, in its own colours and saturation (a dark cover's bright parts shine only a touch). The lit lamps, the record's rim and the line at the mouth have a restrained colour fringe, like a bright light through a lens; there's no grain. Any size works: the wall always runs from a full strip at one margin to a full strip at the other, with as many strips and rows as fit (the lamps stay about the same size), and the LP grows with a taller widget. A tap opens the playing app. It has no controls (nor a progress line), so it animates smoothly; the Music widget has them. Needs **notification access** (to see what's playing; nothing is read from notifications) and, to follow the real sound, **sound levels** (Android's "record audio" permission: it reads the phone's own sound through the audio visualizer, never the microphone). Without sound levels, or on phones that keep their sound to themselves, the lights move with the music on their own |
| **Music** | Just the controls for whatever is playing in any app, on the panel lit in its light: previous, play / pause (lit in the light; it doesn't repeat the song's progress, the line does), next, the time so far, the progress line and the length (the times' digits are all one width and both times as wide as the song's longest, so the line keeps its size as they tick). One row tall (it centres the controls when taller). A tap on the progress line jumps the song to that point (it's split into 24 zones, as widgets can't be dragged); a tap on the panel around them opens the playing app. It's still between changes, so taps get the home screen's full attention, and shows each tap at once. Small widgets leave out the times. Needs notification access |
| **Calendar** | Agenda layout: big day number, week row (Sunday to Saturday, today on a solid tile in the song's colour covering its day name and number), then your upcoming events with colour bar, time range and "in N minutes"; "+" adds an event. Reads the device calendar (READ_CALENDAR), no account or key. A taller widget lists more events, a wider one gives titles more room |

Live updates: world clocks and calendar run on exact per-minute alarms with no notification; only the moving SBB second hand and the Sound widget's lights use a small foreground service (SBB: "Smooth" about 10 pictures a second, "Every second", or "Minute only"; Sound: about 20 pictures a second while something plays, 12 while it plays its patterns). With the screen off it does nothing. Alarms stay as a backup. Location is always where you are (last known device location, refreshed whenever you open the app). Fonts: Carbon+ (Currency) and Carbon+ (all widgets), both from the original KWGT presets.

## Data sources (all free, no key, no account)
| Data | Source |
|---|---|
| City -> coordinates, timezone, country | Open-Meteo geocoding |
| Weather now (condition, temperature) + hourly chance of rain | Open-Meteo forecast |
| Sunrise / sunset, day/night strip | Computed on the phone (no network) |
| Market exchange rates | Wise's live mid-market rate (the feed behind wise.com's own converter), updated every minute |
| Market rate Wise doesn't quote (crypto, metals) or when it doesn't answer | fawazahmed0 currency-api (CDN + mirror), fallback open.er-api.com: a daily rate |
| Visa rate | Visa's public FX endpoint, same call as the original preset (fee=2, date MM/dd/yyyy) |
| Visa rate if Visa refuses | Estimate = market rate + your fee, shown with a `~` |
| 30-day trend behind each row | Wise, in the same request as the rate (one point a day at 00:00 UTC, plus the rate now); fawazahmed0 currency-api dated files for rows Wise doesn't quote |
| Flags | google/region-flags on GitHub, fallback flagcdn.com (downloaded once, then offline) |

Rates and weather inherently need an online source; these are the free ones that need no key.
Everything is cached, so the widget keeps showing the last values when offline.

### Why Wise for the market rate
Currencies have no single exchange: every site shows its own number because it shows a different moment
(live, or a snapshot taken once a day), a different feed, or the buy or sell price instead of the middle.
The closest thing to *the* rate is the live mid-market rate, halfway between what banks buy and sell at.
Checked over a month (Oct-Nov 2026) against the official references: at the ECB's daily 14:15 CET fixing,
Wise was within 0.03 % of the ECB rate on average (worst day 0.09 %), and its average over Bogotá's
trading session within 0.08 % of Colombia's official TRM. The daily snapshots used before were 0.2-0.3 %
off on average, up to 1.2 % on days the market moved. On weekends the market is closed and the rate
stays at Friday's close.

## Visual language: Glass: frosted smoky glass, lit edges, hazy text, the song's light
- **Frosted smoky glass over the wallpaper** (the Glass look, chosen in 1.67 from three, after the two cypher samples
  and Glow_Icon_Text; `Look`, `Stage`, `Backdrop`). Each widget's glass shows the phone's own wallpaper behind it,
  heavily blurred and dimmed (more over a bright wallpaper, so the text reads), with the song's light far behind
  the glass, out of focus (soft blurred shapes of it, like windows of light seen through frost), under a milky veil
  of smoke warmed by it, thicker towards the bottom left. Android doesn't let a widget blur what's behind it, so
  Worldly reads the wallpaper itself (with "All files access" on Android 11 and up; nothing else is read), blurs it
  once per wallpaper, and gives each panel the part behind it. A widget can't ask where it is on the screen, but each
  tap on it says where it was tapped, so its glass lines up the first time it's tapped (a tap passes through
  `TapActivity`, unseen); until then it assumes the middle of the screen. Without the wallpaper (not allowed, or a
  live wallpaper) the glass takes the wallpaper's own colours, which Android gives every app. The currency rows each
  move the song's lights about so they don't repeat, and the world clocks' flags fade down into the clocks card's
  glass. No gloss, no grain.
- **Light falls on the glass.** As on the cypher samples, bright things light the glass behind them: today's tile in
  the calendar, the Music widget's play button and the trend lines spill a wide, soft glow of their light onto it; in
  the app, solid buttons cast a soft glow of their colour on the card under them. The app's room behind its cards is near black, with two faint, slow washes
  of the light and no lighting rig (the old row of chasing lamps along the top is gone).
- **The app's cards** (settings, the pickers, the converter), after the glowing card reference: near black, with just
  a hint of the song's colour towards the right, a hairline edge that's light at the top left and fades round, and a
  faint sheen along the top. Their titles glow softly in the light.
- **Buttons and icons are solid**, after the solid button and icon references: the main action, what's on (the
  chosen SBB mode, the chosen converter chip) and the Music widget's play / pause are solid in the song's colour, a
  shade lighter to the right, with no bloom; other buttons are dark and solid; icon buttons (previous, next, the
  Calendar's "+", the converter's swap) are dark solid circles with a pale symbol; the weather bar's icons are solid,
  a little darker than the line colour. Every control has the same 12 dp corners, on or off.
- **Lit edges instead of borders.** No card has a solid outline any more: its edge is a fine line in the song's line
  colour, brightest at the top left corner where the light comes in, sweeping along both edges and fading out by the
  far corner, with a soft glow inside the card where it's brightest (`Stage.rim`), as on the glowing cards
  reference. The cards keep their rounded-square shape (28 dp).
- **Everything follows the song.** The light is the colour of what's playing (`Ambience`): the album cover's primary
  colour, or the playing app's own, or Worldly's amber when nothing plays. Nothing decorative is fixed any more: the
  line colour (edges, rules, the weather bar's icons and dividers, section titles) is the song's colour paled towards
  a warm white (for amber it's the old gold), labels ("Market", event times, a song's length; not the world clocks, which stay
  white) are soft text tinted by it, and the settings screen and the converter are drawn in it too (redrawn when the song changed while
  they were away). Only the clocks' sun bands keep their own day, golden-hour, blue-hour and night colours, and
  amber still means "needs attention" on the settings screen.
- **A colour fringe on the brightest things** (`Lens`): the Music widget's progress point, the trend lines, the lit
  edges, the Sound widget's lit lamps, its record's rim and the line of light at the sleeve's mouth have the red and
  blue edge a real lens gives a bright light (chromatic aberration), restrained. Never on text, never on the album
  art, never on solid buttons.
- **Light sources in a hazy room.** What gives light is drawn as light in a dark, hazy room, not as a brighter
  picture: bloom, a tight halo, soft edges. The Sound and Music widgets' text glows (`Glow`); on every widget the
  text is hazed by its own light in the song's colour (Haze_Bloom_Text: a wide, faint bloom, a softer glow close round
  and a tight halo, short enough that the letters stay sharp), the big numbers (the clock times, the day number) a
  little more. The world clocks' day, time and city stay white, hazed in white, whatever is playing. Today in the
  calendar sits on a solid tile in the song's colour, covering its day name and number.
- **Labels** are small bold capitals, spaced out, like markings on a piece of equipment.

The type stays the original Carbon+ font from the KWGT preset (`assets/fonts`). Colours and the glass,
well and lit looks live in `Theme.java`; the settings screen, the converter and the widgets all read
from there, so one edit restyles everything.

## How it updates
- Clocks and day/night: redrawn every minute with an exact alarm.
- Rates / weather: refreshed about every 15 minutes, the shortest period Android allows (weather at most every
  10 minutes). Visa publishes once a day, so it's asked at most every 30 minutes (Save & refresh always asks).
- Market rates and their 30-day trend: one request per currency row (Wise), with every refresh and each time the
  converter opens (at most once a minute). Rows Wise doesn't quote fill their trend from dated files once
  (about 30 small requests), then one new day a day, after the rates so they never wait for it.

## Code map (`app/src/main/java/app/currencyexchange/widget/`)
`Renderer` draws everything onto one bitmap; `Trend` draws the rate chart behind each currency row; `Fetcher` does all network calls; `Store` holds settings
and cache; `SunCalc` offline sun times; `WidgetProvider` + `RefreshJobService` handle updates;
`MainActivity` is the settings screen; `Util`, `Net`, `Flags` are helpers.
Sound widget: `NowPlaying` reads the playing media session (through `MediaListener`, the notification listener),
`SoundWave` gives the sound's levels (audio visualizer, or made-up levels that move like music), `SoundLights` is the
wall of lamps (`Airstream` its motion when nothing plays), `SoundRecord` the LP, `SoundPicture` lays out one widget's picture, `Scene` and `Colours` give its
light, and `SoundWidgetProvider` + `LiveService` send the frames. Music widget: `PlayerPicture` and
`PlayerWidgetProvider` (controls drawn by `SoundControls`), updated by `MediaListener`. `Words` writes what's playing
for both; `Stage` is the panel every widget sits on (its black, its pool of light and its lit edge), `Look` how strong
each effect of the Glass look is, `Ambience` the light they share, `Glow` how text and lit things glow in it, and `Lens` the colour fringe on the
brightest things.
Layout units are the 750-wide grid of the original preset.

Layout note: the Calendar and Currency widgets are native views with weighted rows/columns (fonts bundled in res/font), so they follow the widget's real width and height; only flags, the Visa logo and clock backgrounds are small bitmaps.
