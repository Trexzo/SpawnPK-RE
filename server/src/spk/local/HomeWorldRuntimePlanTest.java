package spk.local;

import java.io.*;
import java.util.*;

public final class HomeWorldRuntimePlanTest {
    public static void main(String[] args) throws Exception {
        HomeWorldRuntimePlan p=new HomeWorldRuntimePlan();
        eq(47,p.trackedWorldNpcCount(),"tracked HOME NPCs");

        // Scene replay contract: exact 53 semantic mutations become 19 packet85 base changes + 27 removes + 26 adds.
        ByteArrayOutputStream sink=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(sink,new IsaacCipher(new int[]{0,0,0,0}));
        HomeObjectOverlayReplayer.Stats stats=p.replayScene(w);
        eq(19,stats.basePackets,"packet85 count");
        eq(27,stats.removePackets,"packet101 count");
        eq(26,stats.addPackets,"packet151 count");
        eq(72,stats.totalPackets(),"total scene packets");
        require(sink.size()>0,"scene replay wrote no bytes");

        List<NpcEntity> initial=p.bootstrapNpcs(MovementState.INITIAL_X,MovementState.INITIAL_Y);
        eq(29,initial.size(),"initial visible HOME NPCs");
        eq(29,p.clientVisibleWorldNpcCount(),"tracked client-visible HOME NPCs");
        assertSigned5(initial,MovementState.INITIAL_X,MovementState.INITIAL_Y);
        unique(initial);

        // Walk a deterministic tour through the production hub. The runtime plan must produce
        // stable add/remove transitions while never attempting an out-of-range packet65 add.
        int[][] tour={
            {3087,3495},{3095,3506},{3107,3506},{3100,3513},{3082,3512},
            {3078,3500},{3078,3512},{3090,3499},{3094,3507},{3087,3495}
        };
        int adds=0,removes=0,walks=0;
        HashSet<Integer> everVisible=new HashSet<>();
        for(NpcEntity n:initial) everVisible.add(n.sceneIndex);
        long tick=1;
        for(int lap=0;lap<16;lap++){
            for(int[] xy:tour){
                HomeWorldRuntimePlan.NpcDelta d=p.tick(tick++,xy[0],xy[1]);
                adds+=d.added.size(); removes+=d.removedSceneIndexes.size(); walks+=d.walkDirectionBySceneIndex.size();
                assertSigned5(d.added,xy[0],xy[1]);
                for(NpcEntity n:d.added){ require(HomeWorldRuntimePlan.isHomeWorldSceneIndex(n.sceneIndex),"added non-WORLD scene index "+n); everVisible.add(n.sceneIndex); }
                for(Integer scene:d.removedSceneIndexes) require(HomeWorldRuntimePlan.isHomeWorldSceneIndex(scene),"removed non-WORLD scene index "+scene);
                for(Map.Entry<Integer,Integer> e:d.walkDirectionBySceneIndex.entrySet()){
                    require(HomeWorldRuntimePlan.isHomeWorldSceneIndex(e.getKey()),"walked non-WORLD scene index");
                    require(e.getValue()>=0&&e.getValue()<=7,"bad walk direction "+e);
                    require(d.desiredVisibleSceneIndexes.contains(e.getKey()),"walked NPC not visible");
                }
                require(d.desiredVisibleSceneIndexes.size()<=47,"visible overflow");
            }
        }
        require(adds>20,"expected visibility additions");
        require(removes>20,"expected visibility removals");
        require(walks>20,"expected wander movement");

        // Exhaustive anchor sweep proves every persistent manifest actor can become representable
        // through signed5 packet65 offsets somewhere in HOME.
        for(HomeNpcSpawnRepository.Spawn s:HomeNpcSpawnRepository.defaultReplay()){
            HomeWorldRuntimePlan.NpcDelta d=p.tick(tick++,s.anchorX,s.anchorY);
            assertSigned5(d.added,s.anchorX,s.anchorY);
            for(NpcEntity n:d.added) everVisible.add(n.sceneIndex);
            for(Integer scene:d.desiredVisibleSceneIndexes) everVisible.add(scene);
        }
        eq(47,everVisible.size(),"anchor sweep sees all persistent HOME NPCs");

        // Collision integration contract is exact for additions and deliberately explicit for the remove-only hole.
        List<ClientCollisionDeltaCodec.Delta> collision=HomeWorldRuntimePlan.collisionAddDeltas();
        eq(78,collision.size(),"collision sparse deltas");
        int rectMasks=0,wallMasks=0;
        for(ClientCollisionDeltaCodec.Delta d:collision){
            if((d.mask & 0x100)!=0) rectMasks++; else wallMasks++;
        }
        eq(54,rectMasks,"rectangle cells");
        eq(24,wallMasks,"wall movement/projectile deltas");

        List<HomeWorldRuntimePlan.RemoveOnlyOpenOverride> open=HomeWorldRuntimePlan.removeOnlyOpenOverrides();
        eq(1,open.size(),"remove-only open overrides");
        HomeWorldRuntimePlan.RemoveOnlyOpenOverride o=open.get(0);
        eq(3091,o.worldX,"open override x"); eq(3508,o.worldY,"open override y"); eq(10,o.shape,"open override shape"); eq(1,o.rotation,"open override rotation");

        require(HomeWorldRuntimePlan.isHomeWorldSceneIndex(101),"scene101");
        require(HomeWorldRuntimePlan.isHomeWorldSceneIndex(148),"scene148");
        require(!HomeWorldRuntimePlan.isHomeWorldSceneIndex(NpcRegistry.PET_INDEX),"pet index must not be WORLD-owned");

        System.out.println("WORLD_R5_RUNTIME_PLAN_PASS scenePackets=72 objectMutations=53 initialNpcs=29 persistentNpcs=47 stableSceneIndexes=101..148 visibilityAdds="+adds+" visibilityRemoves="+removes+" wanderWalks="+walks+" collisionDeltas=78 removeOnlyOpen=3091,3508 mainlineNpcStream=MERGE_REQUIRED");
    }

    private static void assertSigned5(List<NpcEntity> xs,int px,int py){
        for(NpcEntity n:xs){int dx=n.x-px,dy=n.y-py;require(dx>=-16&&dx<=15&&dy>=-16&&dy<=15,"signed5 violation "+n+" player="+px+","+py);}
    }
    private static void unique(List<NpcEntity> xs){HashSet<Integer>s=new HashSet<>();for(NpcEntity n:xs)require(s.add(n.sceneIndex),"duplicate scene "+n.sceneIndex);}
    private static void eq(int a,int b,String w){if(a!=b)throw new AssertionError(w+" expected="+a+" got="+b);}
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
