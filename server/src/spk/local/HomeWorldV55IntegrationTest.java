package spk.local;

import java.io.*;
import java.util.*;

/** Offline certification for WORLD-R7 rebased onto exact MAINLINE v5.5. */
public final class HomeWorldV55IntegrationTest {
    public static void main(String[] args)throws Exception{
        int[] seed={7,11,13,17};
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(seed.clone()));
        MovementState m=new MovementState();
        PetState ps=new PetState();
        NpcRegistry r=new NpcRegistry();
        HomeWorldRuntimePlan home=new HomeWorldRuntimePlan();

        HomeObjectOverlayReplayer.Stats scene=home.replayScene(w,MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y);
        r.bootstrapHome(w,m,ps,home);
        if(scene.totalPackets()!=72)throw new AssertionError("scene packets "+scene);
        if(r.visibleCount()!=29)throw new AssertionError("initial HOME visible="+r.visibleCount());
        assertUnique(r.snapshot());
        NpcEntity fountain=findDef(r.snapshot(),1799);
        if(fountain==null||fountain.x!=3079||fountain.y!=3492)throw new AssertionError("production Blood Fountain missing "+fountain);
        if(findAt(r.snapshot(),1488,m.x()+2,m.y())!=null)throw new AssertionError("old diagnostic player dummy survived");
        if(findAt(r.snapshot(),1489,m.x()+2,m.y()+2)!=null)throw new AssertionError("old diagnostic PvM dummy survived");

        PetDefinitionRepository.Def def=PetDefinitionRepository.get(22519);
        if(def==null)throw new AssertionError("pet mapping 22519 missing");
        String spawned=r.spawnPet(def,m,w);
        if(!spawned.startsWith("PET_SPAWN_OK"))throw new AssertionError(spawned);
        if(r.pet()==null||r.pet().sceneIndex!=NpcRegistry.PET_INDEX)throw new AssertionError("pet missing");
        if(countHome(r.snapshot())!=29)throw new AssertionError("HOME count changed after pet spawn");

        int[] xs={3090,3095,3098};
        int[] ys={3500,3505,3506};
        String accepted=m.accept(new MovementRequest(164,false,xs,ys,new byte[0]));
        if(!accepted.startsWith("ACCEPTED"))throw new AssertionError(accepted);
        long tick=0; boolean saw1488=false,saw1489=false,sawVisibilityDelta=false,sawPetTrail=false;
        while(m.queued()>0 && tick<40){
            MovementState.Tick mt=m.advance();
            if(mt!=null) r.queueOwnerMovement(mt);
            String diag=r.tickHome(m,w,home,++tick);
            if(diag.contains("worldAdd=")&&!diag.contains("worldAdd=0 worldRemove=0"))sawVisibilityDelta=true;
            if(r.hasQueuedFollow()){
                String pd=r.tickFollow(m,w);
                if(pd!=null && pd.contains("CARDINAL_TRAIL"))sawPetTrail=true;
            }
            assertUnique(r.snapshot());
            if(findDef(r.snapshot(),1488)!=null)saw1488=true;
            if(findDef(r.snapshot(),1489)!=null)saw1489=true;
        }
        if(!saw1488||!saw1489||!sawVisibilityDelta)throw new AssertionError("north population transition 1488="+saw1488+" 1489="+saw1489+" delta="+sawVisibilityDelta);
        if(!sawPetTrail)throw new AssertionError("v5.6 cardinal breadcrumb pet follow was not exercised");
        if(home.clientVisibleWorldNpcCount()!=countHome(r.snapshot()))throw new AssertionError("WORLD visibility/registry mismatch home="+home.clientVisibleWorldNpcCount()+" registry="+countHome(r.snapshot()));
        if(r.pet()!=null && LocalSession.chebyshev(r.pet().x,r.pet().y,m.x(),m.y())>8)throw new AssertionError("pet separation regression");

        System.out.println("V561_WORLD_R7_MAINLINE_INTEGRATION_PASS scenePackets="+scene.totalPackets()+" initialHome=29 stableWorldScene=101..148 petScene=4 ticked="+tick+
            " northDummiesVisible=true unifiedRegistry=true v56CardinalPet=true finalHomeVisible="+countHome(r.snapshot())+" finalTotalVisible="+r.visibleCount());
    }

    private static int countHome(List<NpcEntity> xs){int n=0;for(NpcEntity x:xs)if(HomeWorldRuntimePlan.isHomeWorldSceneIndex(x.sceneIndex))n++;return n;}
    private static NpcEntity findDef(List<NpcEntity> xs,int def){for(NpcEntity x:xs)if(x.definitionId==def)return x;return null;}
    private static NpcEntity findAt(List<NpcEntity> xs,int def,int x,int y){for(NpcEntity n:xs)if(n.definitionId==def&&n.x==x&&n.y==y)return n;return null;}
    private static void assertUnique(List<NpcEntity> xs){HashSet<Integer>s=new HashSet<>();for(NpcEntity n:xs)if(!s.add(n.sceneIndex))throw new AssertionError("duplicate scene "+n.sceneIndex);}
}
