package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.34: two separate write-once marker instances racing for one
 * destination MUST yield one successful negative fence and one collision.
 * The initial pre-existence check deliberately races in this fixture.
 * The publication primitive itself must never clobber the first marker.
 */
public final class G2134MailboxReviewFenceNoClobberIntegrationTest {
    private static final class Outcome {
        final boolean succeeded;
        final boolean alreadyExists;
        final MailboxDurableReviewFence.Receipt receipt;
        Outcome(
            boolean success,boolean collision,
            MailboxDurableReviewFence.Receipt evidence
        ){
            succeeded=success;
            alreadyExists=collision;
            receipt=evidence;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean bothInstancesReachedPublish=false;
        boolean exactlyOneSuccess=false;
        boolean loserDetectedCollision=false;
        boolean survivorMatchesOriginalProposal=false;
        boolean futureDuplicateCannotReplace=false;
        boolean prePublishFailureLeavesNoMarker=false;
        boolean postPublishUncertaintyStillFenced=false;
        boolean poisonMarkerCannotBeOverwritten=false;
        boolean noTempFilesAfterAllOutcomes=false;
        boolean exactPreparedStillForensicOnly=false;
        boolean newWorldRejectsMarker=false;
        boolean unfencedAccountUnaffected=false;
        boolean liveOwnerHasNoRewardOrClaim=false;
        boolean noGrantOrReplayAuthority=false;

        Path dir=Files.createTempDirectory(
            "g2134-mailbox-no-clobber-"
        );
        FilePlayerRepository.PathResolver resolver=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(resolver);
        MailboxDurableReviewFence reader=
            new MailboxDurableReviewFence(resolver);
        MailboxSettlementPostimagePlanner.Proposal main;
        MailboxSettlementPostimagePlanner.Proposal pre;
        MailboxSettlementPostimagePlanner.Proposal after;
        MailboxSettlementPostimagePlanner.Proposal poison;

        try{
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                main=prepared(world,"g2134-alice","g2134:alice");
                pre=prepared(world,"g2134-bob","g2134:bob");
                after=prepared(world,"g2134-carol","g2134:carol");
                poison=prepared(world,"g2134-dan","g2134:dan");
                repository.save(main.preparedPreimage);
                repository.save(pre.preparedPreimage);
                repository.save(after.preparedPreimage);
                repository.save(poison.preparedPreimage);

                CountDownLatch bothReady=new CountDownLatch(2);
                CountDownLatch go=new CountDownLatch(1);
                MailboxDurableReviewFence.FaultPoint barrier=phase->{
                    if(phase==MailboxDurableReviewFence.Phase
                            .BEFORE_ATOMIC_REPLACE){
                        bothReady.countDown();
                        try{
                            if(!go.await(8,TimeUnit.SECONDS))
                                throw new IOException(
                                    "G21.34 missing concurrent publish release"
                                );
                        }catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();
                            throw new IOException(
                                "G21.34 writer interrupted",interrupted
                            );
                        }
                    }
                };
                MailboxDurableReviewFence first=
                    new MailboxDurableReviewFence(resolver,barrier);
                MailboxDurableReviewFence second=
                    new MailboxDurableReviewFence(resolver,barrier);
                ExecutorService pool=Executors.newFixedThreadPool(2);
                Outcome left;
                Outcome right;
                try{
                    Future<Outcome> a=pool.submit(
                        ()->attempt(first,main)
                    );
                    Future<Outcome> b=pool.submit(
                        ()->attempt(second,main)
                    );
                    bothInstancesReachedPublish=
                        bothReady.await(8,TimeUnit.SECONDS);
                    go.countDown();
                    left=a.get(10,TimeUnit.SECONDS);
                    right=b.get(10,TimeUnit.SECONDS);
                }finally{
                    go.countDown();
                    pool.shutdownNow();
                }

                exactlyOneSuccess=
                    left.succeeded!=right.succeeded&&
                    (left.succeeded||right.succeeded);
                loserDetectedCollision=
                    left.alreadyExists!=right.alreadyExists&&
                    (left.alreadyExists||right.alreadyExists)&&
                    !((left.succeeded&&left.alreadyExists)||
                      (right.succeeded&&right.alreadyExists));
                MailboxDurableReviewFence.Receipt winner=
                    left.succeeded?left.receipt:right.receipt;
                MailboxDurableReviewFence.Record diskRecord=
                    reader.inspect(main.account);
                survivorMatchesOriginalProposal=
                    bothInstancesReachedPublish&&
                    exactlyOneSuccess&&loserDetectedCollision&&
                    winner!=null&&winner.fenceFile.equals(
                        reader.fencePath(main.account)
                    )&&diskRecord.matches(main)&&
                    !winner.grantAuthorized&&!winner.replayAuthorized;

                byte[] original=Files.readAllBytes(
                    reader.fencePath(main.account)
                );
                boolean duplicateDenied=false;
                try{
                    new MailboxDurableReviewFence(resolver).arm(main);
                }catch(IOException expected){
                    duplicateDenied=true;
                }
                futureDuplicateCannotReplace=
                    duplicateDenied&&Arrays.equals(
                        original,Files.readAllBytes(
                            reader.fencePath(main.account)
                        )
                    );

                boolean preFailed=false;
                try{
                    new MailboxDurableReviewFence(resolver,phase->{
                        if(phase==MailboxDurableReviewFence.Phase
                                .BEFORE_ATOMIC_REPLACE)
                            throw new IOException(
                                "G21.34 injected before exclusive publication"
                            );
                    }).arm(pre);
                }catch(IOException expected){
                    preFailed=true;
                }
                prePublishFailureLeavesNoMarker=
                    preFailed&&!reader.present(pre.account);

                boolean afterUnconfirmed=false;
                try{
                    new MailboxDurableReviewFence(resolver,phase->{
                        if(phase==MailboxDurableReviewFence.Phase
                                .BEFORE_DIRECTORY_FORCE)
                            throw new IOException(
                                "G21.34 injected after exclusive publication"
                            );
                    }).arm(after);
                }catch(MailboxDurableReviewFence
                        .UnconfirmedFenceException expected){
                    afterUnconfirmed=true;
                }
                postPublishUncertaintyStillFenced=
                    afterUnconfirmed&&reader.present(after.account)&&
                    reader.inspect(after.account).matches(after)&&
                    denied(world,after.account);

                byte[] originalPoison=
                    "preexisting-untrusted-review-marker".getBytes(
                        StandardCharsets.US_ASCII
                    );
                Files.write(
                    reader.fencePath(poison.account),originalPoison
                );
                boolean poisonRefused=false;
                try{
                    new MailboxDurableReviewFence(resolver).arm(poison);
                }catch(IOException expected){
                    poisonRefused=true;
                }
                poisonMarkerCannotBeOverwritten=
                    poisonRefused&&reader.present(poison.account)&&
                    Arrays.equals(
                        originalPoison,Files.readAllBytes(
                            reader.fencePath(poison.account)
                        )
                    )&&denied(world,poison.account);

                MailboxFencedRestartForensics.Report inspect=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),reader,main.account
                    );
                exactPreparedStillForensicOnly=
                    inspect.state==MailboxFencedRestartForensics.State
                        .EXACT_PREPARED_UNCLAIMED&&
                    !inspect.grantAuthorized&&
                    !inspect.replayAuthorized&&
                    !inspect.releaseFenceAuthorized&&
                    denied(world,main.account);

