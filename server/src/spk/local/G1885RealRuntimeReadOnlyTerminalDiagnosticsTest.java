package spk.local;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * G18.8.5 — the actual public LocalLab factory exposes only an immutable
 * read-only snapshot through the composed server runtime. The dispatcher
 * stays private, and OFF must return no diagnostic snapshot.
 *
 * Real canonical NPC deaths, ground settlement and installed observer:
 * no fabricated event or original SpawnPK kill authority.
 */
public final class G1885RealRuntimeReadOnlyTerminalDiagnosticsTest {
    private static final String USER="g1885-runtime-diag";
    private static final String PROPERTY=
        LocalLabMonsterSpawnerProvisioning
            .ISOLATE_TERMINAL_STAGES_PROPERTY;

    public static void main(String[] args)throws Exception{
        String prior=System.getProperty(PROPERTY);
        World enabledWorld=null;
        World disabledWorld=null;
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        try{
            System.clearProperty(PROPERTY);
            disabledWorld=World.isolatedForTest(60_000L);
            LocalMonsterSpawnerActivationRuntime disabled=
                LocalLabMonsterSpawnerProvisioning.create(disabledWorld);
            check(disabled.terminalDiagnosticSnapshot()==null,
                "default-OFF factory must not export a live dispatcher");
            disabledWorld.close();
            disabledWorld=null;

            System.setProperty(PROPERTY,"true");
            enabledWorld=World.isolatedForTest(60_000L);
            generation=enabledWorld.registerPlayer(owner,USER);
            LocalLabTaskScrollRuntime g15=
                enabledWorld.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult scroll=
                g15.assign(USER,enabledWorld.clock().tick());
            LocalLabAdventureRuntime g16=
                enabledWorld.localLabAdventures();
            LocalLabAdventureRuntime.ActivationResult adventure=
                g16.activate(USER);
            LocalLabDailyMoneyMakingRuntime g18=
                enabledWorld.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult easy=
                g18.activateEasyIfCurrent(USER,owner,generation);
            check(scroll!=null&&scroll.assignedNow&&
                    adventure!=null&&adventure.activatedNow&&
                    easy!=null&&easy.activatedNow,
                "legitimate G15/G16/G18 objectives activated");

            owner.snapshotExtensions().replaceNamespace(
                LocalLabDailyChallengePersistence.NAMESPACE,
                Collections.singletonMap(
                    "version","g1885-test-only-invalid"));

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(enabledWorld);
            LocalLabTerminalStageIsolation.DiagnosticSnapshot initial=
                installed.terminalDiagnosticSnapshot();
            check(initial!=null&&initial.observedCount==0L&&
                    initial.evictedCount==0L&&
                    initial.recentReports.isEmpty(),
                "real property ON factory exposes empty read-only report");

            System.clearProperty(PROPERTY);
            check(installed.terminalDiagnosticSnapshot()!=null,
                "property changed post-composition cannot disable view");

            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc first=spawned.spawn.combat.spawn.npc;
            check(enabledWorld.npcLifecycle().applyDamage(
                    first.id,99,3601L).newlyDied,
                "first real canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult firstFinal=
                installed.runtime().finalizeIfOwned(first);
            check(firstFinal!=null&&
                    firstFinal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    firstFinal.settlement!=null&&
                    firstFinal.settlement.groundItems.size()==1&&
                    firstFinal.settlement.groundItems.get(0)
                        .settledAmount==1,
                "first authentic settlement");
            LocalLabTerminalStageIsolation.DiagnosticSnapshot snapshot1=
                installed.terminalDiagnosticSnapshot();
            check(snapshot1!=null&&snapshot1.observedCount==1L&&
                    snapshot1.evictedCount==0L&&
                    snapshot1.recentReports.size()==1&&
                    snapshot1.recentReports.get(0).npcId.equals(first.id)&&
                    snapshot1.recentReports.get(0).outcome(
                        LocalLabTerminalStageIsolation.Stage.G14)==
                        LocalLabTerminalStageIsolation.Outcome.THREW&&
                    snapshot1.recentReports.get(0).outcome(
                        LocalLabTerminalStageIsolation.Stage.G18)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED,
                "read-only view captures malformed G14 and healthy G18");

            boolean listUnmodifiable=false;
            try{
                snapshot1.recentReports.clear();
            }catch(UnsupportedOperationException expected){
                listUnmodifiable=true;
            }
            boolean stageMapUnmodifiable=false;
            Map<LocalLabTerminalStageIsolation.Stage,
                LocalLabTerminalStageIsolation.Outcome> stageMap=
                    snapshot1.recentReports.get(0).outcomes;
            try{
                stageMap.put(
                    LocalLabTerminalStageIsolation.Stage.G18,
                    LocalLabTerminalStageIsolation.Outcome.THREW
                );
            }catch(UnsupportedOperationException expected){
                stageMapUnmodifiable=true;
            }
            check(listUnmodifiable&&stageMapUnmodifiable&&
                    snapshot1.recentReports.size()==1,
                "snapshot and individual stage outcomes deeply immutable");

            enabledWorld.events().runDue(
                3601L+G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "genuine G1 delayed respawn");
            EntityId secondId=session.spawnedNpcIds.get(0);
            WorldNpc second=enabledWorld.npcs().byId(secondId);
            check(second!=null&&!secondId.equals(first.id)&&
                    enabledWorld.npcLifecycle().applyDamage(
                        second.id,99,3611L).newlyDied,
                "second genuinely new NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult secondFinal=
                installed.runtime().finalizeIfOwned(second);
            check(secondFinal!=null&&
                    secondFinal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    secondFinal.settlement!=null,
                "second genuine terminal settled");

            LocalLabTerminalStageIsolation.DiagnosticSnapshot snapshot2=
                installed.terminalDiagnosticSnapshot();
            check(snapshot2.observedCount==2L&&
                    snapshot2.evictedCount==0L&&
                    snapshot2.recentReports.size()==2&&
                    snapshot2.recentReports.get(1).npcId.equals(secondId)&&
                    snapshot1.observedCount==1L&&
                    snapshot1.recentReports.size()==1&&
                    initial.recentReports.isEmpty(),
                "snapshots detached, old snapshots unaffected by new deaths");
            check(enabledWorld.pvmRecords().receiptCount()==2&&
                    g15.active(USER).objective.progress==2L&&
                    g16.objective(USER).progress==2L&&
                    g18.snapshot(USER).objective.progress==2L&&
                    installed.runtime().finalizeIfOwned(first).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    installed.terminalDiagnosticSnapshot()
                        .observedCount==2L,
                "read-only access never changes reward or finalization");

            System.out.println(
                "G1885_REAL_FACTORY_READONLY_TERMINAL_DIAGNOSTICS_PASS"+
                " disabledReturnsNull=true"+
                " enabledCapturesTwoRealDeaths=true"+
                " historyImmutable=true"+
                " perReportMapImmutable=true"+
                " snapshotsDetached=true"+
                " g1ReceiptsTwo=true"+
                " downstreamHealthyStagesTwo=true"+
                " noDuplicateTerminal=true"+
                " originalSpawnpkPolicy=false"+
                " durableOutbox=false");
        }finally{
            if(enabledWorld!=null){
                if(generation>=0L&&
                    enabledWorld.players().owns(owner,generation))
                    enabledWorld.unregisterPlayer(owner,generation);
                enabledWorld.close();
            }
            if(disabledWorld!=null)
                disabledWorld.close();
            if(prior==null)
                System.clearProperty(PROPERTY);
            else
                System.setProperty(PROPERTY,prior);
        }
    }

    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G18.8.5 "+reason);
    }

    private G1885RealRuntimeReadOnlyTerminalDiagnosticsTest(){}
}
