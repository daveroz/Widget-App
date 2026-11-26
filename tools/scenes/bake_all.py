"""Bakes every place in the app's list: tier 1 for the major cities (majors.txt), tier 2 for the rest; the hand-set
cameras of cities.json where there are, the automatic camera elsewhere. Resumable; three at a time."""
import os, sys, json, unicodedata, re, pickle, traceback, time
from concurrent.futures import ProcessPoolExecutor, as_completed
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'assets')
os.makedirs(OUT, exist_ok=True)

def slug(name, cc):
    s = unicodedata.normalize('NFD', name.split(',')[0].strip().lower())
    s = ''.join(ch for ch in s if unicodedata.category(ch) != 'Mn')
    s = re.sub(r'[^a-z ]', '', s)
    s = re.sub(r'\s+', ' ', s).strip().replace(' ', '-')
    return s + '-' + cc.lower()

CURATED = {'Paris|FR': 'paris', 'Sydney|AU': 'sydney', 'New York|US': 'new-york', 'London|GB': 'london', 'Tokyo|JP': 'tokyo',
           'Charlotte|US': 'charlotte', 'Mexico City|MX': 'mexico-city', 'Dubai|AE': 'dubai', 'Barcelona|ES': 'barcelona',
           'Istanbul|TR': 'istanbul', 'San Francisco|US': 'san-francisco'}

# a major city's iconic landmark where its tallest isn't (the camera still chosen automatically)
HERO = {'Amsterdam|NL': 'amsterdam-rijksmuseum', 'Seattle|US': 'sea-space-needle', 'Singapore|SG': 'singapore-marina-bay-sands',
        'Rome|IT': 'rome-colosseum', 'Athens|GR': 'athens-acropolis', 'Prague|CZ': 'prague-st-vitus-cathedral',
        'Munich|DE': 'munich-frauenkirche', 'Moscow|RU': 'moscow-kremlin-saviour-tower',
        'Abu Dhabi|AE': 'abu-dhabi-sheikh-zayed-grand-mosque', 'Muscat|OM': 'muscat-sultan-qaboos-grand-mosque',
        'Jerusalem|IL': 'jerusalem-dome-of-the-rock', 'Tbilisi|GE': 'tbilisi-holy-trinity-cathedral',
        'Berlin|DE': 'berlin-brandenburg-gate', 'Washington|US': 'dc-us-capitol', 'Toronto|CA': 'tor-cn-tower',
        'Chicago|US': 'chi-willis-tower', 'Kuala Lumpur|MY': 'kl-petronas-towers', 'Los Angeles|US': 'la-us-bank-tower'}

FOCUS = json.load(open(os.path.join(HERE, 'focus.json'))) if os.path.exists(os.path.join(HERE, 'focus.json')) else {}

def jobs():
    majors = set(l.strip() for l in open(os.path.join(HERE, 'majors.txt')) if l.strip() and not l.startswith('#'))
    cur = json.load(open(os.path.join(HERE, 'cities.json')))
    out = []
    for line in open('/home/user/Widget-App/app/src/main/assets/cities.txt'):
        p = line.strip().split('|')
        if len(p) < 5: continue
        name, cc, tz, lat, lon = p[0], p[1], p[2], float(p[3]), float(p[4])
        k = name + '|' + cc
        lat, lon = FOCUS.get(slug(name, cc), (lat, lon))  # the city's centre (Natural Earth), not the time zone's point
        cfg = {'tier': 1 if k in majors else 2, 'lat': lat, 'lon': lon}
        if k in CURATED: cfg.update(cur[CURATED[k]])
        elif k in HERO: cfg['main'] = HERO[k]
        if cfg['tier'] == 2: cfg.update({'height': 40.0, 'width': 60.0})
        out.append((slug(name, cc), cfg))
    return out

def run(item):
    key, cfg = item
    import bake
    t = time.time()
    try:
        scene, stats = bake.bake(key, cfg)
        data = bake.pack3(scene)
        open(os.path.join(OUT, key + '.bin'), 'wb').write(data)
        pickle.dump(scene, open(os.path.join(OUT, key + '.pkl'), 'wb'))
        stats['bytes_v2'] = len(data)
        stats['seconds'] = round(time.time() - t)
        return key, stats, None
    except Exception:
        return key, None, traceback.format_exc()[-800:]

if __name__ == '__main__':
    todo = [j for j in jobs() if not os.path.exists(os.path.join(OUT, j[0] + '.bin'))]
    only = sys.argv[1:]
    if only: todo = [j for j in todo if j[0] in only or str(j[1]['tier']) in only]
    todo.sort(key=lambda j: j[1]['tier'])
    print(len(todo), 'to bake', flush=True)
    with ProcessPoolExecutor(3) as ex, open(os.path.join(OUT, '_stats.jsonl'), 'a') as log:
        for fut in as_completed([ex.submit(run, j) for j in todo]):
            key, stats, err = fut.result()
            if err:
                print('FAIL', key, err.replace('\n', ' | ')[-300:], flush=True)
                log.write(json.dumps({'city': key, 'error': err}) + '\n')
            else:
                print('OK', key, stats['tier'], stats['faces'], stats['bytes_v2'], stats['seconds'], 's', flush=True)
                log.write(json.dumps(stats) + '\n')
            log.flush()
