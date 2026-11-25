"""Worldly's app icon (2.05): a crystal sphere cut with a meridian and the equator, on deep space.
Transparent bevelled crystal; an icy glint at the top left with an anamorphic streak, a warm one at the bottom right
with halation; haze, a few out-of-focus specks, film grain. Writes the adaptive icon's two layers and the legacy
icons into the given res directory, and a preview."""
import sys, os
import numpy as np
from PIL import Image

S = 1024                       # the adaptive icon's 108 dp canvas
cx = cy = S / 2
R = 0.255 * S                  # the sphere (inside the 66 dp safe zone)
yy, xx = np.mgrid[0:S, 0:S].astype(np.float32) + 0.5
dx, dy = xx - cx, yy - cy
d = np.hypot(dx, dy)
th = np.arctan2(dy, dx)
rng = np.random.default_rng(20251125)

ICE = np.array([0.80, 0.90, 1.00]); WHITE = np.ones(3); WARM = np.array([1.0, 0.63, 0.31])
HAL = np.array([1.0, 0.35, 0.18]); STREAK = np.array([0.55, 0.78, 1.0])

def g(v, s):
    return np.exp(-(v / s) ** 2)

def blur(a, s):
    """About a gaussian of sigma s: three box blurs, along both axes."""
    r = max(1, int(s * 0.9))
    out = a.copy()
    for _ in range(3):
        for ax in (0, 1):
            c = np.cumsum(np.pad(out, [(r + 1, r) if i == ax else (0, 0) for i in range(out.ndim)], mode='edge'), axis=ax)
            hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + out.shape[ax]), axis=ax)
            lo = np.take(c, np.arange(0, out.shape[ax]), axis=ax)
            out = (hi - lo) / (2 * r + 1)
    return out

def col(mask, colour):
    return mask[..., None] * colour[None, None, :]

# ---------------------------------------------------------------- the background: deep space, haze, specks, grain
bg = np.zeros((S, S, 3), np.float32) + np.array([0.016, 0.021, 0.034])
bg += col(np.exp(-((dx / (0.62 * S)) ** 2 + ((dy + 0.06 * S) / (0.55 * S)) ** 2)), np.array([0.05, 0.065, 0.095]))
bg += col(np.exp(-(((dx - 0.22 * S) / (0.3 * S)) ** 2 + ((dy - 0.24 * S) / (0.3 * S)) ** 2)), np.array([0.045, 0.026, 0.014]))
specks = np.zeros((S, S, 3), np.float32)
for i in range(22):
    px, py = rng.uniform(0.06, 0.94, 2) * S
    rad = rng.uniform(2.5, 10)
    c = ICE if rng.random() < 0.6 else WARM
    specks += col(g(np.hypot(xx - px, yy - py), rad) * rng.uniform(0.04, 0.14), c)
bg += specks

# ---------------------------------------------------------------- the crystal sphere
inside = np.clip(R - d + 0.5, 0, 1)                      # anti-aliased disc
diag = (dx + dy) / (2 * R)                               # -1 at the top left, +1 at the bottom right
body = col(inside, np.array([0.0, 0.0, 0.0]))
glass = np.array([0.12, 0.15, 0.21])[None, None, :] * (1 - (diag[..., None] + 1) / 2) \
        + np.array([0.026, 0.03, 0.045])[None, None, :] * ((diag[..., None] + 1) / 2)
body_a = 0.9 * inside
light = np.zeros((S, S, 3), np.float32)
# the room reflected in it: a soft band across its upper left, and a warm glow inside its lower right
perp = (dx - dy) / (2 * R)
band = g(diag + 0.48, 0.13) * np.clip(1 - np.abs(perp) * 1.4, 0, 1) * inside
light += col(band * 0.06, ICE)
light += col(g(np.hypot(dx - 0.42 * R, dy - 0.42 * R), 0.5 * R) * inside * 0.09, WARM)
# the bevel: its outer edge and a fainter inner one, lit icy towards the top left and warm towards the bottom right
w_ice = np.clip(np.cos(th + 0.75 * np.pi), 0, 1) ** 3
w_warm = np.clip(np.cos(th - 0.25 * np.pi), 0, 1) ** 4
bev = 0.11 * R
edge = g(d - R + 1.6, 2.8)
inner = g(d - (R - bev), 2.0) * 0.55
thick = np.clip((d - (R - bev)) / bev, 0, 1) * inside
rimcol = col(0.34 + 1.3 * w_ice, ICE) + col(1.05 * w_warm, WARM)
light += rimcol * (edge + inner)[..., None]
light += rimcol * (thick ** 2 * 0.22)[..., None]   # the bevel's polished slope, brightest at the edge
# the cuts: a meridian and the equator, each a bright line with its shadow beside it, brighter towards the light
def ell(rx, ry):
    f = (dx / rx) ** 2 + (dy / ry) ** 2 - 1
    gx, gy = 2 * dx / rx ** 2, 2 * dy / ry ** 2
    return f / (np.hypot(gx, gy) + 1e-6)
