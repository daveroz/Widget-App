# The app's city list: which places stay (2.31)

The list was 479 places: the world's big cities and every place the time-zone database is named after, down to
hamlets, weather stations and uninhabited islands. Now 419, by one rule built from three standard references:

- [UN Degree of Urbanisation](https://unstats.un.org/unsd/statcom/51st-session/documents/BG-Item3j-Recommendation-E.pdf)
  (endorsed by the UN Statistical Commission in 2020): a **city** has 50,000 people or more, a **town** 5,000 or more;
- [GeoNames](https://android.googlesource.com/platform/external/geonames/+/7a853d30915d91f323de5649b130afbd00e609de/export/dump/readme.txt)'
  standard world list: places of **15,000** or more, **plus capitals of any size**;
- [Natural Earth](https://www.naturalearthdata.com/downloads/50m-cultural-vectors/50m-populated-places/): capitals are
  kept for their significance, not their census.

The rule:

1. every **national capital** stays, whatever its size;
2. a **territory's capital** stays if it is a town (5,000 or more);
3. any place of **15,000** or more stays: main cities (1M+), cities (50,000+) and small cities (15,000+);
4. everything else goes (hamlets, stations, uninhabited islands, the small towns some time zones are named after);
5. a world clock's own exception: a UTC offset no kept place has keeps its biggest place (Chatham, +12:45; Eucla,
   +8:45; Kiritimati, +14; the Marquesas, -9:30), so no time is lost.

Populations: [Natural Earth's populated places](https://github.com/nvkelso/natural-earth-vector) (metropolitan
population, `pop_max`; public domain) for 428 places matched by name and country or within 25 km; Overture Maps'
divisions (localities' population) for the other 51, with a few known figures where a neighbouring town was matched.
`decisions.tsv` lists every place, its population, and why it stays or goes. A city a user already picked keeps working
(its place is stored with it), and any place can still be found by the picker's online search.

Run: `python3 match.py && python3 reduce.py` (it rewrites app/src/main/assets/cities.txt from the 479-place list).
