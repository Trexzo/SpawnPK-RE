#!/usr/bin/env python3
"""Read-only donor ZIP manifest inspector: no extraction or runtime trust."""
import argparse, hashlib, json, pathlib, re, sys, zipfile
SOURCE_SUFFIXES=('.kt','.java')
MAX_ENTRY_BYTES=32*1024*1024
MAX_TOTAL_BYTES=512*1024*1024

def analyze(path,compare=None):
    archive=pathlib.Path(path)
    with archive.open('rb') as src: digest=hashlib.file_digest(src,'sha256').hexdigest()
    result={'archive':archive.name,'sha256':digest,'source_counts':{},'plugin_source_counts':{},'reference_docs':[],'warnings':[],'archive_entries':0,'same_path_comparison':{}}
    byname={};total=0
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            name=item.filename.replace('\\','/')
            components=name.split('/')
            if name.startswith('/') or re.match(r'^[A-Za-z]:',name) or '..' in components or '' in components[:-1]:
                raise ValueError('unsafe archive entry: '+name)
            if name in byname:raise ValueError('duplicate archive entry: '+name)
            byname[name]=item
            if item.file_size>MAX_ENTRY_BYTES:raise ValueError('oversize entry: '+name)
            total+=item.file_size
            if total>MAX_TOTAL_BYTES:raise ValueError('uncompressed archive too large')
            result['archive_entries']+=1
            if name.endswith(SOURCE_SUFFIXES):
                bucket='client' if '/client/' in name else 'server' if '/server/' in name else 'other'
                result['source_counts'][bucket]=result['source_counts'].get(bucket,0)+1
                if '/server/plugins/' in name:
                    plugin=name.split('/server/plugins/',1)[1].split('/',1)[0]
                    result['plugin_source_counts'][plugin]=result['plugin_source_counts'].get(plugin,0)+1
            if name.endswith(('HANDOFF_TREXNO_SYSTEMS.md','TREXNO_PORT.md','SERVER_PLAN.md')):
                result['reference_docs'].append(name)
        result['top_plugins']=sorted(result['plugin_source_counts'].items(),key=lambda v:(-v[1],v[0]))[:30]
        candidates=[n for n in byname if '/server/' in n and n.endswith(SOURCE_SUFFIXES)]
        if compare:
            with zipfile.ZipFile(compare) as older_zip:
                def relative(n):return n.partition('/server/')[2] if '/server/' in n else None
                older={relative(n):n for n in older_zip.namelist() if relative(n) and n.endswith(SOURCE_SUFFIXES)}
                common=[n for n in candidates if relative(n) in older]
                identical=sum(hashlib.sha256(z.read(n)).digest()==hashlib.sha256(older_zip.read(older[relative(n)])).digest() for n in common)
                result['same_path_comparison']={'shared_source_paths':len(common),'byte_identical':identical,'different':len(common)-identical,'donor_only':len(candidates)-len(common)}
        trade=next((n for n in byname if n.endswith('/trade/TradeServiceImpl.kt')),None)
        if trade and b'reference `spk/local/TradeService.java`' in z.read(trade)[:2200]:
            result['warnings'].append('trade plugin cites spk/local/TradeService.java: not independent original-protocol authority')
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('archive',type=pathlib.Path)
    p.add_argument('--compare-zip',type=pathlib.Path)
    p.add_argument('--output',type=pathlib.Path)
    args=p.parse_args()
    data=json.dumps(analyze(args.archive,args.compare_zip),sort_keys=True,indent=2)+'\n'
    if args.output:args.output.write_text(data,encoding='utf-8')
    else:sys.stdout.write(data)
