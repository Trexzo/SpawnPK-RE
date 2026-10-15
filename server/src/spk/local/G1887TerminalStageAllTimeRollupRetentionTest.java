package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * G18.8.7: 130 real NPC deaths with malformed test-only G14 prove
 * all-time stage THREW and RETURNED diagnostics survive eviction of
 * old reports, remain immutable, and do not become reward ACKs.
 * Each death still has separate G1 receipt and coin settlement.
 */
public final class G1887TerminalStageAllTimeRollupRetentionTest {
    private static final String USER="g1887-alltime-rollup";
    private static final int COUNT=
        LocalLabTerminalStageIsolation.MAX_REPORTS+2;
    private static final String PROPERTY=
        LocalLabMonsterSpawnerProvisioning
            .ISOLATE_TERMINAL_STAGES_PROPERTY;

    public static void main(String[] args)throws Exception{
        String prior=System.getProperty(PROPERTY);
        World world=null;
        World secondWorld=null;
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        try{
            System.setProperty(PROPERTY,"true");
            world=World.isolatedForTest(60_000L);
            generation=world.registerPlayer(owner,USER);
            // Malformed G14 is test-only, deliberately unhydrated. It
            // throws independently on every actual settled terminal.
            owner.snapshotExtensions().replaceNamespace(
                LocalLabDailyChallengePersistence.NAMESPACE,
                Collections.singletonMap(
                    "version","invalid-g1887-test-only"));
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            check(installed.terminalDiagnosticSnapshot()!=null,
                "real public factory enabled diagnostics on first World");
            System.clearProperty(PROPERTY);

            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult firstSpawn=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=firstSpawn.spawn.combat.spawn.npc;
            WorldNpc veryFirst=npc;
            List<EntityId> uniqueIds=new ArrayList<EntityId>();

            for(int index=0;index<COUNT;index++){
                long tick=4101L+10L*index;
                EntityId id=npc.id;
                check(!uniqueIds.contains(id),
                    "NPC identity not reused within same World "+index);
                uniqueIds.add(id);
                check(world.npcLifecycle().applyDamage(
                        id,99,tick).newlyDied,
                    "real damage and NPC death "+index);
                MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                    installed.runtime().finalizeIfOwned(npc);
                check(terminal!=null&&
                        terminal.status==
                            MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                        terminal.settlement!=null&&
                        terminal.settlement.groundItems.size()==1&&
                        terminal.settlement.groundItems.get(0)
                            .settledAmount==1&&
                        installed.runtime().get(id)==null,
                    "one canonical finalize and drop settlement "+index);

                LocalLabTerminalStageIsolation.DiagnosticSnapshot view=
                    installed.terminalDiagnosticSnapshot();
                check(view!=null&&
                        view.stage(LocalLabTerminalStageIsolation.Stage.G14)
                            .threw==index+1L&&
                        view.stage(LocalLabTerminalStageIsolation.Stage.G18)
                            .returned==index+1L&&
                        view.stage(LocalLabTerminalStageIsolation.Stage.G15)
                            .returned==index+1L&&
                        view.stage(LocalLabTerminalStageIsolation.Stage.G16)
                            .returned==index+1L&&
                        view.stage(LocalLabTerminalStageIsolation.Stage.G4)
                            .returned==index+1L&&
                        view.stage(LocalLabTerminalStageIsolation.Stage.G14)
                            .returned==0L&&
                        view.stage(LocalLabTerminalStageIsolation.Stage.G14)
                            .skipped==0L&&
                        view.observedCount==index+1L&&
                        view.evictedCount==
                            Math.max(0,index+1-LocalLabTerminalStageIsolation
                                .MAX_REPORTS)&&
                        view.recentReports.size()==
                            Math.min(index+1,LocalLabTerminalStageIsolation
                                .MAX_REPORTS),
                    "bounded detached operator snapshot "+index);

                if(index+1<COUNT){
                    world.events().runDue(
                        tick+G1MonsterSpawnerPvmLoopService
                            .RESPAWN_DELAY_TICKS);
                    MonsterSpawnerService.SessionSnapshot session=
                        spawner.getSession(USER);
                    check(session!=null&&
                            session.spawnedNpcIds.size()==1,
                        "real G1 respawn schedule "+index);
                    npc=world.npcs().byId(
                        session.spawnedNpcIds.get(0));
                    check(npc!=null&&!npc.id.equals(id),
                        "new canonical NPC from real respawn "+index);
                }
            }
            LocalLabTerminalStageIsolation.DiagnosticSnapshot full=
                installed.terminalDiagnosticSnapshot();
            check(full.stage(LocalLabTerminalStageIsolation.Stage.G14)
                    .threw==COUNT&&
                    full.stage(LocalLabTerminalStageIsolation.Stage.G18)
                        .returned==COUNT&&
                    full.stage(LocalLabTerminalStageIsolation.Stage.G15)
                        .returned==COUNT&&
                    full.stage(LocalLabTerminalStageIsolation.Stage.G16)
                        .returned==COUNT&&
                    full.stage(LocalLabTerminalStageIsolation.Stage.G4)
                        .returned==COUNT&&
                    full.observedCount==COUNT&&
                    full.evictedCount==COUNT-
                        LocalLabTerminalStageIsolation.MAX_REPORTS&&
                    full.recentReports.size()==
                        LocalLabTerminalStageIsolation.MAX_REPORTS&&
                    full.recentReports.get(0).npcId.equals(
                        uniqueIds.get(COUNT-
                            LocalLabTerminalStageIsolation.MAX_REPORTS))&&
                    full.recentReports.get(
                        full.recentReports.size()-1).npcId.equals(
                            uniqueIds.get(COUNT-1)),
                "first two operator reports gone, latest 128 retained");

            int coins=world.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&
                    USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            check(coins==COUNT&&
                    world.pvmRecords().receiptCount()==COUNT&&
                    world.pvmRecords().snapshot(owner).kills==COUNT&&
                    installed.runtime().finalizeIfOwned(veryFirst).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    installed.terminalDiagnosticSnapshot()
                        .observedCount==COUNT,
                "evicted reports never replay or delete rewards");

            System.setProperty(PROPERTY,"true");
            secondWorld=World.isolatedForTest(60_000L);
            LocalMonsterSpawnerActivationRuntime second=
                LocalLabMonsterSpawnerProvisioning.create(secondWorld);
            LocalLabTerminalStageIsolation.DiagnosticSnapshot fresh=
                second.terminalDiagnosticSnapshot();
            check(fresh!=null&&
                    fresh.stage(LocalLabTerminalStageIsolation.Stage.G14)
                        .threw==0L&&
                    fresh.stage(LocalLabTerminalStageIsolation.Stage.G18)
                        .returned==0L&&
                    fresh.observedCount==0L&&
                    fresh.evictedCount==0L&&
                    fresh.recentReports.isEmpty()&&
                    full.observedCount==COUNT,
                "independent World has its own empty report container");

            boolean readOnly=false;
            try{
                full.recentReports.remove(0);
            }catch(UnsupportedOperationException expected){
                readOnly=true;
            }
            check(readOnly&&
                    installed.terminalDiagnosticSnapshot()
                        .recentReports.size()==
                            LocalLabTerminalStageIsolation.MAX_REPORTS,
                "external diagnostic snapshot cannot mutate live journal");

            boolean rollupReadOnly=false;
            try{
                full.allTimeStageOutcomes.clear();
            }catch(UnsupportedOperationException expected){
                rollupReadOnly=true;
            }
            check(rollupReadOnly&&
                    installed.terminalDiagnosticSnapshot()
                        .stage(LocalLabTerminalStageIsolation.Stage.G14)
                        .threw==COUNT,
                "all-time counters immutable, not altered via returned map");

            System.out.println(
                "G1887_ALLTIME_STAGE_OUTCOMES_SURVIVE_128_REPORT_EVICTION_PASS"+
                " actualNpcDeaths="+COUNT+
                " g1Receipts="+world.pvmRecords().receiptCount()+
                " actualCoinSettlements="+coins+
                " reportsRetained="+full.recentReports.size()+
                " reportsEvicted="+full.evictedCount+
                " g14Throws="+full.stage(
                    LocalLabTerminalStageIsolation.Stage.G14).threw+
                " g18Returns="+full.stage(
                    LocalLabTerminalStageIsolation.Stage.G18).returned+
                " immutableAllTimeRollups=true"+
                " secondWorldIsolated=true"+
                " noOldNpcReplay=true"+
                " noWritableJournalHandle=true"+
                " durableOutbox=false"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(secondWorld!=null)
                secondWorld.close();
            if(world!=null){
                if(generation>=0L&&world.players().owns(owner,generation))
                    world.unregisterPlayer(owner,generation);
                world.close();
            }
            if(prior==null)
                System.clearProperty(PROPERTY);
            else
                System.setProperty(PROPERTY,prior);
        }
    }

    private static void check(boolean result,String message){
        if(!result)throw new AssertionError("G18.8.7 "+message);
    }

    private G1887TerminalStageAllTimeRollupRetentionTest(){}
}
