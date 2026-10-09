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

/** G21.49: force failures AFTER strict ATOMIC_MOVE need restart veto. */
public final class G2149MailboxStrictWriteIntentPostmoveForceIntegrationTest {
    static final class Seed {
        final WorldPlayer player;
        final long generation;
        final String account,message;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(WorldPlayer p,long g,String a,String m,
             MailboxSettlementPostimagePlanner.Proposal x){
            player=p;generation=g;account=a;message=m;proposal=x;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean beforeDirectoryForceUnconfirmed=false;
        boolean beforeForceSidecarPresent=false;
        boolean accountAlreadyReplaced=false;
        boolean afterDirectoryForceUnconfirmed=false;
        boolean afterForceSidecarPresent=false;
        boolean beforeMoveFailureNoSidecar=false;
        boolean beforeMoveFilePreserved=false;
        boolean restartRefusesBothUncertain=false;
        boolean guardedWorldSaveRejected=false;
        boolean independentAccountWorks=false;
        boolean noLiveGrant=false;
        boolean noMarkerTemps=false;
        boolean noAutoRelease=false;
        boolean registryClean=false;

        Path root=Files.createTempDirectory("g2149-force-fence-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        MailboxStrictUncertainFence permanent=new MailboxStrictUncertainFence(paths);
        MailboxStrictWriteIntentFence intent=new MailboxStrictWriteIntentFence(paths);
        try{
            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                Seed early=seed(world,"g2149-early","g2149:early");
                Seed late=seed(world,"g2149-late","g2149:late");
                Seed before=seed(world,"g2149-before","g2149:before");
                Seed other=seed(world,"g2149-other","g2149:other");
                for(Seed s:new Seed[]{early,late,before,other})
                    repo.save(s.proposal.preparedPreimage);

                byte[] originalEarly=Files.readAllBytes(
                    paths.resolve(early.account)
                );
                byte[] originalBefore=Files.readAllBytes(
                    paths.resolve(before.account)
                );
                StrictDurablePlayerSnapshotWriter earlyWriter=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,phase->{
                            if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                    .BEFORE_DIRECTORY_FORCE)
                                throw new IOException(
                                    "G21.49 INJECT_BEFORE_DIRECTORY_FORCE"
                                );
                        }
                    );
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    earlyFuture=submit(world,early,earlyWriter);
                beforeDirectoryForceUnconfirmed=uncertain(earlyFuture);
                beforeForceSidecarPresent=
                    permanent.present(early.account)&&
                    intent.present(early.account);
                accountAlreadyReplaced=
                    !Arrays.equals(originalEarly,
                        Files.readAllBytes(paths.resolve(early.account)))&&
                    repo.load(early.account).get().values().equals(
                        early.proposal.preparedPreimage.values()
                    );

                StrictDurablePlayerSnapshotWriter lateWriter=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,phase->{
                            if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                    .AFTER_DIRECTORY_FORCE)
                                throw new IOException(
                                    "G21.49 INJECT_AFTER_DIRECTORY_FORCE"
                                );
                        }
                    );
                afterDirectoryForceUnconfirmed=uncertain(
                    submit(world,late,lateWriter)
                );
                afterForceSidecarPresent=
                    permanent.present(late.account)&&
                    intent.present(late.account);

