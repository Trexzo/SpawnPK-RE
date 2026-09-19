package spk.local;

import java.util.*;
import java.util.Arrays;

/** Exact new-NPC optional particle-selector field: AUTO absent vs explicit u8. */
public final class NpcSpawnPresentationWorkbenchTest {
    public static void main(String[] args){
        NpcEntity pet=new NpcEntity(4,8210,3087,3495,true,28807,1);
        byte[] legacy=NpcSyncEncoder.encode(Collections.emptyList(),Collections.singletonList(pet),3087,3495);
        byte[] auto=NpcSyncEncoder.encode(Collections.emptyList(),Collections.singletonList(pet),3087,3495,Collections.emptyMap());
        if(!Arrays.equals(legacy,auto))throw new AssertionError("AUTO changed legacy bytes");

        byte[] zero=NpcSyncEncoder.encode(Collections.emptyList(),Collections.singletonList(pet),3087,3495,
            Collections.singletonMap(4,NpcSpawnPresentation.particle(0)));
        byte[] three=NpcSyncEncoder.encode(Collections.emptyList(),Collections.singletonList(pet),3087,3495,
            Collections.singletonMap(4,NpcSpawnPresentation.particle(3)));
        Parsed a=parse(auto), z=parse(zero), t=parse(three);
        if(a.hasSelector)throw new AssertionError("AUTO selector present");
        if(!z.hasSelector||z.selector!=0)throw new AssertionError("explicit0 "+z);
        if(!t.hasSelector||t.selector!=3)throw new AssertionError("explicit3 "+t);
        if(Arrays.equals(auto,zero)||Arrays.equals(zero,three))throw new AssertionError("selector wire states collapsed");
        System.out.println("V591_NPC_SPAWN_PRESENTATION_WORKBENCH_PASS autoAbsent=true explicit0Presence=true explicit3=true legacyBytesUnchanged=true fieldWidth=8 authority=EXACT_CURRENT_CLIENT");
    }
    private static Parsed parse(byte[] b){
        BR r=new BR(b); if(r.read(8)!=0)throw new AssertionError("existing count");
        if(r.read(14)!=4)throw new AssertionError("scene"); r.read(5);r.read(5);
        boolean has=r.read(1)==1; int sel=has?r.read(8):-1;
        if(r.read(1)!=0||r.read(1)!=0||r.read(1)!=0)throw new AssertionError("later optional fields");
        if(r.read(14)!=8210)throw new AssertionError("definition"); if(r.read(1)!=1)throw new AssertionError("owner mask flag");
        return new Parsed(has,sel);
    }
    static final class Parsed{final boolean hasSelector;final int selector;Parsed(boolean h,int s){hasSelector=h;selector=s;}public String toString(){return hasSelector+":"+selector;}}
    static final class BR{final byte[] b;int bit;BR(byte[] b){this.b=b;}int read(int n){int v=0;for(int i=0;i<n;i++){int p=bit++;v=(v<<1)|((b[p>>>3]>>>(7-(p&7)))&1);}return v;}}
}
