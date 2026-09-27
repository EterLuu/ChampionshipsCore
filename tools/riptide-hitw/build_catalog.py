#!/usr/bin/env python3
"""Rebuild the offline HITW catalogue from pinned source masks (no server/world access)."""
import base64, gzip, html, json, struct
from pathlib import Path
from apertures import repair, measure, difficulty
ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
RESOURCE = ROOT / 'championships-core/src/main/resources/riptiderush/hitw-walls.json'
NAMES = {'Beach':'海滩','Classic':'经典','Highrise':'高楼','Medieval':'中世纪','Fishbowl':'鱼缸',
         'Ice_Palace':'冰宫','Labwallatory':'实验室','Dojo':'道场','Rink':'冰场',
         'Beach_(Halloween)':'万圣海滩','Medieval_(Halloween)':'万圣中世纪','Beach_(Winter)':'冬日海滩','Iced_Palace':'冰封宫殿'}
# Geometry uses Minecraft block-local x/y; all authored blocks lie in the z=0 plane.
def boxes(state):
    if state=='air':return []
    if state in ('wood','beam'):return [(0,0,1,1)]
    if state=='bottom':return [(0,0,1,.5)]
    if state=='top':return [(0,.5,1,1)]
    if state.startswith('plate_'):return [(0,13/16 if state.endswith('top') else 0,1,1 if state.endswith('top') else 3/16)]
    if state.startswith('trap_'):
        return [(0,0,3/16,1)] if state.endswith('west') else [(13/16,0,1,1)]
    if state.startswith('fence'):
        # Fences have 1.5-block collision, including their connected arms.
        return [(.375,0,.625,1.5)]+([(0,0,.5,1.5)] if 'w' in state[5:] else [])+([(.5,0,1,1.5)] if 'e' in state[5:] else [])
    half='top' if '_top_' in state else 'bottom';east=state.endswith('east')
    return [(0,.5 if half=='top' else 0,1,1 if half=='top' else .5),
            (.5 if east else 0,0 if half=='top' else .5,1 if east else .5,.5 if half=='top' else 1)]

def collision(cells):
    return [(x-6.5+a,y+b,x-6.5+c,y+d) for y,row in enumerate(cells) for x,s in enumerate(row) for a,b,c,d in boxes(s)]

def routes(cells):
    solids=collision(cells);out=[]
    # Half-block centre positions include passages between two thin upright trapdoors.
    for n in range(-6,7):
        x=n/2
        for feet in sorted({0,.5,1}|{d for a,b,c,d in solids if 0<d<=1}) if any(v.startswith("plate_") for row in cells for v in row) else [0,.5,1]:
            if feet and not any(a<x+.3 and c>x-.3 and b<feet and abs(d-feet)<.001 for a,b,c,d in solids):continue
            for height in [1.8,1.5] if feet==0 else [1.8]:
                if not any(a<x+.3 and c>x-.3 and b<feet+height and d>feet for a,b,c,d in solids):
                    out.append(dict(lateral=x,sill=feet,crouch=height<1.8));break
            else:continue
            break
    return out

def connect(cells):
    for row in cells:
        for x,s in enumerate(row):
            if s.startswith('fence'):
                # Preserve authored disconnected fence pairs: their .75-block gap is intentional (bench13/20).
                row[x]='fence'+('w' if 'w' in s[5:] and x>0 and row[x-1]!='air' else '')+('e' if 'e' in s[5:] and x+1<len(row) and row[x+1]!='air' else '')
    return cells

