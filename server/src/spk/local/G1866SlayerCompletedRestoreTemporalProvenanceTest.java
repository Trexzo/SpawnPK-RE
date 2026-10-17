package spk.local;

import java.util.SortedMap;

/**
 * Chat 2 G18.6.6 — completion timestamp provenance across a fresh
 * CUSTOM_LOCALLAB World. This is a test-only in-memory snapshot copy,
 * not an actual account file/TCP reload or recovered SpawnPK policy.
 *
 * Unlike the historical G1847 conglomerate, this new negative has
 * a dedicated focused test class so it can evolve independently.
 */
public final class G1866SlayerCompletedRestoreTemporalProvenanceTest {
    private static final String USER="g1866-completed-provenance";
    private static final long ORIGINAL_ASSIGNED=77L;
    private static final long ORIGINAL_COMPLETED=91L;
    private static final long RENEWED_ASSIGNED=113L;

    public static void main(String[] args)throws Exception {
        World originalWorld=World.isolatedForTest(60_000L);
        World replayWorld=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=new WorldPlayer();
        long originalGeneration=-1L,replayGeneration=-1L;
        try {
            originalGeneration=originalWorld.registerPlayer(original,USER);
            LocalLabSlayerRuntime originalSlayer=
                originalWorld.localLabSlayer();
            LocalLabSlayerRuntime.StartResult began=
                originalSlayer.startMonsterHunter(
                    USER,ORIGINAL_ASSIGNED
                );
            check(began!=null&&began.created&&
                  began.status.task!=null&&
                  began.status.task.assignedTick==ORIGINAL_ASSIGNED&&
                  began.status.task.transitionTick==-1L&&
                  began.status.task.objective.progress==0L,
                "original legitimate G4 task assigned at tick77");

            LocalMonsterSpawnerActivationRuntime provisioning=
                LocalLabMonsterSpawnerProvisioning.create(originalWorld);
            MonsterSpawnerService spawner=provisioning.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioning.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(spawned.runtime.recipientOrigin!=null&&
                  spawned.runtime.recipientOrigin.player==original&&
                  spawned.runtime.recipientOrigin.generation==
                      originalGeneration&&
                  originalWorld.npcLifecycle().applyDamage(
                      npc.id,99,ORIGINAL_COMPLETED
                  ).newlyDied,
                "genuine original World NPC lethal transition");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioning.runtime().finalizeIfOwned(npc);

            LocalLabSlayerRuntime.StatusSnapshot finished=
                originalSlayer.status(USER);
            SortedMap<String,String> exactOriginal=
                original.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE);
            LocalLabSlayerPersistence.Snapshot source=
                LocalLabSlayerPersistence.decode(exactOriginal);
            check(terminal!=null&&terminal.status==
                     MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                  terminal.settlement!=null&&
                  terminal.settlement.groundItems.size()==1&&
                  terminal.settlement.groundItems.get(0)
                      .settledAmount==1&&
                  finished!=null&&finished.complete()&&
                  finished.completions==1L&&
                  finished.task.assignedTick==ORIGINAL_ASSIGNED&&
                  finished.task.transitionTick==ORIGINAL_COMPLETED&&
                  source!=null&&
                  source.state==LocalLabSlayerPersistence.State.COMPLETED&&
                  source.progress==1L&&source.completions==1L&&
                  source.sourceAssignedTick==ORIGINAL_ASSIGNED&&
                  source.sourceTransitionTick==ORIGINAL_COMPLETED&&
                  originalWorld.pvmRecords().snapshot(original).kills==1L&&
                  originalWorld.pvmRecords().receiptCount()==1&&
                  originalWorld.groundItems().size()==1,
                "source real terminal must persist valid COMPLETED77/91");

            final long replayTick=replayWorld.clock().tick();
            check(replayTick>=0L&&
                  replayTick!=ORIGINAL_ASSIGNED&&
                  replayTick!=ORIGINAL_COMPLETED,
                "fresh World clock intentionally differs from source");
            replayGeneration=replayWorld.registerPlayer(
                replacement,USER);
            synchronized(replacement.mutationLock()) {
                replacement.snapshotExtensions().replaceNamespace(
                    LocalLabSlayerPersistence.NAMESPACE,
                    exactOriginal);
            }
            LocalLabSlayerRuntime target=replayWorld.localLabSlayer();
            LocalLabSlayerRuntime.StatusSnapshot hydrated=
                target.status(USER);
            check(hydrated!=null&&hydrated.persistenceError==null&&
                  hydrated.complete()&&hydrated.completions==1L&&
                  hydrated.task!=null&&
                  hydrated.task.objective.progress==1L&&
                  hydrated.task.assignedTick==replayTick&&
                  hydrated.task.transitionTick==replayTick&&
                  exactOriginal.equals(
                      replacement.snapshotExtensions().namespace(
                          LocalLabSlayerPersistence.NAMESPACE)),
                "COMPLETED hydration creates replay-clock task without "+
                "altering raw extension");

            // No real terminal is delivered in replayWorld. A normal
            // user-level G4 renewal is the FIRST legitimate mutation
            // after hydration, and rewrites this player's namespace.
            LocalLabSlayerRuntime.StartResult renewed=
                target.startMonsterHunter(USER,RENEWED_ASSIGNED);
            LocalLabSlayerPersistence.Snapshot afterRenew=
                LocalLabSlayerPersistence.decode(
                    replacement.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            check(renewed!=null&&renewed.created&&
                  renewed.status.active()&&
                  renewed.status.completions==1L&&
                  renewed.status.task.assignedTick==RENEWED_ASSIGNED&&
                  renewed.status.task.transitionTick==-1L&&
                  renewed.status.task.objective.progress==0L&&
                  afterRenew!=null&&
                  afterRenew.state==LocalLabSlayerPersistence.State.ACTIVE&&
                  afterRenew.sourceAssignedTick==RENEWED_ASSIGNED&&
                  afterRenew.sourceTransitionTick==-1L&&
                  afterRenew.completions==1L&&
                  afterRenew.progress==0L&&
                  originalWorld.pvmRecords().receiptCount()==1&&
                  replayWorld.pvmRecords().receiptCount()==0&&
                  source.sourceAssignedTick==ORIGINAL_ASSIGNED&&
                  source.sourceTransitionTick==ORIGINAL_COMPLETED&&
                  exactOriginal.equals(
                      original.snapshotExtensions().namespace(
                          LocalLabSlayerPersistence.NAMESPACE)),
                "normal renewal uses new tick without phantom replay kill");

            System.out.println(
                "G1866_G4_COMPLETED_RESTORE_REPLAY_TIME_PROVENANCE_PASS"+
                " sourceAssigned77=true"+
                " sourceTransition91=true"+
                " codecValidCompleted=true"+
                " replayAssignedAndCompletedAtFreshWorldClock=true"+
                " hydratedPersistedExtensionInitiallyUnchanged=true"+
                " legitimateRenewalTick113=true"+
                " completionCountPreservedOne=true"+
                " noReplayWorldKillOrReceipt=true"+
                " originalSnapshotIntact=true"+
                " originalServerPolicyClaim=false"+
                " fileOrTcpRoundTrip=false"+
                " productionCodeChanged=false");
        }finally{
            if(replayGeneration>=0L&&
               replayWorld.players().owns(replacement,replayGeneration))
                replayWorld.unregisterPlayer(replacement,replayGeneration);
            if(originalGeneration>=0L&&
               originalWorld.players().owns(original,originalGeneration))
                originalWorld.unregisterPlayer(original,originalGeneration);
            replayWorld.close();
            originalWorld.close();
        }
    }

    private static void check(boolean accepted,String message) {
        if(!accepted)
            throw new AssertionError("G18.6.6 "+message);
    }

    private G1866SlayerCompletedRestoreTemporalProvenanceTest() {}
}
