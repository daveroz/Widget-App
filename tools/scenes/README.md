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
| The Eiffel Tower's girders (2.32) | [FlorianCassayre/eiffel-tower](https://github.com/FlorianCassayre/eiffel-tower): a SolveSpace drawing of one face, after Eiffel's own plates (*La tour de trois cents mètres*, 1900, pl. I and VII), kept as `eiffel_cassayre.json` | MIT (`LICENSE-eiffel-cassayre.txt`) | the tower built from it (`eiffel_parts`): its girders mirrored across the face and wrapped round its four faces at their real taper, as flat bars (kind 11, drawn solid, see-through between them), with its platforms, piers and antennas; the replicas too |
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
6. **Sub-pixel**: any face smaller than a fifth of a pixel goes (since 2.40 the hero's faces only below a twenty-fifth,
   so a far hero such as the Cristo keeps its shape).
7. **Occlusion**: faces with fewer than 2 pixels showing (drawn at twice the size, in painter's order) go.
8. **Far terrain**: the hills become two silhouette lines (2.5–9 km and 9–32 km), not meshes; since 2.40 a building
   further than the first line's ridge in its column and lower than that line on screen is hidden behind the hill and
   goes.
9. **Camera height**: never lower than 50 m (75 m before 2.32); a short landmark is looked down on a little more, from
   higher up.
10. **Air**: nothing within 40 m of the camera stays, so no building swallows the camera (140 m, or a fifth of the
    camera's distance, before 2.40; a building too big in view now goes by rule 17 instead).
12. **Clear view** (2.40, after the user: only what really covers the landmark goes): the hero is drawn on screen and
    every block and landmark nearer than it outlined (its footprint at the ground and at its top); while more than 35% of
    the hero is hidden, the one hiding most of it goes, then the next. A building in front of its foot stays, as in a
    photo. (2.31 to 2.39: any plain building whose top covered any of it went, and any landmark covering its foot.) A
    place with no hero keeps everything.
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

**Rule 20, a landmark on its hill** (2.40, Rio's Cristo Redentor on Corcovado): the scene's ground is flat, so such a
landmark is seen from the city below, far off, as the postcards show it: `"hill": {"az", "d", "hc", "view", "scale",
"ground", "from"}` sets the camera (its side, distance and height over the city's ground, `ground` metres over the sea,
and its lens, degrees up and down), the hills get a third, nearest line from `from` metres to just past the landmark
(finer terrain, zoom 12, so the mountain's shape holds), and the landmark stands where that line is drawn under it, so it
never floats; `scale` draws it larger than life so it reads at a widget's size (the Cristo 2.2 times). At night it is
floodlit whole, a cool white.

**Drawn landmarks** (2.40): a landmark the catalog lacks is drawn by `bake.py` from its published measures and placed as
a catalog entry (`PROC`), with `clear` metres round it free of plain buildings: the Obelisco of Buenos Aires (67.5 m, a
square shaft 6.8 m a side tapering to 3.5 m, a pyramid tip), seen down the Avenida 9 de Julio.

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

- **Tier 1** (`majors.txt`, 160 major cities, Whittier and La Serena among them since 2.40, and 16 small places drawn in
  full since 2.40 because too little of them survived tier 2's blocks: 176): the full map as above, with a hero landmark (the hand-set one in
  `cities.json` or `bake_all.py`'s `HERO`, else the tallest catalog landmark within 6 km, never one on a hilltop) and its
  camera (hand-set, else rule 14).
- **Tier 2** (every other place in the app's list, 245): a low-poly version of itself (rule 15): a 45 m grid, each cell
  with enough building in it a block as tall as its buildings on average (to 4 m), runs of equal cells joined; its real
  water, parks, main roads and hills; the camera chosen the same way.
- Places with almost nothing to show (fewer than 30 shapes) get no scene and keep the drawn one (none since 2.40: the
  16 that did are centred on their main town, `focus.json`, and drawn in full). Scenes are centred on
  Natural Earth's city centre (`focus.json`), not the time zone's point, which can be kilometres off.

More rules: **14, the automatic camera**: of eight sides, the one with the richest view: a deep skyline round and behind
the focus, water in view, mountains on the horizon (weighed well), nothing tall right in front of the camera; **17, no
wall in front**: a block nearer than the hero (or focus) whose outline on screen would cover more than 30% of a square
cell goes (2.40; before, any whose top stood in the top sixth of the cell); **15**: tier
2's low-poly blocks; **16, at screen size**: the ground simplified to 0.6 px and its specks dropped, footprints to half a
pixel, a block showing less than 6 px dropped.

## Bridges and tunnels (2.43)

Overture's road flags mark bridges (`is_bridge`, or a level of 1 and up) and tunnels (`is_tunnel`, or a level under
0), each over its share of a segment. A bridge's road is raised to its deck: on a landmark bridge the middle of the
model's upward faces 4 to 30 m up under it (Tower Bridge about 15 m), on any other `BRIDGE_H` (10 m), with a `RAMP`
(40 m) where a raised stretch meets the ground (not where it meets another bridge's end); the cars' lines carry each
point's height. A bridge that's not a landmark gets its deck (the road and a metre of pavement each side) and its
side turned to the camera (1.6 m deep) as faces between each pair of points, drawn in depth with the buildings; a
landmark bridge has its own, and its cars are always shown on it (hidden through its towers). Tunnels are hidden.
Neither bridges nor tunnels are drawn as roads on the ground any more. The app hides a boat only where something
nearer than it stands (a depth map of every face and block, half size), so boats pass under the decks.

## Boats (2.43, `boats.py`)

The boats on the big rivers and canals are Kenney's Watercraft Kit 2.1 (CC0, kenney.nl; its licence in
`LICENSE-kenney-watercraft.txt`): eleven of its models (three freighters, two tow boats, four houseboats, a fishing boat
and two sailing boats; not its speed boats, round tugs or ferry, whose bare decks read as triangles and blobs at a
widget's size, nor its pirate ships or liners). `boats.py` takes each one's
faces with their colours from the kit's colour map, drops its hull bottom (never seen from above) and its small loose
parts (railings, ropes, antennas: under 6% of its length), then its smallest faces down to 110 to 240, normalises it
(length 1 along +Z, the bow forward, its waterline at 0) and packs them all into `app/src/main/assets/boats.bin`
(about 43 KB): `'BTS1'`, count, then each: kind (0 a freighter or tow boat, 1 a tour boat or houseboat, 2 a small
boat), where it sails (1 rivers, 2 canals, 3 both), length in metres, vertex and face counts, the vertices (floats),
the faces (shorts) and their colours. The app draws each boat in 3D at its heading, faces turned to the camera far to
near, lit by the scene's sun; one a few pixels long as a simple hull.

## Scene file (format 5, `assets/scenes/<name>-<cc>.bin`)

Deflated; little-endian; numbers after the header are zigzag variable-length, points delta-coded in half pixels of the
reference frame (840 x 280: three times as wide as tall, the widest cell), each list's first point from the previous
list's first.

- header: `WSC3`, version (5; 4 before 2.43, 3 before 2.32), tier, width, height, the camera's look azimuth (degrees), its height
  (quarter metres), and since version 4 its lens (the focal length in cell heights, times 10,000);
- hills: count, then each line's points (the far line first; a third, nearest line since 2.40 for rule 20);
- ground: count, then each shape's kind (2 water, 3 park, 4 road) and points;
- items, far to near: count, then each: a **block** (`0`, distance byte, height in half metres, its footprint's points on
  the ground) or a **landmark face** (`1 + kind`: 5 body, 6 glass, 7 light, 9 roof; facing 0-254 of a turn or 255;
  distance byte; the material's hue and chroma; flags, 1 = the hero; its points). Kind 12 (2.43) is a bridge's deck
(facing 255) or one of its sides: the road raised over the water, drawn in the road's colour, its cars shown on it; Kind 10 (2.32) is see-through
  ironwork (a model's `ironwork` material, the Eiffel Tower's lattice): both its sides are kept, it hides nothing behind
  it, and the app draws it as girders (a faint fill, a cross-hatch of bars about every 8 m, rails along its edges);
- (version 4) the cars' roads: count, then each main road's width in metres and its centre line's points (since
  version 5, 2.43, then each point's height in half metres, a bridge's deck, and each point's mode: 0 shown where the
  road shows, 1 on a landmark bridge, always shown, 2 hidden, in a tunnel or a landmark bridge's tower);
- (version 5, 2.43) the boats' lanes: count, then each one's kind (0 a river, 1 a canal) and its centre line's points:
  the rivers' and canals' centre lines (Overture's water lines) where they run inside the water drawn, at least 5 m
  in from its banks (so only big rivers and canals mapped as water), 40 m to 3.5 km off.

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
