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

/** G21.47: negative-only restart quarantine after an uncertain strict save. */
public final class G2147MailboxStrictUncertainFenceIntegrationTest {
    public static void main(String[] ignored)throws Exception{
        Path dir=Files.createTempDirectory("g2147-review-");
        String account="g2147-account",other="g2147-other";
        FilePlayerRepository.PathResolver paths=
            name->dir.resolve(name+".properties");
        FilePlayerRepository disk=new FilePlayerRepository(paths);
        MailboxStrictUncertainFence fence=
            new MailboxStrictUncertainFence(paths);
        CountDownLatch moved=new CountDownLatch(1);
        CountDownLatch resume=new CountDownLatch(1);
        boolean fileMoved=false,unconfirmed=false,quarantined=false;
        boolean saveDenied=false,loginDenied=false,onlineDenied=false;
        boolean otherGood=false,noGrant=false,noTemp=false;
        boolean noOriginalMarker=false,duplicateVeto=false;
        boolean badSidecarVeto=false;
        try{
            try(World world=World.isolatedForTest(60000L,disk)){
                world.start();
                WorldPlayer owner=new WorldPlayer();
                long gen=world.registerPlayer(owner,account);
                WorldPlayer independent=new WorldPlayer();
                world.registerPlayer(independent,other);
                AtomicReference<PlayerSnapshot> prepared=
                    new AtomicReference<>();
                world.submitAndWait(owner,gen,()->{
                    owner.mailbox().deliver(new RewardDeliveryMessage(
                        "g2147:gift","Review-only","NO_GRANT",
                        Collections.singletonList(
                            new RewardDeliveryMessage.Attachment(995,25)
                        ),"G2147_TEST"
                    ));
                    MailboxRewardDeliveryService.Snapshot row=
                        owner.mailbox().get("g2147:gift");
                    MailboxPreparedClaimJournal.stageOnly(
                        owner,MailboxPreparedClaimJournal.prepare(owner,row)
                    );
                    prepared.set(PlayerSnapshotCodec.capture(account,owner));
                },5000L);
                disk.save(prepared.get());
                disk.save(PlayerSnapshotCodec.capture(other,independent));
                byte[] old=Files.readAllBytes(paths.resolve(account));
                StrictDurablePlayerSnapshotWriter writer=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .AFTER_DIRECTORY_FORCE){
                            moved.countDown();
                            try{
                                if(!resume.await(8,TimeUnit.SECONDS))
                                    throw new IOException("release timeout");
                            }catch(InterruptedException e){
                                Thread.currentThread().interrupt();
                                throw new IOException(e);
                            }
                        }
                    });
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    task=world.persistence().submitPreparedStrictBarrier(
                        owner,gen,prepared.get(),writer
                    );
                if(!moved.await(8,TimeUnit.SECONDS))
                    throw new AssertionError("postmove seam not reached");
                fileMoved=!Arrays.equals(old,
                    Files.readAllBytes(paths.resolve(account)));
                world.submitAndWait(owner,gen,()->{
                    owner.movement().setRunEnergy(43);
                },5000L);
                resume.countDown();
                try{
                    task.get(8,TimeUnit.SECONDS);
                }catch(ExecutionException failure){
                    unconfirmed=failure.getCause() instanceof
                        StrictDurablePlayerSnapshotWriter
                            .UnconfirmedCommitException;
                }
                quarantined=fence.present(account)&&
                    disk.hasUnresolvedMailboxReviewFence(account);
                noOriginalMarker=!new MailboxDurableReviewFence(paths)
                    .present(account);
                noGrant=owner.bank().inventorySlots()==0&&
                    owner.mailbox().get("g2147:gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                try{
                    disk.saveForWorld(prepared.get());
                }catch(IOException denied){
                    saveDenied=denied.getMessage().contains(
                        "MAILBOX_DURABLE_REVIEW_SAVE_VETO"
                    );
                }
                otherGood=disk.load(other).isPresent()&&
                    !disk.hasUnresolvedMailboxReviewFence(other);
                try{
                    fence.armInsidePublicationLock(
                        account,StrictDurablePlayerSnapshotWriter
                            .canonicalSnapshotSha256(prepared.get())
                    );
                }catch(IOException expected){
                    duplicateVeto=fence.present(account);
                }
            }
            try(World restart=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                restart.start();
                try{
                    restart.persistence().load(account);
                }catch(IOException denied){
                    loginDenied=denied.getMessage().contains(
                        "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                    );
                }
                try{
                    MailboxActiveSessionReviewGuard.forSession(
                        restart.persistence(),account,true
                    ).requireAtBoundary();
                }catch(IOException denied){
                    onlineDenied=denied.getMessage().contains(
                        "G21.37 MAILBOX_LIVE_REVIEW_FENCE"
                    );
                }
            }
            Files.writeString(fence.fencePath(account),"damaged");
            badSidecarVeto=fence.present(account)&&
                disk.hasUnresolvedMailboxReviewFence(account);
            try(Stream<Path> files=Files.list(dir)){
                noTemp=files.noneMatch(x->x.toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            resume.countDown();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2147_MAILBOX_STRICT_NEGATIVE_DIAGNOSTICS"+
            " accountAlreadyReplaced="+fileMoved+
            " unconfirmedNoReceipt="+unconfirmed+
            " quarantinePersisted="+quarantined+
            " WorldSaveDenied="+saveDenied+
            " restartLoginDenied="+loginDenied+
            " activeBoundaryDenied="+onlineDenied+
            " unrelatedAccountGood="+otherGood+
            " noItemGrant="+noGrant+
            " g2132SidecarUnchanged="+noOriginalMarker+
            " duplicateNoClobber="+duplicateVeto+
            " invalidSidecarStillDenies="+badSidecarVeto+
            " noTempOrLeaseLeaks="+noTemp);
        if(!(fileMoved&&unconfirmed&&quarantined&&saveDenied&&
             loginDenied&&onlineDenied&&otherGood&&noGrant&&
             noOriginalMarker&&duplicateVeto&&badSidecarVeto&&noTemp))
            throw new AssertionError("G21.47 review quarantine");
        System.out.println("G2147_MAILBOX_STRICT_NEGATIVE_QUARANTINE_PASS"+
            " restartAdmissionDenied=true WorldSaveDenied=true"+
            " liveSessionDenied=true grant=false replay=false release=false");
    }
    private G2147MailboxStrictUncertainFenceIntegrationTest(){}
}
