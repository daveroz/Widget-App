"""Today's drawn scene (2.30) beside the scene baked from the real city, at the same dusk."""
import os, pickle
from PIL import Image, ImageDraw, ImageFont
import render
HERE = os.path.dirname(os.path.abspath(__file__))
OLD = os.path.join(HERE, '..', 'render', 'out', 'v230e', 'landmarks_-1.5.jpg')
order = ["Mexico City", "Bucharest", "Brussels", "Charlotte", "Bogota", "Paris", "London", "New York", "Tokyo", "Sydney",
         "Dubai", "Berlin", "Toronto", "Seattle", "Rio de Janeiro", "Cairo", "San Francisco", "Rome", "Barcelona", "Chicago",
         "Los Angeles", "Miami", "Buenos Aires", "Washington", "Singapore", "Kuala Lumpur", "Istanbul", "Amsterdam", "Moscow",
         "Lyon", "Denver", "Nairobi"]
pairs = [('Paris', 'paris'), ('Sydney', 'sydney'), ('New York', 'new-york'), ('Tokyo', 'tokyo'), ('Charlotte', 'charlotte'), ('Istanbul', 'istanbul')]
old = Image.open(OLD)
s, g = 260, 6
font = ImageFont.truetype('/home/user/Widget-App/app/src/main/assets/fonts/futura100-text-medium.otf', 15)
out = Image.new('RGB', (len(pairs) * (s + g) - g, 2 * s + g + 28), 'white')
d0 = ImageDraw.Draw(out)
d0.text((4, 4), 'Now (2.30, drawn)  ↑      ·      From the real city  ↓', fill='black', font=font)
for i, (name, key) in enumerate(pairs):
    k = order.index(name)
    cell = old.crop(((k % 8) * (s + g), (k // 8) * (s + g), (k % 8) * (s + g) + s, (k // 8) * (s + g) + s))
    out.paste(cell, (i * (s + g), 28))
    p = os.path.join(HERE, 'baked', key + '.pkl')
    if os.path.exists(p):
        out.paste(render.render(pickle.load(open(p, 'rb')), -1.5, aspect=1.0, height=s), (i * (s + g), 28 + s + g))
out.save(os.path.join(HERE, 'baked', 'before_after.jpg'), quality=90)
print('ok')