                unfencedAccountUnaffected=
                    !reader.present(pre.account)&&
                    world.persistence().load(pre.account).isPresent();

                try(Stream<Path> files=Files.list(dir)){
                    noTempFilesAfterAllOutcomes=
                        files.noneMatch(path->path.getFileName()
                            .toString().contains(".g2132-")&&
                            path.getFileName().toString().endsWith(".tmp"));
                }
                liveOwnerHasNoRewardOrClaim=true;
                for(WorldPlayer owner:world.players().snapshot()){
                    if(!owner.username().startsWith("g2134-"))
                        continue;
                    liveOwnerHasNoRewardOrClaim &=
                        owner.bank().inventorySlots()==0;
                    for(MailboxRewardDeliveryService.Snapshot row:
                            owner.mailbox().snapshot()){
                        liveOwnerHasNoRewardOrClaim &=
                            row.claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                    }
                }
                noGrantOrReplayAuthority=
                    !winner.grantAuthorized&&!winner.replayAuthorized&&
                    !diskRecord.grantAuthorized&&
                    !diskRecord.replayAuthorized&&
                    !diskRecord.releaseAuthorized;
            }

            try(World restarted=World.isolatedForTest(
                    60000L,new FilePlayerRepository(resolver))){
                restarted.start();
                newWorldRejectsMarker=
                    denied(restarted,main.account)&&
                    denied(restarted,after.account)&&
                    denied(restarted,poison.account)&&
                    restarted.persistence().load(pre.account).isPresent();
            }
        }finally{
            try(Stream<Path> files=Files.walk(dir)){
                for(Path path:files.sorted(
                    Comparator.reverseOrder()
                ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2134_MAILBOX_NO_CLOBBER_DIAGNOSTICS"+
            " bothWritersReachedPublish="+bothInstancesReachedPublish+
            " onlyOneWinner="+exactlyOneSuccess+
            " loserFileAlreadyExists="+loserDetectedCollision+
            " survivingMarkerIdentity="+survivorMatchesOriginalProposal+
            " duplicateNoOverwrite="+futureDuplicateCannotReplace+
            " prePublishNoMarker="+prePublishFailureLeavesNoMarker+
            " uncertainPostPublishFenced="+postPublishUncertaintyStillFenced+
            " poisonMarkerNoOverwrite="+poisonMarkerCannotBeOverwritten+
            " noTempFiles="+noTempFilesAfterAllOutcomes+
            " priorForensicsStillNoGrant="+exactPreparedStillForensicOnly+
            " restartedWorldDenied="+newWorldRejectsMarker+
            " noFenceUnaffected="+unfencedAccountUnaffected+
            " liveOwnerUnchanged="+liveOwnerHasNoRewardOrClaim+
            " noGrantOrReplayAuthority="+noGrantOrReplayAuthority
        );
        require(
            bothInstancesReachedPublish&&exactlyOneSuccess&&
            loserDetectedCollision&&survivorMatchesOriginalProposal&&
            futureDuplicateCannotReplace&&
            prePublishFailureLeavesNoMarker&&
            postPublishUncertaintyStillFenced&&
            poisonMarkerCannotBeOverwritten&&
            noTempFilesAfterAllOutcomes&&
            exactPreparedStillForensicOnly&&newWorldRejectsMarker&&
            unfencedAccountUnaffected&&liveOwnerHasNoRewardOrClaim&&
            noGrantOrReplayAuthority,
            "G21.34 no-clobber fence publication"
        );
        System.out.println(
            "G2134_MAILBOX_NO_CLOBBER_FENCE_PASS"+
            " competingWritersOneWinner=true"+
            " priorMarkerCannotBeReplaced=true"+
            " noRenameFallback=true"+
            " restartLoadVeto=true"+
            " liveItemGrant=false liveMailboxClaim=false"
        );
    }

    private static Outcome attempt(
        MailboxDurableReviewFence writer,
        MailboxSettlementPostimagePlanner.Proposal proposal
    )throws IOException{
        try{
            return new Outcome(true,false,writer.arm(proposal));
        }catch(FileAlreadyExistsException duplicate){
            return new Outcome(false,true,null);
        }
    }

    private static MailboxSettlementPostimagePlanner.Proposal prepared(
        World world,String account,String messageId
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal> out=
            new AtomicReference<>();
        world.submitAndWait(player,generation,()->{
            player.mailbox().deliver(new RewardDeliveryMessage(
                messageId,"G21.34 Review Fence","No live claim",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2134_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot selected=
                player.mailbox().get(messageId);
            MailboxPreparedClaimJournal.stageOnly(
                player,MailboxPreparedClaimJournal.prepare(
                    player,selected
                )
            );
            out.set(MailboxSettlementPostimagePlanner.plan(
                player,generation,selected
            ));
        },5000L);
        return out.get();
    }

    private static boolean denied(World world,String account)
        throws Exception{
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException rejected){
            return rejected.getMessage()!=null&&
                rejected.getMessage().contains(
                    "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                );
        }
    }

    private static void require(boolean yes,String label){
        if(!yes)throw new AssertionError(label);
    }

    private G2134MailboxReviewFenceNoClobberIntegrationTest(){}
}
