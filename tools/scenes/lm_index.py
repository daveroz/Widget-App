"""Every catalog landmark's height and footprint (from its model), once, for choosing a city's hero."""
import json, glob, os, numpy as np, trimesh, bake
from concurrent.futures import ProcessPoolExecutor
def one(pid):
    pl = bake.CAT[pid]
    md = bake.MODEL_FILE.get(pl['model'])
    if not md: return pid, None
    p = os.path.join(bake.REPO, md['file'])
    if not os.path.exists(p): return pid, None
    try:
        sc = trimesh.load(p)
        b = sc.bounds
        return pid, {'h': float(b[1][1] - b[0][1]) * pl.get('scale', 1.0), 'w': float(max(b[1][0] - b[0][0], b[1][2] - b[0][2])),
                     'lng': pl['lng'], 'lat': pl['lat'], 'name': pl.get('name', pid)}
    except Exception:
        return pid, None
if __name__ == '__main__':
    with ProcessPoolExecutor(3) as ex:
        out = {k: v for k, v in ex.map(one, list(bake.CAT)) if v}
    json.dump(out, open('lm_index.json', 'w'))
    print(len(out))
