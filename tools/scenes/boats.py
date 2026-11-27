"""Packs the boats the scenes sail on their big rivers and canals (2.43) from Kenney's Watercraft Kit (CC0, kenney.nl;
version 2.1): each model's faces with their colours (sampled from the kit's colour map), its hull bottom dropped (never
seen from above) and the rest simplified to what still reads at a few pixels on a widget, then normalised (its length 1
along +Z, the bow forward, its waterline at 0) and written to app/src/main/assets/boats.bin.

  python3 boats.py <kit's raw folder with the .glb files> [preview.png]

File (little-endian): 'BTS1', count; then each boat: kind (0 a river freighter or tow boat, 1 a tour boat or houseboat,
2 a small boat), where it sails (1 rivers, 2 canals, 3 both), its length in metres, vertex count, face count, the
vertices (3 floats each), the faces (3 shorts each) and their colours (3 bytes each).
"""
import io, os, struct, sys
import numpy as np
import trimesh
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'app', 'src', 'main', 'assets', 'boats.bin')

# name, kind, where (1 rivers, 2 canals, 3 both), length (m), at most this many faces. Since 2.43 only the two tow
# boats, after the user: a river push boat's real size (25 m long; the app makes them 16 m on a canal), in the kit's own
# proportions (about 12 m across, its wheelhouse 13 m up)
BOATS = [
    ('boat-tow-a', 0, 3, 25, 180), ('boat-tow-b', 0, 3, 25, 180),
]


def load(path):
    """One mesh, with each face's colour from the colour map at its texture coordinates."""
    sc = trimesh.load(path)
    parts = []
    for node in sc.graph.nodes_geometry:
        T, gname = sc.graph[node]
        g = sc.geometry[gname].copy()
        g.apply_transform(T)
        tex = np.asarray(g.visual.material.baseColorTexture.convert('RGB')) if getattr(g.visual.material, 'baseColorTexture', None) is not None else None
        uv = g.visual.uv
        cols = np.zeros((len(g.faces), 3), np.uint8)
        if tex is not None and uv is not None:
            c = uv[g.faces].mean(1)
            h, w = tex.shape[:2]
            x = np.clip((c[:, 0] % 1) * w, 0, w - 1).astype(int)
            y = np.clip((1 - c[:, 1] % 1) * h, 0, h - 1).astype(int)
            cols = tex[y, x]
        parts.append((np.asarray(g.vertices), np.asarray(g.faces), cols))
    v = np.vstack([p[0] for p in parts])
    off = np.cumsum([0] + [len(p[0]) for p in parts[:-1]])
    f = np.vstack([p[1] + o for p, o in zip(parts, off)])
    c = np.vstack([p[2] for p in parts])
    return v, f, c


def normals(v, f):
    n = np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])
    ln = np.linalg.norm(n, axis=1)
    ln[ln == 0] = 1
    return n / ln[:, None]


