import bpy
import json
import math
import os
from pathlib import Path
from mathutils import Matrix, Vector, Euler

FMT = "spawnpk-v308-rig-v1"
OUT = Path(os.environ.get("R9_OUT", "/tmp/chat4-r9"))
OUT.mkdir(parents=True, exist_ok=True)

REST_VERTICES = [
    [-64, -64, -64], [64, -64, -64], [64, 64, -64], [-64, 64, -64],
    [-64, -64,  64], [64, -64,  64], [64, 64,  64], [-64, 64,  64],
]
FACES = [(0,1,2,3),(4,7,6,5),(0,4,5,1),(1,5,6,2),(2,6,7,3),(4,0,3,7)]
SKINS = [0,0,0,0,1,1,1,1]
PIVOT_OFFSETS = {0:[8,-8,0], 1:[-16,0,0]}
DURATIONS = [6,8,10]
LOCAL_POSE = [
    {
      0:{"location":[10,0,0], "rotation":[0,0,-30], "scale":[1,1,1]},
      1:{"location":[0,15,0], "rotation":[20,0,45], "scale":[1.25,0.75,1]},
    },
    {
      0:{"location":[0,-5,10], "rotation":[30,-20,0], "scale":[1.1,1.1,1.1]},
      1:{"location":[-8,0,5], "rotation":[0,15,-35], "scale":[0.8,1.2,1]},
    },
    {
      0:{"location":[-6,4,0], "rotation":[0,0,22.5], "scale":[0.9,0.9,0.9]},
      1:{"location":[3,-6,4], "rotation":[-30,10,30], "scale":[1,0.9,1.1]},
    },
]
MAPPING = "hierarchy baked from poseBone.matrix @ restBone.matrix_local^-1; spk rotation=(+X,+Y,-Z); Euler=ZXY; fixed rest-group pivots"


def centroid(skin):
    ids=[i for i,s in enumerate(SKINS) if s==skin]
    return Vector(tuple(sum(REST_VERTICES[i][a] for i in ids)/len(ids) for a in range(3)))


def pivot(skin):
    return centroid(skin)+Vector(PIVOT_OFFSETS[skin])


