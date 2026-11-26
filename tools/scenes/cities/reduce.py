"""Which of the app's 479 places stay: the rule (UN Degree of Urbanisation, GeoNames, Natural Earth), applied."""
import json, datetime
from zoneinfo import ZoneInfo
rows = json.load(open('matched.json'))
ov = json.load(open('overture_pop.json'))
# Overture's nearest locality was a neighbour for these (the place itself is smaller): known figures
FIX = {'Anguilla|AI': (15000, True), 'Lower Princes|SX': (41000, True), 'St Barthelemy|BL': (10500, True),
       'Marigot|MF': (5700, True), 'Chuuk|FM': (36000, False), 'Kosrae|FM': (6600, False), 'Norfolk|NF': (2200, True),
       'Wallis|WF': (1000, True), 'Fakaofo|TK': (500, True), 'Marquesas|PF': (9300, False), 'Gambier|PF': (1500, False),
       'Kwajalein|MH': (1000, False), 'Metlakatla|US': (1400, False), 'Marengo|US': (800, False), 'Vevay|US': (1600, False),
       'Winamac|US': (2400, False), 'Petersburg|US': (2400, False), 'Thule|GL': (600, False), 'Chagos|IO': (3000, False),
       'Wake|UM': (100, False), 'Midway|UM': (40, False), 'Miquelon|PM': (5800, True), 'Montserrat|MS': (4400, True)}
for r in rows:
    k = r['name'] + '|' + r['cc']
    r['capital'] = r['cls'] in ('Admin-0 capital', 'Admin-0 capital alt')
    r['territory'] = r['cls'] == 'Admin-0 region capital'
    if r['pop'] < 0:
        if k in FIX:
            r['pop'], r['territory'] = FIX[k]
        elif ov.get(k):
            r['pop'] = ov[k][1]  # its nearest town's (a county seat is no territory's capital)
        else:
            r['pop'] = 0
    if k == 'Nauru|NR': r['capital'] = True
    if k in ('Guadalcanal|SB',): r['capital'] = True
# capitals the matching got wrong (a neighbouring village matched, or no capital flag): known figures and status
OVERRIDE = {'Cayman|KY': (35000, 'territory'), 'Faroe|FO': (22000, 'territory'), 'Palau|PW': (11200, 'capital'),
            'Saipan|MP': (43000, 'territory'), 'Rarotonga|CK': (5445, 'territory'), 'Kralendijk|BQ': (12531, 'territory'),
            'St Thomas|VI': (14477, 'territory'), 'Tortola|VG': (12603, 'territory')}
for r in rows:
    o = OVERRIDE.get(r['name'] + '|' + r['cc'])
    if o:
        r['pop'] = o[0]
        r['capital'], r['territory'] = o[1] == 'capital', o[1] == 'territory'

def offsets(tz):
    z = ZoneInfo(tz)
    return tuple(sorted({datetime.datetime(2026, m, 15, 12, tzinfo=z).utcoffset().total_seconds() / 3600 for m in (1, 7)}))
for r in rows:
    p = r['pop']
    if r['capital']: r['keep'], r['why'] = True, 'national capital'
    elif r['territory'] and p >= 5000: r['keep'], r['why'] = True, 'territory capital, a town (5,000+)'
    elif p >= 1_000_000: r['keep'], r['why'] = True, 'main city (1M+)'
    elif p >= 50_000: r['keep'], r['why'] = True, 'city (50,000+)'
    elif p >= 15_000: r['keep'], r['why'] = True, 'small city (15,000+)'
    else: r['keep'], r['why'] = False, 'too small (under 15,000)'
    r['off'] = offsets(r['tz'])
# a world clock's own exception: an offset no kept place has, keeps its biggest place
kept_off = {o for r in rows if r['keep'] for o in r['off']}
for o in sorted({o for r in rows for o in r['off']} - kept_off):
    if o in kept_off: continue
    cands = [r for r in rows if o in r['off'] and not r['keep']]
    best = max(cands, key=lambda r: r['pop'])
    best['keep'], best['why'] = True, 'only place with UTC%+g' % o
    kept_off |= set(best['off'])
json.dump(rows, open('reduced.json', 'w'), indent=0)
keep = {r['name'] + '|' + r['cc'] for r in rows if r['keep']}
src = '/home/user/Widget-App/app/src/main/assets/cities.txt'
lines = [l for l in open(src) if l.strip() and '|'.join(l.split('|')[:2]) in keep]
open(src, 'w').write(''.join(lines))
print('cities.txt now', len(lines))
import collections
print(collections.Counter(r['why'] for r in rows).most_common())
print('keep', sum(r['keep'] for r in rows), 'of', len(rows))
print('dropped:', ', '.join(sorted(r['name'] + ' (' + format(r['pop'], ',') + ')' for r in rows if not r['keep'])))
print('offset keepers:', [(r['name'], r['pop']) for r in rows if r['why'].startswith('only')])
