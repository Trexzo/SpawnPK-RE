import importlib.util
import json
import sys
import os
from pathlib import Path

OUT=Path(os.environ.get('R10_OUT','/tmp/chat4-r10'))
OUT.mkdir(parents=True,exist_ok=True)
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('chat4_r9',HERE/'chat4_r9_blender_hierarchy_fixture.py')
r9=importlib.util.module_from_spec(spec)
sys.modules['chat4_r9']=r9
spec.loader.exec_module(r9)


def skin_id_from_name(name):
    p='spk_skin_'
    if not name.startswith(p): return None
    try: n=int(name[len(p):])
    except ValueError: return None
    return n if 0<=n<=255 else None


def export_mesh_and_skins(obj):
    verts=[[round(float(c),9) for c in v.co] for v in obj.data.vertices]
    faces=[[int(i)+1 for i in p.vertices] for p in obj.data.polygons]
    index_to_skin={vg.index:skin_id_from_name(vg.name) for vg in obj.vertex_groups}
    skins=[]
    for v in obj.data.vertices:
        candidates=[]
        for g in v.groups:
            skin=index_to_skin.get(g.group)
            if skin is not None and g.weight>0:
                candidates.append((float(g.weight),skin))
        if not candidates:
            raise AssertionError(f'vertex {v.index} has no spk_skin_N weight')
        candidates.sort(key=lambda x:(-x[0],x[1]))
        skins.append(candidates[0][1])
    obj_path=OUT/'r10-authored-rest.obj'
    with obj_path.open('w',encoding='utf-8',newline='\n') as f:
        f.write('# Chat4 R10 Blender-authored rest mesh\n')
        for x,y,z in verts:
            f.write(f'v {x:g} {y:g} {z:g}\n')
        for face in faces:
            f.write('f '+' '.join(str(i) for i in face)+'\n')
    sidecar={
      'format':'spawnpk-v308-vertex-skins-v1',
      'vertexCount':len(verts),
      'groups':skins,
    }
    side_path=OUT/'r10-authored-rest.obj.skins.json'
    side_path.write_text(json.dumps(sidecar,indent=2)+'\n',encoding='utf-8')
    return verts,faces,skins,obj_path,side_path


def main():
    scene,obj,arm=r9.setup()
    verts,faces,skins,obj_path,side_path=export_mesh_and_skins(obj)
    for idx,specs in enumerate(r9.LOCAL_POSE):
        for skin in (0,1):
            r9.key_pose(arm.pose.bones[f'spk_skin_{skin}'],idx+1,specs[skin])
    scene.frame_start=1;scene.frame_end=3
    rig,meta=r9.export(scene,obj,arm)
    rig['animationGroup']=3992
    rig['sequenceId']=30007
    rest={'format':'spawnpk-v308-rest-pose-v1','vertices':[[int(round(c)) for c in v] for v in verts],'skins':skins}
    (OUT/'r10-rig-v1.json').write_text(json.dumps(rig,indent=2)+'\n',encoding='utf-8')
    (OUT/'r10-rest-pose.json').write_text(json.dumps(rest,indent=2)+'\n',encoding='utf-8')
    meta['sourceMesh']='r10-authored-rest.obj'
    meta['sourceSkinSidecar']='r10-authored-rest.obj.skins.json'
    (OUT/'r10-blender-evaluated.json').write_text(json.dumps(meta,indent=2)+'\n',encoding='utf-8')
    r9.bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'r10-full-authoring-hierarchy.blend'))
    manifest={
      'format':'spawnpk-v308-blender-authoring-fixture-v1',
      'blenderVersion':r9.bpy.app.version_string,
      'modelId':79999,'animationGroup':3992,'sequenceId':30007,
      'vertexCount':len(verts),'polygonCount':len(faces),'skins':sorted(set(skins)),
      'files':{
        'obj':obj_path.name,'skinSidecar':side_path.name,'rig':'r10-rig-v1.json',
        'restPose':'r10-rest-pose.json','blend':'r10-full-authoring-hierarchy.blend'
      }
    }
    (OUT/'r10-authoring-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(f'R10_BLENDER_RUNTIME_PASS version={r9.bpy.app.version_string}')
    print(f'R10_BLENDER_MESH_SKIN_EXPORT_PASS vertices={len(verts)} polygons={len(faces)} skins={sorted(set(skins))}')
    print('R10_BLENDER_HIERARCHY_BAKE_REUSE_PASS parent=spk_skin_0 child=spk_skin_1')
    print('R10_BLENDER_FULL_AUTHORING_EXPORT_PASS group=3992 sequence=30007 model=79999 frames=3')

if __name__=='__main__': main()
