"""Puts the baked scenes in the app: drops places with almost nothing to show (they keep the drawn scene), copies the
rest to app/src/main/assets/scenes, and sums up."""
import os, json, shutil, glob, pickle, collections
HERE = os.path.dirname(os.path.abspath(__file__))
DST = '/home/user/Widget-App/app/src/main/assets/scenes'
os.makedirs(DST, exist_ok=True)
for f in glob.glob(os.path.join(DST, '*.bin')): os.remove(f)
stats = {}
for line in open(os.path.join(HERE, 'assets', '_stats.jsonl')):
    d = json.loads(line)
    if 'error' not in d: stats[d['city']] = d
kept, dropped, size = collections.Counter(), [], collections.Counter()
for key, d in sorted(stats.items()):
    src = os.path.join(HERE, 'assets', key + '.bin')
    if not os.path.exists(src): continue
    sc = pickle.load(open(os.path.join(HERE, 'assets', key + '.pkl'), 'rb'))
    if len(sc['faces']) + len(sc['ground']) < 30:
        dropped.append(key)
        continue
    shutil.copy(src, os.path.join(DST, key + '.bin'))
    kept[d['tier']] += 1
    size[d['tier']] += os.path.getsize(src)
print('kept', dict(kept), 'dropped', len(dropped), dropped[:20])
for t in sorted(size): print('tier', t, round(size[t] / 1024), 'KB total,', round(size[t] / max(1, kept[t]) / 1024, 1), 'KB each')
print('all', round(sum(size.values()) / 1024 / 1024, 2), 'MB')
