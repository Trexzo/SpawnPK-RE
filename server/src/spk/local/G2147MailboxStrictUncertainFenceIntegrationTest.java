package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.47: persisted negative no-grant veto after a late strict move. */
public final class G2147MailboxStrictUncertainFenceIntegrationTest {
    private static final class Seed {
        final String account,message;
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(String a,String m,WorldPlayer p,long g,
             MailboxSettlementPostimagePlanner.Proposal x){
            account=a;message=m;player=p;generation=g;proposal=x;
        }
    }
    public static void main(String[] args)throws Exception{
        boolean postmoveDivergence=false;
        boolean persistentMarker=false;
        boolean noClobberRepeat=false;
        boolean restartDenied=false;
        boolean normalWorldSaveDenied=false;
        boolean strictWorldSaveDenied=false;
        boolean originalBytesRetainedOnVeto=false;
        boolean independentAccountWorks=false;
        boolean noMarkerTemps=false;
        boolean noGrantOrClaim=false;
        boolean noAutomaticRelease=false;
        boolean noLeaseLeak=false;

        Path dir=Files.createTempDirectory("g2147-strict-fenced-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository disk=new FilePlayerRepository(paths);
        CountDownLatch forced=new CountDownLatch(1);
        CountDownLatch continueWrite=new CountDownLatch(1);
        try{
            try(World world=World.isolatedForTest(60000L,disk)){
                world.start();
                Seed uncertain=seed(world,"g2147-uncertain","g2147:gift");
                Seed other=seed(world,"g2147-other","g2147:other");
                disk.save(uncertain.proposal.preparedPreimage);
                disk.save(other.proposal.preparedPreimage);
                StrictDurablePlayerSnapshotWriter writer=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,phase->{
                            if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                    .AFTER_DIRECTORY_FORCE){
                                forced.countDown();
                                await(continueWrite);
                            }
                        }
                    );
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    submitted=world.persistence()
                        .submitPreparedStrictBarrier(
                            uncertain.player,uncertain.generation,
                            uncertain.proposal.preparedPreimage,writer
                        );
                if(!forced.await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.47 strict account move never reached"
                    );
                world.submitAndWait(
                    uncertain.player,uncertain.generation,
                    ()->uncertain.player.movement().setRunEnergy(28),
                    5000L
                );
                continueWrite.countDown();
                try{
                    submitted.get(8,TimeUnit.SECONDS);
                }catch(ExecutionException ex){
                    postmoveDivergence=ex.getCause() instanceof
                        StrictDurablePlayerSnapshotWriter
                            .UnconfirmedCommitException&&
                        ex.getCause().getMessage().contains(
                            "G21.47_negativeFence=publicationAttemptCompleted"
                        );
                }
                Path account=paths.resolve(uncertain.account);
                Path marker=MailboxStrictUncertainFence.path(account);
                persistentMarker=postmoveDivergence&&
                    MailboxStrictUncertainFence.present(account)&&
                    Files.isRegularFile(marker);
                byte[] originalMarker=Files.readAllBytes(marker);
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(account,()->{
                        MailboxStrictUncertainFence
                            .publishWhileAccountLocked(account);
                        return null;
                    });
                noClobberRepeat=Arrays.equals(
                    originalMarker,Files.readAllBytes(marker)
                );

                byte[] before=Files.readAllBytes(account);
                WorldPlayerPersistence.SaveTicket blocked=
                    save(world,uncertain,"POST_UNCERTAIN_NORMAL_SAVE");
                normalWorldSaveDenied=failed(
                    blocked.completion,"G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO"
                )&&MailboxStrictUncertainFence.present(account);
                strictWorldSaveDenied=false;
                PlayerSnapshot current=PlayerSnapshotCodec.capture(
                    uncertain.account,uncertain.player
                );
                try{
                    new StrictDurablePlayerSnapshotWriter(paths)
                        .saveStrictForWorld(
                            current,disk.accountFilePath(
                                uncertain.account
                            )
                        );
                }catch(IOException denied){
                    strictWorldSaveDenied=denied.getMessage().contains(
                        "G21.47 STRICT_UNCONFIRMED_NEGATIVE_FENCE"
                    )||denied.getMessage().contains(
                        "G21.42 STRICT_WORLD_PREPARED_QUARANTINE"
                    );
                }
                originalBytesRetainedOnVeto=Arrays.equals(
                    before,Files.readAllBytes(account)
                );
                WorldPlayerPersistence.SaveTicket normalOther=
                    save(world,other,"UNRELATED_AFTER_UNCERTAIN");
                normalOther.completion.get(8,TimeUnit.SECONDS);
                independentAccountWorks=
                    disk.load(other.account).isPresent()&&
                    !MailboxStrictUncertainFence.present(
                        paths.resolve(other.account)
                    );

                noGrantOrClaim=
                    uncertain.player.bank().inventorySlots()==0&&
                    other.player.bank().inventorySlots()==0&&
                    uncertain.player.mailbox().get(
                        uncertain.message
                    ).claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                noAutomaticRelease=MailboxStrictUncertainFence
                    .present(account)&&
                    !new MailboxDurableReviewFence(paths)
                        .present(uncertain.account);
                noLeaseLeak=MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
                try(Stream<Path> files=Files.list(dir)){
                    noMarkerTemps=files.noneMatch(
                        file->file.getFileName().toString()
                            .endsWith(".tmp")
                    );
                }
            }
            try(World reboot=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                reboot.start();
                try{
                    reboot.persistence().load("g2147-uncertain");
                }catch(IOException veto){
                    restartDenied=veto.getMessage().contains(
                        "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                    );
                }
                independentAccountWorks &=
                    reboot.persistence().load("g2147-other").isPresent();
            }
        }finally{
            continueWrite.countDown();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path p:files.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }

        System.out.println(
            "G2147_STRICT_UNCERTAIN_DIAGNOSTICS"+
            " unconfirmedAfterMove="+postmoveDivergence+
            " negativeSidecarPersisted="+persistentMarker+
            " sidecarNeverClobbered="+noClobberRepeat+
            " restartSessionRejected="+restartDenied+
            " normalWorldSaveRejected="+normalWorldSaveDenied+
            " guardedStrictSaveRejected="+strictWorldSaveDenied+
            " unchangedAfterSaveVeto="+originalBytesRetainedOnVeto+
            " unrelatedAccountWorks="+independentAccountWorks+
            " noTempFiles="+noMarkerTemps+
            " noLiveGrant="+noGrantOrClaim+
            " noAutoRelease="+noAutomaticRelease+
            " noJvmLockLeaks="+noLeaseLeak
        );
        if(!(postmoveDivergence&&persistentMarker&&noClobberRepeat&&
             restartDenied&&normalWorldSaveDenied&&
             strictWorldSaveDenied&&originalBytesRetainedOnVeto&&
             independentAccountWorks&&noMarkerTemps&&
             noGrantOrClaim&&noAutomaticRelease&&noLeaseLeak))
            throw new AssertionError(
                "G21.47 negative strict uncertainty fence"
            );
        System.out.println(
            "G2147_STRICT_UNCERTAIN_FENCE_PASS"+
            " persistentNegativeVeto=true"+
            " liveGrant=false replay=false release=false"
        );
    }
    private static Seed seed(World w,String name,String message)
        throws Exception{
        WorldPlayer p=new WorldPlayer();
        long generation=w.registerPlayer(p,name);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            proposal=new AtomicReference<>();
        w.submitAndWait(p,generation,()->{
            p.mailbox().deliver(new RewardDeliveryMessage(
                message,"Uncertain strict evidence","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2147_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot m=
                p.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(
                p,MailboxPreparedClaimJournal.prepare(p,m)
            );
            proposal.set(MailboxSettlementPostimagePlanner.plan(
                p,generation,m
            ));
        },5000L);
        return new Seed(name,message,p,generation,proposal.get());
    }
    private static WorldPlayerPersistence.SaveTicket save(
        World w,Seed s,String why)throws Exception{
        AtomicReference<WorldPlayerPersistence.SaveTicket> task=
            new AtomicReference<>();
        w.submitAndWait(s.player,s.generation,()->{
            task.set(w.persistence().captureAndSave(
                s.account,s.player,s.generation,0,"[g2147] ",why
            ));
        },5000L);
        return task.get();
    }
    private static boolean failed(
        CompletableFuture<Void> f,String expected
    )throws Exception{
        try{
            f.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException x){
            return x.getCause() instanceof IOException&&
                x.getCause().getMessage().contains(expected);
        }
    }
    private static void await(CountDownLatch gate)throws IOException{
        try{
            if(!gate.await(8,TimeUnit.SECONDS))
                throw new IOException("G21.47 held writer timed out");
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException("G21.47 interrupted",interrupted);
        }
    }
}
