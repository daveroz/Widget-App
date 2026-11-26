# Worldly (standalone Android app: Currency, SBB Clock and Calendar widgets)

A native replacement for the KWGT preset "Currency Exchange". No KWGT, no Sun Surveyor, no API
keys and no accounts. Install the APK, add the widget, done.

## Icon

Since 2.05 a crystal globe, a meridian and the equator cut into it, on deep space: transparent bevelled crystal, an icy glint with an anamorphic streak at its top left and a warm one with halation at its bottom right, haze and film grain (an adaptive icon with a themed monochrome layer). `tools/make_icon.py` draws it.

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
2. Open **Worldly**: pick your cities under **Clocks** and your currencies and fee under **Rates**, then tap
   **Refresh** (the pill over the tab bar).
3. Under **Status**, allow location for the weather (and whatever else the widgets ask for).
4. Under **Widgets**, tap one to add it to your home screen.
The **Status** tab shows what worked and what failed (cities, market rate, Visa, weather, clock updates).

### The app (since 2.20)
Four tabs (five in 2.20; Home went in 2.22), after the segmented control, tab bar and car app references, in the dark:
- **Tab bar**: frosted glass across the bottom (the pages blur through it, `GlassBar` docked), each tab a line icon
  over its name (`Glyph`); the open tab's icon filled and white, the others slate. A light **Refresh** pill floats
  just above it, as the reference's Map button; it shows what it's doing while it works.
- **Pages** open as the car app's screen: the crystal globe in a glass ring, the page's name in big white letters and
  a grey line under it. **Clocks** (the first): the four cities as tiles with their day, UTC offset and time, and the
  skies' motion. **Rates**: your currencies as lines with their flags and rates, how many rows the
  widget shows, what they convert to, your bank's fee (kept as you type). **Widgets**: a tile for each widget (tap to
  add it), and the SBB clock's motion. **Status**: access (each with an Allow pill until it's allowed) and live data.
- **Capitals where they label or act** (2.23): page titles, the small headings over each group, buttons (REFRESH,
  ALLOW, the segmented choices), the tab names and the widget tiles' names are spaced capitals, as the
  widgets' labels; what you read (descriptions, list lines, notes) stays in sentence case. The clocks keep their day
  and city as written ("Wednesday", "Mexico City"), on the widget and on the clock tiles (2.24).
