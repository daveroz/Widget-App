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
(cities, market rate, Visa, weather).

## Widgets (5)

| Widget | What it is |
|---|---|
| **Currency Exchange** | World clocks and market + Visa rates (the original KWGT widget). Each world clock sits on its city's sky right now (1.98, `Sky`): its colours follow the sun's height there (the blue of the day, golden hour, the purple and orange of sunset, the blue hour, the night), with clouds as many as the city's cloud cover lit by the same light, stars on a clear night, and a veil for overcast, rain, snow or fog. Each city's cloud cover and weather come from Open-Meteo for the four cities at once, about every 15 minutes (free, no key). The day, time and city in front stay white. Below, the currency rows share one card, thin lines between them: each row has its flags, its codes, the market and Visa rates and the last month as a thread of light across it; a tap opens the converter. No weather bar and no flags behind the clocks any more |
| **SBB Clock** | Swiss railway station clock. Second hand: one lap in 58.5 s, then waits at 12; on the minute impulse the minute hand (and the hour hand with it) moves one minute with SBB's elastic swing, exactly when the second hand starts its next lap. Geometry and motion follow SBB's own digital clock (sbbUhr 1.3, dark-background face without frame) |
| **Sound** | The Deco launcher's sound light show on the home screen. A wall of square lamps from margin to margin, in strips three wide, lit in one colour: the album cover's primary colour for music, the app's own colour for videos and apps without a cover. On top: "Now playing · app", the title and the artist on the left and, for music, the cover as a vinyl LP on the right, the record sliding out and spinning at 33⅓ rpm, slowing to a stop on pause. Below, the wall of lamps across the whole widget, its top a little behind the LP: while something plays the lamp columns follow its sound, bass on the left and treble on the right, filling up from the bottom; paused or nothing playing, an airstream with turbulence crosses the wall: two streams of air flowing in from the left on a slowly gusting wind, bent and rolled by large eddies, lighting the lamps they pass (each as bright as when the music kicks in) and leaving trails that fade over about two seconds, in Worldly's own orange when nothing plays. It was tuned in the Sound Wall Simulator, an interactive copy of the wall. It's lit as light in a dark, hazy room rather than a brighter picture: the lamps have warm cores (white-hot only at their brightest) with soft edges, their light blooming into the gaps between them, scattering in a haze over each lit strip and in a faint fog over the whole widget; "Now playing" and the title have the lens's colour fringe (no text glows); the record is luminous coloured vinyl with warm light caught in its grooves and a soft halo of light round its edge; and the light comes from inside the sleeve: a line of light runs along the sleeve's mouth and falls on the record as it comes out, brightest nearest the sleeve, and around the sleeve there's only a soft glow. The album art itself is left almost exactly as it is, in its own colours and saturation (a dark cover's bright parts shine only a touch). The lit lamps, the record's rim and the line at the mouth have a restrained colour fringe, like a bright light through a lens; there's no grain. Any size works: the wall always runs from a full strip at one margin to a full strip at the other, with as many strips and rows as fit (the lamps stay about the same size), and the LP grows with a taller widget. A tap opens the playing app. It has no controls (nor a progress line), so it animates smoothly; the Music widget has them. Needs **notification access** (to see what's playing; nothing is read from notifications) and, to follow the real sound, **sound levels** (Android's "record audio" permission: it reads the phone's own sound through the audio visualizer, never the microphone). Without sound levels, or on phones that keep their sound to themselves, the lights move with the music on their own |
| **Music** | Just the controls for whatever is playing in any app, on the panel lit in its light: previous, play / pause (lit in the light; it doesn't repeat the song's progress, the line does), next, the time so far, the progress line and the length (the times' digits are all one width and both times as wide as the song's longest, so the line keeps its size as they tick). One row tall (it centres the controls when taller). A tap on the progress line jumps the song to that point (it's split into 24 zones, as widgets can't be dragged); a tap on the panel around them opens the playing app. It's still between changes, so taps get the home screen's full attention, and shows each tap at once. Small widgets leave out the times. Needs notification access |
| **Calendar** | Agenda layout: big day number (spanning from the top of the month's capitals to the bottom of the line under it), week row (Sunday to Saturday, a little below the header; today on a solid tile in the song's colour, 40 dp wide whatever the widget's size, covering its day name and number), then your upcoming events on one card, thin lines between them (1.98): the day (weekday over number, in fixed colours, today's brighter) beside each day's first event, then the event's colour bar, its title, and its time with "in N minutes"; when nothing is on today, a line saying so comes first. The card shows what fits (no "+N more"). "+" adds an event. Reads the device calendar (READ_CALENDAR), no account or key. A taller widget lists more events, a wider one gives titles more room |

