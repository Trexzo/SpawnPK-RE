package spk.local;

import java.io.*;
import java.util.*;

/** WORLD-R6 proves semantic world tiles survive production-base -> LocalLab-base reprojection. */
public final class HomeSceneRuntimeBaseTest {
    public static void main(String[] args)throws Exception{
        int[] seed={101,202,303,404};
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(seed.clone()));
        HomeObjectOverlayReplayer.Stats st=HomeObjectOverlayReplayer.replayHome(w,MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y);
        if(st.removePackets!=27||st.addPackets!=26||st.totalPackets()!=72) throw new AssertionError("stats "+st);

        byte[] raw=wire.toByteArray();
        IsaacCipher dec=new IsaacCipher(seed.clone());
        ByteArrayInputStream in=new ByteArrayInputStream(raw);
        int bx=-1,by=-1,mut=0,bases=0;
        List<HomeObjectOverlayRepository.Mutation> expected=HomeObjectOverlayRepository.all();
        boolean portalAdapted=false;
        while(in.available()>0){
            int op=((in.read()&255)-dec.nextInt())&255;
            if(op==85){
                byte[] p=Binary.readExactly(in,2);
                by=SceneObjectPacketCodec.decO(p[0]);
                bx=SceneObjectPacketCodec.decO(p[1]);
                bases++;
                continue;
            }
            if(mut>=expected.size()) throw new AssertionError("extra mutation packet op="+op);
            HomeObjectOverlayRepository.Mutation e=expected.get(mut++);
            if(bx<0||by<0) throw new AssertionError("mutation before scene base");
            int coord,shapeRot,wireId=-1;
            if(op==101){
                if(!e.isRemove()) throw new AssertionError("expected add seq="+e.seq+" got remove");
                byte[] p=Binary.readExactly(in,2);
                shapeRot=SceneObjectPacketCodec.decO(p[0]);
                coord=SceneObjectPacketCodec.decY(p[1]);
            }else if(op==151){
                if(!e.isAdd()) throw new AssertionError("expected remove seq="+e.seq+" got add");
                byte[] p=Binary.readExactly(in,4);
                coord=SceneObjectPacketCodec.decN(p[0]);
                wireId=SceneObjectPacketCodec.decS(p[1],p[2]);
                shapeRot=SceneObjectPacketCodec.decP(p[3]);
                if(wireId!=e.wireObjectId) throw new AssertionError("wire id seq="+e.seq+" got="+wireId+" expected="+e.wireObjectId);
            }else throw new AssertionError("unexpected opcode "+op);

            int localX=bx+((coord>>>4)&7), localY=by+(coord&7);
            int worldX=MovementState.REGION_BASE_X+localX;
            int worldY=MovementState.REGION_BASE_Y+localY;
            int shape=shapeRot>>>2,rot=shapeRot&3;
            if(worldX!=e.worldX||worldY!=e.worldY||shape!=e.shape||rot!=e.rotation)
                throw new AssertionError("semantic mismatch seq="+e.seq+" got="+worldX+","+worldY+" shape="+shape+" rot="+rot+" expected="+e);
            if(e.isAdd()&&e.wireObjectId==15477){
                if(e.localY!=35 || localY!=43) throw new AssertionError("portal base adaptation not exercised captured="+e.localY+" runtime="+localY);
                portalAdapted=true;
            }
        }
        if(mut!=expected.size()||bases!=st.basePackets||!portalAdapted) throw new AssertionError("coverage mut="+mut+" bases="+bases+" portal="+portalAdapted);
        System.out.println("WORLD_R6_SCENE_RUNTIME_BASE_PASS productionBase=3032,3448 runtimeBase="+MovementState.REGION_BASE_X+","+MovementState.REGION_BASE_Y+
            " mutations="+mut+" packets="+st.totalPackets()+" portal15477CapturedLocalY35RuntimeLocalY43=true");
    }
}