- **The converter** (tap a currency row; since 2.23 in the app's design): the crystal globe and CONVERTER with the
  rate under it, the currencies as a segmented control with their flags, the amount in a well lit while typing (since
  2.26 with a comma between each group of three digits as you type: "1,250,000"; since 2.27 without closing the
  converter as the first comma goes in, and a typed comma counts as the decimal point), the
  swap as a glass icon disc, and MARKET and Visa as lines of a list card, their figures in polished silver.
- **Choices are segmented controls** (`Segmented`): a recessed track, the options side by side with their icons, the
  chosen one white on a raised pill that slides across to whatever you pick.

## Widgets (6)

| Widget | What it is |
|---|---|
| **World clocks** | Since 2.26 a widget of its own (until then the top of the Currency widget, the original KWGT widget, now the Rates widget below): one, two, three or four clocks in a row, or six or eight in two even rows, so every clock keeps its shape (set in the app's Clocks tab, which also picks each city; the clocks at the card's corners carry its round corners). What follows describes both, as they were one widget until 2.26. Each world clock sits on its city's sky right now (1.98, `Sky`): its colours follow the sun's height there (the blue of the day, golden hour, the purple and orange of sunset, the blue hour, the night), with clouds as many as the city's cloud cover lit by the same light, stars on a clear night, and a veil for overcast, rain, snow or fog. Each city's cloud cover and weather come from Open-Meteo for the four cities at once, about every 15 minutes (free, no key). The day, time and city in front stay white. Below, the currency rows share one card, thin lines between them: each row has its flags, its codes, and the market and Visa rates; a tap opens the converter. Since 2.00 each clock's sky is one picture at its cell's real width (no seam), the rows line up (every row's arrow and second flag in the same place, the lines between rows running from the flags' left edge to where "Market" and the Visa badge end, both drawn to that same right edge). Since 2.01 the cards are 12 dp apart (16 dp since 2.21) and the rows have no trend lines; since 2.21 the arrow between each row's currencies has the same very faint haze round it as the Music widget's progress line; since 2.02 each sky fills its clock to the card's edge (and since 2.07 each clock's day, time and city sit centred in it, over the corner's landmark if need be; since 2.19 all three a little larger); since 2.04 the rows keep the same 24 dp from the card's edge on both sides (the flags on the left; "Market", the Visa badge and the lines between rows on the right). Since 2.05 the rows follow the mockup's grid: the lines between them run nearly edge to edge (10 dp in), each row has a short upright line between its rates and their labels, "Market" (since 2.09 at the size of the widget's other labels) is centred over a monochrome Visa badge (since 2.25 Visa's own wordmark, white on its dark grey tile), and hairlines part the four clocks. Since 2.15 each clock is square (on Android 12 and up the clocks card is as tall as a clock is wide) and painted as an illustration, after the references: the sky in the hour's colours, different for the morning and the evening (dawn's blue, lavender and pink, sunrise's peach and gold, the midday blue, sunset's orange and red, dusk's indigo, purple and magenta, the blue hour, the deep blue night); crisp stylised clouds lit by the same light (glowing undersides when the sun is low, white heaps at midday), drawn one by one so each keeps its lit edge; a big soft sun (and the moon as big) where it really is (`SunCalc.sun`); heavy cloud with the glow between, long streaks and mist in the rain; since 2.16 the blue hour has its own even, rich blue and steps of its own between dusk and night (and night and dawn), so the colours glide; since 2.17 the day glides too: a low sun's long bands of cloud fade into midday's heaps as the sun climbs from 6 to 14 degrees (and back in the afternoon), and the morning's colours hand over to the evening's by the sun's real direction, half and half at noon, so even a short winter day has no step; rain changes with the hour (a warm glow between the clouds at dawn and dusk, grey by day, dark navy at night, its streaks faintly lit by the city at night); and snow has its own skies (pale grey by day, mauve and rose at dawn and dusk, a soft blue at night), gentle clouds, flakes drifting at two depths and snow on the roofs; and the landmark in the middle, a hazy far city across the whole width behind it and the near streets, darkest, with their grids of lit windows and a tall tower to either side, in front of its foot. Each clock shows its city's corner under its sky (`CityScene`, zoomed in since 2.09, a little less since 2.12, so the details and the motion show): a landmark drawn in code so the live sky lights it (some thirty cities have their own: the Ángel in Mexico City, the Athenaeum in Bucharest, the Atomium in Brussels, Charlotte's skyline, Monserrate in Bogotá, the Eiffel Tower, Big Ben, the Empire State, ...; any other city gets a skyline made from its name, with palms, round trees or firs for its latitude), lit windows and floodlit monuments at night. Since 2.06 the scenes are more lifelike: the rest of the city stands far behind each landmark, low and hazy; buildings catch the light on their sun side and along their roofs, darker towards the ground, with a fine grain; by day their glass catches the sky, at night their windows are mostly warm, some cool white, some amber; the horizon's haze lies over their feet by day and the streets glow warm at night; the low sun shows near the horizon in the morning and evening; since 2.08 the moon is where it really is (`SunCalc.moon`, after Meeus): only while it's above each city's horizon, east on the left and west on the right, as high as it stands there, lit as much as it is with its bright side towards the sun and the dark part faintly showing, pale when it's up by day; round trees have lit and shaded leaves, palms feathery fronds. The skies move (`SkyCell`, about eight frames a second while the screen is on): the stars twinkle, the clouds drift, the trees sway with the city's wind (Open-Meteo), a far plane crosses now and then leaving a contrail in the light of the hour, the beacons blink, the rain falls, and the Eiffel Tower sparkles for five minutes every hour after dark. "World clock skies" in the settings switches between Animated and Still (one picture a minute); animated skies need the live service's notification, as the Sound widget does. No weather bar and no flags behind the clocks any more. Since 2.23 "Market" is in the app's neutral grey and the lines between rows are as faint as the app's list hairlines. Since 2.28 each city's streets are built as its continent's (by its time zone): European town houses with gables, mansards, stepped gables, chimneys and a church spire, never towers; towers in the big cities of the Americas, Asia, Oceania and Africa; low houses with pitched roofs and gardens in smaller cities there. After the reference video (its shapes, not its colours, sun or moon): towers have a pale line along every floor, a darker line down the middle and lit windows scattered at random rather than a grid, and a darker side face; far hills line the horizon and fill with thousands of tiny lights after dark; street lamps glow along the pavement; round trees are cut-paper crowns in two tones; palms are taller and thinner with serrated, drooping fronds; the buildings are flat and clean, with no grain. A flock of birds crosses now and then, and more rarely a helicopter; stars stay behind the moon. Fireworks fill the night sky on New Year's Eve and Day everywhere, on the Lunar New Year where it's kept, and on each country's independence or national day; lightning strikes in a thunderstorm; and all December strings of Christmas lights twinkle across the street |
| **Rates** | The currency rows on one card (since 2.26 a widget of its own; they were under the clocks in the Currency widget): each row its flags, codes, market and Visa rates, a tap opens the converter; as many rows as fit its height and are set up (one to six, in the app's Rates tab). Since 2.28 the arrow between each row's currencies is white, as its codes, with no colour or haze |
| **SBB Clock** | Swiss railway station clock. Second hand: one lap in 58.5 s, then waits at 12; on the minute impulse the minute hand (and the hour hand with it) moves one minute with SBB's elastic swing, exactly when the second hand starts its next lap. Geometry and motion follow SBB's own digital clock (sbbUhr 1.3, dark-background face without frame) |
| **Sound** | The Deco launcher's sound light show on the home screen. A wall of square lamps from margin to margin, in strips three wide, lit in one colour: the album cover's primary colour for music, the app's own colour for videos and apps without a cover. On top: "Now playing · app", the title and the artist on the left (since 2.09 in the Calendar's weight; a little larger again in 2.13 and 2.14) and, for music, the cover as a vinyl LP on the right, the record sliding out and spinning at 33⅓ rpm, slowing to a stop on pause. Below, the wall of lamps across the whole widget, its top a little behind the LP: while something plays the lamp columns follow its sound, bass on the left and treble on the right, filling up from the bottom; paused or nothing playing, an airstream with turbulence crosses the wall: two streams of air flowing in from the left on a slowly gusting wind, bent and rolled by large eddies, lighting the lamps they pass (each as bright as when the music kicks in) and leaving trails that fade over about two seconds, in Worldly's own frosted blue when nothing plays (orange until 2.21). It was tuned in the Sound Wall Simulator, an interactive copy of the wall. It's lit as light in a dark, hazy room rather than a brighter picture: the lamps have warm cores (white-hot only at their brightest) with soft edges, their light blooming into the gaps between them, scattering in a haze over each lit strip and in a faint fog over the whole widget; "Now playing" and the title have the lens's colour fringe (no text glows); the record is luminous coloured vinyl with warm light caught in its grooves and a soft halo of light round its edge; and the light comes from inside the sleeve: a line of light runs along the sleeve's mouth and falls on the record as it comes out, brightest nearest the sleeve, and around the sleeve there's only a soft glow. The album art itself is left almost exactly as it is, in its own colours and saturation (a dark cover's bright parts shine only a touch). The lit lamps, the record's rim and the line at the mouth have a restrained colour fringe, like a bright light through a lens; there's no grain. Any size works: the wall always runs from a full strip at one margin to a full strip at the other, with as many strips and rows as fit (the lamps stay about the same size), and the LP grows with a taller widget. A tap opens the playing app. It has no controls (nor a progress line), so it animates smoothly; the Music widget has them. Needs **notification access** (to see what's playing; nothing is read from notifications) and, to follow the real sound, **sound levels** (Android's "record audio" permission: it reads the phone's own sound through the audio visualizer, never the microphone). Without sound levels, or on phones that keep their sound to themselves, the lights move with the music on their own. Since 2.23 its words sit at the top, on the same line as the other widgets' (the label's capitals 19.6 dp under the card's top, as the Calendar's month), the label in the app's neutral grey and the title white. Since 2.26 the label's capitals and the artist are as tall as the Calendar's "Today, Wednesday", and the title a bit taller. Since 2.28 there's no "Now playing" label: just the title and the artist, and a title too long for its room scrolls across, as a media player's does |
| **Music** | Just the controls for whatever is playing in any app, on the panel lit in its light: previous, play / pause (lit in the light; it doesn't repeat the song's progress, the line does), next, the time so far, the progress line and the length (the times' digits are all one width and both times as wide as the song's longest, so the line keeps its size as they tick). One row tall (it centres the controls when taller). A tap on the progress line jumps the song to that point (it's split into 24 zones, as widgets can't be dragged); a tap on the panel around them opens the playing app. It's still between changes, so taps get the home screen's full attention, and shows each tap at once. Small widgets leave out the times. Needs notification access. Since 2.23 its controls sit at the top, 19 dp down, on the same line as the other widgets' words; previous and next are the app's line icons in white, play and pause filled; with nothing playing it shows a line as the app's Status tab: the note in a glass disc, NOTHING PLAYING in white capitals and what to do in grey; the song's length is in the neutral grey. Since 2.26 the controls (and the empty state) sit in its middle again, the same room above and below |
| **Calendar** | Agenda layout: big day number (spanning from the top of the month's capitals to the bottom of the line under it), week row (Sunday to Saturday in fixed 40 dp columns spread edge to edge, today on a solid tile in the song's colour covering its day name and number; since 2.21 with the progress line's very faint haze hugging its edge), all on one grid since 1.99: the "+" top-aligned with the day number and the month, the "+", Saturday's tile and the events card ending on the same right edge, and equal 10 dp gaps from the "+" to the week row and from the week row to the card, then your upcoming events on one card, thin lines between them (1.98): the day (weekday over number, in fixed colours, today's brighter) beside each day's first event, then the event's colour bar, its title, and its time with "in N minutes"; when nothing is on today, a line saying so comes first. The card shows what fits (no "+N more"). "+" adds an event. Reads the device calendar (READ_CALENDAR), no account or key. The events card fills down to a 20 dp margin, the same as its sides, whatever the widget's height (2.00). A taller widget lists more events, a wider one gives titles more room. Since 2.23 the big day number is in polished silver (as the app's big figures), the labels ("Today, Wednesday · Week 48", the day names, the times) are the app's neutral grey, the "+" is the app's glass icon disc with a line "+" in white, and the lines between events are as faint as the app's hairlines. Since 2.26 today's tile has no colour of its own and no glow: a raised pill of grey glass, as the app's segmented control, its day name and number in white |

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
| 30-day rate history per row (kept, not shown since 2.01) | Wise, in the same request as the rate (one point a day at 00:00 UTC, plus the rate now); fawazahmed0 currency-api dated files for rows Wise doesn't quote |
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
  opaque, and their titles have the lens's colour fringe. The world clocks' flags fade down into their card. Since 2.05 the glass has a fine frost in it (`Stage.grain`), after the crystal logo. The
  app's room behind its cards is black, all over (since 2.21; before, a near black with two faint, slow washes of the
  light and the crystal logo's deep space; the old row of chasing lamps along the top went long ago).
- **Light falls on the cards.** As on the cypher samples, bright things light the card behind them: the Music widget's
  play button spills a wide, soft glow of its light onto it. The Calendar's today tile sits flat, with no glow (2.03).
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
- **The settings screen's header was a bar of frosted glass** until 2.20 (now the tab bar is; see "The app"), after the Justice store's header: it floated
  at the top while the cards scroll under it, and they show through it heavily blurred, under a translucent dark grey
  with a faint sheen and the same glass rim as the buttons, brighter all round. Since 2.18 it really blurs what scrolls under it on every phone: on Android 12 and up the cards' frame is drawn again through the system's blur (`BackdropFrame`, `RenderEffect`); before that a small blurred picture of it is taken, and if that can't be done the glass is drawn nearly solid rather than showing the cards sharp through it. What's behind is drawn small, blurred and stretched back
  up, again after every scroll and now and then for the room's drifting light. Only in the app; the widgets can't see
  what's behind them.
- **Hairline edges instead of borders.** No card has a solid outline: its edge is a hairline in a fixed warm white
  (`Stage.EDGE`, the same whatever is playing), light at the top left and fading round the card (`Stage.rim`). Since 2.02
  it fades to almost nothing on the far side, as clear glass shows its edge only where it catches the light: there's
  never a line in the song's colour there, only the card itself. The world clocks' skies run to their card's very edge,
  and the Calendar's events card's ring fades into its own dark the same way (in solid colours, so nothing shows through).
  Since 2.05 the edge is lit as a pane of crystal lit from two sides (after the crystal logo): icy white round the top
  left corner (`Stage.ICE`), shorter and warmer round the bottom right (`Stage.WARM`), almost nothing along the straight
  sides, and a fainter bevel line inside near each lit corner; all in fixed colours. Since 2.06 the whole look follows
  the crystal logo: the cards are the deep navy of its space, with an icy sheen in their top left, a warm glow inside
  their bottom right and the bevel's polished slope along the lit edges; (until 2.11 a long, faint blue streak ran along the top
  from the top left glint; now its horizontal ray is as short as its vertical one); the buttons and input fields are crystal (lighter towards the light, a rim icy at the top
  left warming to the bottom right, a fainter bevel line inside); hairlines are icy; and the app's room is deep space,
  with a navy haze, out-of-focus specks and film grain under the song's washes. The app's header shows the icon.
  The cards keep their rounded-square shape (28 dp on the widgets).
- **Everything follows the song.** The light is the colour of what's playing (`Ambience`): the album cover's primary
  colour, or the playing app's own, or Worldly's own light when nothing plays: since 2.21 the frosted ice of the
  crystal logo and the glass, a cool pale blue (#8FCBFF lit, a deeper cobalt in the haze, icy white at the hottest
  cores; `Scene.idle`); from 1.89 to 2.20 it was the glowing filaments' orange (#F37613). Every other colour gets its three by the same rule, fitted to that picture (1.91,
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
  red and blue edge a real lens gives a bright light (chromatic aberration) on the progress point,
  the lit lamps, the record's rim and the line at the sleeve's mouth, straight lines getting only a trace of it.
- **Light sources in a hazy room.** What gives light (the lamps, the record, the SBB clock's face,
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
    of light. Since 1.84 only where a card's edge catches the light (the widgets' cards, the app's cards and its glass
    header), never on anything inside a card. Fairly strong (flares 1.9 in `Look`), in fixed colours, so it never
    changes colour with the song. Since 2.05 there are two, as on the crystal logo and the mockup: an icy one at the top
    left corner and a warm one at the bottom right, their two rays pointing into the card running on along its edges. Since 2.18 it follows the crystal video: a small, intense core drawn out along the rim of the top left bend, a little nearer the top, a small icy halo, a short streak along the rim and faint short rays, the rim bright and sharp round it and splitting like a prism (cyan outside, amber inside); the rim is a fine line all round with a faint bevel line inside it, and the glass is neutral and dark (the song's tint much fainter). Since 2.10 only the top left has one, and since 2.15 nothing glows at the bottom right at all (only the faint edge all round); before that the bottom right corner kept just its edge catching the light, in the same icy white (and the buttons' and header's rims, and the cards' inner glow, are that one colour too). Since 2.09 the flare is angled as in the mockup (its diagonal rays a touch smaller since 2.12 and 2.13, still its biggest): the long pair runs along the corner's tangent (as far as the card's picture allows: a widget can't draw past its own edges), a shorter pair crosses it, reaching into the card, and only faint rays run square to the edges.
  - *Glass reflections*: glass on black reads by what it reflects (white-line lighting; glass reflects most at grazing
    angles). Every card has a faint band of the room's light slanting across its top right, a second, fainter edge
    a little inside near the top left (the glass's other face, seen through its thickness), and its top left corner
    catching the light, with a small strike where it's brightest.
  - *Haze, bloom and halation*: each strike has a soft, pearly core, the glow a Black Pro-Mist filter scatters round
    a highlight, and a faint red-orange ring just outside it, the halation film gets round bright points. Since 1.83
    the glow round lit things is a little stronger (bloom 1.4) and every lit thing (the lamps, the record's rim, the
    progress line and point, the SBB face, the sun bands) has a faint halation (1.2).
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
- Market rates and their 30-day history: one request per currency row (Wise), with every refresh and each time the
  converter opens (at most once a minute). Rows Wise doesn't quote fill their history from dated files once
  (about 30 small requests), then one new day a day, after the rates so they never wait for it.

## Code map (`app/src/main/java/app/currencyexchange/widget/`)
`Renderer` draws everything onto one bitmap; `Trend` sets the rate history's window (no longer drawn since 2.01); `Fetcher` does all network calls; `Store` holds settings
and cache; `SunCalc` offline sun times; `WidgetProvider` + `RefreshJobService` handle updates;
`MainActivity` is the app (its four tabs; `Segmented` its choices, `Glyph` its icons); `Util`, `Net`, `Flags` are helpers.
Sound widget: `NowPlaying` reads the playing media session (through `MediaListener`, the notification listener),
`SoundWave` gives the sound's levels (audio visualizer, or made-up levels that move like music), `SoundLights` is the
wall of lamps (`Airstream` its motion when nothing plays), `SoundRecord` the LP, `SoundPicture` lays out one widget's picture, `Scene` and `Colours` give its
light, and `SoundWidgetProvider` + `LiveService` send the frames. Music widget: `PlayerPicture` and
`PlayerWidgetProvider` (controls drawn by `SoundControls`), updated by `MediaListener`. `Words` writes what's playing
for both; `Stage` the card every widget and the app sits on, `Look` how strong each
effect is, `Ambience` the light they share, `Glow` the colours its text is drawn in, `Lens` the colour fringe on the
brightest things, `Optics` the light strikes, glass reflections and halation, and `GlassBar` the app's
frosted tab bar.
Layout units are the 750-wide grid of the original preset.

Layout note: the Calendar and Currency widgets are native views with weighted rows/columns (text drawn as small pictures in the app's font), so they follow the widget's real width and height; only flags, the Visa logo and clock backgrounds are small bitmaps.
