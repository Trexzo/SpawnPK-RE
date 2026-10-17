package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Stream;

/**
 * G18.6.7: real FilePlayerRepository save/load + validated snapshot
 * application, contrasted against World-local completed Slayer replay.
 *
 * The repository writes are explicit test-owned API calls: this is
 * NOT a TCP admission, async World final-save, JVM crash, or recovered
 * original SpawnPK account/Slayer policy.
 */
public final class G1867SlayerCompletedFileRoundtripTemporalTest {
    private static final String USER="g1867-file-completed";
    private static final long SOURCE_ASSIGNED=77L;
    private static final long SOURCE_COMPLETED=91L;
    private static final long RENEW_TICK=113L;

    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("g1867-slayer-file-");
        FilePlayerRepository files=new FilePlayerRepository(
            username->root.resolve(username+".properties"));
        World sourceWorld=null,restoredWorld=null;
        WorldPlayer source=new WorldPlayer();
        WorldPlayer restored=new WorldPlayer();
        long sourceGen=-1L,restoredGen=-1L;
        try {
            sourceWorld=World.isolatedForTest(60_000L,files);
            sourceGen=sourceWorld.registerPlayer(source,USER);
            LocalLabSlayerRuntime.StartResult task=
                sourceWorld.localLabSlayer().startMonsterHunter(
                    USER,SOURCE_ASSIGNED);
            check(task!=null&&task.created&&task.status.active()&&
                  task.status.task.assignedTick==SOURCE_ASSIGNED,
                "legitimate original assignment tick77");

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(sourceWorld);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawn=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=spawn.spawn.combat.spawn.npc;
            check(spawn.runtime.recipientOrigin!=null&&
                  spawn.runtime.recipientOrigin.player==source&&
                  spawn.runtime.recipientOrigin.generation==sourceGen&&
                  sourceWorld.npcLifecycle().applyDamage(
                      npc.id,99,SOURCE_COMPLETED
                  ).newlyDied,
                "real original World NPC kill at tick91");
            MonsterSpawnerPvmRuntime.FinalizeResult finished=
                installed.runtime().finalizeIfOwned(npc);
            SortedMap<String,String> completedSource=
                source.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE);
            LocalLabSlayerPersistence.Snapshot original=
                LocalLabSlayerPersistence.decode(completedSource);
            check(finished!=null&&finished.status==
                      MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                  finished.settlement!=null&&
                  finished.settlement.groundItems.size()==1&&
                  finished.settlement.groundItems.get(0)
                      .settledAmount==1&&
                  original!=null&&
                  original.state==LocalLabSlayerPersistence.State.COMPLETED&&
                  original.progress==1L&&original.completions==1L&&
                  original.sourceAssignedTick==SOURCE_ASSIGNED&&
                  original.sourceTransitionTick==SOURCE_COMPLETED&&
                  sourceWorld.pvmRecords().receiptCount()==1,
                "original actual terminal retained valid 77/91 COMPLETED");

            // Actual file bytes, not a copied extension. This explicit
            // test-owned save does not attest LocalSession final save.
            files.save(PlayerSnapshotCodec.capture(USER,source));
            Optional<PlayerSnapshot> persisted=files.load(USER);
            check(persisted.isPresent(),"source account bytes saved on disk");
            sourceWorld.close();
            sourceWorld=null;

