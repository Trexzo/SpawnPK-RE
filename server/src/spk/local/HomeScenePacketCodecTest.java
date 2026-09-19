package spk.local;

import java.io.*;
import java.util.*;

/** Exact-current-client static certification for packet 85/101/151 encoding. */
public final class HomeScenePacketCodecTest {
    public static void main(String[] args) throws Exception {
        // Portal 15477 @ local 52,35 -> chunk base 48,32, shape 10 rot 0.
        bytes(SceneObjectPacketCodec.sceneBase85(48,32), 0xE0,0xD0); // Y first: -32, X second: -48
        bytes(SceneObjectPacketCodec.objectRemove101(52,35,48,32,10,0), 0xD8,0x43);
        bytes(SceneObjectPacketCodec.objectAdd151(15477,52,35,48,32,10,0), 0xC3,0x75,0x3C,0x58);

        // Decode mirror must recover exact semantics.
        decode85(SceneObjectPacketCodec.sceneBase85(48,32),48,32);
        decode101(SceneObjectPacketCodec.objectRemove101(52,35,48,32,10,0),48,32,52,35,10,0);
        decode151(SceneObjectPacketCodec.objectAdd151(15477,52,35,48,32,10,0),48,32,15477,52,35,10,0);

        // Loot Chest preserves production wire ID 43484, not only 15-bit scene alias 10716.
        byte[] loot=SceneObjectPacketCodec.objectAdd151(43484,58,41,56,40,10,1);
        decode151(loot,56,40,43484,58,41,10,1);
        if(SceneObjectPacketCodec.decS(loot[1],loot[2])!=43484) fail("loot wire ID truncated");

        // Every V9.06 mutation round-trips through the exact packet codec.
        int remove=0,add=0,baseChanges=0,lastBx=-1,lastBy=-1;
        for(HomeObjectOverlayRepository.Mutation m:HomeObjectOverlayRepository.all()) {
            int bx=SceneObjectPacketCodec.chunkBase(m.localX), by=SceneObjectPacketCodec.chunkBase(m.localY);
            if(bx!=lastBx||by!=lastBy){decode85(SceneObjectPacketCodec.sceneBase85(bx,by),bx,by);baseChanges++;lastBx=bx;lastBy=by;}
            if(m.isRemove()) { decode101(SceneObjectPacketCodec.objectRemove101(m.localX,m.localY,bx,by,m.shape,m.rotation),bx,by,m.localX,m.localY,m.shape,m.rotation); remove++; }
            else { decode151(SceneObjectPacketCodec.objectAdd151(m.wireObjectId,m.localX,m.localY,bx,by,m.shape,m.rotation),bx,by,m.wireObjectId,m.localX,m.localY,m.shape,m.rotation); add++; }
        }
        eq(baseChanges,19,"base packet changes"); eq(remove,27,"remove"); eq(add,26,"add");

        if(!HomeWorldManifest.EXACT_PACKET_101_151_SERIALIZER_CERTIFIED) fail("manifest certification flag false");
        HomeWorldManifest.requireSceneSerializerCertification();

        HomeNpcRuntimePlan.Plan near=HomeNpcRuntimePlan.nearbyInitial(3084,3497);
        if(near.size()==0) fail("nearby HOME NPC plan empty");
        HashSet<Integer> idx=new HashSet<>();
        for(NpcEntity n:near.entities){ if(!idx.add(n.sceneIndex))fail("duplicate planned scene index"); if(n.sceneIndex<100)fail("world scene index not isolated"); }

        System.out.println("WORLD_R2_SCENE_CODEC_PASS packet85Exact=true packet101Exact=true packet151Exact=true overlayRoundTrip=53 base85=19 remove101=27 add151=26 nearbyNpcPlan="+near.size());
    }

    private static void decode85(byte[] p,int expX,int expY){
        if(p.length!=2)fail("85 length"); int y=SceneObjectPacketCodec.decO(p[0]),x=SceneObjectPacketCodec.decO(p[1]); eq(x,expX,"85 x");eq(y,expY,"85 y");
    }
    private static void decode101(byte[] p,int bx,int by,int ex,int ey,int shape,int rot){
        if(p.length!=2)fail("101 length"); int sr=SceneObjectPacketCodec.decO(p[0]),coord=SceneObjectPacketCodec.decY(p[1]);
        int x=bx+((coord>>>4)&7),y=by+(coord&7);eq(x,ex,"101 x");eq(y,ey,"101 y");eq(sr>>>2,shape,"101 shape");eq(sr&3,rot,"101 rot");
    }
    private static void decode151(byte[] p,int bx,int by,int obj,int ex,int ey,int shape,int rot){
        if(p.length!=4)fail("151 length"); int coord=SceneObjectPacketCodec.decN(p[0]),id=SceneObjectPacketCodec.decS(p[1],p[2]),sr=SceneObjectPacketCodec.decP(p[3]);
        int x=bx+((coord>>>4)&7),y=by+(coord&7);eq(id,obj,"151 objectId");eq(x,ex,"151 x");eq(y,ey,"151 y");eq(sr>>>2,shape,"151 shape");eq(sr&3,rot,"151 rot");
    }
    private static void bytes(byte[] a,int...e){if(a.length!=e.length)fail("fixture length");for(int i=0;i<e.length;i++)if((a[i]&255)!=e[i])fail("fixture byte "+i+" got="+(a[i]&255)+" exp="+e[i]);}
    private static void eq(int a,int b,String w){if(a!=b)fail(w+" "+a+" != "+b);} private static void fail(String s){throw new AssertionError(s);}
}
