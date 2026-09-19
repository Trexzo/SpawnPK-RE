package spk.local;

import java.util.*;

public final class HomeNpcWorldStateTest {
    public static void main(String[] args) throws Exception {
        eq(48,HomeNpcSpawnRepository.count(),"canonical census");
        eq(47,HomeNpcSpawnRepository.defaultReplay().size(),"default replay");
        eq(204,HomeNpcWanderRepository.edgeCount(),"wander directed graph edges");
        eq(14,HomeNpcWanderRepository.wandererOrdinals().size(),"persistent wanderers");

        HomeNpcWorldState s=new HomeNpcWorldState();
        eq(47,s.size(),"state size");
        uniqueSceneIndexes(s);

        HomeNpcWorldState.Actor fountain=s.actor(14);
        require(fountain!=null && fountain.spawn.npcDefinitionId==1799 && fountain.x==3079 && fountain.y==3492,"Blood Fountain production placement");
        eq(114,fountain.sceneIndex,"Blood Fountain stable WORLD scene index");

        // Exact production dummy strip from V9.06; stable scene indexes are derived from manifest ordinals.
        assertNpc(s,29,1489,3107,3504,129);
        assertNpc(s,31,1488,3107,3506,131);
        assertNpc(s,34,1488,3107,3508,134);
        assertNpc(s,43,1488,3095,3513,143);
        assertNpc(s,44,1488,3097,3513,144);
        assertNpc(s,45,1488,3099,3513,145);

        List<NpcEntity> initial=s.visibleEntities(MovementState.INITIAL_X,MovementState.INITIAL_Y);
        eq(29,initial.size(),"initial signed5 HOME view");
        for(NpcEntity n:initial){
            int dx=n.x-MovementState.INITIAL_X,dy=n.y-MovementState.INITIAL_Y;
            require(dx>=-16&&dx<=15&&dy>=-16&&dy<=15,"signed5 visibility "+n);
        }

        // Every simulated wander move must remain adjacent and inside its exact observed envelope.
        int moved=0;
        for(long tick=1;tick<=200;tick++){
            for(HomeNpcWorldState.Move m:s.tick(tick)){
                moved++;
                HomeNpcWorldState.Actor a=s.actor(m.ordinal);
                require(a.spawn.containsObservedTile(m.toX,m.toY),"wander left observed envelope "+m+" spawn="+a.spawn);
                require(Math.max(Math.abs(m.toX-m.fromX),Math.abs(m.toY-m.fromY))==1,"non-adjacent wander "+m);
                eq(m.direction,MovementState.direction(m.fromX,m.fromY,m.toX,m.toY),"direction parity");
            }
        }
        require(moved>200,"too few deterministic wander moves: "+moved);

        // Reset returns every actor to the production-observed anchor.
        s.reset();
        for(HomeNpcWorldState.Actor a:s.actors()) require(a.x==a.spawn.anchorX&&a.y==a.spawn.anchorY,"reset mismatch "+a.spawn);

        // R3 fixes R2's nearby-sequential scene-index instability: ordinal determines index globally.
        eq(101,HomeNpcRuntimePlan.sceneIndexForOrdinal(1),"ordinal1 scene");
        eq(148,HomeNpcRuntimePlan.sceneIndexForOrdinal(48),"ordinal48 scene");
        HomeNpcRuntimePlan.Plan p=HomeNpcRuntimePlan.nearbyInitial(MovementState.INITIAL_X,MovementState.INITIAL_Y);
        eq(29,p.size(),"runtime plan nearby count");
        for(NpcEntity n:p.entities) require(n.sceneIndex>=101&&n.sceneIndex<=148,"stable scene range "+n);

        System.out.println("WORLD_R3_HOME_NPC_PARITY_PASS canonical=48 defaultReplay=47 static=33 wanderObserved=15 persistentWander=14 wanderGraphEdges=204 initialVisible=29 stableSceneIndexes=101..148 simulatedMoves="+moved+" bloodFountain=1799@3079,3492 dummies=6");
    }

    private static void uniqueSceneIndexes(HomeNpcWorldState s){
        HashSet<Integer> seen=new HashSet<>();
        for(HomeNpcWorldState.Actor a:s.actors()) require(seen.add(a.sceneIndex),"duplicate scene index "+a.sceneIndex);
    }
    private static void assertNpc(HomeNpcWorldState s,int ordinal,int def,int x,int y,int scene){
        HomeNpcWorldState.Actor a=s.actor(ordinal);
        require(a!=null,"missing ordinal "+ordinal);
        eq(def,a.spawn.npcDefinitionId,"def ord "+ordinal);eq(x,a.x,"x ord "+ordinal);eq(y,a.y,"y ord "+ordinal);eq(scene,a.sceneIndex,"scene ord "+ordinal);
    }
    private static void eq(int a,int b,String what){if(a!=b)throw new AssertionError(what+" expected="+a+" got="+b);}
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
