"""The contact sheet (every city at the video's dusk) and a widget row (four clocks with their words)."""
import os, json, pickle, sys
from PIL import Image, ImageDraw, ImageFont
import render
HERE = os.path.dirname(os.path.abspath(__file__))
B = os.path.join(HERE, 'baked')
FONT = sys.argv[1] if len(sys.argv) > 1 else None
cities = list(json.load(open(os.path.join(HERE, 'cities.json'))).keys())
names = {'paris': 'Paris', 'sydney': 'Sydney', 'new-york': 'New York', 'london': 'London', 'tokyo': 'Tokyo',
         'charlotte': 'Charlotte', 'mexico-city': 'Mexico City', 'dubai': 'Dubai', 'barcelona': 'Barcelona',
         'istanbul': 'Istanbul', 'san-francisco': 'San Francisco', 'bruges': 'Bruges'}
def font(sz):
    try: return ImageFont.truetype(FONT, sz)
    except Exception: return ImageFont.load_default(sz)
s, g, cols = 280, 8, 4
done = [c for c in cities if os.path.exists(os.path.join(B, c + '.pkl'))]
rows = (len(done) + cols - 1) // cols
for tag, e in (('dusk', -1.5), ('night', -20)):
    sheet = Image.new('RGB', (cols * s + (cols - 1) * g, rows * s + (rows - 1) * g), 'white')
    for i, c in enumerate(done):
        sc = pickle.load(open(os.path.join(B, c + '.pkl'), 'rb'))
        im = render.render(sc, e, aspect=1.0)
        st = json.load(open(os.path.join(B, c + '.json')))
        d = ImageDraw.Draw(im)
        d.text((10, 8), names.get(c, c), fill='white', font=font(18))
        d.text((10, 30), f"{st['faces']} faces · {st['bytes'] // 1024} KB · {len(st['landmarks'])} landmarks", fill='white', font=font(12))
        sheet.paste(im, ((i % cols) * (s + g), (i // cols) * (s + g)))
    sheet.save(os.path.join(B, 'sheet_' + tag + '.jpg'), quality=90)
# a widget row of four clocks at different hours, with the day, time and city over them
row = [('sydney', 4, 0.0, 'Thursday', '18:12'), ('new-york', -1.5, 0.0, 'Wednesday', '17:02'),
       ('paris', 25, 0.5, 'Thursday', '13:40'), ('tokyo', -20, 0.0, 'Thursday', '22:31')]
cw = 178
img = Image.new('RGB', (4 * cw + 3 * 2, cw), 'black')
for i, (c, e, m, day, tm) in enumerate(row):
    if not os.path.exists(os.path.join(B, c + '.pkl')): continue
    sc = pickle.load(open(os.path.join(B, c + '.pkl'), 'rb'))
    im = render.render(sc, e, morning=m, aspect=1.0, height=cw)
    d = ImageDraw.Draw(im)
    for txt, sz, y in [(day, 22, 34), (tm, 44, 66), (names[c], 22, 122)]:
        f = font(sz)
        w = d.textlength(txt, font=f)
        d.text(((cw - w) / 2, y - sz / 2), txt, fill='white', font=f)
    img.paste(im, (i * (cw + 2), 0))
img = img.resize((img.width * 2, img.height * 2), Image.LANCZOS)
img.save(os.path.join(B, 'widget_row.jpg'), quality=90)
print('sheet', len(done))
