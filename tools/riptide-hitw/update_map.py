#!/usr/bin/env python3
"""Rate all saved PASS snapshots and fix raised 1.5-high slab holes. Dry-run by default.
Requires PyYAML; preserves YAML text outside modified pool rows and version markers.
"""
import argparse, copy, json, re
from pathlib import Path
import yaml
from apertures import unpack, repack, repair, measure, difficulty

def child(node,key): return next(v for k,v in node.value if k.value==key)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('map',type=Path);parser.add_argument('--apply',action='store_true');parser.add_argument('--report',type=Path)
    args=parser.parse_args();before=args.map.read_text();original=yaml.safe_load(before);expected=copy.deepcopy(original)
    doc=yaml.compose(before);pool=child(child(doc,'course'),'pool');edits=[];report=[]
    catalog=json.loads((Path(__file__).resolve().parents[2]/'championships-core/src/main/resources/riptiderush/hitw-walls.json').read_text())
    catalog={r['id']:r for r in catalog['pool']}
    def replace(node,value):
        old=before[node.start_mark.index:node.end_mark.index]
        suffix=re.search(r'\n[ \t]*$',old)
        edits.append((node.start_mark.index,node.end_mark.index,value+(suffix.group() if suffix else '')))
    for row,node in zip(expected['course']['pool'],pool.value):
        if row['type']!='PASS' or 'building' not in row: continue
        prior=copy.deepcopy(row);root,cells=unpack(row['building']['schematic']);changes=repair(cells)
        new=catalog.get(row['id'])
        if new and row['building']['schematic'] in (new.get('previous-schematic'),new.get('previous-v2-schematic')):
            row['building']=copy.deepcopy(new['building']);_,cells=unpack(row['building']['schematic'])
        elif changes:row['building']['schematic']=repack(root,cells)
        size=measure(cells);row['difficulty']=difficulty(size)
        assert not repair(copy.deepcopy(cells)),row['id']
        if row!=prior:replace(node,json.dumps(row,ensure_ascii=False))
        report.append(dict(id=row['id'],name=row['name'],area=size,difficulty=row['difficulty'],previous_difficulty=prior['difficulty'],changes=changes))
    for parent,key,value in [('course','hitw-catalog-version',3),('dont-edit-this','version',12)]:
        value=max(value,expected[parent].get(key,0))
        replace(child(child(doc,parent),key),str(value));expected[parent][key]=value
    after=before
    for start,end,value in sorted(edits,reverse=True):after=after[:start]+value+after[end:]
    assert yaml.safe_load(after)==expected
    assert original['prepare']==expected['prepare']
    assert [r for r in original['course']['pool'] if r['type']!='PASS']==[r for r in expected['course']['pool'] if r['type']!='PASS']
    # Re-parse every final snapshot, preserving every non-building/non-rating attribute.
    for old,new in zip(original['course']['pool'],expected['course']['pool']):
        assert {k:v for k,v in old.items() if k not in ('building','difficulty')}=={k:v for k,v in new.items() if k not in ('building','difficulty')}
        if new.get('building'):unpack(new['building']['schematic'])
    if args.report:args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(dict(total=len(report),ratings={i:sum(r['difficulty']==i for r in report) for i in (1,2,3)},
        repaired=[{'name':r['name'],'blocks':len(r['changes'])} for r in report if r['changes']],
        zero_area=[r['name'] for r in report if r['area']==0],prepare=expected['prepare']),ensure_ascii=False))
    if args.apply:
        assert args.map.read_text()==before,'Map changed while preparing migration'
        args.map.write_text(after);print('Applied')
    else:print('Dry run; map unchanged')
if __name__=='__main__':main()