            restoredWorld=World.isolatedForTest(60_000L,files);
            PlayerSnapshotCodec.applyValidated(persisted.get(),restored);
            restoredGen=restoredWorld.registerPlayer(restored,USER);
            SortedMap<String,String> hydratedBytes=
                restored.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE);
            check(completedSource.equals(hydratedBytes),
                "decoded file reconstituted exact completed namespace");

            long replayTick=restoredWorld.clock().tick();
            check(replayTick>=0L&&replayTick!=SOURCE_ASSIGNED&&
                  replayTick!=SOURCE_COMPLETED,
                "fresh World clock distinct from persisted history");
            LocalLabSlayerRuntime.StatusSnapshot status=
                restoredWorld.localLabSlayer().status(USER);
            check(status!=null&&status.persistenceError==null&&
                  status.complete()&&status.completions==1L&&
                  status.task!=null&&
                  status.task.assignedTick==replayTick&&
                  status.task.transitionTick==replayTick&&
                  status.task.objective.progress==1L&&
                  completedSource.equals(
                      restored.snapshotExtensions().namespace(
                          LocalLabSlayerPersistence.NAMESPACE)),
                "World replays completed state at new tick without "+
                "rewriting hydrated namespace");

            // A status read must not itself write a new account file.
            Optional<PlayerSnapshot> unchanged=files.load(USER);
            check(unchanged.isPresent(),"raw file remains present");
            WorldPlayer diskProbe=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(unchanged.get(),diskProbe);
            check(completedSource.equals(
                diskProbe.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE)),
                "status hydration did not mutate disk source77/91");
            check(restoredWorld.pvmRecords().receiptCount()==0&&
                  restoredWorld.groundItems().size()==0,
                "no phantom NPC terminal, G1 receipt or item grant");

            // A legitimate renewal mutates the owned extension but
            // does NOT publish to disk before an explicit repository save.
            LocalLabSlayerRuntime.StartResult renewal=
                restoredWorld.localLabSlayer().startMonsterHunter(
                    USER,RENEW_TICK);
            LocalLabSlayerPersistence.Snapshot renewed=
                LocalLabSlayerPersistence.decode(
                    restored.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            check(renewal!=null&&renewal.created&&
                  renewal.status.active()&&
                  renewal.status.completions==1L&&
                  renewed!=null&&
                  renewed.state==LocalLabSlayerPersistence.State.ACTIVE&&
                  renewed.progress==0L&&renewed.completions==1L&&
                  renewed.sourceAssignedTick==RENEW_TICK&&
                  renewed.sourceTransitionTick==-1L,
                "legitimate G4 renewal persists ACTIVE113 in World");
            WorldPlayer preWriteProbe=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                files.load(USER).get(),preWriteProbe);
            check(completedSource.equals(
                preWriteProbe.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE)),
                "disk still has original COMPLETED77/91 before next save");

            files.save(PlayerSnapshotCodec.capture(USER,restored));
            Optional<PlayerSnapshot> newDisk=files.load(USER);
            check(newDisk.isPresent(),"explicit second file save present");
            WorldPlayer postWriteProbe=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                newDisk.get(),postWriteProbe);
            LocalLabSlayerPersistence.Snapshot diskRenew=
                LocalLabSlayerPersistence.decode(
                    postWriteProbe.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            check(diskRenew!=null&&
                  diskRenew.state==LocalLabSlayerPersistence.State.ACTIVE&&
                  diskRenew.progress==0L&&diskRenew.completions==1L&&
                  diskRenew.sourceAssignedTick==RENEW_TICK&&
                  diskRenew.sourceTransitionTick==-1L&&
                  restoredWorld.pvmRecords().receiptCount()==0&&
                  restoredWorld.groundItems().size()==0,
                "explicit persisted postimage ACTIVE113 and no G1 replay");

            System.out.println(
                "G1867_COMPLETED_SLAYER_FILE_ROUNDTRIP_PROVENANCE_PASS"+
                " realFileRepositorySaveLoad=true"+
                " sourceValidCompleted77_91=true"+
                " validatedSnapshotApplication=true"+
                " freshWorldRuntimeReplayClock=true"+
                " originalDiskUntouchedByHydration=true"+
                " noPhantomG1CreditOrItem=true"+
                " renewalExplicitlyPersistsActive113=true"+
                " secondFilePublicationExplicit=true"+
                " originalSpawnpkPolicyClaim=false"+
                " socketFinalSaveTested=false"+
                " productionCodeChanged=false");
        }finally{
            if(restoredWorld!=null)restoredWorld.close();
            if(sourceWorld!=null)sourceWorld.close();
            if(Files.exists(root)){
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path path:paths.sorted(
                            Comparator.reverseOrder())
                        .toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void check(boolean valid,String message) {
        if(!valid)throw new AssertionError("G18.6.7 "+message);
    }

    private G1867SlayerCompletedFileRoundtripTemporalTest(){}
}
