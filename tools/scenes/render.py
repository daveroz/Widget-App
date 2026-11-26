"""Draws a baked city scene in the app's palette (the video's style) for a light, as the app would."""
import math, random, sys, os, pickle, json
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import palette as P

HERE = os.path.dirname(os.path.abspath(__file__))
KIND = {'wall': 0, 'roof': 1, 'water': 2, 'park': 3, 'road': 4, 'lm': 5, 'lm_glass': 6, 'lm_light': 7, 'land': 8, 'lm_roof': 9}

def tint(base, col):
    if col is None: return base
    L, C, h = P.lch(col[:3])
    if C < 0.12: return base
    bl, bc, bh = P.lch(base)
    k = min(1.0, (C - 0.12) / 0.1) * 0.3  # a gold, a red steel: a hint of it, staying in the palette
    dh = ((h - bh + 180) % 360) - 180
    return P.from_lch(bl * (1 + 0.08 * k), bc + (C * 0.7 - bc) * k, bh + dh * k)

def crown(d, x, y, r, col, lit, rnd):
    pts = []
    for i in range(9):
        a = (i + rnd.random() * 0.4) / 9 * math.tau
        k = r * (1 if i % 2 == 0 else 0.78)
        pts.append((x + math.cos(a) * k * 1.1, y + math.sin(a) * k * 0.9))
    d.polygon(pts, fill=col)
    d.ellipse([x - r * 0.9, y - r * 0.85, x + r * 0.35, y + r * 0.1], fill=lit)

def lit_edge(faz, sun): return False

