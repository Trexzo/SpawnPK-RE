package spk.local;

import java.util.ArrayList;
import java.util.List;

/**
 * G18.9.4: after 130 REAL World NPC terminal events, the oldest
 * report is evicted. Yet retaining the exact first FinalizeResult
 * must not permit the first G4/G14/G15/G16/G18 callbacks to replay.
 * No global ever-growing EntityId deduplication journal is created.
 */
public final class G1894OptinEvictedTerminalEventReplayVetoTest {
    private static final String USER="g1894-evicted-event-veto";
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
            MonsterSpawnerPvmRuntime.FinalizeResult oldestEvent=null;

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
                if(i==0)
                    oldestEvent=terminal;
                check(terminal!=null&&terminal.sourceWorld==world&&
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
            check(groundCoins==EVENTS&&oldestEvent!=null&&
                    isolation.diagnosticSnapshot().observedCount==EVENTS&&
                    isolation.diagnosticSnapshot().evictedCount==2L,
                "130 real coins/records but first two reports evicted");

            boolean oldRejected=false;
            try{
                isolation.deliver(world,oldestEvent);
            }catch(IllegalStateException expected){
                oldRejected=
                    "TERMINAL_STAGE_EVENT_ALREADY_DELIVERED_NO_REPLAY"
                        .equals(expected.getMessage());
            }
            check(oldRejected&&
                    isolation.diagnosticSnapshot().observedCount==EVENTS&&
                    isolation.diagnosticSnapshot().evictedCount==2L&&
                    isolation.diagnosticSnapshot()
                        .recentReports.size()==
                            LocalLabTerminalStageIsolation.MAX_REPORTS&&
                    isolation.diagnosticSnapshot().stage(
                        LocalLabTerminalStageIsolation.Stage.G15)
                        .returned==EVENTS&&
                    world.pvmRecords().receiptCount()==EVENTS&&
                    world.pvmRecords().snapshot(owner).kills==EVENTS,
                "evicted report does not reopen old event's stage delivery");

            check(installed.runtime().finalizeIfOwned(
                    start.spawn.combat.spawn.npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    isolation.history().size()==
                        LocalLabTerminalStageIsolation.MAX_REPORTS,
                "old canonical NPC cannot be re-finalized after report eviction");

            System.out.println(
                "G1894_EVICTED_TERMINAL_EVENT_REPLAY_VETO_PASS"+
                " genuineNpcDeaths="+EVENTS+
                " g1Receipts="+world.pvmRecords().receiptCount()+
                " retainedReports="+bounded.size()+
                " oldestTwoReportsEvicted=true"+
                " oldestFinalizedObjectReplayVeto=true"+
                " noUnboundedGlobalDedupSet=true"+
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
        if(!ok)throw new AssertionError("G18.9.4 "+why);
    }

    private G1894OptinEvictedTerminalEventReplayVetoTest(){}
}
