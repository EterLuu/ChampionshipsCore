"""Offline cross-section rating; no path search or construction-save restrictions."""
import base64, gzip, io, struct

# Preserve all NBT tags (including block entities) when changing palette indices.
def decode(payload):
    stream = io.BytesIO(gzip.decompress(base64.b64decode(payload)))
    def num(fmt): return struct.unpack('>'+fmt, stream.read(struct.calcsize('>'+fmt)))[0]
    def string(): return stream.read(num('H')).decode('utf-8')
    def value(t):
        if t in range(1,7): return num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==7: return stream.read(num('i'))
        if t==8: return string()
        if t==9:
            kind,count=num('B'),num('i'); return (kind,[value(kind) for _ in range(count)])
        if t==10:
            out={}
            while (kind:=num('B')):
                name=string(); out[name]=(kind,value(kind))
            return out
        if t in (11,12): return [num('i' if t==11 else 'q') for _ in range(num('i'))]
        raise ValueError(t)
    t=num('B'); name=string(); return t,name,value(t)

def encode(root):
    def string(s):
        b=s.encode('utf-8'); return struct.pack('>H',len(b))+b
    def value(t,v):
        if t in range(1,7): return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],v)
        if t==7: return struct.pack('>i',len(v))+v
        if t==8: return string(v)
        if t==9: return bytes([v[0]])+struct.pack('>i',len(v[1]))+b''.join(value(v[0],x) for x in v[1])
        if t==10: return b''.join(bytes([k])+string(n)+value(k,x) for n,(k,x) in v.items())+b'\0'
        if t in (11,12): return struct.pack('>i',len(v))+b''.join(struct.pack('>i' if t==11 else '>q',x) for x in v)
        raise ValueError(t)
    t,n,v=root
    return base64.b64encode(gzip.compress(bytes([t])+string(n)+value(t,v),mtime=0)).decode()

def unpack(payload):
    root=decode(payload); s=root[2].get('Schematic',(10,root[2]))[1]
    w,h,l=(s[n][1] for n in ('Width','Height','Length'))
    off=s['Offset'][1]; b=s['Blocks'][1]; palette={v[1]:k for k,v in b['Palette'][1].items()}
    indices=[]; v=shift=0
    for byte in b['Data'][1]:
        v|=(byte&127)<<shift
        if byte&128: shift+=7
        else: indices.append(v); v=shift=0
    assert len(indices)==w*h*l and shift==0
    cells={(x+off[0],y+off[1]-1,z+off[2]):palette[indices[x+w*z+w*l*y]] for y in range(h) for z in range(l) for x in range(w)}
    return root,cells

def repack(root,cells):
    s=root[2].get('Schematic',(10,root[2]))[1]; w,h,l=(s[n][1] for n in ('Width','Height','Length')); off=s['Offset'][1]
    b=s['Blocks'][1]; palette=b['Palette'][1]; data=bytearray()
    for y in range(h):
        for z in range(l):
            for x in range(w):
                state=cells[x+off[0],y+off[1]-1,z+off[2]]
                if state not in palette: palette[state]=(3,max(v[1] for v in palette.values())+1)
                v=palette[state][1]
                while v>=128: data.append((v&127)|128); v>>=7
                data.append(v)
    b['Data']=(7,bytes(data)); return encode(root)

def shape(state):
    if state in ('minecraft:air','minecraft:cave_air','minecraft:void_air'): return []
    if 'trapdoor' in state:
        if 'open=false' in state: return [(0,13/16 if 'half=top' in state else 0,1,1 if 'half=top' in state else 3/16)]
        if 'facing=east' in state: return [(0,0,3/16,1)]
        if 'facing=west' in state: return [(13/16,0,1,1)]
        return [(0,0,1,1)] # front/back upright panel blocks a forward crossing
    if '_fence[' in state:
        return [(.375,0,.625,1.5)]+([(0,0,.5,1.5)] if 'west=true' in state else [])+([(.5,0,1,1.5)] if 'east=true' in state else [])
    if '_slab[' in state:
        return [(0,.5 if 'type=top' in state else 0,1,.5 if 'type=bottom' in state else 1)]
    if '_stairs[' in state:
        top='half=top' in state; east='facing=east' in state
        if not ('facing=east' in state or 'facing=west' in state): return [(0,0,1,1)]
        return [(0,.5 if top else 0,1,1 if top else .5),(.5 if east else 0,0 if top else .5,1 if east else .5,.5 if top else 1)]
    return [(0,0,1,1)]

def section(cells,z):
    return [(x-.5+a,y+b,x-.5+c,y+d) for (x,y,zz),s in cells.items() if zz==z for a,b,c,d in shape(s)]

def area(solids):
    # Exact shape boundaries horizontally; union all usable rectangles vertically.
    xs=sorted({-3.5,3.5}|{max(-3.5,min(3.5,v)) for a,b,c,d in solids for v in (a,c)})
    marked=[[] for _ in xs[:-1]]
    for feet in sorted({0.0}|{d for a,b,c,d in solids if 0<d<=1}):
        minimum=1.5 if feet<=.5 else 1.8
        ceilings=[]
        for left,right in zip(xs,xs[1:]):
            column=[(b,d) for a,b,c,d in solids if a<right-1e-9 and c>left+1e-9]
            support=feet==0 or any(abs(d-feet)<1e-9 for b,d in column)
            blocked=any(b<=feet+1e-9 and d>feet+1e-9 for b,d in column)
            ceiling=min([3.0]+[b for b,d in column if b>feet+1e-9])
            ceilings.append(ceiling if not blocked and ceiling-feet>=minimum-1e-9 else None)
        # A run must fit a 0.6-wide body at the same elevation; tiny slots add zero.
        start=0
        while start<len(ceilings):
            if ceilings[start] is None: start+=1; continue
            end=start+1
            while end<len(ceilings) and ceilings[end] is not None: end+=1
            if xs[end]-xs[start]>=.6-1e-9:
                for i in range(start,end): marked[i].append((feet,ceilings[i]))
            start=end
    total=0
    for i,intervals in enumerate(marked):
        end=-1; height=0
        for a,b in sorted(intervals): height+=max(0,b-max(a,end)); end=max(end,b)
        total+=(xs[i+1]-xs[i])*height
    return total

def measure(cells):
    sections=sorted({z for (x,y,z),s in cells.items() if shape(s) and -.5+x<3.5 and .5+x>-3.5})
    return min((area(section(cells,z)) for z in sections),default=21.0)

def difficulty(size): return 1 if size>=6 else 2 if size>=3 else 3

def repair(cells):
    changes=[]
    # One full block sill, one air block and a TOP slab: 1.5 air above a raised sill.
    for (x,y,z),s in list(cells.items()):
        if y!=2 or '_slab[' not in s or 'type=top' not in s: continue
        if cells.get((x,y-1,z))!='minecraft:air' or shape(cells.get((x,y-2,z),'minecraft:air'))!=[(0,0,1,1)]: continue
        old=s; cells[x,y,z]='minecraft:stripped_spruce_wood[axis=x]'
        # Retain a useful entrance where closing it would leave less than 3 square blocks.
        if area(section(cells,z))<3: cells[x,y,z]='minecraft:air'
        changes.append(dict(x=x,y=y,z=z,before=old,after=cells[x,y,z]))
    return changes