def setup():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene=bpy.context.scene
    scene.unit_settings.system='NONE'
    scene.render.fps=50

    mesh=bpy.data.meshes.new('SPK_R9_Mesh')
    mesh.from_pydata([tuple(v) for v in REST_VERTICES],[],FACES)
    mesh.update()
    obj=bpy.data.objects.new('SPK_R9_HierarchicalMesh',mesh)
    bpy.context.collection.objects.link(obj)

    arm_data=bpy.data.armatures.new('SPK_R9_ArmatureData')
    arm=bpy.data.objects.new('SPK_R9_Armature',arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active=arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    bones={}
    for skin in (0,1):
        p=pivot(skin)
        b=arm_data.edit_bones.new(f'spk_skin_{skin}')
        b.head=tuple(p)
        b.tail=(p.x,p.y+32,p.z)
        b.roll=0.0
        b.use_deform=True
        bones[skin]=b
    bones[1].parent=bones[0]
    bones[1].use_connect=False
    bpy.ops.object.mode_set(mode='OBJECT')

    for skin in (0,1):
        vg=obj.vertex_groups.new(name=f'spk_skin_{skin}')
        ids=[i for i,s in enumerate(SKINS) if s==skin]
        vg.add(ids,1.0,'REPLACE')
        arm.pose.bones[f'spk_skin_{skin}'].rotation_mode='ZXY'
    mod=obj.modifiers.new('SPK_R9_ArmatureModifier','ARMATURE')
    mod.object=arm
    return scene,obj,arm


def key_pose(pb,frame,spec):
    pb.location=tuple(spec['location'])
    rx,ry,rz=spec['rotation']
    pb.rotation_euler=(math.radians(rx),math.radians(ry),math.radians(rz))
    pb.scale=tuple(spec['scale'])
    for path in ('location','rotation_euler','scale'):
        pb.keyframe_insert(data_path=path,frame=frame)


def rounded(v,n=9):
    return [round(float(x),n) for x in v]


def max_matrix_diff(a,b):
    return max(abs(a[r][c]-b[r][c]) for r in range(4) for c in range(4))


def extract_group(ev_arm,skin):
    name=f'spk_skin_{skin}'
    pb=ev_arm.pose.bones[name]
    rest=ev_arm.data.bones[name].matrix_local.copy()
    delta=pb.matrix.copy() @ rest.inverted()
    loc,quat,scl=delta.decompose()
    recon=Matrix.LocRotScale(loc,quat,scl)
    shear_err=max_matrix_diff(delta,recon)
    if shear_err>2e-5:
        raise AssertionError(f'non-decomposable/shear transform skin={skin}: matrixError={shear_err}')

    e=quat.to_euler('ZXY')
    rot=[math.degrees(e.x),math.degrees(e.y),-math.degrees(e.z)]
    p=pivot(skin)
    A=delta.to_3x3()
    t=delta.translation-p+(A@p)
    tq=[int(math.floor(x+0.5)) if x>=0 else -int(math.floor(-x+0.5)) for x in t]
    return {
      'pivotOffset':PIVOT_OFFSETS[skin],
      'scale':rounded(scl),
      'rotationDegrees':{'x':round(rot[0],9),'y':round(rot[1],9),'z':round(rot[2],9)},
      'translation':tq,
    }, delta, shear_err, rounded(t)


def float_rig_point(rest,skin,g):
    p=pivot(skin)
    r=g['rotationDegrees']
    e=Euler((math.radians(r['x']),math.radians(r['y']),math.radians(-r['z'])),'ZXY')
    R=e.to_matrix()
    S=Matrix.Diagonal(Vector(g['scale']))
    A=R@S
    v=Vector(rest)
    t=Vector(g['translation'])
    return p + A@(v-p) + t


def export(scene,obj,arm):
    frames=[]; meta_frames={}; global_max_recon=0.0
    for idx in range(3):
        scene.frame_set(idx+1)
        deps=bpy.context.evaluated_depsgraph_get()
        ev_obj=obj.evaluated_get(deps)
        ev_arm=arm.evaluated_get(deps)
        eval_verts=[rounded(ev_obj.matrix_world@v.co) for v in ev_obj.data.vertices]
        groups={}; shear={}; tfloat={}
        for skin in (0,1):
            g,delta,se,tf=extract_group(ev_arm,skin)
            groups[str(skin)]=g; shear[str(skin)]=se; tfloat[str(skin)]=tf
        baked=[]
        for i,v in enumerate(REST_VERTICES):
            q=float_rig_point(v,SKINS[i],groups[str(SKINS[i])])
            baked.append(rounded(q))
        max_recon=max(abs(baked[i][a]-eval_verts[i][a]) for i in range(8) for a in range(3))
        global_max_recon=max(global_max_recon,max_recon)
        if max_recon>1.1:
            raise AssertionError(f'baked hierarchy reconstruction too far frame={idx}: {max_recon}')
        frames.append({'index':idx,'duration':DURATIONS[idx],'groups':groups})
        meta_frames[str(idx)]={
          'blenderEvaluatedVertices':eval_verts,
          'bakedFloatVerticesWithIntegerTranslation':baked,
          'maxBakedVsBlender':max_recon,
          'shearMatrixError':shear,
          'translationFloatBeforeIntegerQuantization':tfloat,
        }
    rig={
      'format':FMT,'modelId':79999,'animationGroup':3993,'sequenceId':30006,
      'groups':[{'skin':0,'name':'parent'},{'skin':1,'name':'child'}],
      'frames':frames,
    }
    meta={
      'blenderVersion':bpy.app.version_string,
      'mapping':MAPPING,
      'hierarchy':{'spk_skin_0':None,'spk_skin_1':'spk_skin_0'},
      'weights':'one-hot 1.0 per vertex',
      'restriction':'parent scale may be uniform; exporter rejects matrices with measurable shear',
      'globalMaxBakedVsBlenderBeforeV308Quantization':global_max_recon,
      'frames':meta_frames,
    }
    return rig,meta


def main():
    scene,obj,arm=setup()
    for idx,specs in enumerate(LOCAL_POSE):
        for skin in (0,1):
            key_pose(arm.pose.bones[f'spk_skin_{skin}'],idx+1,specs[skin])
    scene.frame_start=1;scene.frame_end=3
    rig,meta=export(scene,obj,arm)
    rest={'format':'spawnpk-v308-rest-pose-v1','vertices':REST_VERTICES,'skins':SKINS}
    (OUT/'r9-hierarchy-rig-v1.json').write_text(json.dumps(rig,indent=2)+'\n',encoding='utf-8')
    (OUT/'r9-rest-pose.json').write_text(json.dumps(rest,indent=2)+'\n',encoding='utf-8')
    (OUT/'r9-hierarchy-evaluated.json').write_text(json.dumps(meta,indent=2)+'\n',encoding='utf-8')
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'r9-parent-child-rigid-armature.blend'))
    print(f'R9_BLENDER_HIERARCHY_RUNTIME_PASS version={bpy.app.version_string}')
    print('R9_BLENDER_PARENT_CHILD_PASS parent=spk_skin_0 child=spk_skin_1 rigidWeights=one-hot')
    print('R9_HIERARCHY_GLOBAL_DELTA_BAKE_PASS formula=pose.matrix@rest.matrix_local^-1')
    print(f"R9_HIERARCHY_NO_SHEAR_GUARD_PASS maxBakedVsBlender={meta['globalMaxBakedVsBlenderBeforeV308Quantization']:.9f}")
    print('R9_BLENDER_HIERARCHY_RIG_V1_EXPORT_PASS group=3993 sequence=30006 groups=2 frames=3')

if __name__=='__main__':
    main()