                StrictDurablePlayerSnapshotWriter beforeWriter=
                    new StrictDurablePlayerSnapshotWriter(
                        paths,phase->{
                            if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                    .BEFORE_ATOMIC_REPLACE)
                                throw new IOException(
                                    "G21.49 INJECT_PREMOVE"
                                );
                        }
                    );
                boolean preMoveFailed=false;
                try{
                    submit(world,before,beforeWriter).get(
                        8,TimeUnit.SECONDS
                    );
                }catch(ExecutionException expected){
                    preMoveFailed=expected.getCause() instanceof IOException&&
                        !(expected.getCause() instanceof
                            StrictDurablePlayerSnapshotWriter
                                .UnconfirmedCommitException);
                }
                beforeMoveFailureNoSidecar=preMoveFailed&&
                    !permanent.present(before.account)&&
                    !intent.present(before.account);
                beforeMoveFilePreserved=Arrays.equals(
                    originalBefore,
                    Files.readAllBytes(paths.resolve(before.account))
                );

                // Existing quarantines cannot be silently overwritten
                // by ordinary World save/capture after uncertainty.
                AtomicReference<WorldPlayerPersistence.SaveTicket> blocked=
                    new AtomicReference<>();
                world.submitAndWait(early.player,early.generation,()->{
                    blocked.set(world.persistence().captureAndSave(
                        early.account,early.player,early.generation,0,
                        "[g2149] ","UNCERTAIN_FORCE_SAVE_DENIED"
                    ));
                },5000L);
                guardedWorldSaveRejected=failed(
                    blocked.get().completion
                );
                AtomicReference<WorldPlayerPersistence.SaveTicket> healthy=
                    new AtomicReference<>();
                world.submitAndWait(other.player,other.generation,()->{
                    healthy.set(world.persistence().captureAndSave(
                        other.account,other.player,other.generation,0,
                        "[g2149] ","INDEPENDENT_UNQUARANTINED"
                    ));
                },5000L);
                healthy.get().completion.get(8,TimeUnit.SECONDS);
                independentAccountWorks=
                    repo.load(other.account).isPresent()&&
                    !permanent.present(other.account)&&
                    !intent.present(other.account);

                noLiveGrant=true;
                for(Seed s:new Seed[]{early,late,before,other})
                    noLiveGrant &=
                        s.player.bank().inventorySlots()==0&&
                        s.player.mailbox().get(s.message).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                noAutoRelease=beforeForceSidecarPresent&&
                    afterForceSidecarPresent;
                try(Stream<Path> files=Files.list(root)){
                    noMarkerTemps=files.noneMatch(
                        p->p.getFileName().toString().endsWith(".tmp")
                    );
                }
                registryClean=MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
            }
            try(World reboot=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                reboot.start();
                restartRefusesBothUncertain=
                    refused(reboot,"g2149-early")&&
                    refused(reboot,"g2149-late")&&
                    reboot.persistence().load("g2149-other").isPresent();
            }
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path file:files.sorted(
                    Comparator.reverseOrder()
                ).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
        System.out.println(
            "G2149_STRICT_POSTMOVE_DIAGNOSTICS"+
            " beforeDirectoryForceUnconfirmed="+
                beforeDirectoryForceUnconfirmed+
            " beforeForceMarker="+beforeForceSidecarPresent+
            " accountAlreadyMoved="+accountAlreadyReplaced+
            " afterDirectoryForceUnconfirmed="+
                afterDirectoryForceUnconfirmed+
            " afterForceMarker="+afterForceSidecarPresent+
            " preMoveNoMarker="+beforeMoveFailureNoSidecar+
            " preMoveOriginalBytes="+beforeMoveFilePreserved+
            " freshWorldRefuses="+restartRefusesBothUncertain+
            " WorldSaveBlocked="+guardedWorldSaveRejected+
            " otherAccountHealthy="+independentAccountWorks+
            " noLiveItemGrant="+noLiveGrant+
            " noTemps="+noMarkerTemps+
            " noAutoRelease="+noAutoRelease+
            " noPublicationLeaseLeaks="+registryClean
        );
        if(!(beforeDirectoryForceUnconfirmed&&beforeForceSidecarPresent&&
             accountAlreadyReplaced&&afterDirectoryForceUnconfirmed&&
             afterForceSidecarPresent&&beforeMoveFailureNoSidecar&&
             beforeMoveFilePreserved&&restartRefusesBothUncertain&&
             guardedWorldSaveRejected&&independentAccountWorks&&
             noLiveGrant&&noMarkerTemps&&noAutoRelease&&registryClean))
            throw new AssertionError(
                "G21.49 strict postmove metadata force safety"
            );
        System.out.println(
            "G2149_STRICT_POSTMOVE_FORCE_FENCE_PASS"+
            " beforeAndAfterDirectoryForceNegativeMarker=true"+
            " preMoveFailureNotMisclassified=true"+
            " grant=false replay=false release=false"
        );
    }

    private static CompletableFuture<
        StrictDurablePlayerSnapshotWriter.Receipt> submit(
        World world,Seed seed,
        StrictDurablePlayerSnapshotWriter writer
    ){
        return world.persistence().submitPreparedStrictBarrier(
            seed.player,seed.generation,
            seed.proposal.preparedPreimage,writer
        );
    }

    private static boolean uncertain(
        CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt> f
    )throws Exception{
        try{
            f.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException ex){
            return ex.getCause() instanceof
                StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException;
        }
    }

    private static boolean failed(
        CompletableFuture<Void> f
    )throws Exception{
        try{
            f.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException ex){
            return ex.getCause() instanceof IOException&&
                ex.getCause().getMessage().contains(
                    "G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO"
                );
        }
    }
    private static boolean refused(World world,String account)
        throws Exception{
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException denied){
            return denied.getMessage().contains(
                "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
            );
        }
    }
    private static Seed seed(World world,String account,String message)
        throws Exception{
        WorldPlayer p=new WorldPlayer();
        long g=world.registerPlayer(p,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            proposal=new AtomicReference<>();
        world.submitAndWait(p,g,()->{
            p.mailbox().deliver(new RewardDeliveryMessage(
                message,"Postmove strict force","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2149_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot row=
                p.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(
                p,MailboxPreparedClaimJournal.prepare(p,row)
            );
            proposal.set(MailboxSettlementPostimagePlanner.plan(
                p,g,row
            ));
        },5000L);
        return new Seed(p,g,account,message,proposal.get());
    }
}
