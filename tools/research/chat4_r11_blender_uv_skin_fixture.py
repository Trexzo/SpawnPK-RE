import importlib.util
import json
import sys
import os
from pathlib import Path

OUT=Path(os.environ.get('R11_OUT','/tmp/chat4-r11'))
OUT.mkdir(parents=True,exist_ok=True)
HERE=Path(__file__).resolve().parent

spec=importlib.util.spec_from_file_location('chat4_r9',HERE/'chat4_r9_blender_hierarchy_fixture.py')
r9=importlib.util.module_from_spec(spec)
sys.modules['chat4_r9']=r9
spec.loader.exec_module(r9)

TEXTURE_ID=278
UV_LAYER_NAME='SPK_UV'
MATERIAL_NAME='SPK_Texture_278'
ANIMATION_GROUP=3990
SEQUENCE_ID=30009
MODEL_ID=79999


def skin_id_from_name(name):
    prefix='spk_skin_'
    if not name.startswith(prefix):
        return None
    try:
        value=int(name[len(prefix):])
    except ValueError:
        return None
    return value if 0 <= value <= 255 else None


def triangulate_mesh(obj):
    import bmesh
    bm=bmesh.new()
    bm.from_mesh(obj.data)
    bmesh.ops.triangulate(bm,faces=list(bm.faces))
    bm.to_mesh(obj.data)
    bm.free()
    obj.data.update()
    if len(obj.data.polygons) != 12 or any(len(p.vertices) != 3 for p in obj.data.polygons):
        raise AssertionError(f'expected 12 triangles after triangulation, got {len(obj.data.polygons)} polygons')


