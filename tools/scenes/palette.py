"""Python twin of the app's Palette.java (2.30): the day's colours as one rule, OKLab blends, the city from the sky."""
import math

def _lin(c):
    v = c / 255
    return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4

def _enc(v):
    v = max(0.0, min(1.0, v))
    s = 12.92 * v if v <= 0.0031308 else 1.055 * v ** (1 / 2.4) - 0.055
    return round(max(0, min(1, s)) * 255)

def lab(c):
    r, g, b = (_lin(x) for x in c)
    l = (0.4122214708*r + 0.5363325363*g + 0.0514459929*b) ** (1/3)
    m = (0.2119034982*r + 0.6806995451*g + 0.1073969566*b) ** (1/3)
    s = (0.0883024619*r + 0.2817188376*g + 0.6299787005*b) ** (1/3)
    return [0.2104542553*l + 0.7936177850*m - 0.0040720468*s, 1.9779984951*l - 2.4285922050*m + 0.4505937099*s,
            0.0259040371*l + 0.7827717662*m - 0.8086757660*s]

def rgb(L, a, b):
    l, m, s = L + 0.3963377774*a + 0.2158037573*b, L - 0.1055613458*a - 0.0638541728*b, L - 0.0894841775*a - 1.2914855480*b
    l, m, s = l ** 3, m ** 3, s ** 3
    return (_enc(4.0767416621*l - 3.3077115913*m + 0.2309699292*s), _enc(-1.2684380046*l + 2.6097574011*m - 0.3413193965*s),
            _enc(-0.0041960863*l - 0.7034186147*m + 1.7076147010*s))

def lch(c):
    L, a, b = lab(c)
    return [L, math.hypot(a, b), math.degrees(math.atan2(b, a)) % 360]

def from_lch(L, C, h):
    return rgb(L, C * math.cos(math.radians(h)), C * math.sin(math.radians(h)))

def hexc(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255)

def mix(a, b, t): return tuple(round(x + (y - x) * t) for x, y in zip(a, b))

NIGHT = [hexc(0x0C0F2E), hexc(0x151B4A), hexc(0x262C6A)]
BLUE_HOUR = [hexc(0x1C1F66), hexc(0x2F2E8A), hexc(0x5648AA)]
DUSK = [hexc(0x5E40A6), hexc(0xA55FCB), hexc(0xEB8ECB)]
GOLDEN = [hexc(0x7470C8), hexc(0xE292B6), hexc(0xFFC48E)]
DAY = [hexc(0x3E7ADC), hexc(0x6EA6EE), hexc(0xBCDCF6)]
WINDOW, FAR_LIGHT, LAMP = hexc(0xF8C08E), hexc(0xE8E2FF), hexc(0xFFD6A6)

def _bell(e, at, w): return math.exp(-((e - at) / w) ** 2)
def _smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)

def weights(e):
    w = [1 - _smooth(-15, -9, e), _bell(e, -8, 2.8), _bell(e, -1.5, 2.6), _bell(e, 5, 3.2), _smooth(6, 20, e)]
    s = sum(w)
    return [v / max(1e-4, s) for v in w]

def _dawn(l, m, stop):
    turn = math.radians((16 if stop == 2 else 10 if stop == 1 else 6) * m)
    a = l[1] * math.cos(turn) - l[2] * math.sin(turn)
    b = l[1] * math.sin(turn) + l[2] * math.cos(turn)
    soft = 1 - 0.1 * m
    return [min(0.97, l[0] + 0.02 * m), a * soft, b * soft]

def sky(e, morning=0.0):
    w = weights(e)
    out = []
    for k in range(3):
        L = A = B = 0
        for i, light in enumerate([NIGHT, BLUE_HOUR, DUSK, GOLDEN, DAY]):
            l = lab(light[k])
            if morning > 0 and i in (2, 3): l = _dawn(l, morning, k)
            L += w[i] * l[0]; A += w[i] * l[1]; B += w[i] * l[2]
        out.append(rgb(L, A, B))
    return out

ROLES = [(0.96, 23, 0.95), (0.89, 19, 0.89), (0.81, 16, 0.82), (0.67, 3, 0.72), (0.72, 13, 0.76), (0.66, 8, 0.71), (1.04, 10, 0.59)]
FAR, LIT, NEAR, SHADE, STREET, FRONT, EDGE, RIM = range(8)

def city(e, morning=0.0):
    s = sky(e, morning)
    top, hor = lch(s[0]), lch(s[2])
    mean = (top[0] + hor[0]) / 2
    day, night = _smooth(4, 18, e), 1 - _smooth(-12, -2, e)
    out = []
    for r in ROLES:
        L = mean * r[0] + 0.085 * night  # the night city lifted to a moonlit blue, so lit windows read against it
        h = max(258, min(296, top[2] - r[1]))
        C = max(0.035 + 0.03 * night, top[1] * r[2] * (1 - 0.28 * day))
        out.append(from_lch(min(0.9, L), C, h))
    out.append(from_lch(min(0.94, mean * 1.34 + 0.03 * night), hor[1] * 0.38, hor[2]))
    return out

def light(e, morning=0.0):
    """Everything a scene needs for the sun e degrees up."""
    return {'sky': sky(e, morning), 'city': city(e, morning), 'night': max(0, min(1, (2 - e) / 8)),
            'lights': max(0, min(1, (7 - e) / 10)), 'e': e}
