package spk.local;

import java.util.ArrayList;
import java.util.List;

/**
 * G18.8.2: a genuine 130-death canonical NPC/settlement/respawn
 * sequence must leave at most the latest 128 diagnostic reports.
 * The history is observational and memory-only, never a replay queue
 * or proof of durable progression ACK/credit.
 */
public final class G1882OptinTerminalReportBoundedHistoryTest {
    private static final String USER="g1882-stage-report-bound";
    private static final int EVENTS=
        LocalLabTerminalStageIsolation.MAX_REPORTS+2;

    public static void main(String[] args)throws Exception {
        World world=World.isolatedForTest(60_000L);
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        try {
            generation=world.registerPlayer(owner,USER);
            LocalLabTerminalStageIsolation isolation=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world,isolation);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult start=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc current=start.spawn.combat.spawn.npc;
            List<EntityId> genuineNpcs=new ArrayList<EntityId>();

            for(int i=0;i<EVENTS;i++){
                long deathTick=2401L+10L*i;
                EntityId id=current.id;
                check(!genuineNpcs.contains(id),
                    "new canonical NPC identity, index="+i);
                genuineNpcs.add(id);
                check(world.npcLifecycle().applyDamage(
                        id,99,deathTick).newlyDied,
                    "real lethal damage index="+i);
                MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                    installed.runtime().finalizeIfOwned(current);
                check(terminal!=null&&
                        terminal.status==
                            MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                        terminal.settlement!=null&&
                        installed.runtime().get(id)==null,
                    "real drop settlement and canonical removal "+i);
                check(isolation.history().size()==
                        Math.min(i+1,
                            LocalLabTerminalStageIsolation.MAX_REPORTS),
                    "bounded report size after event "+i);

                if(i+1<EVENTS){
                    world.events().runDue(
                        deathTick+G1MonsterSpawnerPvmLoopService
                            .RESPAWN_DELAY_TICKS);
                    MonsterSpawnerService.SessionSnapshot session=
                        spawner.getSession(USER);
                    check(session!=null&&
                            session.spawnedNpcIds.size()==1,
                        "actual G1 delayed respawn index="+i);
                    current=world.npcs().byId(
                        session.spawnedNpcIds.get(0));
                    check(current!=null&&!current.id.equals(id),
                        "new independently spawned WorldNpc index="+i);
                }
            }

            List<LocalLabTerminalStageIsolation.Report> bounded=
                isolation.history();
            check(bounded.size()==
                    LocalLabTerminalStageIsolation.MAX_REPORTS&&
                    bounded.get(0).npcId.equals(genuineNpcs.get(2))&&
                    bounded.get(bounded.size()-1).npcId.equals(
                        genuineNpcs.get(EVENTS-1))&&
                    bounded.get(0).observedGeneration==generation&&
                    bounded.get(bounded.size()-1)
                        .observedGeneration==generation&&
                    world.pvmRecords().receiptCount()==EVENTS&&
                    world.pvmRecords().snapshot(owner).kills==EVENTS,
                "first two diagnostic entries evicted, actual G1 records not lost");

            int groundCoins=world.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            check(groundCoins==EVENTS,
                "one real coin settled per distinct NPC without report replay");
            check(installed.runtime().finalizeIfOwned(
                    start.spawn.combat.spawn.npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    isolation.history().size()==
                        LocalLabTerminalStageIsolation.MAX_REPORTS,
                "old canonical NPC cannot be re-finalized after report eviction");

            System.out.println(
                "G1882_TERMINAL_DIAGNOSTIC_BOUNDED_128_REAL_NPC_PASS"+
                " genuineNpcDeaths="+EVENTS+
                " g1Receipts="+world.pvmRecords().receiptCount()+
                " retainedReports="+bounded.size()+
                " oldestTwoReportsEvicted=true"+
                " groundCoins="+groundCoins+
                " noOldNpcReplay=true"+
                " durableOutbox=false"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(generation>=0L&&world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            world.close();
        }
    }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G18.8.2 "+why);
    }

    private G1882OptinTerminalReportBoundedHistoryTest(){}
}
