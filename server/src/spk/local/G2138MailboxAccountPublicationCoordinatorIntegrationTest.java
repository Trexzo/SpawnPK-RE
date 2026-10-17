package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.38 deterministic opposite-order publication interleavings.
 *
 * Uses distinct FilePlayerRepository and MailboxDurableReviewFence
 * instances with the same canonical account path and JVM/FileLock.
 * No native Mailbox widget or reward-grant code is executed.
 */
public final class G2138MailboxAccountPublicationCoordinatorIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean worldSaveOwnsPublicationBeforeMarker=false;
        boolean markerWaitsForEarlierSave=false;
        boolean earlierSaveCompletesThenMarkerPublishes=false;
        boolean markerOwnsPublicationBeforeSave=false;
        boolean worldWriterReachedPreLockWhenMarkerHeld=false;
        boolean markerPublishesFirstAndWorldSaveRefused=false;
        boolean markerFirstKeepsOldFileBytes=false;
        boolean noResidualTempFile=false;
        boolean unrelatedAccountStillWrites=false;
        boolean restartFencesBothAccounts=false;
        boolean consistentLockFilePaths=false;
        boolean noLiveGrantOrClaim=false;
        boolean noAutomaticMarkerRelease=false;

        Path dir=Files.createTempDirectory(
            "g2138-account-publication-"
        );
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        String firstAccount="g2138-save-first";
        String secondAccount="g2138-marker-first";
        String otherAccount="g2138-unrelated";
        CountDownLatch saveInsideLock=new CountDownLatch(1);
        CountDownLatch releaseSaveLock=new CountDownLatch(1);
        CountDownLatch markerBeforeLock=new CountDownLatch(1);
        CountDownLatch markerInsideLock=new CountDownLatch(1);
        CountDownLatch releaseMarkerLock=new CountDownLatch(1);
        CountDownLatch secondWriterBeforeLock=new CountDownLatch(1);

        FilePlayerRepository file=new FilePlayerRepository(
            paths,
            account->{
                if(secondAccount.equals(account))
                    secondWriterBeforeLock.countDown();
            },
            account->{
                if(firstAccount.equals(account)){
                    saveInsideLock.countDown();
                    await(releaseSaveLock,"G21.38 save release");
                }
            }
        );
        MailboxDurableReviewFence inspector=
            new MailboxDurableReviewFence(paths);

        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            try(World world=World.isolatedForTest(60000L,file)){
                world.start();
                Seed one=seed(world,firstAccount,"g2138:first");
                Seed two=seed(world,secondAccount,"g2138:second");
                file.save(one.proposal.preparedPreimage);
                file.save(two.proposal.preparedPreimage);
                byte[] secondBefore=Files.readAllBytes(
                    paths.resolve(secondAccount)
                );
                consistentLockFilePaths=
                    MailboxAccountPublicationCoordinator.lockPath(
                        paths.resolve(firstAccount)
                    ).equals(MailboxAccountPublicationCoordinator
                        .lockPath(paths.resolve(firstAccount)))&&
                    !MailboxAccountPublicationCoordinator.lockPath(
                        paths.resolve(firstAccount)
                    ).equals(MailboxAccountPublicationCoordinator
                        .lockPath(paths.resolve(secondAccount)));

                // Interleaving A: an admitted World save passes its final
                // marker check and stays INSIDE exclusive publication.
                WorldPlayerPersistence.SaveTicket firstTicket=
                    capture(world,one,"FIRST_WORLD_SAVE");
                worldSaveOwnsPublicationBeforeMarker=
                    saveInsideLock.await(8,TimeUnit.SECONDS)&&
                    !firstTicket.completion.isDone()&&
                    !inspector.present(firstAccount);

                MailboxDurableReviewFence markerAfterSave=
                    new MailboxDurableReviewFence(paths,phase->{
                        if(phase==MailboxDurableReviewFence.Phase
                                .BEFORE_ATOMIC_REPLACE)
                            markerBeforeLock.countDown();
                    });
                Future<MailboxDurableReviewFence.Receipt> delayedMarker=
                    pool.submit(()->markerAfterSave.arm(one.proposal));
                markerWaitsForEarlierSave=
                    markerBeforeLock.await(8,TimeUnit.SECONDS)&&
                    !delayedMarker.isDone()&&
                    !inspector.present(firstAccount);

                releaseSaveLock.countDown();
                firstTicket.completion.get(8,TimeUnit.SECONDS);
                MailboxDurableReviewFence.Receipt firstRecord=
                    delayedMarker.get(8,TimeUnit.SECONDS);
                earlierSaveCompletesThenMarkerPublishes=
                    firstRecord.record.matches(one.proposal)&&
                    inspector.present(firstAccount)&&
                    file.load(firstAccount).get().values().equals(
                        one.proposal.preparedPreimage.values()
                    )&&
                    !firstRecord.grantAuthorized;

                // Interleaving B: the independent negative marker owns
                // the SAME account lock before its hard-link publication.
                // An admitted World save serializes its temp meanwhile.
                MailboxDurableReviewFence markerFirst=
                    new MailboxDurableReviewFence(paths,phase->{
                        if(phase==MailboxDurableReviewFence.Phase
                                .INSIDE_EXCLUSIVE_PUBLICATION_BEFORE_LINK){
                            markerInsideLock.countDown();
                            await(releaseMarkerLock,
                                "G21.38 marker release");
                        }
                    });
                Future<MailboxDurableReviewFence.Receipt> earlyMarker=
                    pool.submit(()->markerFirst.arm(two.proposal));
                markerOwnsPublicationBeforeSave=
                    markerInsideLock.await(8,TimeUnit.SECONDS)&&
                    !inspector.present(secondAccount)&&
                    !earlyMarker.isDone();

                WorldPlayerPersistence.SaveTicket secondTicket=
                    capture(world,two,"SECOND_WORLD_SAVE");
                worldWriterReachedPreLockWhenMarkerHeld=
                    secondWriterBeforeLock.await(8,TimeUnit.SECONDS)&&
                    !secondTicket.completion.isDone()&&
                    !inspector.present(secondAccount);

                releaseMarkerLock.countDown();
                MailboxDurableReviewFence.Receipt secondRecord=
                    earlyMarker.get(8,TimeUnit.SECONDS);
                markerPublishesFirstAndWorldSaveRefused=
                    secondRecord.record.matches(two.proposal)&&
                    inspector.present(secondAccount)&&
                    failsWith(secondTicket,
                        "G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO");
                markerFirstKeepsOldFileBytes=
                    Arrays.equals(secondBefore,
                        Files.readAllBytes(paths.resolve(secondAccount)))&&
                    file.load(secondAccount).get().values().equals(
                        two.proposal.preparedPreimage.values());

                try(Stream<Path> files=Files.list(dir)){
                    noResidualTempFile=files.noneMatch(path->
                        path.getFileName().toString().endsWith(
                            ".properties.tmp"
                        )||
                        path.getFileName().toString().endsWith(".tmp")
                    );
                }

                WorldPlayer separate=new WorldPlayer();
                long generation=world.registerPlayer(
                    separate,otherAccount
                );
                AtomicReference<WorldPlayerPersistence.SaveTicket>
                    unrelated=new AtomicReference<>();
                world.submitAndWait(separate,generation,()->{
                    unrelated.set(world.persistence().captureAndSave(
                        otherAccount,separate,generation,0,
                        "[g2138] ","UNRELATED_WORLD_SAVE"
                    ));
                },5000L);
                unrelated.get().completion.get(8,TimeUnit.SECONDS);
                unrelatedAccountStillWrites=
                    file.load(otherAccount).isPresent()&&
                    !inspector.present(otherAccount);

                noLiveGrantOrClaim=true;
                for(Seed seed:new Seed[]{one,two}){
                    noLiveGrantOrClaim &=
                        seed.player.bank().inventorySlots()==0&&
                        seed.player.mailbox().get(
                            seed.messageId
                        ).claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }
                noAutomaticMarkerRelease=
                    !firstRecord.grantAuthorized&&
                    !firstRecord.replayAuthorized&&
                    !secondRecord.grantAuthorized&&
                    !secondRecord.replayAuthorized&&
                    !secondRecord.record.releaseAuthorized;
            }

            try(World reboot=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                reboot.start();
                restartFencesBothAccounts=
                    refused(reboot,firstAccount)&&
                    refused(reboot,secondAccount)&&
                    reboot.persistence().load(otherAccount).isPresent();
            }
        }finally{
            releaseSaveLock.countDown();
            releaseMarkerLock.countDown();
            pool.shutdownNow();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path filePath:files.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(filePath);
            }
        }

        System.out.println(
            "G2138_MAILBOX_ACCOUNT_PUBLICATION_DIAGNOSTICS"+
            " saveOwnsLock="+worldSaveOwnsPublicationBeforeMarker+
            " subsequentMarkerWaits="+markerWaitsForEarlierSave+
            " saveThenMarker="+earlierSaveCompletesThenMarkerPublishes+
            " markerOwnsLock="+markerOwnsPublicationBeforeSave+
            " laterSaveReachedBoundary="+
                worldWriterReachedPreLockWhenMarkerHeld+
            " markerThenSaveRejected="+
                markerPublishesFirstAndWorldSaveRefused+
            " oldFileBytesUnchanged="+markerFirstKeepsOldFileBytes+
            " noTempFiles="+noResidualTempFile+
            " otherAccountWrites="+unrelatedAccountStillWrites+
            " restartLoadVeto="+restartFencesBothAccounts+
            " stableAccountLockPaths="+consistentLockFilePaths+
            " noLiveClaimOrGrant="+noLiveGrantOrClaim+
            " noAutomaticRelease="+noAutomaticMarkerRelease
        );
        require(
            worldSaveOwnsPublicationBeforeMarker&&
            markerWaitsForEarlierSave&&
            earlierSaveCompletesThenMarkerPublishes&&
            markerOwnsPublicationBeforeSave&&
            worldWriterReachedPreLockWhenMarkerHeld&&
            markerPublishesFirstAndWorldSaveRefused&&
            markerFirstKeepsOldFileBytes&&noResidualTempFile&&
            unrelatedAccountStillWrites&&restartFencesBothAccounts&&
            consistentLockFilePaths&&noLiveGrantOrClaim&&
            noAutomaticMarkerRelease,
            "G21.38 coordinated account/fence publication"
        );
        System.out.println(
            "G2138_MAILBOX_ACCOUNT_PUBLICATION_COORDINATED_PASS"+
            " cooperatingSaveAndFenceSerialized=true"+
            " saveFirstThenFenceAllowed=true"+
            " fenceFirstThenSaveDenied=true"+
            " noAccountOverwriteAfterMarker=true"+
            " unrelatedAccountSaveWorks=true"+
            " grant=false replay=false release=false"
        );
    }

    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final String account;
        final String messageId;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(WorldPlayer player,long generation,String account,
             String messageId,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.player=player;
            this.generation=generation;
            this.account=account;
            this.messageId=messageId;
            this.proposal=proposal;
        }
    }

    private static Seed seed(
        World world,String account,String messageId
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            proposal=new AtomicReference<>();
        world.submitAndWait(player,generation,()->{
            player.mailbox().deliver(new RewardDeliveryMessage(
                messageId,"Coordinated negative fence","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2138_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot selected=
                player.mailbox().get(messageId);
            MailboxPreparedClaimJournal.stageOnly(
                player,MailboxPreparedClaimJournal.prepare(
                    player,selected
                )
            );
            proposal.set(MailboxSettlementPostimagePlanner.plan(
                player,generation,selected
            ));
        },5000L);
        return new Seed(player,generation,account,messageId,
            proposal.get());
    }

    private static WorldPlayerPersistence.SaveTicket capture(
        World world,Seed seed,String reason
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.SaveTicket> out=
            new AtomicReference<>();
        world.submitAndWait(seed.player,seed.generation,()->{
            out.set(world.persistence().captureAndSave(
                seed.account,seed.player,seed.generation,0,
                "[g2138] ",reason
            ));
        },5000L);
        return out.get();
    }

    private static boolean failsWith(
        WorldPlayerPersistence.SaveTicket ticket,String marker
    )throws Exception{
        try{
            ticket.completion.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException e){
            Throwable failure=e.getCause();
            return failure instanceof IOException&&
                failure.getMessage()!=null&&
                failure.getMessage().contains(marker);
        }
    }

    private static boolean refused(
        World world,String account
    )throws Exception{
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                );
        }
    }

    private static void await(
        CountDownLatch release,String reason
    )throws IOException{
        try{
            if(!release.await(8,TimeUnit.SECONDS))
                throw new IOException(reason+" timed out");
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException(reason+" interrupted",interrupted);
        }
    }

    private static void require(boolean ok,String label){
        if(!ok)throw new AssertionError(label);
    }

    private G2138MailboxAccountPublicationCoordinatorIntegrationTest(){}
}
