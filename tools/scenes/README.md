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
9. **Camera height**: never lower than 75 m; a short landmark is looked down on a little more, from higher up.
10. **Air**: nothing within 140 m (or a fifth of the camera's distance) of the camera stays, so no building fills the
    view or swallows the camera.
12. **Clear view**: a plain building between the camera and the main landmark (or the city's focus), nearer than it,
    whose top would cover any of it, goes, so the landmark is never hidden.
13. **Hero**: another landmark that would stand taller on screen than the main one (and than about 60% of the cell)
    goes, so a near tower never dwarfs the city's landmark.
11. **Lamps**: street lamps hidden behind a nearer face go; the rest carry their distance, so they're drawn in depth
    order with the buildings (as are each face's lit windows, right after the face).

## Camera

Level (verticals stay vertical, as the video's), the horizon 64% of the way down (the clocks' skies), the main
landmark's foot 80% down and a tall landmark's top about 30% down; never nearer than 260 m, and back far enough that the
landmark fits in 55% of a square cell. `cities.json` holds each city's camera DNA: the main landmark (`main`, a catalog id;
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
  against it, as the poster's.

## Scene file

Deflated; little-endian. Header (width, height, face count, ground count), then each face: kind (wall, roof, landmark,
landmark glass, landmark light, landmark roof), facing (0–254 of a turn, 255 for none), distance (log), a tint hue, for a
wall its width and height (2 m steps), and its points delta-coded in quarter pixels; then the ground polygons (water, parks, roads), the two hill lines, and the
street lamps (points with distance). The app colours each face for the light: lit or in shade by its facing against the
sun's side, roofs lighter, hazed by distance, windows and lamps lit at night.

## Run

```sh
pip install pyarrow shapely trimesh numpy pillow matplotlib
git clone --depth 1 https://github.com/alexwohlbruck/landmarks /home/user/alexwohlbruck/landmarks
python3 bake.py            # every city in cities.json (or name some)
python3 render.py paris    # dusk (3:1 and square), golden hour, day, night
```
