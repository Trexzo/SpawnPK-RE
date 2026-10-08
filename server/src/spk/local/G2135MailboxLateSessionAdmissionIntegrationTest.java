package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.35 actual LocalSessionPlayerInitializer late admission: an
 * independent marker appearing AFTER the first account load must be
 * rejected BEFORE World registration. No network login response path,
 * reward grant, replay, or review marker deletion is introduced.
 */
public final class G2135MailboxLateSessionAdmissionIntegrationTest {
    private static final String TAG="[g2135] ";

    private static final class Attempt {
        final boolean registered;
        final Throwable rejection;
        final WorldPlayer player;

        Attempt(boolean registered,Throwable rejection,WorldPlayer player){
            this.registered=registered;
            this.rejection=rejection;
            this.player=player;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean baselinePreparedLoadable=false;
        boolean markerAppearedAfterFirstLoad=false;
        boolean finalRegistrationVetoed=false;
        boolean noSessionMembershipPublished=false;
        boolean markerSurvivesVeto=false;
        boolean accountFileUnchanged=false;
        boolean originalPlayerNotCredited=false;
        boolean normalAccountStillRegisters=false;
        boolean unrelatedFencedAccountIsolated=false;
        boolean changedCanonicalSnapshotRejected=false;
        boolean deletedSnapshotRejected=false;
        boolean createdDuringMissingLoadRejected=false;
        boolean stillMissingAccountRegisters=false;
        boolean noAutomaticGrantOrReplay=false;
        boolean normalFinalCheckPreservesIdentity=false;

        Path dir=Files.createTempDirectory("g2135-late-admission-");
        FilePlayerRepository.PathResolver resolver=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(resolver);
        MailboxDurableReviewFence fence=
            new MailboxDurableReviewFence(resolver);
        String alice="g2135-alice";
        String bob="g2135-bob";
        String changed="g2135-changed";
        String deleted="g2135-deleted";
        String appeared="g2135-appeared";
        String stillMissing="g2135-missing";
        MailboxSettlementPostimagePlanner.Proposal proposal;

        try{
            // Detached seed: no member of a future login World is reserved.
            try(World seed=World.isolatedForTest(60000L,repository)){
                WorldPlayer owner=new WorldPlayer();
                long generation=seed.registerPlayer(owner,alice);
                seed.start();
                AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                    prepared=new AtomicReference<>();
                seed.submitAndWait(owner,generation,()->{
                    owner.mailbox().deliver(new RewardDeliveryMessage(
                        "g2135:gift","Recheck gift","No claim",
                        Collections.singletonList(
                            new RewardDeliveryMessage.Attachment(995,25)
                        ),"CUSTOM_LOCALLAB_G2135_FIXTURE"
                    ));
                    MailboxRewardDeliveryService.Snapshot row=
                        owner.mailbox().get("g2135:gift");
                    MailboxPreparedClaimJournal.stageOnly(
                        owner,MailboxPreparedClaimJournal.prepare(
                            owner,row
                        )
                    );
                    prepared.set(MailboxSettlementPostimagePlanner.plan(
                        owner,generation,row
                    ));
                },5000L);
                proposal=prepared.get();
                repository.save(proposal.preparedPreimage);
                baselinePreparedLoadable=
                    MailboxPreparedRestartAdmission.inspect(
                        proposal.preparedPreimage
                    ).admissionAllowed&&
                    !fence.present(alice);
            }

            // Two threads deterministically pause real initialization
            // AFTER persistence.load(), before the new final check.
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                CountDownLatch initialLoadDone=new CountDownLatch(1);
                CountDownLatch allowFinalCheck=new CountDownLatch(1);
                Runnable pause=()->{
                    initialLoadDone.countDown();
                    try{
                        if(!allowFinalCheck.await(8,TimeUnit.SECONDS))
                            throw new IllegalStateException(
                                "G21.35 marker injection timed out"
                            );
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                };
                WorldPlayer candidate=new WorldPlayer();
                LocalSessionPlayerInitializer initializer=initializer(
                    world,candidate,pause
                );
                ExecutorService pool=Executors.newSingleThreadExecutor();
                try{
                    CompletableFuture<Attempt> pending=
                        CompletableFuture.supplyAsync(()->{
                            try{
                                initializer.initialize(alice,TAG);
                                return new Attempt(true,null,candidate);
                            }catch(Throwable failed){
                                return new Attempt(false,failed,candidate);
                            }
                        },pool);
                    if(!initialLoadDone.await(8,TimeUnit.SECONDS))
                        throw new AssertionError(
                            "G21.35 actual initializer never reached boundary"
                        );
                    markerAppearedAfterFirstLoad=
                        fence.arm(proposal).record.matches(proposal);
                    allowFinalCheck.countDown();
                    Attempt rejected=pending.get(10,TimeUnit.SECONDS);
                    finalRegistrationVetoed=
                        !rejected.registered&&
                        rejectionContains(rejected,
                            "G21.35 MAILBOX_LATE_SESSION_ADMISSION_REJECTED")&&
                        rejectionContains(rejected,
                            "G21.32 MAILBOX_DURABLE_REVIEW_FENCE");
                    noSessionMembershipPublished=
                        world.players().byName(alice)==null&&
                        !candidate.registered();
                    markerSurvivesVeto=
                        fence.present(alice)&&
                        fence.inspect(alice).matches(proposal);
                    accountFileUnchanged=
                        repository.load(alice).get().values().equals(
                            proposal.preparedPreimage.values()
                        );
                    originalPlayerNotCredited=
                        candidate.bank().inventorySlots()==0&&
                        candidate.mailbox().get("g2135:gift").claimState==
                            MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                }finally{
                    allowFinalCheck.countDown();
                    pool.shutdownNow();
                }
            }

            saveClean(repository,bob);
            saveClean(repository,changed);
            saveClean(repository,deleted);

            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                Attempt normal=attempt(world,bob,()->{});
                normalAccountStillRegisters=
                    normal.registered&&normal.rejection==null&&
                    world.players().byName(bob)==normal.player;
                unrelatedFencedAccountIsolated=
                    fence.present(alice)&&
                    !fence.present(bob)&&
                    normal.registered&&
                    world.persistence().load(bob).isPresent();
                normalFinalCheckPreservesIdentity=
                    normal.registered&&
                    "g2135-bob".equals(normal.player.username());
            }

            // Ordinary accounts also refuse a silently changed, deleted
            // or newly appeared file after first hydration.
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                Attempt modified=attempt(world,changed,()->{
                    try{
                        PlayerSnapshot original=
                            repository.load(changed).get();
                        TreeMap<String,String> values=
                            new TreeMap<>(original.values());
                        values.put(
                            "extension.g2135.changed","during-login"
                        );
                        repository.save(new PlayerSnapshot(
                            PlayerSnapshot.CURRENT_VERSION,
                            changed,values
                        ));
                    }catch(IOException failure){
                        throw new IllegalStateException(failure);
                    }
                });
                changedCanonicalSnapshotRejected=
                    !modified.registered&&
                    rejectionContains(modified,
                        "ACCOUNT_CHANGED_DURING_LOGIN")&&
                    world.players().byName(changed)==null;
            }
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                Attempt removed=attempt(world,deleted,()->{
                    try{
                        Files.delete(resolver.resolve(deleted));
                    }catch(IOException failure){
                        throw new IllegalStateException(failure);
                    }
                });
                deletedSnapshotRejected=
                    !removed.registered&&
                    rejectionContains(removed,
                        "ACCOUNT_REMOVED_DURING_LOGIN")&&
                    world.players().byName(deleted)==null;
            }
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                Attempt created=attempt(world,appeared,()->{
                    try{
                        saveClean(repository,appeared);
                    }catch(IOException failure){
                        throw new IllegalStateException(failure);
                    }
                });
                createdDuringMissingLoadRejected=
                    !created.registered&&
                    rejectionContains(created,
                        "ACCOUNT_CREATED_DURING_LOGIN")&&
                    world.players().byName(appeared)==null;
            }

            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                Attempt missing=attempt(world,stillMissing,()->{});
                stillMissingAccountRegisters=
                    missing.registered&&missing.rejection==null&&
                    world.players().byName(stillMissing)==missing.player&&
                    !Files.exists(resolver.resolve(stillMissing));
            }

            noAutomaticGrantOrReplay=
                fence.present(alice)&&
                !fence.inspect(alice).grantAuthorized&&
                !fence.inspect(alice).replayAuthorized&&
                !fence.inspect(alice).releaseAuthorized;
        }finally{
            try(Stream<Path> files=Files.walk(dir)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }

        System.out.println(
            "G2135_MAILBOX_LATE_SESSION_DIAGNOSTICS"+
            " validInitialPrepared="+baselinePreparedLoadable+
            " markerArmedDuringLogin="+markerAppearedAfterFirstLoad+
            " finalAdmissionDenied="+finalRegistrationVetoed+
            " noWorldMembership="+noSessionMembershipPublished+
            " fenceRetained="+markerSurvivesVeto+
            " snapshotNotRewritten="+accountFileUnchanged+
            " livePlayerNeverCredited="+originalPlayerNotCredited+
            " normalLoginRegisters="+normalAccountStillRegisters+
            " unrelatedMarkerIsolated="+unrelatedFencedAccountIsolated+
            " changedSnapshotDenied="+changedCanonicalSnapshotRejected+
            " removedSnapshotDenied="+deletedSnapshotRejected+
            " insertedSnapshotDenied="+createdDuringMissingLoadRejected+
            " stableMissingAccountRegisters="+stillMissingAccountRegisters+
            " noGrantReplayOrRelease="+noAutomaticGrantOrReplay+
            " snapshotIdentityPreserved="+normalFinalCheckPreservesIdentity
        );
        require(
            baselinePreparedLoadable&&markerAppearedAfterFirstLoad&&
            finalRegistrationVetoed&&noSessionMembershipPublished&&
            markerSurvivesVeto&&accountFileUnchanged&&
            originalPlayerNotCredited&&normalAccountStillRegisters&&
            unrelatedFencedAccountIsolated&&
            changedCanonicalSnapshotRejected&&deletedSnapshotRejected&&
            createdDuringMissingLoadRejected&&
            stillMissingAccountRegisters&&noAutomaticGrantOrReplay&&
            normalFinalCheckPreservesIdentity,
            "G21.35 last-chance login admission"
        );
        System.out.println(
            "G2135_MAILBOX_LATE_SESSION_ADMISSION_PASS"+
            " fenceArmedAfterLoadBeforeRegisterDenied=true"+
            " secondSameFifoLoad=true noWorldRegistrationOnVeto=true"+
            " changedOrMissingSnapshotDenied=true"+
            " normalLoginUnaffected=true noGrantOrReplay=true"
        );
    }

    private static Attempt attempt(
        World world,String username,Runnable beforeRegistration
    ){
        WorldPlayer candidate=new WorldPlayer();
        try{
            initializer(world,candidate,beforeRegistration)
                .initialize(username,TAG);
            return new Attempt(true,null,candidate);
        }catch(Throwable e){
            return new Attempt(false,e,candidate);
        }
    }

    private static LocalSessionPlayerInitializer initializer(
        World world,WorldPlayer player,Runnable before
    ){
        return new LocalSessionPlayerInitializer(
            world,player,player.bank(),player.equipment(),
            player.movement(),player.petState(),player.playerState(),
            player.petEffects(),player.petAccessoryState(),before
        );
    }

    private static void saveClean(
        FilePlayerRepository repository,String account
    )throws IOException{
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(account);
        repository.save(PlayerSnapshotCodec.capture(account,player));
    }

    private static boolean rejectionContains(
        Attempt attempt,String expected
    ){
        for(Throwable cause=attempt.rejection;cause!=null;
                cause=cause.getCause()){
            if(cause.getMessage()!=null&&
                cause.getMessage().contains(expected))
                return true;
        }
        return false;
    }

    private static void require(boolean yes,String label){
        if(!yes)throw new AssertionError(label);
    }

    private G2135MailboxLateSessionAdmissionIntegrationTest(){}
}
