import bpy
import json
import math
import os
from pathlib import Path

FMT = "spawnpk-v308-rig-v1"
OUT = Path(os.environ.get("R8_OUT", "/tmp/chat4-r8"))
OUT.mkdir(parents=True, exist_ok=True)

REST_VERTICES = [
    [-64, -64, -64], [64, -64, -64], [64, 64, -64], [-64, 64, -64],
    [-64, -64,  64], [64, -64,  64], [64, 64,  64], [-64, 64,  64],
]
FACES = [(0,1,2,3),(4,7,6,5),(0,4,5,1),(1,5,6,2),(2,6,7,3),(4,0,3,7)]
SKINS = [0,0,0,0,1,1,1,1]
PIVOT_OFFSETS = {0: [8,-8,0], 1: [-16,0,0]}
DURATIONS = [5,7,9]
FRAME_SPECS = [
    {
      0: {"scale":[1.0,1.0,1.0], "rot":[0,0,90], "trans":[12,0,0]},
      1: {"scale":[1.5,0.5,1.0], "rot":[0,0,-45], "trans":[0,20,0]},
    },
    {
      0: {"scale":[1.0,1.0,1.0], "rot":[45,-45,0], "trans":[0,0,8]},
      1: {"scale":[0.75,1.25,1.0], "rot":[45,0,45], "trans":[-10,0,0]},
    },
    {
      0: {"scale":[1.125,0.875,1.0], "rot":[0,0,22.5], "trans":[-7,5,0]},
      1: {"scale":[1.0,1.125,0.875], "rot":[0,-22.5,0], "trans":[3,-4,6]},
    },
]
EXPECTED_MAPPING = "spk=(blenderX,blenderY,blenderZ); boneLocalAxes=worldXYZ; rotation=(+X,+Y,-Z); poseEulerMode=ZXY"


def centroid_for_skin(skin):
    ids=[i for i,s in enumerate(SKINS) if s==skin]
    return [sum(REST_VERTICES[i][a] for i in ids)/len(ids) for a in range(3)]


def pivot_for_skin(skin):
    c=centroid_for_skin(skin); p=PIVOT_OFFSETS[skin]
    return [c[a]+p[a] for a in range(3)]


def setup_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene=bpy.context.scene
    scene.unit_settings.system='NONE'
    scene.render.fps=50

    mesh=bpy.data.meshes.new("SPK_R8_Mesh")
    mesh.from_pydata([tuple(v) for v in REST_VERTICES], [], FACES)
    mesh.update()
    mesh_obj=bpy.data.objects.new("SPK_R8_RigidMesh", mesh)
    bpy.context.collection.objects.link(mesh_obj)

    arm_data=bpy.data.armatures.new("SPK_R8_ArmatureData")
    arm_obj=bpy.data.objects.new("SPK_R8_Armature", arm_data)
    bpy.context.collection.objects.link(arm_obj)
    bpy.context.view_layer.objects.active=arm_obj
    arm_obj.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    for skin in (0,1):
        pivot=pivot_for_skin(skin)
        bone=arm_data.edit_bones.new(f"spk_skin_{skin}")
        bone.head=tuple(pivot)
        bone.tail=(pivot[0],pivot[1]+32,pivot[2])
        bone.roll=0.0
        bone.use_deform=True
    bpy.ops.object.mode_set(mode='OBJECT')

    for skin in (0,1):
        vg=mesh_obj.vertex_groups.new(name=f"spk_skin_{skin}")
        ids=[i for i,s in enumerate(SKINS) if s==skin]
        vg.add(ids,1.0,'REPLACE')
    mod=mesh_obj.modifiers.new("SPK_R8_ArmatureModifier",'ARMATURE')
    mod.object=arm_obj

    for skin in (0,1):
        pb=arm_obj.pose.bones[f"spk_skin_{skin}"]
        pb.rotation_mode='ZXY'
    return scene,mesh_obj,arm_obj


def set_pose_key(pb, frame_no, spec):
    tx,ty,tz=spec['trans']
    pb.location=(tx,ty,tz)
    rx,ry,rz=spec['rot']
    pb.rotation_euler=(math.radians(rx),math.radians(ry),math.radians(-rz))
    pb.scale=tuple(spec['scale'])
    pb.keyframe_insert(data_path='location',frame=frame_no)
    pb.keyframe_insert(data_path='rotation_euler',frame=frame_no)
    pb.keyframe_insert(data_path='scale',frame=frame_no)