def cut(de, clip, back=None):
    lit = np.clip(0.3 + 0.7 * (-(dx + dy) / (1.4 * R) + 0.35), 0.15, 1)
    if back is not None: lit = lit * back
    return g(de, 2.6) * lit * clip, g(de - 4.2, 2.3) * clip
clip = np.clip((R - 0.8 * bev) - d, 0, 1)
MR, MZ = (0.40 * R, 0.90 * R), (0.90 * R, 0.17 * R)
dm, dz = ell(*MR), ell(*MZ)
# the facets the two cuts make, each catching a different share of the light (a cut gem, lit from the top left)
inner_m = np.clip(-dm / 1.5 + 0.5, 0, 1)                 # inside the meridian
arc = MZ[1] * np.sqrt(np.clip(1 - (dx / MZ[0]) ** 2, 0, 1))  # the equator's front curve
upper = np.clip((arc - dy) / 1.5 + 0.5, 0, 1)            # above it
left = np.clip(-dx / 1.5 + 0.5, 0, 1)
facet = (upper * ((1 - inner_m) * left * 0.17 + inner_m * 0.11 + (1 - inner_m) * (1 - left) * 0.06)
         + (1 - upper) * ((1 - inner_m) * left * 0.08 + inner_m * 0.05 + (1 - inner_m) * (1 - left) * 0.035))
light += col(facet * clip, ICE) * 0.75
light += col((1 - upper) * (1 - inner_m) * (1 - left) * clip * 0.05, WARM)
for de, back in ((dm, None), (dz, np.where(dy < 0, 0.45, 1.0))):     # the equator's far side, seen through, fainter
    hi, lo = cut(de, clip, back)
    light += col(hi * 1.0, ICE)
    glass = glass * (1 - 0.5 * lo[..., None])

# ---------------------------------------------------------------- the glints
def star(x0, y0, core, halo, spikes_h, spikes_v, tint, halo_col, strength):
    out = np.zeros((S, S, 3), np.float32)
    rr = np.hypot(xx - x0, yy - y0)
    out += col(g(rr, core) * 2.2 * strength, WHITE)
    out += col(g(rr, halo) * 0.55 * strength, halo_col)
    out += col(g(rr, halo * 3.4) * 0.12 * strength, halo_col)
    ax, ay = np.abs(xx - x0), np.abs(yy - y0)
    out += col(g(yy - y0, 1.6) * np.exp(-ax / spikes_h) * 0.9 * strength, tint)
    out += col(g(xx - x0, 1.4) * np.exp(-ay / spikes_v) * 0.6 * strength, halo_col)
    return out
k = 0.7071
ix, iy = cx - R * k, cy - R * k
wx, wy = cx + R * k, cy + R * k
glints = star(ix, iy, 6, 28, 270, 55, STREAK, ICE, 1.0)
glints += col(g(yy - iy, 2.2) * np.exp(-np.abs(xx - ix) / 640) * 0.22, STREAK)   # the anamorphic streak
glints += star(wx, wy, 6, 26, 180, 42, WARM, WARM, 0.85)
glints += col(g(np.hypot(xx - wx, yy - wy) - 58, 13) * 0.07, HAL)                 # halation round the warm one
glints += col(g(np.hypot(xx - wx, yy - wy), 95) * 0.08, WARM)
glints += star(cx, cy - 0.90 * R, 3, 12, 60, 22, ICE, ICE, 0.4)                # a spark where the meridian meets the top
light += glints

# bloom: the brightest light spreads a little into the haze
lum = light.max(axis=2)
light += blur(light * np.clip(lum - 0.6, 0, None)[..., None], 18) * 0.6

# ---------------------------------------------------------------- the two layers, legacy icons, preview
def tone(x):
    return 1 - np.exp(-1.3 * x)