def make_blender_texture_material(obj):
    image=r9.bpy.data.images.new('SPK_Texture_278_Image',width=128,height=128,alpha=False)
    # Authored two-colour checker/atlas-like image. The exact v308 texture binary path
    # remains a separate previously-proven conversion/cache stage; this fixture proves
    # that texture identity and UVs originate in Blender.
    pixels=[]
    for y in range(128):
        for x in range(128):
            if ((x//32)+(y//32)) % 2 == 0:
                pixels.extend((0.0,1.0,1.0,1.0))
            else:
                pixels.extend((1.0,0.0,1.0,1.0))
    image.pixels=tuple(pixels)
    image.filepath_raw=str(OUT/'r11-texture-278.png')
    image.file_format='PNG'
    image.save()

    mat=r9.bpy.data.materials.new(MATERIAL_NAME)
    mat['spk_texture_id']=TEXTURE_ID
    mat['spk_texture_role']='LocalLab research bootstrap texture'
    mat.use_nodes=True
    nodes=mat.node_tree.nodes
    links=mat.node_tree.links
    bsdf=nodes.get('Principled BSDF')
    tex=nodes.new('ShaderNodeTexImage')
    tex.name='SPK_Texture_278'
    tex.image=image
    if bsdf is not None:
        links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
    obj.data.materials.append(mat)
    for p in obj.data.polygons:
        p.material_index=0
    return mat,image


def make_canonical_uvs(obj):
    uv=obj.data.uv_layers.new(name=UV_LAYER_NAME)
    canonical=((0.0,0.0),(1.0,0.0),(0.0,1.0))
    for poly in obj.data.polygons:
        if len(poly.loop_indices) != 3:
            raise AssertionError('R11 UV fixture requires triangulated polygons')
        for local_i,loop_i in enumerate(poly.loop_indices):
            uv.data[loop_i].uv=canonical[local_i]
    return uv


def extract_discrete_skins(obj):
    index_to_skin={vg.index:skin_id_from_name(vg.name) for vg in obj.vertex_groups}
    skins=[]
    for v in obj.data.vertices:
        candidates=[]
        for g in v.groups:
            skin=index_to_skin.get(g.group)
            if skin is not None and g.weight > 0:
                candidates.append((float(g.weight),skin))
        if not candidates:
            raise AssertionError(f'vertex {v.index} has no spk_skin_N weight')
        candidates.sort(key=lambda item:(-item[0],item[1]))
        skins.append(candidates[0][1])
    return skins


def export_obj_uv_skin(obj,mat):
    verts=[[round(float(c),9) for c in v.co] for v in obj.data.vertices]
    skins=extract_discrete_skins(obj)
    uv_layer=obj.data.uv_layers.get(UV_LAYER_NAME)
    if uv_layer is None:
        raise AssertionError('missing R11 Blender UV layer')

    obj_path=OUT/'r11-authored-rest.obj'
    side_path=OUT/'r11-authored-rest.obj.skins.json'
    material_path=OUT/'r11-material.json'

    vt_rows=[]
    face_rows=[]
    for poly in obj.data.polygons:
        if len(poly.loop_indices) != 3:
            raise AssertionError('OBJ exporter requires triangles')
        corners=[]
        for loop_i in poly.loop_indices:
            loop=obj.data.loops[loop_i]
            uv=uv_layer.data[loop_i].uv
            vt_rows.append((float(uv.x),float(uv.y)))
            vt_index=len(vt_rows)
            corners.append((int(loop.vertex_index)+1,vt_index))
        face_rows.append(corners)

    with obj_path.open('w',encoding='utf-8',newline='\n') as f:
        f.write('# Chat4 R11 Blender-authored textured/skinned rest mesh\n')
        f.write(f'# spk_texture_id {int(mat["spk_texture_id"])}\n')
        f.write(f'# uv_layer {UV_LAYER_NAME}\n')
        for x,y,z in verts:
            f.write(f'v {x:g} {y:g} {z:g}\n')
        for u,v in vt_rows:
            f.write(f'vt {u:.9g} {v:.9g}\n')
        for corners in face_rows:
            f.write('f '+' '.join(f'{vi}/{ti}' for vi,ti in corners)+'\n')

    sidecar={
      'format':'spawnpk-v308-vertex-skins-v1',
      'vertexCount':len(verts),
      'groups':skins,
    }
    side_path.write_text(json.dumps(sidecar,indent=2)+'\n',encoding='utf-8')

    material={
      'format':'spawnpk-v308-blender-material-v1',
      'materialName':mat.name,
      'textureId':int(mat['spk_texture_id']),
      'uvLayer':UV_LAYER_NAME,
      'image':'r11-texture-278.png',
      'mappingPolicy':'canonical triangle UV: (0,0),(1,0),(0,1)',
      'note':'Texture-278 cache conversion/injection is a separately proven exact-v308 stage; this file binds Blender-authored material identity to the model export.',
    }
    material_path.write_text(json.dumps(material,indent=2)+'\n',encoding='utf-8')
    return verts,skins,obj_path,side_path,material_path,len(face_rows),len(vt_rows)


def verify_blender_uv_contract(obj):
    uv=obj.data.uv_layers.get(UV_LAYER_NAME)
    expected=((0.0,0.0),(1.0,0.0),(0.0,1.0))
    for poly in obj.data.polygons:
        got=[tuple(round(float(x),9) for x in uv.data[li].uv) for li in poly.loop_indices]
        for i in range(3):
            if any(abs(got[i][a]-expected[i][a]) > 1e-7 for a in range(2)):
                raise AssertionError(f'UV mismatch polygon={poly.index}: {got}')
    return True


def main():
    scene,obj,arm=r9.setup()
    triangulate_mesh(obj)
    mat,image=make_blender_texture_material(obj)
    make_canonical_uvs(obj)
    verify_blender_uv_contract(obj)

    verts,skins,obj_path,side_path,material_path,triangle_count,uv_count=export_obj_uv_skin(obj,mat)

    for idx,specs in enumerate(r9.LOCAL_POSE):
        for skin in (0,1):
            r9.key_pose(arm.pose.bones[f'spk_skin_{skin}'],idx+1,specs[skin])
    scene.frame_start=1
    scene.frame_end=3

    rig,meta=r9.export(scene,obj,arm)
    rig['modelId']=MODEL_ID
    rig['animationGroup']=ANIMATION_GROUP
    rig['sequenceId']=SEQUENCE_ID

    rest={
      'format':'spawnpk-v308-rest-pose-v1',
      'vertices':[[int(round(c)) for c in v] for v in verts],
      'skins':skins,
    }

    (OUT/'r11-rig-v1.json').write_text(json.dumps(rig,indent=2)+'\n',encoding='utf-8')
    (OUT/'r11-rest-pose.json').write_text(json.dumps(rest,indent=2)+'\n',encoding='utf-8')

    meta['sourceMesh']=obj_path.name
    meta['sourceSkinSidecar']=side_path.name
    meta['sourceMaterial']=material_path.name
    meta['uvLayer']=UV_LAYER_NAME
    meta['textureId']=TEXTURE_ID
    meta['triangleCount']=triangle_count
    meta['uvLoopCount']=uv_count
    (OUT/'r11-blender-evaluated.json').write_text(json.dumps(meta,indent=2)+'\n',encoding='utf-8')

    r9.bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'r11-blender-uv-skin-hierarchy.blend'))

    manifest={
      'format':'spawnpk-v308-blender-uv-skin-animation-fixture-v1',
      'blenderVersion':r9.bpy.app.version_string,
      'modelId':MODEL_ID,
      'animationGroup':ANIMATION_GROUP,
      'sequenceId':SEQUENCE_ID,
      'textureId':TEXTURE_ID,
      'vertexCount':len(verts),
      'triangleCount':triangle_count,
      'uvLoopCount':uv_count,
      'skins':sorted(set(skins)),
      'hierarchy':{'spk_skin_0':None,'spk_skin_1':'spk_skin_0'},
      'uvContract':'each triangle uses (0,0),(1,0),(0,1), so its texture basis is the triangle itself and follows the same animated vertices',
      'files':{
        'obj':obj_path.name,
        'skinSidecar':side_path.name,
        'material':material_path.name,
        'texturePng':'r11-texture-278.png',
        'rig':'r11-rig-v1.json',
        'restPose':'r11-rest-pose.json',
        'blend':'r11-blender-uv-skin-hierarchy.blend',
      },
    }
    (OUT/'r11-authoring-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')

    print(f'R11_BLENDER_UV_RUNTIME_PASS version={r9.bpy.app.version_string}')
    print(f'R11_BLENDER_UV_LAYER_PASS triangles={triangle_count} uvLoops={uv_count} texture={TEXTURE_ID} layer={UV_LAYER_NAME}')
    print(f'R11_BLENDER_UV_SKIN_EXPORT_PASS vertices={len(verts)} triangles={triangle_count} skins={sorted(set(skins))}')
    print(f'R11_BLENDER_MATERIAL_BINDING_PASS material={mat.name} texture={int(mat["spk_texture_id"])} image={image.name}')
    print(f'R11_BLENDER_HIERARCHY_UV_RIG_V1_EXPORT_PASS group={ANIMATION_GROUP} sequence={SEQUENCE_ID} model={MODEL_ID} frames=3')

if __name__=='__main__':
    main()
