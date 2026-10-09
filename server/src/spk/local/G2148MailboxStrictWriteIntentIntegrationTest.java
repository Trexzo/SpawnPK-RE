package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.48: negative write-ahead intent exists before account replacement. */
public final class G2148MailboxStrictWriteIntentIntegrationTest {
    private static final class Seed {
        final String name;
        final WorldPlayer player;
        final long generation;
        final PlayerSnapshot prepared;
        Seed(String name,WorldPlayer player,long generation,
             PlayerSnapshot prepared){
            this.name=name;
            this.player=player;
            this.generation=generation;
            this.prepared=prepared;
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2148-wal-");
        FilePlayerRepository.PathResolver paths=
            name->root.resolve(name+".properties");
        FilePlayerRepository disk=new FilePlayerRepository(paths);
        MailboxStrictWriteIntentFence intent=
            new MailboxStrictWriteIntentFence(paths);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(paths);

        boolean beganBeforeMove=false,earlyTaskFailed=false;
        boolean preimageNeverReplaced=false;
        boolean strandedIntentVetoes=false;
        boolean noSpuriousPermanentMarker=false;
        boolean normalWriterReceipted=false;
        boolean normalIntentCleaned=false;
        boolean cleanPlayerLoadedAfterRestart=false;
        boolean strandedPlayerDeniedAfterRestart=false;
        boolean normalAccountUnfenced=false;
        boolean malformedIntentStillBlocks=false;
        boolean duplicateIntentNoClobber=false;
        boolean unrelatedAccountUnaffected=false;
        boolean noRewardCredit=false;
        boolean noTempOrLeaseLeaks=false;
        try{
            try(World world=World.isolatedForTest(60000L,disk)){
                world.start();
                Seed failed=seed(world,"g2148-failed","g2148:failed");
                Seed normal=seed(world,"g2148-normal","g2148:normal");
                Seed other=seed(world,"g2148-other","g2148:other");
                for(Seed item:new Seed[]{failed,normal,other})
                    disk.save(item.prepared);
                byte[] old=Files.readAllBytes(
                    paths.resolve(failed.name)
                );
                byte[] otherOld=Files.readAllBytes(
                    paths.resolve(other.name)
                );
                StrictDurablePlayerSnapshotWriter interrupted=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .AFTER_WRITE_AHEAD_INTENT)
                            throw new IOException(
                                "G21.48 deliberate pre-ATOMIC_MOVE failure"
                            );
                    });
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    failedTask=submit(world,failed,interrupted);
                try{
                    failedTask.get(8,TimeUnit.SECONDS);
                }catch(ExecutionException expected){
                    earlyTaskFailed=
                        expected.getCause() instanceof IOException&&
                        expected.getCause().getMessage().contains(
                            "deliberate pre-ATOMIC_MOVE failure"
                        );
                }
                beganBeforeMove=intent.present(failed.name);
                preimageNeverReplaced=Arrays.equals(
                    old,Files.readAllBytes(
                        paths.resolve(failed.name)
                    )
                );
                strandedIntentVetoes=
                    disk.hasUnresolvedMailboxReviewFence(failed.name);
                noSpuriousPermanentMarker=
                    !permanent.present(failed.name);

                boolean duplicate=false;
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(
                            paths.resolve(failed.name),()->{
                                intent.armInsidePublicationLock(
                                    failed.name,
                                    StrictDurablePlayerSnapshotWriter
                                        .canonicalSnapshotSha256(
                                            failed.prepared
                                        )
                                );
                                return null;
                            }
                        );
                }catch(IOException expected){
                    duplicate=true;
                }
                duplicateIntentNoClobber=duplicate&&
                    intent.present(failed.name);