def render(scene, e, morning=0.0, sun_left=True, aspect=1.0, height=280, ss=2):
    L = P.light(e, morning)
    sky, c = L['sky'], L['city']
    night, lights = L['night'], L['lights']
    W2, H = scene['w'], scene['h']
    k = height / H * ss
    w = int(round(aspect * H * k)); h = int(round(H * k))
    x0 = (W2 - aspect * H) / 2  # crop the middle
    def S(p): return [((x - x0) * k, y * k) for x, y in p]
    img = Image.new('RGB', (w, h))
    d = ImageDraw.Draw(img)
    hz = 0.64 * h
    for y in range(h):  # the sky (Sky.java's stops), the ground below
        t = y / h
        if t < 0.64 * 0.56: col = P.mix(sky[0], sky[1], t / (0.64 * 0.56))
        elif t < 0.61: col = P.mix(sky[1], sky[2], (t - 0.64 * 0.56) / (0.61 - 0.64 * 0.56))
        else: col = sky[2]
        d.line([(0, y), (w, y)], fill=col)
    far_haze = P.mix(c[P.FAR], sky[2], 0.45)
    # the hills, far layer then near
    for i, line in enumerate(reversed(scene['hills'])):
        col = P.mix(c[P.FAR], sky[2], 0.55 if i == 0 else 0.35)
        pts = S(line) + [((line[-1][0] - x0) * k, hz + 4 * ss), ((line[0][0] - x0) * k, hz + 4 * ss)]
        d.polygon(pts, fill=col)
    # the ground
    land_far, land_near = P.mix(c[P.FAR], sky[2], 0.3), P.mix(c[P.STREET], c[P.FAR], 0.15)
    for y in range(int(hz), h):
        t = (y - hz) / (h - hz)
        d.line([(0, y), (w, y)], fill=P.mix(land_far, land_near, min(1, t * 2.2)))
    water_far, water_near = P.mix(sky[2], sky[1], 0.12), P.mix(P.mix(sky[2], sky[1], 0.55), c[P.NEAR], 0.3)
    road = P.mix(c[P.STREET], c[P.EDGE], 0.18)
    park = P.mix(c[P.FRONT], c[P.STREET], 0.35)
    def gcol(kind, y):
        t = max(0, min(1, (y / k - 0.64 * H) / (0.36 * H) * 2.2))
        if kind == KIND['water']: return P.mix(water_far, water_near, t)
        if kind == KIND['park']: return P.mix(P.mix(park, far_haze, 0.5), park, t)
        return P.mix(P.mix(road, far_haze, 0.6), road, t)
    for kind in (KIND['park'], KIND['water'], KIND['road']):
        for gk, s in scene['ground']:
            if gk != kind: continue
            pts = S(s)
            ymid = sum(p[1] for p in pts) / len(pts)
            d.polygon(pts, fill=gcol(kind, max(hz, ymid)))
    # trees in the parks, as pseudo faces in depth order
    rnd = random.Random(7)
    items = []
    hc = scene['cam']['hc']
    f = 0.5 / math.tan(math.radians(20)) * H
    for gk, s in scene['ground']:
        if gk != KIND['park']: continue
        xs, ys = s[:, 0], s[:, 1]
        area = 0.5 * abs(np.dot(xs, np.roll(ys, 1)) - np.dot(ys, np.roll(xs, 1)))
        n = int(min(60, area / 14))
        from matplotlib.path import Path as MP
        mp = MP(s)
        for _ in range(n):
            x, y = rnd.uniform(xs.min(), xs.max()), rnd.uniform(ys.min(), ys.max())
            if y <= 0.64 * H + 1 or not mp.contains_point((x, y)): continue
            z = hc * f / (y - 0.64 * H)
            items.append((z, 'tree', (x, y, max(0.5, 9.0 / z * f))))
    sun = scene['cam']['look'] + (-115 if sun_left else 115)  # from the left (or right), a little towards the camera
    for dist, kind, s, faz, col in scene['faces']:
        if kind == -1:  # a block: its faces (walls far to near, then the roof) in its place
            for j, (k2, pts2, faz2, dims) in enumerate(col):
                items.append((dist - j * 1e-3, 'face', (k2, pts2, faz2, dims)))
        else:
            items.append((dist, 'face', (kind, s, faz, col)))
    if lights > 0.05:
        # the street lamps go in depth order with the buildings, so a nearer building hides them (their distance from
        # the camera, worked out from where they are on screen and how deep they are)
        for x, y, v in scene['lamps']:
            dist = v if scene.get('lamp_dist') == 'euclid' else v * math.sqrt(1 + ((x - W2 / 2) / f) ** 2 + ((y - 0.64 * H) / f) ** 2)
            items.append((dist, 'lamp', (x, y, dist)))
    items.sort(key=lambda t: -t[0])
    lit_c, shade_c, roof_c = c[P.LIT], c[P.SHADE], P.mix(c[P.NEAR], c[P.RIM], 0.22)
    tree_c, tree_lit = P.mix(c[P.FRONT], c[P.NEAR], 0.2), P.mix(c[P.FRONT], c[P.LIT], 0.35 * (1 - 0.6 * night))
    da = ImageDraw.Draw(img, 'RGBA')  # one pass, far to near: a face's windows right after it, lamps in their place
    # the night's glow (the reference's bloom): windows and lamps also go on this layer, bigger and orange-pink; every
    # face clears what's behind it here too, so a nearer building hides the glow of what it stands in front of
    glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    from matplotlib.path import Path as MP
    lamp_c = (255, 214, 236)
    warm = [(255, 220, 140), (255, 196, 110), (255, 178, 122), (255, 232, 176), (255, 205, 150)]
    share = (0.42 if night > 0.5 else 0.3) * lights
    def lerp2(a, b, t): return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
    for n_it, (dist, typ, it) in enumerate(items):
        fog = (1 - math.exp(-dist / 4200)) * (0.62 - 0.25 * night)
        if typ == 'tree':
            x, y, r = it
            rr = r * 0.35 * k
            if rr < 0.6: continue
            crown(da, (x - x0) * k, y * k - rr, rr, P.mix(tree_c, far_haze, fog), P.mix(tree_lit, far_haze, fog), rnd)
            continue
        if typ == 'lamp':
            x, y, z = it
            rad = max(0.35, min(1.4, 150 / max(z, 1))) * ss
            X, Y = (x - x0) * k, y * k
            if -5 < X < w + 5:
                da.ellipse([X - rad * 2.6, Y - rad * 0.9, X + rad * 2.6, Y + rad * 0.9], fill=(255, 196, 150, int(70 * lights)))  # its pool
                da.ellipse([X - rad * 0.55, Y - rad * 1.6, X + rad * 0.55, Y - rad * 0.5], fill=lamp_c + (int(235 * lights),))
                gd.ellipse([X - rad * 2.2, Y - rad * 2.6, X + rad * 2.2, Y + rad * 0.6], fill=(255, 92, 170, int(150 * lights)))
            continue
        kind, s, faz, col = it
        if kind in (KIND['roof'], KIND['lm_roof']): base = roof_c
        elif faz < 0: base = c[P.NEAR]
        else:
            lit = math.cos(math.radians(faz - sun))
            base = lit_c if lit > 0.1 else shade_c if lit < -0.1 else c[P.NEAR]
        if kind in (KIND['lm'], KIND['lm_roof']): base = tint(base, col)
        if kind == KIND['lm_glass']: base = P.mix(base, shade_c, 0.4)
        if isinstance(col, tuple) and len(col) == 4: fog = 0.0  # the main landmark stands clear of the haze
        colr = P.mix(base, far_haze, fog)
        pts = S(s)
        da.polygon(pts, fill=colr)
        if lights > 0.05: gd.polygon(pts, fill=(0, 0, 0, 0))
        if kind == KIND['wall'] and len(pts) == 4 and abs(pts[2][0] - pts[3][0]) > 1.5 * ss and abs(pts[0][1] - pts[3][1]) > 8 * ss:  # the rim along a tall one's top
            da.line([pts[3], pts[2]], fill=P.mix(colr, c[P.RIM], 0.55 * (1 - fog)), width=max(1, int(ss * 0.8)))
        if lights <= 0.05: continue
        if kind == KIND['lm_glass']:  # a landmark's glass: lit as windows in a grid, not as one glowing slab
            xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
            if max(xs) - min(xs) < 1.5 * ss or max(ys) - min(ys) < 1.5 * ss: continue
            mp = MP(pts)
            rw = random.Random(hash((round(s[0][0]), round(s[0][1]))))
            stepx, stepy = 3.0 * ss, 3.6 * ss
            gx0 = math.floor(min(xs) / stepx) * stepx
            gy0 = math.floor(min(ys) / stepy) * stepy
            yy = gy0
            while yy < max(ys):
                xx = gx0
                while xx < max(xs):
                    cx_, cy_ = xx + stepx * 0.5, yy + stepy * 0.5
                    if mp.contains_point((cx_, cy_)) and rw.random() < share * 0.8:
                        wc = rw.choice(warm)
                        da.rectangle([cx_ - 0.8 * ss, cy_ - 0.8 * ss, cx_ + 0.8 * ss, cy_ + 0.8 * ss], fill=wc + (int(230 * lights * (1 - 0.6 * fog)),))
                        gd.ellipse([cx_ - 2 * ss, cy_ - 1.6 * ss, cx_ + 2 * ss, cy_ + 1.6 * ss], fill=(255, 118, 104, int(70 * lights)))
                    xx += stepx
                yy += stepy
            continue
        if kind != KIND['wall'] or len(pts) != 4 or not isinstance(col, tuple): continue
        # a wall's windows: a grid, a floor every 3.3 m and a bay every 3.2 m, each lit or not (the same each time)
        wm, hm = col
        floors, bays = max(1, int(hm / 3.3)), max(1, int(wm / 3.2))
        hpx = math.hypot(pts[3][0] - pts[0][0], pts[3][1] - pts[0][1])
        wpx = math.hypot(pts[1][0] - pts[0][0], pts[1][1] - pts[0][1])
        merged = 1
        while floors > 1 and hpx / floors < 1.6 * ss: floors = (floors + 1) // 2; merged *= 2  # too fine: fewer
        while bays > 1 and wpx / bays < 1.8 * ss: bays = (bays + 1) // 2; merged *= 2
        if hpx < 1.0 * ss or wpx < 0.8 * ss: continue
        rw = random.Random(hash((round(s[0][0]), round(s[0][1]), round(s[1][0]))))
        busy = rw.choice([0.15, 0.35, 0.6, 0.85, 1.0, 1.0])  # each building its own: offices dark, flats lit
        fshare = share * busy * (1.0 if merged == 1 else 0.7 if merged <= 4 else 0.45)
        a_core = int(255 * min(1, lights * 1.1) * (1 - 0.6 * fog))
        span = 0.56 if merged == 1 else 0.34  # a merged cell holds a single window, smaller in it
        for i in range(floors):
            if hm > 8 and i == 0 and floors > 2: continue  # the ground floor: shops, dark or lit below
            for j in range(bays):
                if rw.random() > fshare: continue
                cu, cv = j + 0.5 + (rw.random() - 0.5) * (0.4 if merged > 1 else 0), i + 0.52
                u0, u1 = (cu - span / 2) / bays, (cu + span / 2) / bays
                v0, v1 = (cv - 0.22) / floors, (cv + 0.22) / floors
                q = []
                for u, v in ((u0, v0), (u1, v0), (u1, v1), (u0, v1)):
                    bot, top = lerp2(pts[0], pts[1], u), lerp2(pts[3], pts[2], u)
                    q.append(lerp2(bot, top, v))
                wc = rw.choice(warm)
                da.polygon(q, fill=wc + (a_core,))
                gx = sum(p[0] for p in q) / 4; gy = sum(p[1] for p in q) / 4
                gr = max(1.0 * ss, min(5 * ss, math.hypot(q[1][0] - q[0][0], q[1][1] - q[0][1]) * 1.2))
                gd.ellipse([gx - gr, gy - gr * 0.8, gx + gr, gy + gr * 0.8], fill=(255, 118, 104, int(85 * lights)))
    if lights > 0.05:  # the bloom: the glow layer blurred and laid over by screen
        g = glow.filter(ImageFilter.GaussianBlur(2.0 * ss))
        ga = np.asarray(g).astype(np.float32) / 255
        base_a = np.asarray(img.convert('RGB')).astype(np.float32) / 255
        add = ga[:, :, :3] * ga[:, :, 3:4] * (0.75 + 0.25 * night)
        out = 1 - (1 - base_a) * (1 - np.clip(add, 0, 1))
        img = Image.fromarray((out * 255).astype(np.uint8))
    return img.resize((int(w / ss), int(h / ss)), Image.LANCZOS)

if __name__ == '__main__':
    keys = sys.argv[1:]
    for key in keys:
        sc = pickle.load(open(os.path.join(HERE, 'baked', key + '.pkl'), 'rb'))
        frames = [render(sc, e, morning=m, aspect=1.0) for e, m in [(-1.5, 0), (4, 0), (25, 0.5), (-20, 0)]]
        wide = render(sc, -1.5, aspect=3.0)
        sheet = Image.new('RGB', (max(wide.width, 4 * frames[0].width + 18), wide.height + frames[0].height + 6), 'white')
        sheet.paste(wide, ((sheet.width - wide.width) // 2, 0))
        for i, fr in enumerate(frames): sheet.paste(fr, (i * (fr.width + 6), wide.height + 6))
        sheet.save(os.path.join(HERE, 'baked', key + '.jpg'), quality=90)
        print(key, 'rendered')