def components(f, nv):
    """The faces in groups that share vertices (each separate part of the model)."""
    parent = np.arange(nv)
    def root(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for a, b, c in f:
        ra, rb, rc = root(a), root(b), root(c)
        parent[rb] = ra
        parent[rc] = ra
    groups = {}
    for i, t in enumerate(f): groups.setdefault(root(t[0]), []).append(i)
    return [np.array(g) for g in groups.values()]


def simplify(v, f, c, most):
    """What can't show at a few pixels goes: the hull bottom (never seen from above), then the small parts standing apart
    (railings, ropes, antennas, lamps: under 6% of the boat's length), then the smallest faces left, until at most
    `most` are left. Nothing is reshaped, so the boat keeps its lines and colours."""
    keep = normals(v, f)[:, 1] > -0.3
    f, c = f[keep], c[keep]
    m = trimesh.Trimesh(v, f, process=False)
    m.merge_vertices()
    L = float(np.ptp(m.vertices, 0).max())
    parts = components(np.asarray(m.faces), len(m.vertices))
    keep = np.zeros(len(m.faces), bool)
    for comp in parts:
        ext = np.ptp(m.vertices[m.faces[comp]].reshape(-1, 3), 0).max()
        if ext >= 0.06 * L: keep[comp] = True
    ff, cc = np.asarray(m.faces)[keep], c[keep]
    if len(ff) > most:
        area = np.linalg.norm(np.cross(m.vertices[ff[:, 1]] - m.vertices[ff[:, 0]], m.vertices[ff[:, 2]] - m.vertices[ff[:, 0]]), axis=1)
        top = np.argsort(-area)[:most]
        ff, cc = ff[np.sort(top)], cc[np.sort(top)]
    used = np.unique(ff)
    remap = -np.ones(len(m.vertices), int)
    remap[used] = np.arange(len(used))
    return np.asarray(m.vertices)[used], remap[ff], cc


def normalise(v):
    """Length 1 along +Z (the kit's bow forward), centred, its waterline at 0 (a tenth of its height under)."""
    ext = np.ptp(v, 0)
    if ext[0] > ext[2]:  # its length along X: turn it
        v = v[:, [2, 1, 0]] * np.array([-1.0, 1.0, 1.0])  # a turn, not a mirror (keeps the faces outward)
    v = v - np.array([(v[:, 0].max() + v[:, 0].min()) / 2, v[:, 1].min(), (v[:, 2].max() + v[:, 2].min()) / 2])
    L = np.ptp(v[:, 2])
    v = v / L  # the kit's boats face +Z already
    v[:, 1] -= 0.1 * v[:, 1].max()
    return v


def preview(models, path):
    """Each boat as packed, flat shaded, from a three-quarter view a little above."""
    s = 220
    im = Image.new('RGB', (s * 4, s * ((len(models) + 3) // 4)), (40, 44, 60))
    d = ImageDraw.Draw(im)
    a, el = np.radians(35), np.radians(25)
    R = np.array([[np.cos(a), 0, -np.sin(a)], [0, 1, 0], [np.sin(a), 0, np.cos(a)]])
    E = np.array([[1, 0, 0], [0, np.cos(el), -np.sin(el)], [0, np.sin(el), np.cos(el)]])
    for i, (name, v, f, c) in enumerate(models):
        p = v @ R.T @ E.T
        n = normals(p, f)
        order = np.argsort(-p[f].mean(1)[:, 2])
        ox, oy = (i % 4) * s + s / 2, (i // 4) * s + s * 0.6
        for j in order:
            if n[j, 2] < 0: continue  # turned away (the viewer looks along +Z)
            sh = 0.7 + 0.3 * max(0.0, n[j] @ np.array([-0.4, 0.8, -0.4]))
            col = tuple(int(min(255, x * sh)) for x in c[j])
            d.polygon([(ox + q[0] * s * 0.75, oy - q[1] * s * 0.75) for q in p[f[j]]], fill=col)
        d.text(((i % 4) * s + 6, (i // 4) * s + 6), f'{name} {len(f)}', fill=(230, 230, 240))
    im.save(path)


def main():
    src = sys.argv[1]
    b = io.BytesIO()
    b.write(b'BTS1')
    b.write(bytes([len(BOATS)]))
    models = []
    for name, kind, where, length, most in BOATS:
        v, f, c = load(os.path.join(src, name + '.glb'))
        n0 = len(f)
        v, f, c = simplify(v, f, c, most)
        v = normalise(v)
        models.append((name, v, f, c))
        print(f'{name}: {n0} faces -> {len(f)}')
        b.write(bytes([kind, where, length]))
        b.write(struct.pack('<HH', len(v), len(f)))
        b.write(np.asarray(v, '<f4').tobytes())
        b.write(np.asarray(f, '<u2').tobytes())
        b.write(np.asarray(c, np.uint8).tobytes())
    open(OUT, 'wb').write(b.getvalue())
    print(OUT, len(b.getvalue()), 'bytes')
    if len(sys.argv) > 2: preview(models, sys.argv[2])


if __name__ == '__main__':
    main()
