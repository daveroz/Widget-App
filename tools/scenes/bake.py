"""Bakes a real city into a flat scene for a world clock, as the reference video draws it.

Sources (all open):
  - Overture Maps (2026-11-18): buildings with heights or floors, water (rivers, bays, the ocean), parks, roads.
  - alexwohlbruck/landmarks (CC0): low-poly landmarks, placed at their real anchors and bearings; they replace the
    plain buildings they stand for (by OpenStreetMap id).
  - Terrarium elevation tiles (AWS open data): the hills and mountains on the horizon.

The camera is fixed per city (high over the city, level, the horizon 64% of the way down, as the clocks' skies), so
everything is baked to 2D: faces already projected, in painter's order, hidden ones removed. Reduction rules:
  1. view: only what's inside the widest cell's view (3:1) and nearer than the far limit;
  2. size: a building smaller than about a pixel at the reference size (a 280 px tall cell) goes;
  3. shape: footprints simplified to about a pixel (the tolerance grows with distance); small ones become rectangles;
  4. merge: touching buildings of about the same height become one block (the bins widen with distance);
  5. faces: walls facing away and roofs above the camera are never stored; holes (courtyards) are dropped;
  6. sub-pixel: any face smaller than a fifth of a pixel goes;
  7. occlusion: faces hidden behind nearer ones (fewer than 2 pixels showing at twice the size) go;
  8. far terrain: the hills become two silhouette lines, not meshes.
"""
import json, math, os, sys, glob, struct, zlib, random, io, urllib.request
import numpy as np
import shapely, shapely.wkb as W
from shapely.geometry import Polygon, MultiPolygon, box as sbox
from shapely.ops import unary_union
from PIL import Image, ImageDraw
import trimesh
import overture

REPO = '/home/user/alexwohlbruck/landmarks'
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'baked')
os.makedirs(OUT, exist_ok=True)

# the reference cell: 280 px tall (a big clock at 2 px per dp); bake for the widest cell, three times as wide
REF_H = 280
ASPECT = 3.0
FN = 0.5 / math.tan(math.radians(20))  # focal length in cell heights (a 40 degree view up and down)
HZ = 0.64  # the horizon, as a share of the height
FOOT = 0.80  # where the main landmark's foot stands (a lower camera: more faces, fewer roofs, as the video)
TOP = 0.30  # where a tall landmark's top reaches
FAR = 4500.0  # metres: nothing further except the hills
FACE_MIN_PX2 = 0.2
HMIN = 50.0  # the camera is never lower than this (metres over the landmark's ground)
CLOSER = 0.85  # every camera a little nearer its landmark than the composition rule puts it
# rule 18 (2.32): one rule for every camera. Its distance from the landmark's height, D_BASE at 60 m and growing slowly
# (340 m for 60 m, 570 m for 330 m, 750 m for 830 m), so the city round it is at much the same scale everywhere; the
# landmark's top always at least TOP_MARGIN below the top edge; a tall one that doesn't fit first has its foot lower
# (to FOOT_LOW), then a wider lens (to FN_WIDE, a 60 degree view up and down, as a photographer shoots a skyscraper),
# and only then the camera steps back
FN0 = FN
D_BASE, D_EXP = 340.0, 0.3
TOP_MARGIN = 0.10
FOOT_LOW = 0.88
FN_WIDE = 0.5 / math.tan(math.radians(30))
OCC_MIN_PX = 2

KINDS = {'wall': 0, 'roof': 1, 'water': 2, 'park': 3, 'road': 4, 'lm': 5, 'lm_glass': 6, 'lm_light': 7, 'land': 8, 'lm_roof': 9}

CAT = {}
for f in glob.glob(REPO + '/catalog/landmarks/*.json'):
    d = json.load(open(f))
    CAT[d['id']] = d
MODEL_FILE = {}
for f in glob.glob(REPO + '/catalog/models/*.json'):
    d = json.load(open(f))
    MODEL_FILE[d['id']] = d


class Cam:
    """A level camera at (cx, hc, cz) in local metres (x east, y up, z south) looking along azimuth `look`."""
    def __init__(self, cx, cz, hc, look):
        self.c = np.array([cx, hc, cz], float)
        a = math.radians(look)
        self.fwd = np.array([math.sin(a), 0, -math.cos(a)])
        self.right = np.array([math.cos(a), 0, math.sin(a)])
        self.f = FN * REF_H

    def project(self, p):
        """Points (n,3) to screen (n,2) in reference pixels (x from the middle, y from the top), and depth."""
        q = p - self.c
        z = q @ self.fwd
        x = q @ self.right
        y = q[:, 1]
        zs = np.maximum(z, 0.1)
        return np.stack([x / zs * self.f, HZ * REF_H - y / zs * self.f], 1), z


def to_local(lon0, lat0):
    kx = math.cos(math.radians(lat0)) * 111320.0
    kz = 110540.0
    def f(lon, lat): return (lon - lon0) * kx, -(lat - lat0) * kz
    def inv(x, z): return lon0 + x / kx, lat0 - z / kz
    return f, inv


def geom_local(g, f):
    return shapely.transform(g, lambda a: np.stack(f(a[:, 0], a[:, 1]), 1))


def polys(g):
    if g is None or g.is_empty: return []
    if isinstance(g, Polygon): return [g]
    if isinstance(g, MultiPolygon): return list(g.geoms)
    if hasattr(g, 'geoms'): return [p for x in g.geoms for p in polys(x)]
    return []


def height_of(row):
    if row.get('height'): return float(row['height'])
    if row.get('num_floors'): return float(row['num_floors']) * 3.3 + 1.5
    c = row.get('class') or ''
    return {'house': 7, 'detached': 7, 'garage': 3.5, 'shed': 3, 'garages': 3.5, 'church': 18, 'cathedral': 30,
            'apartments': 16, 'residential': 12, 'commercial': 14, 'office': 24, 'retail': 8, 'industrial': 9,
            'warehouse': 9, 'school': 10, 'hospital': 18, 'hotel': 24}.get(c, 11)