Live updates: world clocks and calendar run on exact per-minute alarms with no notification; only the moving SBB second hand and the Sound widget's lights use a small foreground service (SBB: "Smooth" about 10 pictures a second, "Every second", or "Minute only"; Sound: about 20 pictures a second while something plays, 12 while it plays its patterns). The two animations each run on a thread of their own at the priority Android gives drawing the screen, apart from the heavier once-a-minute work, and their frames keep a steady timeline (a late frame is dropped instead of pushing the rest back), since 1.93. With the screen off it does nothing. Alarms stay as a backup. Location is always where you are (last known device location, refreshed whenever you open the app). Font: Futura 100 everywhere (Text Medium for text and numbers, the world clocks' times included; Bold for titles), since 1.85.

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

## Visual language: see-through cards, hazy text, the song's light
- **Cards, the widgets and the app's alike** (`Stage`), after the glowing card reference: near black, with just a
  hint of the song's colour towards the right and a faint glow of it there, a faint sheen along the top, and a
  hairline edge that's light at the top left and fades round (with the glass reflections below). On the widgets the black is a little see-through (84% opaque),
  so the home screen shows faintly through it; the app's cards (settings, the pickers, the converter) are nearly
  opaque, and their titles have the lens's colour fringe. The world clocks' flags fade down into their card. No grain. The
  app's room behind its cards is near black, with two faint, slow washes of the light and no lighting rig (the old
  row of chasing lamps along the top is gone).
- **Light falls on the cards.** As on the cypher samples, bright things light the card behind them: today's tile in
  the calendar, the Music widget's play button and the trend lines spill a wide, soft glow of their light onto it.
