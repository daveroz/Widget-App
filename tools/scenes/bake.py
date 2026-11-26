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
HMIN = 75.0  # the camera is never lower than this (metres over the landmark's ground)
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


def bake(key, cfg):
    main = cfg.get('main')
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
    ta, tf = (HZ - TOP) / FN, (cfg.get('foot', FOOT) - HZ) / FN
    d = hF / (ta + tf)
    d = max(d, wF * FN / 0.55, cfg.get('min_dist', 260))
    d *= cfg.get('zoom', 1.0)
    hc = d * tf
    if hc < cfg.get('hmin', HMIN):  # rule 9: never lower than HMIN (a short landmark: look down at it a little more, from higher up)
        tf = (max(0.86, cfg.get('foot', FOOT)) - HZ) / FN
        hc = cfg.get('hmin', HMIN)
        d = max(d, hc / tf)
    az = math.radians(cfg['az'])
    cam = Cam(math.sin(az) * d, -math.cos(az) * d, hc, (cfg['az'] + 180) % 360)
    look = (cfg['az'] + 180) % 360
    half = math.atan(ASPECT * 0.5 / FN) + 0.03
    # the box to read: the camera and the view's wedge out to the far limit
    pts = [(cam.c[0], cam.c[2])]
    for k in range(9):
        a = math.radians(look) - half + 2 * half * k / 8
        pts.append((cam.c[0] + math.sin(a) * FAR, cam.c[2] - math.cos(a) * FAR))
    xs, zs = zip(*pts)
    lo0, la1 = inv(min(xs), min(zs))
    lo1, la0 = inv(max(xs), max(zs))
    box = (lo0, la0, lo1, la1)
    wedge = Polygon(pts)
    stats = {'city': key}

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
        if ok and along < FAR and (pid == main or math.hypot(x - cam.c[0], z - cam.c[2]) > air):
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
            if not ok or along > FAR: continue
            if poly.distance(cam_pt) < air: continue  # rule 10
            h = height_of(r)
            px_h = h / along * cam.f
            px_w = math.sqrt(max(poly.area, 1)) / along * cam.f
            if px_h < 0.7 and px_w < 1.5: continue  # rule 2
            tol = max(0.8, along / cam.f * 0.9)  # rule 3: about a pixel
            p2 = Polygon(poly.exterior).simplify(tol, preserve_topology=True)
            if p2.is_empty or p2.area < 1: continue
            if len(p2.exterior.coords) > 9 and px_w < 6: p2 = p2.minimum_rotated_rectangle
            blds.append((p2, h, along))
    stats['buildings_kept'] = len(blds)
    # rule 4: merge touching buildings of about the same height, cell by cell
    cells = {}
    for p, h, along in blds:
        hb = 3 + along * 0.006
        k = (int(p.centroid.x // 160), int(p.centroid.y // 160), int(h // hb))
        cells.setdefault(k, []).append((p, h))
    merged = []
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
            q = q.simplify(max(0.8, along / cam.f * 0.9), preserve_topology=True)
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
    stats['cleared_for_view'] = cleared
    stats['blocks'] = len(merged)
    for q, h in merged:
        ring = np.array(q.exterior.coords)[:-1]
        if Polygon(ring).exterior.is_ccw: ring = ring[::-1]
        n = len(ring)
        for i in range(n):
            a, b = ring[i], ring[(i + 1) % n]
            ex, ez = b[0] - a[0], b[1] - a[1]
            nx, nz = -ez, ex  # the outward normal of a clockwise ring in (x, z)
            mx, mz = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            if nx * (cam.c[0] - mx) + nz * (cam.c[2] - mz) <= 0: continue  # rule 5
            p3 = np.array([[a[0], 0, a[1]], [b[0], 0, b[1]], [b[0], h, b[1]], [a[0], h, a[1]]])
            faz = math.degrees(math.atan2(nx, -nz)) % 360
            faces.append((KINDS['wall'], p3, faz, (math.hypot(ex, ez), h)))  # its size in metres: its windows' grid
        if h < cam.c[1] - 1:
            faces.append((KINDS['roof'], np.array([[x, h, z] for x, z in ring]), -1, None))

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
        u = unary_union(acc).simplify(2.5)
        for p in polys(u):
            if p.area < 30: continue
            ground.append((KINDS[kind], p))
    t, _ = overture.read('transportation/segment', box, ['subtype', 'class', 'geometry'])
    lamps = []
    if t is not None:
        widths = {'motorway': 22, 'trunk': 18, 'primary': 14, 'secondary': 11, 'tertiary': 9}
        lines = []
        for r in t.to_pylist():
            if r['subtype'] != 'road' or r['class'] not in widths: continue
            g = geom_local(W.loads(r['geometry']), f)
            lines.append(g.buffer(widths[r['class']] / 2, cap_style=2).intersection(view))
            for t_ in np.arange(0, g.length, 34):
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
        if area < FACE_MIN_PX2: continue  # rule 6
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
    proj.sort(key=lambda t: -t[0])
    # rule 7: occlusion at twice the size
    S = 2
    ids = Image.new('I', (int(W2 * S), REF_H * S), 0)
    dr = ImageDraw.Draw(ids)
    for i, (dist, kind, s, faz, col) in enumerate(proj):
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
    shown = set(int(v) - 1 for v, c in zip(vis, counts) if v > 0 and c >= OCC_MIN_PX)
    before = len(proj)
    proj = [p for i, p in enumerate(proj) if i in shown]
    stats['faces_before_occlusion'] = before
    stats['faces'] = len(proj)
    stats['ground_polys'] = len(gproj)

    sil, base = terrain_silhouette(lon0, lat0, cam, inv, look)
    scene = {'city': key, 'w': W2, 'h': REF_H, 'faces': proj, 'ground': gproj, 'hills': sil, 'lamps': lp, 'lamp_dist': 'euclid',
             'cam': {'hc': cam.c[1], 'dist': d, 'look': look}}
    stats['lamps'] = len(lp)
    stats['bytes'] = len(pack(scene))
    stats['cam'] = {'height_m': round(cam.c[1]), 'distance_m': round(d), 'look': look}
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
