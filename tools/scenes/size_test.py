"""How small a baked city can get: the same scene packed smarter."""
import pickle, glob, zlib, io, struct, math, os
import numpy as np
import shapely
from shapely.geometry import Polygon
import bake

def varint(b, v):
    v = (v << 1) ^ (v >> 31)  # zigzag
    while True:
        x = v & 0x7F; v >>= 7
        if v: b.write(bytes([x | 0x80]))
        else: b.write(bytes([x])); return

def area(s): return 0.5 * abs(np.dot(s[:, 0], np.roll(s[:, 1], 1)) - np.dot(s[:, 1], np.roll(s[:, 0], 1)))

def pack2(sc, q=2.0, min_area=0.5, ground_tol=1.0, lamps=False):
    b = io.BytesIO()
    faces = [f for f in sc['faces'] if area(f[2]) >= min_area]
    b.write(struct.pack('<HH', len(faces), len(sc['ground'])))
    def pts(s):
        qq = np.round(s * q).astype(np.int64)
        varint(b, len(qq)); prev = np.array([0, 0])
        for v in qq:
            varint(b, int(v[0] - prev[0])); varint(b, int(v[1] - prev[1])); prev = v
    prevp = None
    for dist, kind, s, faz, col in faces:
        b.write(bytes([kind, 255 if faz < 0 else int(faz / 360 * 254), min(255, int(math.log1p(dist) * 28))]))
        if kind == 0 and isinstance(col, tuple): b.write(bytes([min(255, round(col[0] / 2)), min(255, round(col[1] / 2))]))
        pts(s)
    for kind, s in sc['ground']:
        p = Polygon(s).simplify(ground_tol)
        if p.is_empty or p.area < 2: continue
        b.write(bytes([kind])); pts(np.array(p.exterior.coords))
    for line in sc['hills']:
        varint(b, len(line)); prev = 0
        for x, y in line: v = int(round(y * q)); varint(b, v - prev); prev = v
    if lamps:
        for x, y, z in sc['lamps']: varint(b, int(x * q)); varint(b, int(y * q))
    raw = b.getvalue()
    return len(zlib.compress(raw, 9)), len(faces)

tot = [0, 0, 0]
for p in sorted(glob.glob('baked/*.pkl')):
    sc = pickle.load(open(p, 'rb'))
    a = len(bake.pack(sc))
    b2, n2 = pack2(sc)
    b3, n3 = pack2(sc, q=1.0, min_area=1.0, ground_tol=1.5)
    tot[0] += a; tot[1] += b2; tot[2] += b3
    print(f"{os.path.basename(p)[:-4]:14} now {a // 1024:3} KB ({len(sc['faces'])} faces) | smart {b2 // 1024:3} KB ({n2}) | smarter {b3 // 1024:3} KB ({n3})")
n = len(glob.glob('baked/*.pkl'))
print('average KB:', [round(t / n / 1024, 1) for t in tot])