def landmark_mesh(pid, f):
    """A catalog landmark as triangles in local metres, with their material names and colours."""
    pl = CAT[pid]
    md = MODEL_FILE.get(pl['model'])
    if not md: return None
    path = os.path.join(REPO, md['file'])
    if not os.path.exists(path): return None
    sc = trimesh.load(path)
    x0, z0 = f(pl['lng'], pl['lat'])
    b = math.radians(-pl.get('bearing', 0.0))
    s = pl.get('scale', 1.0)
    rot = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    out = []
    for node in sc.graph.nodes_geometry:
        T, gname = sc.graph[node]
        g = sc.geometry[gname]
        if not hasattr(g, 'faces') or len(g.faces) == 0: continue
        v = trimesh.transformations.transform_points(g.vertices, T) * s
        v = v @ rot.T + np.array([x0, pl.get('elevation', 0.0), z0])
        mat = getattr(g.visual, 'material', None)
        name = (getattr(mat, 'name', '') or '').lower()
        col = getattr(mat, 'baseColorFactor', None)
        col = tuple(int(c) for c in col[:3]) if col is not None else (220, 214, 204)
        out.append((v, np.asarray(g.faces), name, col))
    return out


def terrain_silhouette(lon, lat, cam, inv, look):
    """The hills' line on the horizon: per column, the highest angle of the ground 3 to 30 km off (two layers)."""
    z = 10
    n = 2 ** z
    cache = {}
    def tile(tx, ty):
        k = (tx, ty)
        if k not in cache:
            p = os.path.join(HERE, 'terr', f'{z}_{tx}_{ty}.png')
            os.makedirs(os.path.dirname(p), exist_ok=True)
            if not os.path.exists(p):
                try:
                    urllib.request.urlretrieve(f'https://elevation-tiles-prod.s3.amazonaws.com/terrarium/{z}/{tx}/{ty}.png', p)
                except Exception:
                    cache[k] = None
                    return None
            im = np.asarray(Image.open(p).convert('RGB')).astype(np.float32)
            cache[k] = im[:, :, 0] * 256 + im[:, :, 1] + im[:, :, 2] / 256 - 32768
        return cache[k]
    def elev(lo, la):
        x = (lo + 180) / 360 * n
        y = (1 - math.log(math.tan(math.radians(la)) + 1 / math.cos(math.radians(la))) / math.pi) / 2 * n
        t = tile(int(x), int(y))
        if t is None: return 0.0
        return float(t[min(255, int((y % 1) * 256)), min(255, int((x % 1) * 256))])
    base = elev(lon, lat)
    half = math.atan(ASPECT * 0.5 / FN)
    cols = 120
    layers = [(2500, 9000), (9000, 32000)]
    out = []
    for d0, d1 in layers:
        line = []
        for i in range(cols + 1):
            a = -half + 2 * half * i / cols
            az = math.radians(look) + a
            best = -1e9
            for d in np.geomspace(d0, d1, 26):
                x = cam.c[0] + math.sin(az) * d
                zz = cam.c[2] - math.cos(az) * d
                lo, la = inv(x, zz)
                h = elev(lo, la) - base - d * d / (2 * 6371000) * 0.87  # the earth's curve, less refraction
                along = d * math.cos(a)
                best = max(best, (h - cam.c[1]) / along)
            sx = math.tan(a) * cam.f + ASPECT * REF_H / 2
            line.append((sx, HZ * REF_H - best * cam.f))
        out.append(line)
    return out, base


TIERS = {
    # tier 1, a major city: the full map. Tier 2, any other place: its terrain, water and a low-poly version of its
    # streets (bigger merges, coarser shapes, fewer faces, main roads only)
    1: {'far': FAR, 'cell': 160, 'hb0': 3, 'hbk': 0.006, 'tol': 0.9, 'fmin': 1.0, 'occ': 2, 'gtol': 2.5, 'lamp': 34,
        'roads': ('motorway', 'trunk', 'primary', 'secondary', 'tertiary')},
    2: {'far': 2600.0, 'cell': 280, 'hb0': 6, 'hbk': 0.012, 'tol': 2.2, 'fmin': 2.0, 'occ': 5, 'gtol': 5.0, 'lamp': 70,
        'roads': ('motorway', 'trunk', 'primary')},
}
LM_INDEX = json.load(open(os.path.join(HERE, 'lm_index.json'))) if os.path.exists(os.path.join(HERE, 'lm_index.json')) else {}


# landmarks on a hilltop (the scene's ground is flat, so they'd stand at street level): never a city's hero
HILLTOP = {'rio-christ-the-redeemer', 'seoul-n-seoul-tower', 'tbilisi-tv-tower', 'barcelona-tibidabo-sagrat-cor',
           'barcelona-torre-collserola', 'quito-virgen-del-panecillo', 'tbilisi-kartlis-deda', 'tbilisi-narikala',
           'yerevan-mother-armenia', 'barcelona-montjuic-castle', 'prague-petrin-tower', 'la-hollywood-sign',
           'la-griffith-observatory'}


def hero_near(lat, lon, radius=6000.0):
    """The tallest catalog landmark within `radius` metres of a place (the city's hero), or None."""
    best, bh = None, 0.0
    kx = math.cos(math.radians(lat)) * 111320.0
    for pid, m in LM_INDEX.items():
        if pid in HILLTOP: continue
        dx, dz = (m['lng'] - lon) * kx, (m['lat'] - lat) * 110540.0
        dd = math.hypot(dx, dz)
        if dd > radius: continue
        score = m['h'] * (1.0 - 0.5 * dd / radius)  # tall, and not far from the middle
        if score > bh: best, bh = pid, score
    return best


