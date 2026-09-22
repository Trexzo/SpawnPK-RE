import bpy
import json
import math
import os
from pathlib import Path

FMT = "spawnpk-v308-rig-v1"
OUT = Path(os.environ.get("R7_OUT", "/tmp/chat4-r7"))
OUT.mkdir(parents=True, exist_ok=True)

REST_VERTICES = [
    [-64, -64, -64], [64, -64, -64], [64, 64, -64], [-64, 64, -64],
    [-64, -64,  64], [64, -64,  64], [64, 64,  64], [-64, 64,  64],
]
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

EXPECTED_MAPPING = "spk=(blenderX,blenderY,blenderZ); rotation=(+X,+Y,-Z); blenderEulerMode=ZXY"

def centroid_for_skin(skin):
    ids=[i for i,s in enumerate(SKINS) if s==skin]
    return [sum(REST_VERTICES[i][a] for i in ids)/len(ids) for a in range(3)]

def clear_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene=bpy.context.scene
    scene.unit_settings.system='NONE'
    scene.render.fps=50
    return scene

def make_group_object(skin):
    ids=[i for i,s in enumerate(SKINS) if s==skin]
    centroid=centroid_for_skin(skin)
    po=PIVOT_OFFSETS[skin]
    pivot=[centroid[a]+po[a] for a in range(3)]
    local=[tuple(REST_VERTICES[i][a]-pivot[a] for a in range(3)) for i in ids]
    mesh=bpy.data.meshes.new(f"SPK_Group_{skin}_Mesh")
    mesh.from_pydata(local, [], [(0,1,2,3)])
    mesh.update()
    obj=bpy.data.objects.new(f"SPK_Group_{skin}",mesh)
    bpy.context.collection.objects.link(obj)
    obj.rotation_mode='ZXY'
    obj["spk_skin"]=skin
    obj["spk_pivot_offset"]=PIVOT_OFFSETS[skin]
    obj["spk_rest_centroid"]=centroid
    obj["spk_rest_pivot"]=pivot
    return obj, ids, pivot

def set_key(obj, pivot, frame_no, spec):
    tx,ty,tz=spec["trans"]
    obj.location=(pivot[0]+tx,pivot[1]+ty,pivot[2]+tz)
    rx,ry,rz=spec["rot"]
    obj.rotation_euler=(math.radians(rx), math.radians(ry), math.radians(-rz))
    obj.scale=tuple(spec["scale"])
    obj.keyframe_insert(data_path="location",frame=frame_no)
    obj.keyframe_insert(data_path="rotation_euler",frame=frame_no)
    obj.keyframe_insert(data_path="scale",frame=frame_no)

def rounded_tuple(vals, digits=9):
    return [round(float(x),digits) for x in vals]

def extract_rig(scene, group_objs):
    frames=[]
    blender_eval={}
    for index,specs in enumerate(FRAME_SPECS):
        frame_no=index+1
        scene.frame_set(frame_no)
        groups={}
        eval_vertices=[None]*len(REST_VERTICES)
        for skin,(obj,ids,pivot) in group_objs.items():
            deps=bpy.context.evaluated_depsgraph_get()
            ev=obj.evaluated_get(deps)
            centroid=centroid_for_skin(skin)
            loc=ev.location
            scl=ev.scale
            eul=ev.rotation_euler.copy()
            for local_i,rest_i in enumerate(ids):
                co=ev.matrix_world @ ev.data.vertices[local_i].co
                eval_vertices[rest_i]=rounded_tuple(co,9)

            trans=[int(round(loc[a]-pivot[a])) for a in range(3)]
            rot=[
                round(math.degrees(eul.x),9),
                round(math.degrees(eul.y),9),
                round(-math.degrees(eul.z),9),
            ]
            scale=rounded_tuple(scl,9)
            po=[int(round(pivot[a]-centroid[a])) for a in range(3)]
            groups[str(skin)]={
                "pivotOffset":po,
                "scale":scale,
                "rotationDegrees":{"x":rot[0],"y":rot[1],"z":rot[2]},
                "translation":trans,
            }
            expected=specs[skin]
            if trans != expected["trans"]:
                raise AssertionError(f"translation mismatch frame={index} skin={skin}: {trans} vs {expected['trans']}")
            if any(abs(scale[a]-expected["scale"][a])>1e-7 for a in range(3)):
                raise AssertionError(f"scale mismatch frame={index} skin={skin}: {scale} vs {expected['scale']}")
            if any(abs(rot[a]-expected["rot"][a])>1e-5 for a in range(3)):
                raise AssertionError(f"rotation mismatch frame={index} skin={skin}: {rot} vs {expected['rot']}")
        if any(v is None for v in eval_vertices):
            raise AssertionError("missing evaluated vertex")
        blender_eval[str(index)]=eval_vertices
        frames.append({"index":index,"duration":DURATIONS[index],"groups":groups})
    return {
      "format":FMT,
      "modelId":79999,
      "animationGroup":3995,
      "sequenceId":30004,
      "groups":[{"skin":0,"name":"lower"},{"skin":1,"name":"upper"}],
      "frames":frames,
    }, blender_eval

def main():
    scene=clear_scene()
    group_objs={}
    for skin in (0,1):
        obj,ids,pivot=make_group_object(skin)
        group_objs[skin]=(obj,ids,pivot)
    for index,specs in enumerate(FRAME_SPECS):
        for skin in (0,1):
            obj,ids,pivot=group_objs[skin]
            set_key(obj,pivot,index+1,specs[skin])
    scene.frame_start=1
    scene.frame_end=len(FRAME_SPECS)
    rig,blender_eval=extract_rig(scene,group_objs)
    rest={"format":"spawnpk-v308-rest-pose-v1","vertices":REST_VERTICES,"skins":SKINS}
    meta={
      "blenderVersion":bpy.app.version_string,
      "mapping":EXPECTED_MAPPING,
      "keyframeFrames":[1,2,3],
      "objectRotationMode":"ZXY",
      "blenderEvaluatedVertices":blender_eval,
    }
    (OUT/"r7-rig-v1.json").write_text(json.dumps(rig,indent=2)+"\n",encoding="utf-8")
    (OUT/"r7-rest-pose.json").write_text(json.dumps(rest,indent=2)+"\n",encoding="utf-8")
    (OUT/"r7-blender-evaluated.json").write_text(json.dumps(meta,indent=2)+"\n",encoding="utf-8")
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"r7-two-group-fixture.blend"))
    print(f"R7_BLENDER_RUNTIME_EXECUTED_PASS version={bpy.app.version_string}")
    print("R7_BLENDER_NATIVE_KEYFRAMES_PASS frames=3 groups=2 channels=location,rotation_euler,scale")
    print("R7_BLENDER_AXIS_CONTRACT_EMIT_PASS "+EXPECTED_MAPPING)
    print("R7_BLENDER_RIG_V1_EXPORT_PASS group=3995 sequence=30004 groups=2 frames=3")
    print(f"R7_OUTPUT_DIR {OUT}")

if __name__=="__main__":
    main()