                StrictDurablePlayerSnapshotWriter writer=
                    new StrictDurablePlayerSnapshotWriter(paths);
                StrictDurablePlayerSnapshotWriter.Receipt success=
                    submit(world,normal,writer).get(
                        8,TimeUnit.SECONDS
                    );
                normalWriterReceipted=success.matchesSnapshot(
                    normal.prepared
                );
                normalIntentCleaned=!intent.present(normal.name)&&
                    !permanent.present(normal.name);
                normalAccountUnfenced=
                    !disk.hasUnresolvedMailboxReviewFence(normal.name);
                unrelatedAccountUnaffected=
                    Arrays.equals(otherOld,Files.readAllBytes(
                        paths.resolve(other.name)
                    ))&&!intent.present(other.name);
                noRewardCredit=true;
                for(Seed item:new Seed[]{failed,normal,other})
                    noRewardCredit &=
                        item.player.bank().inventorySlots()==0&&
                        item.player.mailbox().get(
                            "g2148:"+item.name.substring(6)
                        )!=null;
            }

            try(World reboot=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                reboot.start();
                cleanPlayerLoadedAfterRestart=
                    reboot.persistence().load("g2148-normal").isPresent();
                try{
                    reboot.persistence().load("g2148-failed");
                }catch(IOException refused){
                    strandedPlayerDeniedAfterRestart=
                        refused.getMessage().contains(
                            "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                        );
                }
            }
            Files.writeString(
                intent.fencePath("g2148-failed"),"broken-intent"
            );
            malformedIntentStillBlocks=
                intent.present("g2148-failed")&&
                disk.hasUnresolvedMailboxReviewFence("g2148-failed");
            try(Stream<Path> files=Files.list(root)){
                noTempOrLeaseLeaks=files.noneMatch(
                    x->x.getFileName().toString().endsWith(".tmp")
                )&&MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2148_MAILBOX_WRITE_AHEAD_DIAGNOSTICS"+
            " intentBeforeMove="+beganBeforeMove+
            " injectedPreMoveFailure="+earlyTaskFailed+
            " originalAccountPreserved="+preimageNeverReplaced+
            " strandedIntentVeto="+strandedIntentVetoes+
            " noPermanentMarkerInvented="+noSpuriousPermanentMarker+
            " normalStrictReceipt="+normalWriterReceipted+
            " normalTransientCleanup="+normalIntentCleaned+
            " normalRestartAdmission="+cleanPlayerLoadedAfterRestart+
            " strandedRestartVeto="+strandedPlayerDeniedAfterRestart+
            " healthyAccountUnfenced="+normalAccountUnfenced+
            " corruptedIntentStillVetoes="+malformedIntentStillBlocks+
            " duplicateIntentDenied="+duplicateIntentNoClobber+
            " otherAccountPreserved="+unrelatedAccountUnaffected+
            " noInventoryGrant="+noRewardCredit+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks
        );
        if(!(beganBeforeMove&&earlyTaskFailed&&
              preimageNeverReplaced&&strandedIntentVetoes&&
              noSpuriousPermanentMarker&&normalWriterReceipted&&
              normalIntentCleaned&&cleanPlayerLoadedAfterRestart&&
              strandedPlayerDeniedAfterRestart&&
              normalAccountUnfenced&&malformedIntentStillBlocks&&
              duplicateIntentNoClobber&&unrelatedAccountUnaffected&&
              noRewardCredit&&noTempOrLeaseLeaks))
            throw new AssertionError("G21.48 write-ahead negative intent");

        System.out.println("G2148_MAILBOX_STRICT_WRITE_AHEAD_INTENT_PASS"+
            " intentBeforeAccountMove=true"+
            " crashWindowRestartQuarantined=true"+
            " successClearsOnlyTransientIntent=true"+
            " grant=false replay=false permanentRelease=false");
    }

    private static Seed seed(
        World world,String name,String message
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,name);
        AtomicReference<PlayerSnapshot> snapshot=
            new AtomicReference<>();
        world.submitAndWait(player,generation,()->{
            player.mailbox().deliver(
                new RewardDeliveryMessage(
                    message,"Strict intent test","NO_GRANT",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)
                    ),"G2148_TEST"
                )
            );
            MailboxRewardDeliveryService.Snapshot row=
                player.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(
                player,
                MailboxPreparedClaimJournal.prepare(player,row)
            );
            snapshot.set(PlayerSnapshotCodec.capture(name,player));
        },5000L);
        return new Seed(name,player,generation,snapshot.get());
    }

    private static CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
        submit(World world,Seed seed,
               StrictDurablePlayerSnapshotWriter writer){
        return world.persistence().submitPreparedStrictBarrier(
            seed.player,seed.generation,seed.prepared,writer
        );
    }

    private G2148MailboxStrictWriteIntentIntegrationTest(){}
}