def choose_az(f, inv, d, hc, T, focus_h, lon0=None, lat0=None, cands=None):
    """Rule 14, the automatic camera: of eight sides, the one with the richest view: a deep skyline round and behind
    the focus, water in view, and nothing tall right in front of the camera."""
    R = d + T['far'] * 0.6
    lo0, la1 = inv(-R, -R)
    lo1, la0 = inv(R, R)
    tab, _ = overture.read('buildings/building', (lo0, la0, lo1, la1), ['height', 'num_floors', 'class', 'bbox'])
    pts = []
    if tab is not None:
        bb = tab.column('bbox').combine_chunks()
        xs_ = (bb.field('xmin').to_numpy(zero_copy_only=False) + bb.field('xmax').to_numpy(zero_copy_only=False)) / 2
        ys_ = (bb.field('ymin').to_numpy(zero_copy_only=False) + bb.field('ymax').to_numpy(zero_copy_only=False)) / 2
        hs = [height_of(r) for r in tab.select(['height', 'num_floors', 'class']).to_pylist()]
        for lo, la, h in zip(xs_, ys_, hs):
            x, z = f(lo, la)
            pts.append((x, z, h))
    water = []
    wt, _ = overture.read('base/water', (lo0, la0, lo1, la1), ['subtype', 'class', 'geometry'])
    if wt is not None:
        for r in wt.to_pylist():
            if r['subtype'] in ('ocean', 'water', 'lake', 'river', 'canal', 'reservoir', 'lagoon') or r['class'] in ('bay', 'river', 'harbour', 'lake', 'riverbank'):
                g = geom_local(W.loads(r['geometry']), f)
                if g.geom_type in ('Polygon', 'MultiPolygon'): water.append(g)
    wu = unary_union(water).buffer(0) if water else None
    P = np.array(pts) if pts else np.zeros((0, 3))
    fpx = FN * REF_H
    best, bs = 200, -1e18
    for az in (cands if cands is not None else range(0, 360, 45)):
        a = math.radians(az)
        cx, cz = math.sin(a) * d, -math.cos(a) * d
        lk = math.radians((az + 180) % 360)
        fx, fz = math.sin(lk), -math.cos(lk)
        rx, rz = math.cos(lk), math.sin(lk)
        score = 0.0
        if len(P):
            dx, dz = P[:, 0] - cx, P[:, 1] - cz
            along = dx * fx + dz * fz
            across = dx * rx + dz * rz
            ok = (along > 30) & (np.abs(across) < along * 0.55) & (along < T['far'])
            px = P[:, 2] / np.maximum(along, 1) * fpx
            near = ok & (np.hypot(dx, dz) < max(140, 0.2 * d) * 1.6) & (px > 0.3 * REF_H)
            front = ok & (along < d * 0.85) & (px > 0.45 * REF_H)
            sky = ok & (along > d * 0.6)
            score += np.minimum(px[sky], 0.35 * REF_H).sum() - 900 * near.sum() - 300 * front.sum()
        if lon0 is not None:  # mountains in view: the real hills' line, how high it stands over the horizon
            try:
                sil, _ = terrain_silhouette(lon0, lat0, Cam(cx, cz, hc, (az + 180) % 360), inv, (az + 180) % 360)
                above = [max(0.0, HZ * REF_H - y) for line in sil for x, y in line]
                score += 3500 * (sum(above) / max(1, len(above)))  # mountains are part of a city: weigh them well
            except Exception:
                pass
        if wu is not None and not wu.is_empty:
            wedge = Polygon([(cx, cz), (cx + (fx * 3000 - rx * 1600), cz + (fz * 3000 - rz * 1600)),
                             (cx + (fx * 3000 + rx * 1600), cz + (fz * 3000 + rz * 1600))])
            score += 9000 * wu.intersection(wedge).area / wedge.area
        if score > bs: best, bs = az, score
    return best