grain = rng.normal(0, 1, (S, S)).astype(np.float32)
bg_t = tone(bg) + grain[..., None] * 0.011
fg_p = glass * body_a[..., None] + light                     # premultiplied light over the glass
fg_p = tone(fg_p) + grain[..., None] * 0.008 * inside[..., None]
lightness = np.clip(tone(light).max(axis=2), 0, 1)
fg_a = np.clip(body_a + (1 - body_a) * lightness, 0, 1)

def chroma(img, amt=0.0022):
    """A touch of colour fringe: red a hair outward, blue a hair inward, from the centre."""
    out = img.copy()
    for ch, s in ((0, 1 + amt), (2, 1 - amt)):
        sx = np.clip(cx + (xx - cx) / s - 0.5, 0, S - 1.001)
        sy = np.clip(cy + (yy - cy) / s - 0.5, 0, S - 1.001)
        x0, y0 = sx.astype(int), sy.astype(int)
        fx, fy = sx - x0, sy - y0
        a = img[..., ch]
        out[..., ch] = (a[y0, x0] * (1 - fx) * (1 - fy) + a[y0, x0 + 1] * fx * (1 - fy)
                        + a[y0 + 1, x0] * (1 - fx) * fy + a[y0 + 1, x0 + 1] * fx * fy)
    return out

fg_p = chroma(fg_p)
comp = bg_t * (1 - fg_a[..., None]) + fg_p
def img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8))
fg_c = np.where(fg_a[..., None] > 1e-4, fg_p / np.maximum(fg_a[..., None], 1e-4), 0)
fg_rgba = np.dstack([np.clip(fg_c, 0, 1), fg_a])
bg_img, fg_img, comp_img = img(bg_t), Image.fromarray((np.clip(fg_rgba, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA'), img(comp)

res = sys.argv[1]
dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for name, f in dens.items():
    n = int(round(108 * f))
    folder = os.path.join(res, 'mipmap-' + name)
    os.makedirs(folder, exist_ok=True)
    bg_img.resize((n, n), Image.LANCZOS).save(os.path.join(folder, 'ic_launcher_background.png'))
    fg_img.resize((n, n), Image.LANCZOS).save(os.path.join(folder, 'ic_launcher_foreground.png'))
    # legacy icons (48 dp): the middle 72 of the 108 dp, a rounded square and a circle
    m = int(round(48 * f))
    crop = comp_img.crop((int(S * 18 / 108), int(S * 18 / 108), int(S * 90 / 108), int(S * 90 / 108))).resize((m, m), Image.LANCZOS)
    for shape, fname in (('square', 'ic_launcher.png'), ('circle', 'ic_launcher_round.png')):
        big = 4 * m
        mask = Image.new('L', (big, big), 0)
        from PIL import ImageDraw
        dr = ImageDraw.Draw(mask)
        if shape == 'circle': dr.ellipse((0, 0, big - 1, big - 1), fill=255)
        else: dr.rounded_rectangle((0, 0, big - 1, big - 1), radius=int(big * 0.22), fill=255)
        mask = mask.resize((m, m), Image.LANCZOS)
        out = crop.convert('RGBA'); out.putalpha(mask)
        out.save(os.path.join(folder, fname))

prev = sys.argv[2]
P = 512
c = comp_img.resize((P, P), Image.LANCZOS)
tiles = []
from PIL import ImageDraw
for shape in ('circle', 'squircle', 'rounded'):
    mask = Image.new('L', (P * 4, P * 4), 0); dr = ImageDraw.Draw(mask)
    inset = int(P * 4 * 18 / 108)
    box = (inset, inset, P * 4 - inset, P * 4 - inset)
    if shape == 'circle': dr.ellipse(box, fill=255)
    elif shape == 'squircle': dr.rounded_rectangle(box, radius=int((box[2] - box[0]) * 0.36), fill=255)
    else: dr.rounded_rectangle(box, radius=int((box[2] - box[0]) * 0.18), fill=255)
    mask = mask.resize((P, P), Image.LANCZOS)
    t = Image.new('RGB', (P, P), (236, 236, 238)); t.paste(c, (0, 0), mask); tiles.append(t)
small = [comp_img.crop((int(S*18/108),)*2 + (int(S*90/108),)*2).resize((s, s), Image.LANCZOS) for s in (48, 72, 96)]
W = P * 3 + 40
sheet = Image.new('RGB', (W, P + 140), (236, 236, 238))
for i, t in enumerate(tiles): sheet.paste(t, (i * (P + 20), 0))
x = 20
for s in small:
    sheet.paste(s, (x, P + 20)); x += s.width + 30
sheet.save(prev)
comp_img.save(os.path.splitext(prev)[0] + '_full.png')
print('ok')
