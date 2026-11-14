"""Deterministic 16px wear masks over the existing inverted vanilla cat texture.
The first notch follows the upper-left chip at 7:37 in BV1YiGt6vEbK.
Later stages are an extension, not additional frames claimed from the video.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mcanomalyarchives'
base = Image.open(ASSETS / 'textures/item/negative_music_disc.png').convert('RGBA')
assert base.size == (16, 16)
# Each use removes additional pixels; never regenerate or recolour the surviving disc.
chips = [
    [(5, 3), (5, 4)],
    [(4, 4), (5, 5)],
    [(4, 5), (6, 5)],
    [(13, 9), (14, 9), (14, 10)],
    [(12, 9), (13, 10), (13, 11)],
    [(11, 10), (12, 10), (12, 11), (12, 12)],
    [(4, 11), (4, 12), (5, 12), (5, 13)],
    [(5, 11), (6, 11), (6, 12), (6, 13), (6, 14)],
    [(6, 4), (7, 4), (7, 3), (8, 3), (8, 4), (8, 5), (9, 4), (9, 3)],
]
preview = Image.new('RGBA', (800, 360), (28, 26, 34, 255))
draw = ImageDraw.Draw(preview)
overrides = []
current = base.copy()
previous_alpha = sum(p[3] > 0 for p in current.getdata())
for used in range(10):
    if used:
        for point in chips[used-1]:
            current.putpixel(point, (0, 0, 0, 0))
        alpha = sum(p[3] > 0 for p in current.getdata())
        assert alpha < previous_alpha, f'Stage {used} must visibly lose pixels'
        previous_alpha = alpha
        name = f'negative_music_disc_wear_{used}'
        current.save(ASSETS / f'textures/item/{name}.png')
        (ASSETS / f'models/item/{name}.json').write_text(json.dumps({
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'mcanomalyarchives:item/{name}'}
        }, indent=2)+'\n', encoding='utf-8')
        overrides.append({'predicate': {'mcanomalyarchives:disc_wear': used / 10}, 'model': f'mcanomalyarchives:item/{name}'})
    x, y = (used % 5)*160, (used // 5)*180
    preview.alpha_composite(current.resize((128, 128), Image.Resampling.NEAREST), (x+16, y+12))
    draw.text((x+42, y+151), f'{10-used}/10 left', fill='white')
(ASSETS / 'models/item/negative_music_disc.json').write_text(json.dumps({
    'parent': 'minecraft:item/generated', 'textures': {'layer0': 'mcanomalyarchives:item/negative_music_disc'},
    'overrides': overrides
}, indent=2)+'\n', encoding='utf-8')
preview.convert('RGB').save(ROOT / 'tools/assets/source/negative-disc-wear-preview.png')
print('Nine cumulative wear masks, models and preview generated.')