- **Buttons are solid, with a glass rim**, after the Justice store's buttons (without their glow), in the app and on
  the widgets alike: the main action and what's on (the Music widget's play / pause, the chosen SBB mode, the
  converter's chosen chip, the FX badge) are a deep shade of the song's colour (the light's main colour, the middle of its three, with near-black text on a bright colour) with white
  text or symbol; other buttons (the Music widget's previous and next, the Calendar's "+", the converter's swap and
  the app's other buttons) are near black, their symbols pale. No glow under any of them. The weather bar's icons are
  solid, a little darker than the line colour. Their edge is a
  glass rim (`Theme.glassEdge`), not a flat border: light caught by the glass's edge, brightest at the top left,
  fading almost to nothing across the top middle and the bottom left, and coming back, softer, at the bottom right
  (measured off the store's "Shop all" button; fuller on the lit buttons). Every control has the same 12 dp corners,
  on or off.
- **The settings screen's header is a bar of frosted glass** (`GlassBar`), after the Justice store's header: it floats
  at the top while the cards scroll under it, and they show through it heavily blurred, under a translucent dark grey
  with a faint sheen and the same glass rim as the buttons, brighter all round. What's behind is drawn small, blurred and stretched back
  up, again after every scroll and now and then for the room's drifting light. Only in the app; the widgets can't see
  what's behind them.
- **Hairline edges instead of borders.** No card has a solid outline: its edge is a hairline in a fixed warm white
  (`Stage.EDGE`, the same whatever is playing), light at the top left and fading round the card (`Stage.rim`). The cards keep their
  rounded-square shape (28 dp on the widgets).
- **Everything follows the song.** The light is the colour of what's playing (`Ambience`): the album cover's primary
  colour, or the playing app's own, or Worldly's own orange when nothing plays (since 1.89: the glowing filaments of the
  reference picture, measured off its pixels: #F37613 lit, a deeper red-orange in the haze, yellow at the hottest
  cores; `Scene.idle`). Every other colour gets its three by the same rule, fitted to that picture (1.91,
  `Scene(int)`): the hot core is the colour overexposed as a camera sees a bright light (each channel times 1.6,
  clipped, so it shifts toward its lighter neighbour: orange to yellow, teal to cyan, violet to lavender) with a third
  of white; the deep haze is the colour with more contrast and a little darker. Text in the light (card titles,
  "Now playing", the calendar's labels) and lit buttons are in the main colour, the middle of the three. Nothing decorative is fixed any more: the
  line colour (edges, rules, the weather bar's icons and dividers, section titles) is the song's colour paled towards
  a warm white, labels ("Market", event times, a song's length; not the world clocks, which stay
  white) are soft text tinted by it, and the settings screen and the converter are drawn in it too (redrawn when the song changed while
  they were away). Only the clocks' sun bands keep their own day, golden-hour, blue-hour and night colours, and
  amber still means "needs attention" on the settings screen.
- **No colour fringe** (since 1.83, picked in the light lab). The code for it stays (`Lens`, set in `Look`): the
  red and blue edge a real lens gives a bright light (chromatic aberration) on the progress point, the trend lines,
  the lit lamps, the record's rim and the line at the sleeve's mouth, straight lines getting only a trace of it.
- **Light sources in a hazy room.** What gives light (the lamps, the record, the trend lines, the SBB clock's face,
  the world clocks' sun bands) is drawn as light in a dark, hazy room, not as a brighter picture: bloom, a tight
  halo, soft edges. The sun bands (day, golden hour, blue hour, night) have a brighter core, their light rising off
  them into the glass, and a faint red-orange halation above it.
- **Text has no effects**: no glow and no fringe, on the widgets or in the app; it's just the Futura letters in
  their colour. The world clocks' day, time and city stay white whatever is playing. Today in the calendar sits
  on a solid tile in the song's colour, covering its day name and number.
- **What a camera does to light, kept subtle** (`Optics`), after the glass-on-black references and the filters that
  make those looks on a real camera:
  - *Light strikes*: a cross-screen ("star") filter turns a point of light into thin spikes; here four, the
    horizontal pair a little longer and fading to blue, like a 2-point star or anamorphic streak. Only on true points
    of light. Since 1.84 only where a card's edge catches the light, at its top left corner (the widgets' cards, the
    app's cards and its glass header), never on anything inside a card. Fairly strong (flares 1.9 in `Look`), in the
    edge's fixed warm white, so it never changes colour with the song.
  - *Glass reflections*: glass on black reads by what it reflects (white-line lighting; glass reflects most at grazing
    angles). Every card has a faint band of the room's light slanting across its top right, a second, fainter edge
    a little inside near the top left (the glass's other face, seen through its thickness), and its top left corner
    catching the light, with a small strike where it's brightest.
  - *Haze, bloom and halation*: each strike has a soft, pearly core, the glow a Black Pro-Mist filter scatters round
    a highlight, and a faint red-orange ring just outside it, the halation film gets round bright points. Since 1.83
    the glow round lit things is a little stronger (bloom 1.4) and every lit thing (the lamps, the record's rim, the
    progress line and point, the trend lines, the SBB face, the sun bands) has a faint halation (1.2).
  - *Fringe*: none (see above).
- **Labels** are small bold capitals, spaced out, like markings on a piece of equipment.

The type is Futura 100 (`assets/fonts`): Text Medium, the cut made for small sizes, for text and numbers (the world
clocks' times too, in the calendar's weight), and Bold for titles. The app's name (WORLDLY) and the calendar's month
are in capitals. It replaced Carbon+ in 1.85; every size was tuned for Carbon+, so text is
drawn at Carbon+'s capital height (`Theme.FONT_SCALE`, and `TextBmp` lays text out by its capitals). Colours and the glass,
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
for both; `Stage` the card every widget and the app sits on, `Look` how strong each
effect is, `Ambience` the light they share, `Glow` the colours its text is drawn in, `Lens` the colour fringe on the
brightest things, `Optics` the light strikes, glass reflections and halation, and `GlassBar` the settings screen's
frosted header.
Layout units are the 750-wide grid of the original preset.

Layout note: the Calendar and Currency widgets are native views with weighted rows/columns (text drawn as small pictures in the app's font), so they follow the widget's real width and height; only flags, the Visa logo and clock backgrounds are small bitmaps.
