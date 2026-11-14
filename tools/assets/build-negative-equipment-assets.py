"""Invert vanilla textures deterministically; keep original alpha, dimensions and equipment UVs."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mcanomalyarchives'
CLIENT = Path('C:/Users/Administrator/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
names = ['block/dirt', 'block/oak_log', 'block/oak_log_top', 'block/oak_planks', 'item/wooden_pickaxe']


with ZipFile(CLIENT) as archive:
    for name in names:
        original = Image.open(BytesIO(archive.read(f'assets/minecraft/textures/{name}.png'))).convert('RGBA')
        inverted = Image.new('RGBA', original.size)
        for y in range(original.height):
            for x in range(original.width):
                r, g, b, a = original.getpixel((x, y))
                inverted.putpixel((x, y), (255-r, 255-g, 255-b, a))
        directory, base = name.rsplit('/', 1)
        output = ASSETS / f'textures/{directory}/negative_{base}.png'
        output.parent.mkdir(parents=True, exist_ok=True)
        inverted.save(output)
        if directory == 'item':
            model = {'parent':'minecraft:item/handheld','textures':{'layer0':f'mcanomalyarchives:item/negative_{base}'}}
            (ASSETS / f'models/item/negative_{base}.json').write_text(json.dumps(model, indent=2)+'\n', encoding='utf-8')
    for block in ['dirt', 'oak_log', 'oak_planks']:
        textures = {'all':f'mcanomalyarchives:block/negative_{block}'}
        parent = 'minecraft:block/cube_all'
        if block == 'oak_log':
            parent = 'minecraft:block/cube_column'
            textures = {'side':'mcanomalyarchives:block/negative_oak_log', 'end':'mcanomalyarchives:block/negative_oak_log_top'}
        (ASSETS / f'models/item/negative_{block}.json').write_text(json.dumps({'parent':parent,'textures':textures},indent=2)+'\n',encoding='utf-8')
print('Vanilla inversion assets ready: three negative block items.')
