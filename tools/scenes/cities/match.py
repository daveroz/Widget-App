import json, math, unicodedata, re, csv
ne = [f['properties'] for f in json.load(open('ne_places.geojson'))['features']]
def norm(s):
    s = unicodedata.normalize('NFD', s.lower()); s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    return re.sub(r'[^a-z]', '', s)
def km(a, b, c, d):
    p = math.pi / 180
    return 6371 * math.acos(min(1, math.sin(a*p)*math.sin(c*p) + math.cos(a*p)*math.cos(c*p)*math.cos((b-d)*p)))
rows = []
for line in open('/home/user/Widget-App/app/src/main/assets/cities.txt'):
    p = line.strip().split('|')
    if len(p) < 5: continue
    name, cc, tz, lat, lon = p[0], p[1], p[2], float(p[3]), float(p[4])
    best, bd, how = None, 1e9, ''
    for q in ne:
        if (norm(q['name']) == norm(name) or norm(q.get('nameascii') or '') == norm(name)) and (q.get('iso_a2') == cc):
            dd = km(lat, lon, q['latitude'], q['longitude'])
            if dd < 60 and dd < bd: best, bd, how = q, dd, 'name'
    if not best:
        for q in ne:
            dd = km(lat, lon, q['latitude'], q['longitude'])
            if dd < 25 and (best is None or q['pop_max'] > best['pop_max'] or dd < 3): 
                if best is None or dd < 3 or q['pop_max'] > best['pop_max']: best, bd, how = q, dd, 'near'
    rows.append({'name': name, 'cc': cc, 'tz': tz, 'lat': lat, 'lon': lon,
                 'ne': best['name'] if best else '', 'pop': best['pop_max'] if best else -1,
                 'cls': best['featurecla'] if best else '', 'how': how, 'km': round(bd, 1) if best else ''})
json.dump(rows, open('matched.json', 'w'), indent=0)
print(sum(1 for r in rows if r['pop'] >= 0), 'matched of', len(rows))
print('unmatched:', [r['name'] + '|' + r['cc'] for r in rows if r['pop'] < 0])
