# City scenes from the real world (prototype)

Bakes each world clock's city from open map data into a small, flat scene in the reference video's style: real
buildings, water, parks and main roads, the city's landmarks from the CC0 landmarks collection at their real places, and
the real hills on the horizon. The camera is fixed per city, so the scene is baked to 2D (faces projected, sorted,
hidden ones removed); the app only has to colour and fill polygons, by its palette rule, for any light.

## Sources

| What | Source | Licence | Used for |
|---|---|---|---|
| Buildings (footprints, heights, floors), water incl. the ocean, land use (parks), roads | [Overture Maps](https://overturemaps.org) release 2026-11-18, read straight from its public S3 bucket | ODbL 1.0 (OpenStreetMap-derived) and CDLA-Permissive (some building sources) | every city's own streets |
| Landmarks (2,313 low-poly models, 114 places) | [alexwohlbruck/landmarks](https://github.com/alexwohlbruck/landmarks) | CC0 (models); placements ODbL (from OSM) | every landmark in view, at its anchor and bearing, replacing the plain buildings it stands for |
| Elevation | [Terrarium tiles](https://registry.opendata.aws/terrain-tiles/) (AWS open data, Mapzen/Tilezen) | open, attribution | the hills and mountains on the horizon |

Looked at and not used (yet): OSM2World (MIT, Java; converts raw OSM to detailed 3D, needs OSM extracts, which this
environment can't download), streets-gl (MIT, browser renderer; a reference for roof shapes), OSMBuildings (viewer),
Kenney's CC0 kits (Watercraft for boats on the water cities, Nature for trees; no aircraft or birds), qbki/plane-assets
(MIT, a low-poly helicopter). At a clock's size, birds, planes and helicopters are silhouettes, so the app's own drawn
ones stay.

Attribution the app must show: "© OpenStreetMap contributors, Overture Maps Foundation; elevation: Mapzen/Tilezen".
The baked scene files are a derivative database of ODbL data, so they're offered under ODbL.

## Reduction rules

1. **View**: only what's inside the widest cell's view (3:1) and within 4.5 km.
2. **Size**: a building shorter than 0.7 px and narrower than 1.5 px at the reference size (a 280 px tall cell) goes.
3. **Shape**: footprints simplified to about a pixel (the tolerance grows with distance); small ones with many corners
   become rectangles; courtyards are dropped.
4. **Merge**: touching buildings of about the same height (bins of 3 m near, widening with distance) in the same 160 m
   cell become one block, as the video's blocks.
5. **Faces**: walls facing away from the camera and roofs above it are never stored.
6. **Sub-pixel**: any face smaller than a fifth of a pixel goes.
7. **Occlusion**: faces with fewer than 2 pixels showing (drawn at twice the size, in painter's order) go.
8. **Far terrain**: the hills become two silhouette lines (2.5–9 km and 9–32 km), not meshes.
9. **Camera height**: never lower than 50 m (75 m before 2.32); a short landmark is looked down on a little more, from
   higher up.
10. **Air**: nothing within 140 m (or a fifth of the camera's distance) of the camera stays, so no building fills the
    view or swallows the camera.
12. **Clear view**: a plain building between the camera and the main landmark (or the city's focus), nearer than it,
    whose top would cover any of it, goes, so the landmark is never hidden; since 2.32 another landmark there that would
    cover the hero's foot goes too (Dubai's Address Sky View before the Burj Khalifa).
13. **Hero**: another landmark that would stand taller on screen than the main one (and than about 60% of the cell)
    goes, so a near tower never dwarfs the city's landmark.
11. **Lamps**: street lamps hidden behind a nearer face go; the rest carry their distance, so they're drawn in depth
    order with the buildings (as are each face's lit windows, right after the face).

## Camera

Level (verticals stay vertical, as the video's), the horizon 64% of the way down (the clocks' skies). Since 2.32 one rule
places every camera (**rule 18**), so the city round each landmark is at much the same scale everywhere and nearer than
before:

- its distance comes from the main landmark's height (or the focus's, 60 m), growing slowly: 340 m for 60 m, about 570 m
  for the Eiffel Tower's 330 m, 750 m for 830 m; never so near that a wide landmark overflows a square cell;
- the landmark's foot 80% of the way down, and its top always at least 10% below the top edge;
- a tall one that doesn't fit first has its foot lower (to 88%), then a wider lens (to a 60 degree view up and down,
  from the standard 40, as a photographer shoots a skyscraper: Tokyo, Dubai), and only then the camera steps back;
- the camera never lower than 50 m (the foot lower, else a step back).

2.31's distances were 400 m to 1.9 km; 2.32's are 340 m to about 920 m (Dubai, wide lens).

**Rule 19, from a corner** (2.32): the landmark is seen as a photographer would after walking a couple of blocks from
the front view: its faces' directions come from its footprint (the smallest rectangle round its model), and the camera
stands on one of the four diagonals between them, so two of its sides show. A hand-set side (`az`) moves to the nearest
corner (of two equally near, the one with the better view by rule 14); an automatic camera takes the corner with the
richest view. Seen from a corner a wide landmark is narrower, so its distance is worked out again for its width as seen
(Tower Bridge comes nearer). `"diagonal": false` in `cities.json` keeps a city's set side. `cities.json` holds each city's camera DNA: the main landmark (`main`, a catalog id;
or `lat`/`lon` and a focus `height` and `width`), the side it's seen from (`az`: the direction from the landmark to the
camera) and, where a city needs it, `foot` (where the landmark's foot stands; nearer the horizon brings the camera down,
so a low, wide landmark such as Tower Bridge stands against the sky), `hmin` (the lowest camera) and `zoom`.

## Night lights

After the reference poster (warm windows in grids, an orange-pink bloom, pink street lamps) and the approach in
stolslilabs/paved#513 (windows as emissive faces given a halo by bloom, warm pools of light on the ground; amber light):

- every wall carries its size in metres, so its windows are a real grid: a floor every 3.3 m, a bay every 3.2 m; where
  the grid is too fine to see, cells merge and hold one smaller window;
- each window is lit or not by a seeded hash (the same every night), each building its own share (offices darker,
  flats busier), fewer at dusk than at night; the ground floor of a tall building is left dark;
- warm cores in five shades (yellow to peach), and on a separate layer a glow round each, orange-pink; every face clears
  that layer behind it as it's drawn, so a nearer building hides the glow of what's behind it; the layer is blurred and
  laid over by screen (the bloom);
- landmarks' window materials glow as a whole, softly; street lamps are pink-white with a pink halo and a warm pool on
  the ground, in depth order;
- the night city itself is lifted to a moonlit blue (the palette rule's night lightness up a little), so the windows read
  against it, as the poster's;
- every building is lit from the street (2.32): warm at its foot (a warm rose laid on by screen), fading out 60% of the
  way up, less far off; the hero the same, a little stronger;
- the cars (2.32): small streaks of light along the main roads' centre lines, each way in its own lane (on the left where
  they drive so), warm white headlights coming, red tail lights going, 25 to 47 km/h, smaller and fainter further off,
  hidden where a building stands in front (the roads' visible part is kept as a mask while the scene is drawn).

## Two tiers

- **Tier 1** (`majors.txt`, 158 major cities): the full map as above, with a hero landmark (the hand-set one in
  `cities.json` or `bake_all.py`'s `HERO`, else the tallest catalog landmark within 6 km, never one on a hilltop) and its
  camera (hand-set, else rule 14).
- **Tier 2** (every other place in the app's list, 321): a low-poly version of itself (rule 15): a 45 m grid, each cell
  with enough building in it a block as tall as its buildings on average (to 4 m), runs of equal cells joined; its real
  water, parks, main roads and hills; the camera chosen the same way.
- Places with almost nothing to show (fewer than 30 shapes) get no scene and keep the drawn one. Scenes are centred on
  Natural Earth's city centre (`focus.json`), not the time zone's point, which can be kilometres off.

More rules: **14, the automatic camera**: of eight sides, the one with the richest view: a deep skyline round and behind
the focus, water in view, mountains on the horizon (weighed well), nothing tall right in front of the camera; **17, no
wall in front**: a plain block nearer than the focus whose top would stand in the top sixth of the cell goes; **15**: tier
2's low-poly blocks; **16, at screen size**: the ground simplified to 0.6 px and its specks dropped, footprints to half a
pixel, a block showing less than 6 px dropped.

## Scene file (format 4, `assets/scenes/<name>-<cc>.bin`)

Deflated; little-endian; numbers after the header are zigzag variable-length, points delta-coded in half pixels of the
reference frame (840 x 280: three times as wide as tall, the widest cell), each list's first point from the previous
list's first.

- header: `WSC3`, version (4; 3 before 2.32), tier, width, height, the camera's look azimuth (degrees), its height
  (quarter metres), and since version 4 its lens (the focal length in cell heights, times 10,000);
- hills: count, then each line's points (the far line first);
- ground: count, then each shape's kind (2 water, 3 park, 4 road) and points;
- items, far to near: count, then each: a **block** (`0`, distance byte, height in half metres, its footprint's points on
  the ground) or a **landmark face** (`1 + kind`: 5 body, 6 glass, 7 light, 9 roof; facing 0-254 of a turn or 255;
  distance byte; the material's hue and chroma; flags, 1 = the hero; its points);
- (version 4) the cars' roads: count, then each main road's width in metres and its centre line's points.

Distance bytes are `log1p(metres) * 28`. A block's walls are rebuilt by the app: the camera is level, so every vertical
edge stays vertical and a corner's top is straight above its foot, as far above the horizon as the block's height over
the camera's, scaled by how far below the horizon the foot is; the ground points give each wall's real width and facing
(for its windows and its light). Street lamps and park trees are placed by the app (along the roads, in the parks).

## Run

```sh
pip install pyarrow shapely trimesh numpy pillow matplotlib
git clone --depth 1 https://github.com/alexwohlbruck/landmarks /home/user/alexwohlbruck/landmarks
python3 lm_index.py        # every landmark's height, once
python3 bake_all.py        # every place in the app's list (resumable; or name some)
python3 finalize.py        # into app/src/main/assets/scenes
python3 bake.py paris && python3 render.py paris   # the 12 test cities' previews
```
