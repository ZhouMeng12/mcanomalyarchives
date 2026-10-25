"""Deterministic cuboid concept assets. Each JSON face references a PNG UV tile.
These are preview sources, not registered Minecraft entities or Java models.
"""
from pathlib import Path
from PIL import Image
import json, random, uuid

ROOT = Path(__file__).parent / 'assets'
ROOT.mkdir(exist_ok=True)
PALETTES = {
 'flesh':['#833747','#b44a59','#da7172','#f2b8a7'],
 'ivory':['#c8d2c5','#e4e6cd','#9baea5','#f4f3df'],
 'moss':['#394b32','#768b45','#a9b15b','#d1c86a'],
 'pink':['#61304e','#b85084','#ee8fbd','#fbc7d8'],
 'crystal':['#527d81','#87b5b6','#c6e4d5','#e8f3da'],
 'void':['#070a0c','#0c1114','#172025','#263034'],
 'gold':['#694b22','#aa7b32','#d4b759','#f0d98d'],
 'wool':['#a5a89d','#d0d0ba','#e6e2cd','#f6f0d9'],
 'eye':['#172124','#172124','#172124','#fff1b6'],
 'wood':['#4a382a','#725536','#987342','#c59d62'],
 'flower':['#484d24','#81903b','#e0bd38','#f2dc6c']
}
keys=list(PALETTES)
atlas=Image.new('RGB',(16*len(keys),16))
rng=random.Random(8304)
for k,palette in PALETTES.items():
 x0=keys.index(k)*16
 for y in range(16):
  for x in range(16):
   tone=rng.choices(range(4),[2,5,4,1])[0]
   if k=='flesh': tone=1 if (x+y//2)%5<3 else 2
   if k=='gold': tone=2 if 1<x<14 and 1<y<14 else 1; tone=3 if y==2 or x==2 else tone
   if k=='void': tone=0 if (x+2*y)%9<4 else 1; tone=2 if (x+y)%13==0 else tone
   if k=='wood': tone=1 if x%5<3 else 2; tone=0 if (x+y//6)%11==0 else tone
   if k=='eye': tone=0
   c=palette[tone]
   if k=='crystal' and (x+y*2)%13==0: c=palette[3]
   atlas.putpixel((x0+x,y),tuple(bytes.fromhex(c[1:])))
atlas.save(ROOT/'specimen-atlas.png')
for i,k in enumerate(keys): atlas.crop((i*16,0,i*16+16,16)).save(ROOT/f'{k}.png')

def box(name,pos,size,mat,rot=None):
 return dict(name=name,center=pos,size=size,material=mat,rotation=rot or [0,0,0])
def legs(mat):
 return [box(f'leg_{x}_{z}',[x,3,z],[2.5,6,2.5],mat) for x in [-3,3] for z in [-5,5]]
models=[]
def add(id,title,band,desc,parts):
 models.append(dict(id=id,title=title,band=band,description=desc,parts=parts))
add('redfin','红鳍异鱼','01 / 近岸','熟悉的鱼形，苍白的眼睛；离开水面后鱼鳍仍在逆向摆动。',[
 box('body',[0,6,0],[5,6,12],'flesh'),box('tail',[0,6,8],[1,7,5],'flesh'),
 box('dorsal',[0,10,0],[1,3,6],'ivory'),box('fin_l',[-4,5,0],[4,1,4],'flesh',[0,0,-20]),box('fin_r',[4,5,0],[4,1,4],'flesh',[0,0,20]),
 box('eye_l',[-2.6,7,-4],[.4,2,2],'ivory'),box('eye_r',[2.6,7,-4],[.4,2,2],'ivory')])
add('bloomcow','蒲公英牛','02 / 异域草甸','花从皮肤裂口长出。花茎随呼吸颤动，仍保持食草动物的身体。',[
 box('body',[0,10,0],[9,9,16],'ivory'),box('head',[0,13,-11],[7,7,6],'moss'),box('muzzle',[0,11,-14],[6,3,2],'ivory'),*legs('moss'),
 box('horn_l',[-4,17,-10],[1.5,3,1.5],'ivory'),box('horn_r',[4,17,-10],[1.5,3,1.5],'ivory'),
 *[box('stem',[x,17,z],[.7,6,.7],'moss') for x,z in [(-2,-3),(2,3),(-1,5)]],
 *[box('flower_plane_'+axis,[x,23,z],[7,8,.18],'flower',[0,angle,0]) for x,z in [(-2,-3),(2,3),(-1,5)] for axis,angle in [('a',0),('b',90)]],
 box('eye_l',[-3.6,14,-12],[.3,1,1.5],'eye'),box('eye_r',[3.6,14,-12],[.3,1,1.5],'eye')])
add('walksquid','荧光行足','03 / 荧海','只保留原版鱿鱼躯干与八条触腕；触腕形态不合常理，落地后会寻找水。',[
 box('mantle',[0,17,0],[12,16,12],'pink'),
 *[box(f'tentacle_{i}',[x,5,z],[2,18,2],'pink',[0,0,a]) for i,(x,z,a) in enumerate([(-5,-4,-20),(5,-4,20),(-5,4,-20),(5,4,20),(-2,-5,-8),(2,-5,8),(-2,5,-8),(2,5,8)])],
])
add('pale','白色末影人','04 / 褪色地带','表皮正在晶化，胸口露出错位的肋骨。它的轮廓依然能被认出。',[
 box('pelvis',[0,16,0],[5,3,3],'crystal'),box('spine',[0,22,0],[1.5,10,2],'crystal'),
 *[box('rib',[0,y,0],[6,1,4],'ivory') for y in [19,22,25]],
 box('head',[0,30,0],[7,6,6],'ivory'),box('eye_l',[-2,30,-3.1],[2,.8,.3],'eye'),box('eye_r',[2,30,-3.1],[2,.8,.3],'eye'),
 box('arm_l',[-5,19,0],[2,18,2],'ivory',[0,0,-5]),box('arm_r',[5,19,0],[2,18,2],'crystal',[0,0,5]),
 box('leg_l',[-2,8,0],[2,16,2],'ivory'),box('leg_r',[2,8,0],[2,16,2],'crystal')])
add('remnant','不明残肢','05 / 深界','断面里还有另一层关节。收线后仍试图向不存在的身体靠拢。',[
 box('stump',[-7,7,0],[8,10,8],'flesh',[0,0,20]),box('bone',[-7,13,0],[3,4,3],'ivory'),
 box('joint',[0,5,0],[10,7,7],'void'),box('forelimb',[7,4,0],[10,5,5],'void',[0,0,-12]),
 *[box('digit',[15,3,z],[9,2,2],'void',[0,z*3,-15]) for z in [-3,0,3]],
 *[box('innerjoint',[x,7,-3],[2,2,2],'crystal') for x in [-1,3,7]]])
add('fold','折叠空壳','06 / 远界','像骨架，又像某种器官的边界。中心空洞里悬着一块无法归位的组织。',[
 *[box(f'frame_{i}',[x,y,z],[a,b,c],'void',[0,i*8,i*11]) for i,(x,y,z,a,b,c) in enumerate([(-9,13,0,3,21,4),(8,16,0,3,23,4),(0,25,0,19,3,4),(0,4,0,19,3,4),(-5,12,7,3,17,3),(5,18,-7,3,17,3)])],
 box('floating_tissue',[0,14,0],[5,5,5],'flesh',[30,25,20]),box('inner_bone',[0,14,0],[2,11,2],'crystal',[0,0,30])])
add('sheep','命名试体 · 羊','UO–008','保留羊的轮廓；黄金从面部、蹄部向身体侵入。',[
 box('body',[0,11,0],[11,10,15],'wool'),box('head',[0,12,-10],[6,7,6],'wool'),*legs('wool'),
 box('eye_l',[-3.1,13,-11],[.3,1,1.5],'eye'),box('eye_r',[3.1,13,-11],[.3,1,1.5],'eye')])
add('stick','命名试体 · 木棍','UO–008 / FAILURE','原有木纹仍可辨认，压缩后释放一次跨类转化失败爆炸。',[
 box('stick',[0,0,0],[2,19,2],'wood',[0,0,-25])])

# Match the recognisable vanilla silhouettes before adding abnormal structures.
for m in models:
 for p in m['parts']:
  n=p['name']
  if m['id']=='bloomcow':
   if n=='body':p['size']=[12,10,18];p['center']=[0,12,0]
   if n=='head':p['size']=[8,8,6];p['center']=[0,15,-12]
   if n.startswith('leg_'):p['size']=[4,8,4];p['center'][1]=4
   if n=='muzzle':p['center']=[0,13,-15.2]
   if n.startswith('horn_'):p['center'][1]=20
   if n=='stem':p['center'][1]=20
   if n.startswith('flower_plane'):p['center'][1]=23;p['size']=[7,8,.18]
   if n.startswith('eye_'):p['center']=[-4.1 if n.endswith('l') else 4.1,16,-13]
  if m['id']=='sheep':
   if n.startswith('leg_'):p['size']=[3,7,3];p['center'][1]=3.5
   if n=='head':p['size']=[6,6,8];p['center']=[0,12,-10]
  if m['id']=='walksquid':
   if n=='mantle':p['size']=[12,16,12];p['center'][1]=17
   if n.startswith('tentacle'):p['size'][1]=18;p['center'][1]=0
  if m['id']=='pale':
   if n=='head':p['size']=[8,8,8];p['center'][1]=42
   if n.startswith('eye_'):p['center'][1]=42;p['center'][2]=-4.1
   if n=='pelvis':p['center'][1]=25
   if n=='spine':p['center'][1]=31
   if n=='rib':p['center'][1]+=9
   if n.startswith('arm_'):p['size'][1]=25;p['center'][1]=23
   if n.startswith('leg_'):p['size'][1]=24;p['center'][1]=12

def vanilla_face(image,u,v,w,h,d,face):
 rects=[(u+d,v+d,w,h),(u+2*d+w,v+d,w,h),(u,v+d,d,h),(u+d+w,v+d,d,h),(u+d,v,w,d),(u+d+w,v,w,d)]
 x,y,a,b=rects[face]
 return image.crop((x,y,x+a,y+b)).convert('RGB')

def surface(m,p,face,w,h):
 """Per-face UV: vanilla pixel structure, re-coloured and hand placed abnormal marks."""
 im=Image.new('RGB',(w,h));mat=p['material'];n=p['name'];ref=None;refroot=ROOT/'reference'
 if mat=='flower':
  if face>=2:return Image.new('RGBA',(w,h),(0,0,0,0))
  return Image.open(refroot/'dandelion.png').convert('RGBA').resize((w,h),Image.Resampling.NEAREST)
 spec=None
 if m['id']=='bloomcow' and n in ['head','body'] or m['id']=='bloomcow' and n.startswith('leg_'):
  spec=('cow',0,0,8,8,6) if n=='head' else ('cow',18,4,12,18,10) if n=='body' else ('cow',0,16,4,12,4)
 if m['id']=='sheep':
  spec=('sheep',0,0,6,6,8) if n=='head' else ('sheep_fur',28,8,8,16,6) if n=='body' else ('sheep',0,16,4,12,4)
 if m['id']=='pale' and n=='head':spec=('enderman',0,0,8,8,8)
 if spec and (refroot/(spec[0]+'.png')).exists():
  ref=vanilla_face(Image.open(refroot/(spec[0]+'.png')),*spec[1:],face).resize((w,h),Image.Resampling.NEAREST)
 palette=[tuple(bytes.fromhex(c[1:])) for c in PALETTES[mat]]
 seed=sum(map(ord,m['id']+n))+face*313
 r=random.Random(seed)
 for y in range(h):
  for x in range(w):
   tone=1 if r.random()<.65 else 2
   if mat=='gold':tone=2 if x>0 and y>0 else 3
   if mat=='void':tone=0 if (x+y//3)%6<3 else 1
   if mat=='wood':tone=1 if x%4<2 else 2
   if mat=='eye':tone=0
   c=palette[tone]
   if ref:
    orig=ref.getpixel((x,y));lum=sum(orig)/3
    if m['id']=='sheep':c=orig if n!='body' else tuple(min(255,int(q*.92+12)) for q in orig)
    elif m['id']=='bloomcow':
     c=tuple(int(a*.9) for a in (225,222,187)) if lum>145 else (48+int(lum*.23),66+int(lum*.34),36+int(lum*.15))
     if lum<40:c=(35,33,27)
    elif m['id']=='pale':c=(201+int(lum*.08),216+int(lum*.06),197+int(lum*.12))
   if m['id']=='redfin':
    c=(148+(y%3)*14,51+(y%3)*7,58+(y%3)*6)
    if y<h//3:c=(95,41,49)
    if y>h*.72:c=(212,170,141)
    if n=='body' and (x+y//2)%5==0:c=(190,91,86)
    if n.startswith('eye_'):c=(233,226,197) if x!=w//2 or y!=h//2 else (30,31,29)
   if m['id']=='walksquid':
    shade=int((x+y)%(max(1,w//3)+1))*2;c=(128+shade,51+shade//2,87+shade)
   if m['id']=='pale' and mat in ['ivory','crystal'] and (x*2+y)%13==0:c=(101,144,144)
   if m['id']=='remnant' and mat=='flesh':c=(8,11,13) if (x+y)%5 else (28,34,37)
   if n.startswith('leg_') and y>h*.78:c=(57,52,42)
   if mat=='moss' and n=='stem':c=(59,88,43) if x%2 else (97,119,50)
   im.putpixel((x,y),c)
 return im

for m in models:
 packed=Image.new('RGBA',(128,256),(21,30,30,255));x=1;y=1;row=0
 for p in m['parts']:
  sx,sy,sz=[max(1,round(q)) for q in p['size']]
  sizes=[(sx,sy),(sx,sy),(sz,sy),(sz,sy),(sx,sz),(sx,sz)]
  p['uv']=[]
  for f,(w,h) in enumerate(sizes):
   if x+w+1>128:x=1;y+=row+1;row=0
   if y+h>=256:raise RuntimeError('UV atlas overflow')
   packed.paste(surface(m,p,f,w,h),(x,y));p['uv'].append([x,y,w,h]);x+=w+1;row=max(row,h)
 height=2**max(5,(y+row).bit_length());packed=packed.crop((0,0,128,height))
 packed.save(ROOT/(m['id']+'.png'));m['texture']=m['id']+'.png';m['textureSize']=[128,height]
manifest=dict(format='anomaly-cuboids-v1',units='1/16 block',coordinates='right-handed; +Y up',texture=dict(file='specimen-atlas.png',width=atlas.width,height=16,tiles={k:[i*16,0,16,16] for i,k in enumerate(keys)}),models=models)
(ROOT/'specimens.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
for m in models:
 elements=[]
 for p in m['parts']:
  origin=p['center'];size=p['size'];uv=[keys.index(p['material'])*16,0,keys.index(p['material'])*16+16,16]
  el=dict(name=p['name'],from_=[origin[i]-size[i]/2 for i in range(3)],to=[origin[i]+size[i]/2 for i in range(3)],faces={f:dict(uv=uv,texture='#0') for f in ['north','south','east','west','up','down']})
  el['from']=el.pop('from_');elements.append(el)
 # Blockbench free-model source preserves per-part Euler rotation and shared texture.
 bb=dict(meta=dict(format_version='4.10',model_format='free',box_uv=False),name=m['title'],resolution=dict(width=atlas.width,height=16),elements=[dict(name=p['name'],type='cube',uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,f'anomaly/{m["id"]}/{i}')),**{'from':el['from'],'to':el['to']},origin=p['center'],rotation=p['rotation'],faces={f:dict(uv=uv,texture=0) for f,uv in [(f,el['faces'][f]['uv']) for f in el['faces']]}) for i,(p,el) in enumerate(zip(m['parts'],elements))],textures=[dict(name='specimen-atlas.png',path='specimen-atlas.png',id='0',source='data:image/png;base64,'+__import__('base64').b64encode((ROOT/'specimen-atlas.png').read_bytes()).decode())])
 bb['outliner']=[e['uuid'] for e in bb['elements']]
 bb['resolution']={'width':m['textureSize'][0],'height':m['textureSize'][1]}
 facekeys=['north','south','west','east','up','down']
 for element,p in zip(bb['elements'],m['parts']):
  for face,uv in zip(facekeys,p['uv']):element['faces'][face]={'uv':[uv[0],uv[1],uv[0]+uv[2],uv[1]+uv[3]],'texture':0}
 bb['textures'][0].update(name=m['texture'],path=m['texture'],uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,'anomaly/'+m['id'])),width=m['textureSize'][0],height=m['textureSize'][1],uv_width=m['textureSize'][0],uv_height=m['textureSize'][1],source='data:image/png;base64,'+__import__('base64').b64encode((ROOT/m['texture']).read_bytes()).decode())
 (ROOT/f'{m["id"]}.bbmodel').write_text(json.dumps(bb,ensure_ascii=False),encoding='utf-8')
print(f'Generated {len(models)} models, {len(keys)} pixel textures, Blockbench sources and shared atlas.')
