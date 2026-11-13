"""Build original, animated dye-colored portal surfaces. Uses only Python's standard library."""
from pathlib import Path
import io,json,math,struct,zlib,zipfile
ROOT=Path(__file__).resolve().parents[1]
COLORS={'white':0xF9FFFE,'orange':0xF9801D,'magenta':0xC74EBD,'light_blue':0x3AB3DA,'yellow':0xFED83D,'lime':0x80C71F,'pink':0xF38BAA,'gray':0x474F52,'light_gray':0x9D9D97,'cyan':0x169C9C,'purple':0x8932B8,'blue':0x3C44AA,'brown':0x835432,'green':0x5E7C16,'red':0xB02E26,'black':0x1D1D21}
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
def texture(rgb):
 palette=bytearray();alpha=bytearray()
 for n in range(32):
  shade=.20+.80*n/31;highlight=max(0,(n/31-.82)/.18)*.30
  for shift in (16,8,0):palette.append(round(min(255,((rgb>>shift)&255)*shade*(1-highlight)+255*highlight)))
  alpha.append(190+round(55*n/31))
 raw=bytearray()
 for frame in range(32):
  t=frame*2*math.pi/32
  for y in range(32):
   raw.append(0)
   for x in range(32):
    # Periodic flow, with identical opposite edges in every frame. Sharing the
    # same animation across tiles also preserves these seams during interpolation.
    u=2*math.pi*(x%31)/31;v=2*math.pi*(y%31)/31
    wave=math.sin(u+.85*math.sin(v+t)-t)+math.sin(v+.85*math.sin(u-t)+t)+.5*math.sin(2*u+2*v-2*t)+.25*math.cos(3*u-2*v+t)
    raw.append(max(0,min(31,round((wave/5.5+.5)*31))))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,1024,8,3,0,0,0))+chunk(b'PLTE',bytes(palette))+chunk(b'tRNS',bytes(alpha))+chunk(b'IDAT',zlib.compress(bytes(raw),9))+chunk(b'IEND',b'')
files={}
def js(name,obj):files[name]=json.dumps(obj,separators=(',',':')).encode()
js('pack.mcmeta',{'pack':{'pack_format':34,'description':'FFA animated colored portal surfaces (Minecraft 1.21.1)'}})
files['LICENSE.txt']=b'Original procedural textures and models for Fantasy Feudalism Administration. No Minecraft artwork is redistributed.\n'
overrides=[]
for i,(name,rgb) in enumerate(COLORS.items()):
 files[f'assets/ffa/textures/block/portal_{name}.png']=texture(rgb)
 js(f'assets/ffa/textures/block/portal_{name}.png.mcmeta',{'animation':{'frametime':2,'interpolate':True}})
 js(f'assets/ffa/models/item/portal_{name}.json',{'gui_light':'front','render_type':'minecraft:translucent','textures':{'portal':f'ffa:block/portal_{name}','particle':f'ffa:block/portal_{name}'},'elements':[{'from':[0,0,6],'to':[16,16,10],'shade':False,'faces':{'north':{'texture':'#portal','uv':[0,0,16,16]},'south':{'texture':'#portal','uv':[0,0,16,16]}}}]})
 overrides.append({'predicate':{'custom_model_data':600000+i},'model':f'ffa:item/portal_{name}'})
js('assets/ffa/models/item/portal_base_default.json',{'parent':'minecraft:block/purple_stained_glass'})
overrides.append({'predicate':{'custom_model_data':600016},'model':'ffa:item/portal_base_default'})
js('assets/minecraft/models/item/purple_stained_glass.json',{'parent':'minecraft:block/purple_stained_glass','overrides':overrides})
out=ROOT/'src/main/resources/resourcepacks/ffa-portals.zip';out.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for name,data in sorted(files.items()):
  info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
print(f'{out.name}: {len(files)} entries, {out.stat().st_size} bytes')
