"""Reads Overture Maps (the open world map: buildings with heights, roads, water, land use) for a small box anywhere.
The release's files are spatially sorted: an index of every row group's bounding box (built once, cached) lets a city's
box be read with a few small ranged requests."""
import pyarrow.fs as pafs, pyarrow.parquet as pq, pyarrow as pa, os, json, urllib.parse
from concurrent.futures import ThreadPoolExecutor

REL = 'overturemaps-us-west-2/release/2026-11-18.0/'
HERE = os.path.dirname(os.path.abspath(__file__))

def fs():
    p = urllib.parse.urlparse(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy'))
    return pafs.S3FileSystem(anonymous=True, region='us-west-2', connect_timeout=20, request_timeout=60,
                             proxy_options={'scheme': 'http', 'host': p.hostname, 'port': p.port})

def index(kind):
    """[(path, row_group, xmin, ymin, xmax, ymax)] for theme=../type=.. ('buildings/building', 'base/water', ...)."""
    cache = os.path.join(HERE, 'idx_' + kind.replace('/', '_') + '.json')
    if os.path.exists(cache): return json.load(open(cache))
    theme, typ = kind.split('/')
    f = fs()
    files = [x.path for x in f.get_file_info(pafs.FileSelector(REL + f'theme={theme}/type={typ}/')) if x.path.endswith('.parquet')]
    def one(path):
        md = pq.ParquetFile(path, filesystem=fs()).metadata
        cols = {md.row_group(0).column(j).path_in_schema: j for j in range(md.row_group(0).num_columns)}
        out = []
        for i in range(md.num_row_groups):
            rg = md.row_group(i)
            s = {k: rg.column(cols['bbox.' + k]).statistics for k in ('xmin', 'ymin', 'xmax', 'ymax')}
            out.append((path, i, s['xmin'].min, s['ymin'].min, s['xmax'].max, s['ymax'].max))
        return out
    with ThreadPoolExecutor(24) as ex:
        rows = [r for part in ex.map(one, files) for r in part]
    json.dump(rows, open(cache, 'w'))
    return rows

def read(kind, box, columns):
    """The rows of `kind` whose bbox lies in box (west, south, east, north)."""
    w, s, e, n = box
    hits = [(p, i) for p, i, x0, y0, x1, y1 in index(kind) if x0 <= e and x1 >= w and y0 <= n and y1 >= s]
    by_file = {}
    for p, i in hits: by_file.setdefault(p, []).append(i)
    def one(item):
        p, groups = item
        t = pq.ParquetFile(p, filesystem=fs()).read_row_groups(groups, columns=columns + ['bbox'])
        b = t.column('bbox').combine_chunks()
        xmin, ymin, xmax, ymax = (b.field(k).to_numpy(zero_copy_only=False) for k in ('xmin', 'ymin', 'xmax', 'ymax'))
        import numpy as np
        m = (xmax >= w) & (xmin <= e) & (ymax >= s) & (ymin <= n)
        return t.filter(pa.array(m))
    with ThreadPoolExecutor(12) as ex:
        parts = list(ex.map(one, by_file.items()))
    return pa.concat_tables(parts) if parts else None, len(hits)