def adapt(source):
    original=[row[:] for row in reversed(source['cells'])]
    while all(s=='air' for s in original[0]):original.pop(0)
    mapping=list(range(14));removed=[]
    # Delete only two columns, first exact repeated solid profiles, then exact repeated profiles.
    # If none repeat, discard the least distinct solid-rich profile; every deletion is in the audit.
    while len(mapping)>12:
        def cost(i):
            x=mapping[i];col=[r[x] for r in original]
            neighbors=[mapping[j] for j in [i-1,i+1] if 0<=j<len(mapping)]
            delta=min(sum(a!=r[n] for a,r in zip(col,original)) for n in neighbors)
            detail=sum(s not in ('wood','air') for s in col)
            gaps=sum(s=='air' for s in col)
            return (delta,detail,gaps,abs(i-(len(mapping)-1)/2))
        i=min(range(1,len(mapping)-1),key=cost);removed.append(mapping.pop(i))
    cells=connect([[r[x] for x in mapping] for r in original]);changes=[]
    if not routes(cells):
        options=[]
        for x in range(3,10):
            for feet in [0,1]:
                trial=[r[:] for r in cells]
                while len(trial)<feet+2:trial.append(['air']*12)
                # One local two-block-high window, with an ordinary one-block sill or ground entrance.
                before=[trial[y][x] for y in range(feet,feet+2)]
                for y in range(feet,feet+2):trial[y][x]='air'
                if feet==0 and source['group']=='X' and before[1]!='air':trial[1][x]='top'
                trial=connect(trial)
                available=routes(trial)
                if not available:continue
                removedArea=sum(sum((c-a)*(d-b) for a,b,c,d in boxes(s)) for s in before)-sum(sum((c-a)*(d-b) for a,b,c,d in boxes(trial[y][x])) for y in range(feet,feet+2))
                options.append((removedArea,abs(x-(4 if source['number']%2 else 8)),feet,x,trial,available))
        assert options,source['file']
        _,_,feet,x,cells,_=min(options,key=lambda q:q[:4]);changes.append(dict(column=x,fromHeight=feet,toHeight=feet+2,reason='ordinary-jump-clearance'))
    # Use transverse grain for beams, vertical grain for columns, matching bench1–32.
    for y,row in enumerate(cells):
        for x,s in enumerate(row):
            if s=='wood' and ((x>0 and row[x-1] in ('wood','beam')) or (x<11 and row[x+1]=='wood')):
                if y==0 or cells[y-1][x] not in ('wood','beam'):row[x]='beam'
    cells=connect(cells)
    snapshot={(x-6,y,0):blockstate(s) for y,row in enumerate(cells) for x,s in enumerate(row)}
    for change in repair(snapshot):
        x,y=change['x']+6,change['y']
        cells[y][x]='air' if change['after']=='minecraft:air' else 'beam'
        changes.append(dict(column=x,fromHeight=y,toHeight=y+1,reason='raised-half-gap',replacement=cells[y][x]))
    mask=[]
    for yy in reversed(range(len(cells)*2)):
        mask.append(''.join('#' if any(a<=xx/2+.25<c and b<=yy/2+.25<d for a,b,c,d in collision(cells)) else '.' for xx in range(-12,12)))
    return cells,mask,mapping,removed,changes,routes(cells)

def blockstate(s):
    if s=='air':return 'minecraft:air'
    if s in ('wood','beam'):return 'minecraft:stripped_spruce_wood[axis='+('x' if s=='beam' else 'y')+']'
    if s in ('top','bottom'):return 'minecraft:spruce_slab[type='+s+',waterlogged=false]'
    if s.startswith('stairs_'):
        _,half,facing=s.split('_');return f'minecraft:spruce_stairs[facing={facing},half={half},shape=straight,waterlogged=false]'
    if s.startswith('plate_'):
        return 'minecraft:spruce_trapdoor[facing=east,half='+s.split('_')[1]+',open=false,powered=false,waterlogged=false]'
    if s.startswith('trap_'):
        # An open trapdoor facing east occupies the WEST edge (opposite its facing).
        facing='east' if s.endswith('west') else 'west'
        return f'minecraft:spruce_trapdoor[facing={facing},half=bottom,open=true,powered=false,waterlogged=false]'
    return 'minecraft:spruce_fence[east='+str('e' in s[5:]).lower()+',north=false,south=false,waterlogged=false,west='+str('w' in s[5:]).lower()+']'

def string(s):
    b=s.encode();return struct.pack('>H',len(b))+b

def tag(t,n,p):return bytes([t])+string(n)+p

def integer(n,v):return tag(3,n,struct.pack('>i',v))

def compound(n,p):return tag(10,n,p+b'\0')

def schematic(cells):
    height=max(5,len(cells));data=bytearray(225*height);palette=['minecraft:air']
    for y,row in enumerate(cells):
        for x,s in enumerate(row):
            state=blockstate(s)
            if state not in palette:palette.append(state)
            data[x+1+7*15+y*225]=palette.index(state)
    assert len(palette)<128
    blocks=compound('Palette',b''.join(integer(n,i) for i,n in enumerate(palette)))+tag(7,'Data',struct.pack('>i',len(data))+data)
    body=integer('Version',3)+integer('DataVersion',4671)
    body+=b''.join(tag(2,n,struct.pack('>h',v)) for n,v in [('Width',15),('Height',height),('Length',15)])
    body+=tag(11,'Offset',struct.pack('>iiii',3,-7,1,-7))+compound('Blocks',blocks)
    return base64.b64encode(gzip.compress(compound('',compound('Schematic',body)),mtime=0)).decode(),height

def picture(cells):
    rect=[];h=max(5,len(cells))
    for y,row in enumerate(cells):
        for x,s in enumerate(row):
            for a,b,c,d in boxes(s):rect.append(f'<rect x="{(x+a)*24}" y="{(h-y-d)*24}" width="{(c-a)*24}" height="{(d-b)*24}" fill="'+('#c39460' if s=='beam' else '#98734c')+'" stroke="#34281d" stroke-width=".6"/>')
    rect.append(f'<path d="M72 {h*24+3}h168" stroke="#eab75a" stroke-width="3"/>')
    return f'<svg viewBox="0 0 288 {h*24+8}">'+''.join(rect)+'</svg>'