def rounded(vals,digits=9):
    return [round(float(x),digits) for x in vals]


def export(scene,mesh_obj,arm_obj):
    frames=[]; evaluated={}
    for index,specs in enumerate(FRAME_SPECS):
        scene.frame_set(index+1)
        deps=bpy.context.evaluated_depsgraph_get()
        ev_mesh=mesh_obj.evaluated_get(deps)
        ev_arm=arm_obj.evaluated_get(deps)
        verts=[]
        for v in ev_mesh.data.vertices:
            co=ev_mesh.matrix_world @ v.co
            verts.append(rounded(co))
        evaluated[str(index)]=verts
        groups={}
        for skin in (0,1):
            pb=ev_arm.pose.bones[f"spk_skin_{skin}"]
            loc=rounded(pb.location)
            scl=rounded(pb.scale)
            eul=pb.rotation_euler.copy()
            rot=[round(math.degrees(eul.x),9),round(math.degrees(eul.y),9),round(-math.degrees(eul.z),9)]
            expected=specs[skin]
            if any(abs(loc[a]-expected['trans'][a])>1e-5 for a in range(3)):
                raise AssertionError(f"location mismatch f={index} skin={skin}: {loc} vs {expected['trans']}")
            if any(abs(scl[a]-expected['scale'][a])>1e-6 for a in range(3)):
                raise AssertionError(f"scale mismatch f={index} skin={skin}: {scl} vs {expected['scale']}")
            if any(abs(rot[a]-expected['rot'][a])>1e-5 for a in range(3)):
                raise AssertionError(f"rotation mismatch f={index} skin={skin}: {rot} vs {expected['rot']}")
            groups[str(skin)]={
              'pivotOffset':PIVOT_OFFSETS[skin],
              'scale':scl,
              'rotationDegrees':{'x':rot[0],'y':rot[1],'z':rot[2]},
              'translation':[int(round(x)) for x in loc],
            }
        frames.append({'index':index,'duration':DURATIONS[index],'groups':groups})
    rig={
      'format':FMT,'modelId':79999,'animationGroup':3994,'sequenceId':30005,
      'groups':[{'skin':0,'name':'lower'},{'skin':1,'name':'upper'}],
      'frames':frames,
    }
    meta={
      'blenderVersion':bpy.app.version_string,
      'mapping':EXPECTED_MAPPING,
      'poseRotationMode':'ZXY',
      'boneNames':['spk_skin_0','spk_skin_1'],
      'boneParents':[None,None],
      'weights':'one-hot 1.0 per vertex',
      'evaluatedVertices':evaluated,
    }
    return rig,meta


def main():
    scene,mesh_obj,arm_obj=setup_scene()
    for index,specs in enumerate(FRAME_SPECS):
        for skin in (0,1):
            set_pose_key(arm_obj.pose.bones[f"spk_skin_{skin}"],index+1,specs[skin])
    scene.frame_start=1; scene.frame_end=3
    rig,meta=export(scene,mesh_obj,arm_obj)
    rest={'format':'spawnpk-v308-rest-pose-v1','vertices':REST_VERTICES,'skins':SKINS}
    (OUT/'r8-armature-rig-v1.json').write_text(json.dumps(rig,indent=2)+'\n',encoding='utf-8')
    (OUT/'r8-rest-pose.json').write_text(json.dumps(rest,indent=2)+'\n',encoding='utf-8')
    (OUT/'r8-armature-evaluated.json').write_text(json.dumps(meta,indent=2)+'\n',encoding='utf-8')
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'r8-two-bone-rigid-armature.blend'))
    print(f"R8_BLENDER_ARMATURE_RUNTIME_PASS version={bpy.app.version_string}")
    print("R8_BLENDER_TWO_BONE_RIG_PASS bones=2 parents=none rigidWeights=one-hot")
    print("R8_BLENDER_POSE_KEYFRAMES_PASS frames=3 channels=location,rotation_euler,scale")
    print("R8_BLENDER_ARMATURE_RIG_V1_EXPORT_PASS group=3994 sequence=30005 groups=2 frames=3")
    print("R8_BLENDER_ARMATURE_AXIS_CONTRACT_PASS "+EXPECTED_MAPPING)

if __name__=='__main__':
    main()
