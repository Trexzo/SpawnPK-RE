package spk.local;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * G19.6.6: the DEFAULT installed progression observer processes 131
 * actual canonical NPC deaths, retaining per-stage outcome totals even
 * after the detailed 128-entry diagnostic ring evicts three reports.
 * RETURNED is only callback status, NEVER a reward-applied ACK.
 */
public final class G1966SequentialStageOutcomeEvictionTotalsTest {
    public static void main(String[] args)throws Exception {
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        final String name="g1966-actual-stage-outcome-evictions";
        try {
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(false,100,3087,3495,0);
            LocalMonsterSpawnerActivationRuntime graph=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService service=graph.service();
            service.openSession(name,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            service.selectRow(name,0);
            service.activate(name,1);

            int cycles=LocalLabSequentialTerminalStageTrace.MAX_REPORTS+3;
            ArrayList<EntityId> ids=new ArrayList<EntityId>();
            HashSet<EntityId> unique=new HashSet<EntityId>();
            LocalLabSequentialTerminalStageTrace.Snapshot early=null;
            for(int n=0;n<cycles;n++){
                if(n>0)service.activate(name,1);
                MonsterSpawnerPvmRuntime.SpawnResult spawned=
                    graph.runtime().spawnAndBind(name,name,3088,3495,0);
                WorldNpc npc=spawned.spawn.combat.spawn.npc;
                check(npc!=null&&unique.add(npc.id),
                    "each cycle uses a distinct real NPC identity");
                ids.add(npc.id);
                check(world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                    "genuine lifecycle lethal damage "+n);
                MonsterSpawnerPvmRuntime.FinalizeResult result=
                    graph.runtime().finalizeIfOwned(npc);
                check(result!=null&&
                        result.status==
                            MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                        result.settlement!=null&&
                        graph.runtime().get(npc.id)==null&&
                        world.npcs().byId(npc.id)==null,
                    "canonical death and ground settlement exactly once "+n);
                if(n==0)early=graph.sequentialTerminalDiagnosticSnapshot();
            }

            LocalLabSequentialTerminalStageTrace.Snapshot after=
                graph.sequentialTerminalDiagnosticSnapshot();
            check(early!=null&&early.observed==1L&&
                    early.evicted==0L&&early.recent.size()==1&&
                    after.observed==cycles&&after.evicted==3L&&
                    after.recent.size()==128&&unique.size()==cycles,
                "bounded detail and monotonic aggregate observations");
            check(after.recent.get(0).npcId.equals(ids.get(3))&&
                    after.recent.get(127).npcId.equals(ids.get(cycles-1)),
                "oldest three genuine death reports evicted first");
            for(LocalLabSequentialTerminalStageTrace.Stage stage:
                    LocalLabSequentialTerminalStageTrace.Stage.values()){
                check(early.outcomeCount(stage,
                        LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                        ==1L&&
                        after.outcomeCount(stage,
                            LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                            ==cycles,
                    "returned callbacks persist in detached totals "+stage);
                for(LocalLabSequentialTerminalStageTrace.Outcome outcome:
                        LocalLabSequentialTerminalStageTrace.Outcome.values())
                    if(outcome!=
                            LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                        check(after.outcomeCount(stage,outcome)==0L,
                            "no fabricated failure/skip/reach outcomes "+stage);
            }
            boolean outerReadonly=false;
            boolean innerReadonly=false;
            try{
                after.outcomeCounts.clear();
            }catch(UnsupportedOperationException expected){
                outerReadonly=true;
            }
            try{
                after.outcomeCounts.get(
                    LocalLabSequentialTerminalStageTrace.Stage.G4).put(
                        LocalLabSequentialTerminalStageTrace.Outcome.RETURNED,
                        0L);
            }catch(UnsupportedOperationException expected){
                innerReadonly=true;
            }
            check(outerReadonly&&innerReadonly&&
                    early.outcomeCount(
                        LocalLabSequentialTerminalStageTrace.Stage.G4,
                        LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                        ==1L,
                "historical snapshot deep immutable and detached");
            GroundItem item=world.groundItems().findOwned(
                995,3088,3495,0,name);
            check(item!=null&&item.amount==cycles&&
                    graph.runtime().terminalObserverDiagnostics()
                        .totalAttempts==cycles,
                "actual ground settlement and observer count one per death");
            System.out.println(
                "G1966_SEQUENTIAL_STAGE_OUTCOME_EVICTION_TOTALS_PASS"+
                " actualCanonicalNpcDeaths=131"+
                " boundedReports=128"+
                " preservedPerStageReturns=131"+
                " immutableDetachedCounters=true"+
                " durableRewardAck=false");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G19.6.6 "+why);
    }
    private G1966SequentialStageOutcomeEvictionTotalsTest(){}
}