def main():
    sources=json.loads((HERE/'sources.json').read_text());legacy=json.loads((HERE/'legacy-v1.json').read_text());legacy2=json.loads((HERE/'legacy-v2.json').read_text());result=[];audit=[];cards=[]
    for s in sources:
        cells,mask,mapping,removed,changes,passages=adapt(s);payload,height=schematic(cells)
        id='hitw_'+s['map'].lower().replace('(','').replace(')','')+'_'+s['group'].lower()+str(s['number'])
        name=NAMES[s['map']]+' · '+s['group']+str(s['number']).zfill(2)
        entry=dict(id=id,name=name,type='PASS',variant='CUSTOM',enabled=True,weight=10,difficulty=difficulty(measure({(x-6,y,0):blockstate(v) for y,row in enumerate(cells) for x,v in enumerate(row)})),
                   building=dict(schematic=payload,extent=0,width=15,height=height,floor=[]),routes=passages,
                   **{'passable-area':measure({(x-6,y,0):blockstate(v) for y,row in enumerate(cells) for x,v in enumerate(row)})})
        if id in legacy:entry['previous-schematic']=legacy[id]
        if id in legacy2:entry['previous-v2-schematic']=legacy2[id]
        result.append(entry);audit.append(dict(id=id,name=name,source=s['url'],map=s['map'],group=s['group'],cells=cells,mask=mask,offset=-6,**{'source-columns':mapping,'removed-columns':removed},changes=changes,routes=passages))
        cards.append('<article data-map="'+html.escape(s['map'])+'"><h2>'+html.escape(name)+'</h2><div class="pair"><div><a href="'+html.escape(s['url'])+'">原图（点击查看）</a><img loading="lazy" src="'+html.escape(s['url'])+'"></div><div>十二格木墙 · 方块碰撞侧视'+picture(cells)+'</div></div><p>仅删除原列 '+str(removed)+'；难度 '+str(entry['difficulty'])+'（有效截面积 '+str(entry['passable-area'])+' 格²）；局部门洞修改 '+str(len(changes))+' 处。金线表示现有七格甲板。</p></article>')
    RESOURCE.write_text(json.dumps({'source':'https://mccisland.wiki/Hole_in_the_Wall','revision':32764,'version':4,'pool':result},ensure_ascii=False,indent=2)+'\n')
    area=ROOT/'championships-core/src/main/resources/riptiderush/area.yml';text=area.read_text().replace('hitw-catalog-version: 3','hitw-catalog-version: 4');start=text.index('    # BEGIN HITW CATALOG\n');end=text.index('    # END HITW CATALOG\n',start)+len('    # END HITW CATALOG\n')
    rows=[{k:v for k,v in r.items() if k not in ('routes','previous-schematic','previous-v2-schematic','passable-area')} for r in result]
    area.write_text(text[:start]+'    # BEGIN HITW CATALOG\n'+''.join('    - '+json.dumps(r,ensure_ascii=False)+'\n' for r in rows)+'    # END HITW CATALOG\n'+text[end:])
    (HERE/'adapted.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n')
    (HERE/'preview.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>激流 HITW 十二格木墙对照</title><style>body{background:#18221b;color:#e5eddf;font:16px system-ui;max-width:1200px;margin:30px auto}article{border:1px solid #52634f;padding:16px;margin:12px 0}h2{font-size:18px}a{color:#afd889}.pair{display:grid;grid-template-columns:1fr 1fr;gap:25px}svg,img{width:100%;height:180px;object-fit:contain}select{font-size:18px;padding:8px}</style><h1>354 个十二格穿越变体</h1><p>原图需要联网加载。保留楼梯、半砖、栅栏和竖起的活版门；悬空一格门槛上的1.5格洞口按剩余通路封实或移除顶半砖。侧视图按碰撞尺寸绘制（栅栏碰撞高1.5格）。方块主体采用 bench 的云杉木风格。仍需真人试玩。</p><select><option value="">全部地图</option>'+''.join('<option>'+html.escape(m)+'</option>' for m in NAMES)+'</select>'+''.join(cards)+'<script>document.querySelector("select").onchange=e=>document.querySelectorAll("article").forEach(a=>a.hidden=!!e.target.value&&a.dataset.map!==e.target.value)</script></html>')
    print('Generated',len(result),'12-block walls;',sum(bool(a['changes']) for a in audit),'local aperture adjustments')
if __name__=='__main__':main()