def bake(key, cfg):
    global FN
    FN = FN0  # the standard lens until this city's is chosen
    T = TIERS[cfg.get('tier', 1)]
    main = cfg.get('main')
    if not main and cfg.get('tier', 1) == 1 and 'lat' in cfg:
        main = hero_near(cfg['lat'], cfg['lon'])
    if main:
        pl = CAT[main]
        lon0, lat0 = pl['lng'], pl['lat']
    else:
        lon0, lat0 = cfg['lon'], cfg['lat']
    f, inv = to_local(lon0, lat0)
    # the composition: the main landmark's foot at 88% down, its top near 20%, inside 55% of a square cell's width
    hF, wF = cfg.get('height', 60.0), cfg.get('width', 60.0)
    if main:
        m = landmark_mesh(main, f)
        allv = np.vstack([x[0] for x in m])
        hF = float(allv[:, 1].max())
        wF = float(max(np.ptp(allv[:, 0]), np.ptp(allv[:, 2])))
    def compose(wv):  # rule 18 for a landmark wv metres wide as seen
        d = D_BASE * (max(hF, 30.0) / 60.0) ** D_EXP  # rule 18
        d = max(d, wv * FN0 / 0.62, cfg.get('min_dist', 0))  # a wide one fits across a square cell
        d *= cfg.get('zoom', 1.0)
        foot, fn = cfg.get('foot', FOOT), FN0
        if foot - hF * fn / d < TOP_MARGIN:  # its top must keep clear of the top edge: first its foot lower,
            foot = max(foot, min(FOOT_LOW, TOP_MARGIN + hF * fn / d))
        if foot - hF * fn / d < TOP_MARGIN:  # then a wider lens,
            fn = max(FN_WIDE, (foot - TOP_MARGIN) * d / hF)
        if foot - hF * fn / d < TOP_MARGIN:  # and only then a step back
            d = hF * fn / (foot - TOP_MARGIN)
        hc = d * (foot - HZ) / fn
        hmin = cfg.get('hmin', HMIN)
        if hc < hmin:  # rule 9: never lower than HMIN: the foot a little lower, else a step back
            foot = min(FOOT_LOW, HZ + hmin * fn / d)
            d = max(d, hmin * fn / (foot - HZ))
            hc = hmin
        return d, hc, fn, foot
    d, hc, fn, foot = compose(wF)
    FN = fn  # this city's lens, for everything below (and the scene file)
    # rule 19 (2.32): the landmark seen from a corner, as a photographer moves a couple of blocks from the front view:
    # its faces' directions from its footprint (the smallest rectangle round it), the camera at one of the four
    # diagonals between them; a hand-set side moves to the nearest corner (the better view of two equally near), an
    # automatic camera takes the corner with the richest view
    corners = None
    if main and cfg.get('diagonal', True):
        rect = shapely.MultiPoint(allv[:, [0, 2]]).convex_hull.minimum_rotated_rectangle
        if rect.geom_type == 'Polygon':
            rc = np.array(rect.exterior.coords)
            ex, ez = rc[1] - rc[0]
            edge = math.degrees(math.atan2(ex, -ez)) % 90  # x east, z south: compass bearing of one side
            corners = [round((edge + 45 + 90 * i) % 360, 1) for i in range(4)]
    if corners is not None:
        if 'az' in cfg:
            a0 = cfg['az']
            off = lambda a: abs((a - a0 + 180) % 360 - 180)
            near = sorted(corners, key=off)
            az_new = near[0] if off(near[1]) - off(near[0]) > 10 else choose_az(f, inv, d, hc, T, hF, lon0, lat0, cands=near[:2])
        else:
            az_new = choose_az(f, inv, d, hc, T, hF, lon0, lat0, cands=corners)
        cfg = dict(cfg, az=az_new)
        # seen from a corner a wide landmark is narrower than from its side: the distance again, for its width as seen
        a_ = math.radians(cfg['az'])
        wv = float(np.ptp(allv[:, 0] * -math.cos(a_) + allv[:, 2] * -math.sin(a_)))
        d, hc, fn, foot = compose(wv)
        FN = fn
    elif 'az' not in cfg:
        cfg = dict(cfg, az=choose_az(f, inv, d, hc, T, hF, lon0, lat0))
    az = math.radians(cfg['az'])
    cam = Cam(math.sin(az) * d, -math.cos(az) * d, hc, (cfg['az'] + 180) % 360)
    look = (cfg['az'] + 180) % 360
    half = math.atan(ASPECT * 0.5 / FN) + 0.03
    # the box to read: the camera and the view's wedge out to the far limit
    pts = [(cam.c[0], cam.c[2])]
    for k in range(9):
        a = math.radians(look) - half + 2 * half * k / 8
        pts.append((cam.c[0] + math.sin(a) * T['far'], cam.c[2] - math.cos(a) * T['far']))
    xs, zs = zip(*pts)
    lo0, la1 = inv(min(xs), min(zs))
    lo1, la0 = inv(max(xs), max(zs))
    box = (lo0, la0, lo1, la1)
    wedge = Polygon(pts)
    stats = {'city': key, 'tier': cfg.get('tier', 1), 'main': main, 'az': cfg['az'], 'corners': corners}

    air = max(140.0, 0.2 * d)  # rule 10: nothing stands in the air round the camera
    cam_pt = shapely.Point(cam.c[0], cam.c[2])

    def in_view(cx, cz):
        dx, dz = cx - cam.c[0], cz - cam.c[2]
        along = dx * cam.fwd[0] + dz * cam.fwd[2]
        if along < 25: return False, 0
        across = dx * cam.right[0] + dz * cam.right[2]
        return abs(across / along) < math.tan(half), along

    faces = []  # (depth, kind, pts3d (n,3), facing azimuth or -1, tint)

    # --- landmarks: every catalog landmark in view
    replaced = set()
    lms = []
    for pid, p in CAT.items():
        x, z = f(p['lng'], p['lat'])
        ok, along = in_view(x, z)
        if ok and along < T['far'] and (pid == main or math.hypot(x - cam.c[0], z - cam.c[2]) > air):
            lms.append(pid)
            for r in p.get('replaces', []):
                replaced.add(r.replace('way/', 'w').replace('relation/', 'r').replace('node/', 'n'))
    # rule 13: the main landmark is the hero: another landmark that would stand taller on screen than it (and than about
    # 60% of the cell) goes
    main_px = hF / d * cam.f
    keep = []
    for pid in lms:
        if pid == main:
            keep.append(pid); continue
        p = CAT[pid]
        x, z = f(p['lng'], p['lat'])
        ok, along = in_view(x, z)
        mm = landmark_mesh(pid, f)
        if not mm: continue
        top = float(max(v[:, 1].max() for v, _, _, _ in mm))
        if top / max(along, 1) * cam.f > max(1.15 * main_px, 0.62 * REF_H) and along < d: continue
        # rule 12 for landmarks too (2.32): one between the camera and the hero, nearer than it, that would cover its
        # foot goes, so the hero is seen whole
        if main and along < d - (wF * 0.65 + 10) * 0.5:
            across = (x - cam.c[0]) * cam.right[0] + (z - cam.c[2]) * cam.right[2]
            allv2 = np.vstack([v for v, _, _, _ in mm])
            half_lm = 0.5 * float(max(np.ptp(allv2[:, 0]), np.ptp(allv2[:, 2])))
            lane = 12 + (wF * 0.65 + 10 - 12) * along / d
            foot_y0 = HZ * REF_H + cam.c[1] / d * cam.f
            if abs(across) < lane + half_lm and HZ * REF_H - (top - cam.c[1]) / max(along, 1) * cam.f < foot_y0 - 2:
                continue
        keep.append(pid)
    lms = keep
    stats['landmarks'] = lms

    # --- buildings
    tab, groups = overture.read('buildings/building', box, ['id', 'height', 'num_floors', 'class', 'geometry', 'sources'])
    rows = tab.to_pylist() if tab is not None else []
    stats['buildings_read'] = len(rows)
    blds = []
    for r in rows:
        src = (r.get('sources') or [{}])[0].get('record_id') or ''
        if src.split('@')[0] in replaced: continue
        g = geom_local(W.loads(r['geometry']), f)
        for poly in polys(g):
            c = poly.centroid
            ok, along = in_view(c.x, c.y)
            if not ok or along > T['far']: continue
            if poly.distance(cam_pt) < air: continue  # rule 10
            h = height_of(r)
            px_h = h / along * cam.f
            px_w = math.sqrt(max(poly.area, 1)) / along * cam.f
            if px_h < 0.7 and px_w < 1.5: continue  # rule 2
            tol = max(0.8, along / cam.f * T['tol'])  # rule 3: about a pixel (two in tier 2)
            p2 = Polygon(poly.exterior).simplify(tol, preserve_topology=True)
            if p2.is_empty or p2.area < 1: continue
            if len(p2.exterior.coords) > 9 and px_w < 6: p2 = p2.minimum_rotated_rectangle
            blds.append((p2, h, along))
    stats['buildings_kept'] = len(blds)
    if cfg.get('tier', 1) == 2:
        merged = lowpoly(blds)  # rule 15: a low-poly town of coarse blocks
        blds = []
    # rule 4: merge touching buildings of about the same height, cell by cell
    cells = {}
    for p, h, along in blds:
        hb = T['hb0'] + along * T['hbk']
        k = (int(p.centroid.x // T['cell']), int(p.centroid.y // T['cell']), int(h // hb))
        cells.setdefault(k, []).append((p, h))
    merged = merged if cfg.get('tier', 1) == 2 else []
    for k, items in cells.items():
        if len(items) == 1:
            merged.append(items[0]); continue
        u = unary_union([p.buffer(0.6, join_style=2) for p, _ in items]).buffer(-0.6, join_style=2)
        hs = [h for _, h in items]
        for q in polys(u):
            q = Polygon(q.exterior)
            if q.area < 2: continue
            c = q.centroid
            ok, along = in_view(c.x, c.y)
            q = q.simplify(max(0.8, along / cam.f * T['tol']), preserve_topology=True)
            hh = max([h for p, h in items if p.intersects(q)] or [float(np.median(hs))]) if len(items) < 40 else float(np.median(hs))
            merged.append((q, hh))
    # rule 12: the view to the main landmark (or the city's focus) is kept clear: a plain building between the camera and
    # it, nearer than it, whose top would cover any of it, goes
    half_w = wF * 0.65 + 10
    corridor = Polygon([(cam.c[0] + cam.right[0] * 12, cam.c[2] + cam.right[2] * 12),
                        (cam.c[0] - cam.right[0] * 12, cam.c[2] - cam.right[2] * 12),
                        (-cam.right[0] * half_w, -cam.right[2] * half_w), (cam.right[0] * half_w, cam.right[2] * half_w)])
    foot_y = HZ * REF_H + cam.c[1] / d * cam.f
    kept, cleared = [], 0
    for q, h in merged:
        if q.intersects(corridor):
            cq = q.centroid
            ok, along = in_view(cq.x, cq.y)
            if along < d - half_w * 0.5 and HZ * REF_H - (h - cam.c[1]) / max(along, 1) * cam.f < foot_y - 2:
                cleared += 1
                continue
        kept.append((q, h))
    merged = kept
    # rule 17: no wall of a tower right in front: a plain block nearer than the focus whose top would stand in the top
    # sixth of the cell goes (a near tower would fill the view, the wide cell's most of all)
    kept, giants = [], 0
    for q, h in merged:
        cq = q.centroid
        ok, along = in_view(cq.x, cq.y)
        if ok and along < d and HZ * REF_H - (h - cam.c[1]) / max(along, 1) * cam.f < REF_H / 6:
            giants += 1
            continue
        kept.append((q, h))
    merged = kept
    stats['giants_cleared'] = giants
    stats['cleared_for_view'] = cleared
    stats['blocks'] = len(merged)
    blocks = []  # each block once: its footprint on the ground and its height; the app rebuilds its walls and roof
    for q, h in merged:
        ring = np.array(q.exterior.coords)[:-1]
        if len(ring) < 3: continue
        if Polygon(ring).exterior.is_ccw: ring = ring[::-1]
        blocks.append((ring, float(h)))

    # --- landmark faces
    for pid in lms:
        m = landmark_mesh(pid, f)
        if not m: continue
        for v, fs, name, col in m:
            tri = v[fs]
            nrm = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
            ln = np.linalg.norm(nrm, axis=1)
            ok = ln > 1e-9
            nrm[ok] /= ln[ok, None]
            ctr = tri.mean(1)
            vis = ((cam.c - ctr) * nrm).sum(1) > 0
            kind = KINDS['lm_glass'] if ('glass' in name or 'window' in name) else KINDS['lm_light'] if any(
                k in name for k in ('neon', 'lamp', 'light')) else KINDS['lm']
            for j in np.nonzero(vis & ok)[0]:
                k2 = KINDS['lm_roof'] if nrm[j][1] > 0.6 and kind == KINDS['lm'] else kind
                faz = math.degrees(math.atan2(nrm[j][0], -nrm[j][2])) % 360 if abs(nrm[j][1]) < 0.9 else -1
                faces.append((k2, tri[j], faz, col + (1,) if pid == main else col))  # the hero: marked, never hazed

    # --- the ground: water (with the ocean), parks, roads
    ground = []
    view = wedge.intersection(sbox(min(xs), min(zs), max(xs), max(zs)))
    for kind, theme, cols, keep in [
        ('water', 'base/water', ['subtype', 'class', 'geometry'], lambda r: r['subtype'] in ('ocean', 'water', 'lake', 'river', 'canal', 'reservoir', 'pond', 'lagoon') or r['class'] in ('bay', 'river', 'harbour', 'basin', 'water', 'lake', 'riverbank', 'canal', 'reservoir')),
        ('park', 'base/land_use', ['subtype', 'class', 'geometry'], lambda r: r['subtype'] in ('park', 'recreation', 'golf', 'cemetery', 'horticulture') or r['class'] in ('park', 'garden', 'grass', 'forest', 'wood', 'recreation_ground', 'cemetery', 'golf_course', 'village_green', 'meadow')),
    ]:
        t, _ = overture.read(theme, box, cols)
        if t is None: continue
        acc = []
        for r in t.to_pylist():
            if not keep(r): continue
            g = geom_local(W.loads(r['geometry']), f)
            if g.geom_type not in ('Polygon', 'MultiPolygon'): continue
            acc.append(g.intersection(view))
        if not acc: continue
        u = unary_union(acc).simplify(T['gtol'])
        for p in polys(u):
            if p.area < 30: continue
            ground.append((KINDS[kind], p))
    t, _ = overture.read('transportation/segment', box, ['subtype', 'class', 'geometry'])
    lamps = []
    centre = []  # the main roads' centre lines (their width), for the cars at night (2.32)
    if t is not None:
        widths = {'motorway': 22, 'trunk': 18, 'primary': 14, 'secondary': 11, 'tertiary': 9}
        lines = []
        for r in t.to_pylist():
            if r['subtype'] != 'road' or r['class'] not in T['roads']: continue
            g = geom_local(W.loads(r['geometry']), f)
            lines.append(g.buffer(widths[r['class']] / 2, cap_style=2).intersection(view))
            centre.append((widths[r['class']], g))
            for t_ in np.arange(0, g.length, T['lamp']):
                p = g.interpolate(t_)
                ok, along = in_view(p.x, p.y)
                if ok and along < 3000: lamps.append((p.x, 0.0, p.y))
        if lines:
            u = unary_union(lines).simplify(2.0)
            for p in polys(u):
                if p.area > 40: ground.append((KINDS['road'], p))
    stats['ground'] = len(ground)

    # --- project everything; painter's order; sub-pixel and hidden faces out
    W2 = ASPECT * REF_H
    proj = []
    for kind, p3, faz, col in faces:
        s, z = cam.project(p3)
        if (z < 2).any(): continue
        s = s + np.array([W2 / 2, 0])
        area = 0.5 * abs(np.dot(s[:, 0], np.roll(s[:, 1], 1)) - np.dot(s[:, 1], np.roll(s[:, 0], 1)))
        if area < T['fmin']: continue  # rule 6
        if s[:, 0].max() < 0 or s[:, 0].min() > W2 or s[:, 1].max() < 0 or s[:, 1].min() > REF_H: continue
        dist = float(np.linalg.norm(p3.mean(0) - cam.c))
        proj.append((dist, kind, s, faz, col))
    gproj = []
    for kind, p in ground:
        ring = np.array(p.exterior.coords)
        p3 = np.c_[ring[:, 0], np.zeros(len(ring)), ring[:, 1]]
        s, z = cam.project(p3)
        if (z < 2).all(): continue
        # a ground polygon that crosses behind the camera: clip it to what's in front first
        if (z < 2).any():
            front = Polygon(ring).intersection(Polygon([(cam.c[0] + cam.fwd[0] * 2 + cam.right[0] * 1e5, cam.c[2] + cam.fwd[2] * 2 + cam.right[2] * 1e5),
                                                        (cam.c[0] + cam.fwd[0] * 2 - cam.right[0] * 1e5, cam.c[2] + cam.fwd[2] * 2 - cam.right[2] * 1e5),
                                                        (cam.c[0] + cam.fwd[0] * 1e5, cam.c[2] + cam.fwd[2] * 1e5)]))
            for q in polys(front):
                ring = np.array(q.exterior.coords)
                p3 = np.c_[ring[:, 0], np.zeros(len(ring)), ring[:, 1]]
                s, z = cam.project(p3)
                gproj.append((kind, s + np.array([W2 / 2, 0])))
            continue
        gproj.append((kind, s + np.array([W2 / 2, 0])))
    for ring, h in blocks:
        p3 = np.c_[ring[:, 0], np.zeros(len(ring)), ring[:, 1]]
        s, z = cam.project(p3)
        if (z < 2).any(): continue
        s = s + np.array([W2 / 2, 0])
        if Polygon(s).area < 0.05 and h / max(1.0, float(np.mean(z))) * cam.f < 1: continue
        fl = block_faces(s, h, cam.c[1], cam.f, look)
        if not fl: continue
        vis_area = sum(0.5 * abs(np.dot(p[:, 0], np.roll(p[:, 1], 1)) - np.dot(p[:, 1], np.roll(p[:, 0], 1))) for _, p, _, _ in fl)
        if vis_area < T['fmin'] * 2: continue
        xs_ = np.concatenate([p[:, 0] for _, p, _, _ in fl]); ys_ = np.concatenate([p[:, 1] for _, p, _, _ in fl])
        if xs_.max() < 0 or xs_.min() > W2 or ys_.max() < 0 or ys_.min() > REF_H: continue
        cen = np.r_[ring.mean(0)[0], h / 2, ring.mean(0)[1]]
        dist = float(np.linalg.norm(np.array([cen[0], cen[1], cen[2]]) - cam.c))
        proj.append((dist, -1, s, h, fl))  # a block: kind -1, its footprint on screen, its height, its faces
    proj.sort(key=lambda t: -t[0])
    # rule 7: occlusion at twice the size
    S = 2
    ids = Image.new('I', (int(W2 * S), REF_H * S), 0)
    dr = ImageDraw.Draw(ids)
    for i, (dist, kind, s, faz, col) in enumerate(proj):
        if kind == -1:
            for _, p, _, _ in col: dr.polygon([tuple(v) for v in (p * S)], fill=i + 1)
        else:
            dr.polygon([tuple(v) for v in (s * S)], fill=i + 1)
    idarr = np.asarray(ids)
    vis, counts = np.unique(idarr, return_counts=True)
    # street lamps: their distance from the camera, and only those not hidden behind a nearer face
    lp = []
    if lamps:
        s, z = cam.project(np.array(lamps))
        for (x, y), zz, p3 in zip(s, z, lamps):
            x += W2 / 2
            if not (0 <= x < W2 and HZ * REF_H <= y < REF_H): continue
            dist = float(np.linalg.norm(np.array(p3) - cam.c))
            under = int(idarr[min(idarr.shape[0] - 1, int(y * S)), min(idarr.shape[1] - 1, int(x * S))])
            if under > 0 and proj[under - 1][0] < dist * 0.98: continue
            lp.append((x, y, dist))
    stats['lamps_hidden'] = len(lamps) - len(lp)
    shown = set(int(v) - 1 for v, c in zip(vis, counts) if v > 0 and c >= T['occ'])
    before = len(proj)
    proj = [p for i, p in enumerate(proj) if i in shown]
    stats['faces_before_occlusion'] = before
    stats['faces'] = len(proj)
    # rule 16, at screen size: the ground simplified to 0.6 px and its specks dropped; footprints simplified to half a
    # pixel; a block showing less than 6 px dropped
    g2 = []
    for kind, s in gproj:
        p = Polygon(s).buffer(0).simplify(0.6)
        for q in polys(p):
            if q.area >= 3.0: g2.append((kind, np.array(q.exterior.coords)))
    gproj = g2
    p2 = []
    for dist, kind, s, faz, col in proj:
        if kind == -1:
            vis_a = sum(0.5 * abs(np.dot(q[:, 0], np.roll(q[:, 1], 1)) - np.dot(q[:, 1], np.roll(q[:, 0], 1))) for _, q, _, _ in col)
            if vis_a < 6.0: continue
            p = Polygon(s).simplify(0.5)
            if p.is_empty or len(p.exterior.coords) < 4: continue
            s = np.array(p.exterior.coords)[:-1]
        p2.append((dist, kind, s, faz, col))
    proj = p2
    stats['faces'] = len(proj)
    stats['blocks_kept'] = sum(1 for p in proj if p[1] == -1)
    stats['ground_polys'] = len(gproj)

    # the cars' roads (2.32): each main road's centre line in view, 20 m to 2.5 km off, simplified to half a pixel
    traffic = []
    for wdt, g in centre:
        for ln in (g.geoms if hasattr(g, 'geoms') else [g]):
            if ln.geom_type != 'LineString' or ln.length < 5: continue
            ln = shapely.segmentize(ln, 25.0)
            c2 = np.array(ln.coords)
            sp, zz = cam.project(np.c_[c2[:, 0], np.zeros(len(c2)), c2[:, 1]])
            sp = sp + np.array([W2 / 2, 0])
            ok = (zz > 20) & (zz < 2500) & (sp[:, 0] > -30) & (sp[:, 0] < W2 + 30)
            run = []
            for v, o in zip(sp, ok):
                if o: run.append(v); continue
                if len(run) >= 2: traffic.append((wdt, run))
                run = []
            if len(run) >= 2: traffic.append((wdt, run))
    t2 = []
    for wdt, run in traffic:
        ls = shapely.LineString(run).simplify(0.5)
        if ls.length >= 3: t2.append((wdt, np.array(ls.coords)))
    traffic = t2
    stats['traffic_lines'] = len(traffic)
    sil, base = terrain_silhouette(lon0, lat0, cam, inv, look)
    scene = {'city': key, 'w': W2, 'h': REF_H, 'faces': proj, 'ground': gproj, 'hills': sil, 'lamps': lp, 'lamp_dist': 'euclid',
             'cam': {'hc': cam.c[1], 'dist': d, 'look': look, 'fn': FN}, 'traffic': traffic}
    stats['lamps'] = len(lp)
    stats['bytes'] = len(pack3(scene))
    stats['cam'] = {'height_m': round(cam.c[1]), 'distance_m': round(d), 'look': look,
                    'view_deg': round(2 * math.degrees(math.atan(0.5 / FN)), 1)}
    return scene, stats


def pack(scene):
    """The scene as the app would ship it: quantized, delta-coded, deflated."""
    b = io.BytesIO()
    b.write(struct.pack('<HHHH', int(scene['w']), scene['h'], len(scene['faces']), len(scene['ground'])))
    def pts(s):
        q = np.round(s * 4).astype(np.int32)  # quarter-pixel steps at the reference size
        b.write(struct.pack('<H', len(q)))
        prev = np.array([0, 0])
        for v in q:
            dlt = v - prev
            b.write(struct.pack('<hh', int(np.clip(dlt[0], -32768, 32767)), int(np.clip(dlt[1], -32768, 32767))))
            prev = v
    for dist, kind, s, faz, col in scene['faces']:
        wall = kind == KINDS['wall']
        b.write(struct.pack('<BBBB', kind, 255 if faz < 0 else int(faz / 360 * 254), min(255, int(math.log1p(dist) * 28)),
                            0 if col is None or wall else (int(math.degrees(math.atan2(col[2] - col[1], col[0] - col[1])) % 360 / 360 * 255))))
        if wall:  # a wall's width and height in metres, for its grid of windows
            w_, h_ = col if isinstance(col, tuple) else (0, 0)
            b.write(struct.pack('<BB', min(255, round(w_ / 2)), min(255, round(h_ / 2))))
        pts(s)
    for kind, s in scene['ground']:
        b.write(struct.pack('<B', kind))
        pts(s)
    for line in scene['hills']:
        b.write(struct.pack('<H', len(line)))
        for x, y in line: b.write(struct.pack('<hh', int(x * 4), int(np.clip(y * 4, -32768, 32767))))
    b.write(struct.pack('<H', len(scene['lamps'])))
    for x, y, z in scene['lamps']: b.write(struct.pack('<hhB', int(x * 4), int(y * 4), min(255, int(math.log1p(z) * 28))))
    return zlib.compress(b.getvalue(), 9)


if __name__ == '__main__':
    import pickle
    cities = json.load(open(os.path.join(HERE, 'cities.json')))
    only = sys.argv[1:]
    for key, cfg in cities.items():
        if only and key not in only: continue
        scene, stats = bake(key, cfg)
        pickle.dump(scene, open(os.path.join(OUT, key + '.pkl'), 'wb'))
        open(os.path.join(OUT, key + '.bin'), 'wb').write(pack(scene))
        json.dump(stats, open(os.path.join(OUT, key + '.json'), 'w'), indent=1)
        print(json.dumps({k: v for k, v in stats.items() if k != 'landmarks'}), len(stats['landmarks']), 'landmarks', flush=True)


def _varint(b, v):
    v = (v << 1) ^ (v >> 63)  # zigzag
    while True:
        x = v & 0x7F
        v >>= 7
        if v:
            b.write(bytes([x | 0x80]))
        else:
            b.write(bytes([x]))
            return


def _uvar(b, v): _varint(b, int(v))


def pack2(scene):
    """The scene file the app ships (format 2): half-pixel points, zigzag variable-length deltas, deflated. See README."""
    import palette as PAL
    b = io.BytesIO()
    b.write(b'WSC2')
    b.write(bytes([2, int(scene.get('tier', 1))]))
    b.write(struct.pack('<HHHH', int(round(scene['w'])), int(scene['h']), int(scene['cam']['look']) % 360,
                        int(min(65535, scene['cam']['hc']))))
    q = 2.0
    last = [0, 0]

    def pts(s):
        qq = np.round(np.asarray(s, float) * q).astype(np.int64)
        _uvar(b, len(qq))
        _varint(b, int(qq[0][0] - last[0])); _varint(b, int(qq[0][1] - last[1]))
        last[0], last[1] = int(qq[0][0]), int(qq[0][1])
        prev = qq[0]
        for v in qq[1:]:
            _varint(b, int(v[0] - prev[0])); _varint(b, int(v[1] - prev[1]))
            prev = v
    _uvar(b, len(scene['hills']))
    for line in scene['hills']:
        pts(np.clip(np.asarray(line, float), -8000, 8000))
    _uvar(b, len(scene['ground']))
    for kind, s in scene['ground']:
        b.write(bytes([kind]))
        pts(s)
    _uvar(b, len(scene['faces']))
    for dist, kind, s, faz, col in scene['faces']:
        b.write(bytes([kind, 255 if faz < 0 else int(faz / 360 * 254) % 255, min(255, int(math.log1p(dist) * 28))]))
        if kind == KINDS['wall']:
            w_, h_ = col if isinstance(col, tuple) else (0, 0)
            b.write(bytes([min(255, round(w_ / 2)), min(255, round(h_ / 2))]))
        elif kind in (KINDS['lm'], KINDS['lm_glass'], KINDS['lm_light'], KINDS['lm_roof']):
            L, C, h = PAL.lch(col[:3]) if col is not None else (0, 0, 0)
            b.write(bytes([int(h / 360 * 255) % 256, min(255, int(C * 800)), 1 if (col is not None and len(col) == 4) else 0]))
        pts(s)
    _uvar(b, len(scene['lamps']))
    px, py = 0, 0
    for x, y, dist in scene['lamps']:
        xi, yi = int(round(x * q)), int(round(y * q))
        _varint(b, xi - px); _varint(b, yi - py)
        px, py = xi, yi
        b.write(bytes([min(255, int(math.log1p(dist) * 28))]))
    return zlib.compress(b.getvalue(), 9)


def lowpoly(blds, G=45.0):
    """Rule 15, tier 2: the town as coarse blocks: a 45 m grid, each cell with enough building in it a block as tall as
    its buildings on average (to 4 m), runs of equal cells along a row joined into one."""
    cells = {}
    for p, h, along in blds:
        c = p.centroid
        k = (int(math.floor(c.x / G)), int(math.floor(c.y / G)))
        a = p.area
        e = cells.setdefault(k, [0.0, 0.0])
        e[0] += a
        e[1] += h * a
    rows = {}
    for (i, j), (a, ha) in cells.items():
        cov = a / (G * G)
        if cov < 0.15: continue
        h = max(4.0, round(ha / a / 4.0) * 4.0)
        rows.setdefault(j, []).append((i, h, min(0.92, math.sqrt(cov * 1.3))))
    out = []
    for j, cs in rows.items():
        cs.sort()
        run = None
        for i, h, side in cs + [(None, None, None)]:
            if run and i is not None and i == run[1] + 1 and h == run[2]:
                run[1] = i; run[3] = max(run[3], side)
                continue
            if run:
                i0, i1, hh, sd = run
                inset = G * (1 - sd) / 2
                out.append((sbox(i0 * G + inset, j * G + inset, (i1 + 1) * G - inset, (j + 1) * G - inset), hh))
            run = [i, i, h, side] if i is not None else None
    return out


def block_faces(s, h, hc, f, look):
    """A block's visible faces from its footprint on screen (s, n x 2, x from the left of the 3:1 frame) and its height:
    what the app does too. The camera is level, so every vertical edge stays vertical: a corner's top is straight above
    its foot, as far above the horizon as the block's height over the camera's, scaled by how far below it the foot is.
    Returns [(kind, points, facing azimuth, (width, height) in metres)], walls far to near, then the roof."""
    W2 = ASPECT * REF_H
    hz = HZ * REF_H
    dy = np.maximum(s[:, 1] - hz, 0.25)
    Z = hc * f / dy
    X = (s[:, 0] - W2 / 2) * Z / f
    top = hz - (h - hc) * dy / hc
    area = 0.5 * np.sum(X * np.roll(Z, -1) - np.roll(X, -1) * Z)
    out = []
    n = len(s)
    for i in range(n):
        k = (i + 1) % n
        ex, ez = X[k] - X[i], Z[k] - Z[i]
        nx, nz = (ez, -ex) if area > 0 else (-ez, ex)
        mx, mz = (X[i] + X[k]) / 2, (Z[i] + Z[k]) / 2
        if nx * (-mx) + nz * (-mz) <= 0: continue
        faz = (look + math.degrees(math.atan2(nx, nz))) % 360
        pts = np.array([[s[i][0], s[i][1]], [s[k][0], s[k][1]], [s[k][0], top[k]], [s[i][0], top[i]]])
        out.append((math.hypot(mx, mz), 0, pts, faz, (math.hypot(ex, ez), h)))
    out.sort(key=lambda t: -t[0])
    faces = [(kind, pts, faz, dims) for _, kind, pts, faz, dims in out]
    if h < hc - 1:
        faces.append((1, np.c_[s[:, 0], top], -1, None))
    return faces


def pack3(scene):
    """The scene file the app ships (format 3): blocks once (footprint and height), landmark faces, the ground, the
    hills; zigzag variable-length deltas, deflated. See README."""
    import palette as PAL
    b = io.BytesIO()
    b.write(b'WSC3')
    b.write(bytes([4, int(scene.get('tier', 1))]))
    b.write(struct.pack('<HHHHH', int(round(scene['w'])), int(scene['h']), int(scene['cam']['look']) % 360,
                        int(min(65535, round(scene['cam']['hc'] * 4))), int(round(scene['cam'].get('fn', FN0) * 10000))))
    last = [0, 0]

    def pts(s, q):
        qq = np.round(np.asarray(s, float) * q).astype(np.int64)
        _uvar(b, len(qq))
        _varint(b, int(qq[0][0] - last[0])); _varint(b, int(qq[0][1] - last[1]))
        last[0], last[1] = int(qq[0][0]), int(qq[0][1])
        prev = qq[0]
        for v in qq[1:]:
            _varint(b, int(v[0] - prev[0])); _varint(b, int(v[1] - prev[1]))
            prev = v
    _uvar(b, len(scene['hills']))
    for line in scene['hills']:
        pts(np.clip(np.asarray(line, float), -8000, 8000), 2)
    _uvar(b, len(scene['ground']))
    for kind, s in scene['ground']:
        b.write(bytes([kind]))
        pts(s, 2)
    _uvar(b, len(scene['faces']))
    for dist, kind, s, faz, col in scene['faces']:
        db = min(255, int(math.log1p(dist) * 28))
        if kind == -1:  # a block: its footprint in quarter pixels, its height in half metres
            b.write(bytes([0, db]))
            _uvar(b, int(round(faz * 2)))
            pts(s, 2)
            continue
        b.write(bytes([1 + kind, 255 if faz < 0 else int(faz / 360 * 254) % 255, db]))
        L, C, h = PAL.lch(col[:3]) if col is not None else (0, 0, 0)
        b.write(bytes([int(h / 360 * 255) % 256, min(255, int(C * 800)), 1 if (col is not None and len(col) == 4) else 0]))
        pts(s, 2)
    # (format 4, 2.32) the cars' roads: count, then each: its width in metres and its centre line's points
    _uvar(b, len(scene.get('traffic', [])))
    for wdt, s in scene.get('traffic', []):
        b.write(bytes([min(255, int(wdt))]))
        pts(s, 2)
    return zlib.compress(b.getvalue(), 9)
