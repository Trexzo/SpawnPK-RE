package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.33 read-only fenced account restart forensics. Existing account
 * loader's veto remains authoritative; a forensic result never hydrates,
 * acknowledges, grants, rolls back, replays or clears the marker.
 */
public final class G2133MailboxFencedRestartForensicsIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean noFenceNotAuthorization=false;
        boolean exactPreparedObserved=false;
        boolean exactPreparedStillLoginDenied=false;
        boolean exactHypotheticalObserved=false;
        boolean hypotheticalStillLoginDenied=false;
        boolean divergentAccountQuarantined=false;
        boolean strippedJournalNotRecovered=false;
        boolean missingAccountObserved=false;
        boolean malformedFenceQuarantined=false;
        boolean interruptedMarkerObserved=false;
        boolean failedReadQuarantined=false;
        boolean afterRestartStillDenied=false;
        boolean unaffectedAccountStillLoads=false;
        boolean allObservationsNoAuthority=false;
        boolean noForensicWritesOrLiveGrant=false;

        Path dir=Files.createTempDirectory("g2133-forensics-");
        FilePlayerRepository.PathResolver resolver=
            username->dir.resolve(username+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(resolver);
        MailboxDurableReviewFence fence=
            new MailboxDurableReviewFence(resolver);
        String account="g2133-alice",other="g2133-bob";
        WorldPlayer owner=new WorldPlayer();
        MailboxSettlementPostimagePlanner.Proposal proposal;
        try{
            try(World world=World.isolatedForTest(60000L,repository)){
                long generation=world.registerPlayer(owner,account);
                world.start();
                AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                    proposed=new AtomicReference<>();
                world.submitAndWait(owner,generation,()->{
                    owner.mailbox().deliver(new RewardDeliveryMessage(
                        "g2133:gift","Forensic gift","No reward granted",
                        Collections.singletonList(
                            new RewardDeliveryMessage.Attachment(995,25)
                        ),"CUSTOM_LOCALLAB_G2133_FIXTURE"
                    ));
                    MailboxRewardDeliveryService.Snapshot selected=
                        owner.mailbox().get("g2133:gift");
                    MailboxPreparedClaimJournal.stageOnly(
                        owner,MailboxPreparedClaimJournal.prepare(
                            owner,selected
                        )
                    );
                    proposed.set(MailboxSettlementPostimagePlanner.plan(
                        owner,generation,selected
                    ));
                },5000L);
                proposal=proposed.get();
                repository.save(proposal.preparedPreimage);

                WorldPlayer bob=new WorldPlayer();
                bob.markRegistered(other);
                PlayerSnapshot bobSnapshot=
                    PlayerSnapshotCodec.capture(other,bob);
                repository.save(bobSnapshot);
                MailboxFencedRestartForensics.Report noFence=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,other
                    );
                noFenceNotAuthorization=
                    noFence.state==
                        MailboxFencedRestartForensics.State
                            .NO_FENCE_NO_AUTHORITY&&
                    vetoed(noFence);
                unaffectedAccountStillLoads=
                    world.persistence().load(other).get()
                        .values().equals(bobSnapshot.values());

                MailboxDurableReviewFence.Receipt marker=
                    fence.arm(proposal);
                MailboxFencedRestartForensics.Report prepared=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,account
                    );
                exactPreparedObserved=
                    prepared.state==
                        MailboxFencedRestartForensics.State
                            .EXACT_PREPARED_UNCLAIMED&&
                    proposal.account.equals(prepared.account)&&
                    marker.record.preparedSha256.equals(
                        prepared.observedSnapshotSha256
                    )&&vetoed(prepared);
                exactPreparedStillLoginDenied=denied(world,account);

                repository.save(proposal.hypotheticalPostimage);
                MailboxFencedRestartForensics.Report hypothetical=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,account
                    );
                exactHypotheticalObserved=
                    hypothetical.state==
                        MailboxFencedRestartForensics.State
                            .EXACT_HYPOTHETICAL_CLAIMED&&
                    hypothetical.observedSnapshotSha256.equals(
                        marker.record.hypotheticalSha256
                    )&&vetoed(hypothetical);
                hypotheticalStillLoginDenied=denied(world,account);

                TreeMap<String,String> extra=new TreeMap<>(
                    proposal.hypotheticalPostimage.values()
                );
                extra.put("extension.g2133.unrelated","tampered");
                repository.save(new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,account,extra
                ));
                MailboxFencedRestartForensics.Report divergent=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,account
                    );
                divergentAccountQuarantined=
                    divergent.state==
                        MailboxFencedRestartForensics.State
                            .OTHER_ACCOUNT_POSTIMAGE&&vetoed(divergent);

                TreeMap<String,String> stripped=new TreeMap<>(
                    proposal.hypotheticalPostimage.values()
                );
                stripped.keySet().removeIf(key->key.startsWith(
                    "extension."+MailboxPreparedClaimJournal.NAMESPACE+"."
                ));
                repository.save(new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,account,stripped
                ));
                MailboxFencedRestartForensics.Report strippedResult=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,account
                    );
                strippedJournalNotRecovered=
                    strippedResult.state==
                        MailboxFencedRestartForensics.State
                            .OTHER_ACCOUNT_POSTIMAGE&&
                    denied(world,account)&&vetoed(strippedResult);

                Files.delete(resolver.resolve(account));
                MailboxFencedRestartForensics.Report missing=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,account
                    );
                missingAccountObserved=
                    missing.state==
                        MailboxFencedRestartForensics.State
                            .MISSING_ACCOUNT&&
                    denied(world,account)&&vetoed(missing);

                repository.save(proposal.preparedPreimage);
                Files.write(
                    fence.fencePath(account),
                    "corrupt negative marker".getBytes(
                        StandardCharsets.US_ASCII
                    )
                );
                MailboxFencedRestartForensics.Report corrupt=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,account
                    );
                malformedFenceQuarantined=
                    corrupt.state==
                        MailboxFencedRestartForensics.State
                            .INVALID_OR_UNREADABLE_FENCE&&
                    denied(world,account)&&vetoed(corrupt);

                allObservationsNoAuthority=
                    vetoed(prepared)&&vetoed(hypothetical)&&
                    vetoed(divergent)&&vetoed(strippedResult)&&
                    vetoed(missing)&&vetoed(corrupt)&&vetoed(noFence);

                noForensicWritesOrLiveGrant=
                    owner.bank().inventorySlots()==0&&
                    owner.mailbox().get("g2133:gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                    repository.load(account).get()
                        .values().equals(proposal.preparedPreimage.values());
            }

            // The disk remains in review-required state under a new World
            // and a new persistence worker, with no in-memory G21.27 token.
            try(World restart=World.isolatedForTest(
                    60000L,new FilePlayerRepository(resolver))){
                restart.start();
                MailboxFencedRestartForensics.Report persisted=
                    MailboxFencedRestartForensics.inspect(
                        restart.persistence(),fence,account
                    );
                afterRestartStillDenied=
                    persisted.state==
                        MailboxFencedRestartForensics.State
                            .INVALID_OR_UNREADABLE_FENCE&&
                    denied(restart,account)&&vetoed(persisted);
            }

            // Deterministic same-FIFO I/O blocking proves marker changes
            // DURING a read do not become positive evidence.
            Files.delete(fence.fencePath(account));
            fence.arm(proposal);
            BlockingRepository block=new BlockingRepository(repository);
            try(World testWorld=World.isolatedForTest(60000L,block)){
                testWorld.start();
                ExecutorService otherThread=
                    Executors.newSingleThreadExecutor();
                try{
                    CompletableFuture<MailboxFencedRestartForensics.Report>
                        pending=CompletableFuture.supplyAsync(
                            ()->MailboxFencedRestartForensics.inspect(
                                testWorld.persistence(),fence,account
                            ),otherThread
                        );
                    if(!block.entered.await(5,TimeUnit.SECONDS))
                        throw new AssertionError(
                            "G21.33 FIFO forensic read never entered"
                        );
                    Files.write(
                        fence.fencePath(account),
                        "invalid-during-read".getBytes(
                            StandardCharsets.US_ASCII
                        )
                    );
                    block.release.countDown();
                    MailboxFencedRestartForensics.Report changed=
                        pending.get(8,TimeUnit.SECONDS);
                    interruptedMarkerObserved=
                        changed.state==
                            MailboxFencedRestartForensics.State
                                .FENCE_DISAPPEARED_OR_CHANGED&&
                        vetoed(changed);
                }finally{
                    block.release.countDown();
                    otherThread.shutdownNow();
                }
            }

            // A repository read failure is an explicit forensic veto,
            // never a no-fence or exact snapshot classification.
            Files.delete(fence.fencePath(account));
            fence.arm(proposal);
            PlayerRepository broken=new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                )throws IOException{
                    throw new IOException("G21.33 injected read failure");
                }
                @Override public void save(PlayerSnapshot snapshot)
                    throws IOException{
                    throw new IOException("read-only fixture");
                }
            };
            try(World bad=World.isolatedForTest(60000L,broken)){
                bad.start();
                MailboxFencedRestartForensics.Report failure=
                    MailboxFencedRestartForensics.inspect(
                        bad.persistence(),fence,account
                    );
                failedReadQuarantined=
                    failure.state==
                        MailboxFencedRestartForensics.State
                            .ACCOUNT_READ_FAILED&&vetoed(failure);
            }
        }finally{
            try(Stream<Path> files=Files.walk(dir)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2133_MAILBOX_FENCED_FORENSICS_DIAGNOSTICS"+
            " noFenceNoAuthority="+noFenceNotAuthorization+
            " exactPrepared="+exactPreparedObserved+
            " preparedLoginDenied="+exactPreparedStillLoginDenied+
            " exactClaimedPostimage="+exactHypotheticalObserved+
            " claimedLoginDenied="+hypotheticalStillLoginDenied+
            " divergentQuarantined="+divergentAccountQuarantined+
            " journalStrippedQuarantined="+strippedJournalNotRecovered+
            " missingAccount="+missingAccountObserved+
            " malformedFence="+malformedFenceQuarantined+
            " markerRaceDetected="+interruptedMarkerObserved+
            " failedReadVeto="+failedReadQuarantined+
            " freshWorldVeto="+afterRestartStillDenied+
            " otherAccountUnchanged="+unaffectedAccountStillLoads+
            " allOutcomesNoAuthority="+allObservationsNoAuthority+
            " forensicNoWritesOrLiveGrant="+noForensicWritesOrLiveGrant
        );
        require(noFenceNotAuthorization&&exactPreparedObserved&&
            exactPreparedStillLoginDenied&&exactHypotheticalObserved&&
            hypotheticalStillLoginDenied&&divergentAccountQuarantined&&
            strippedJournalNotRecovered&&missingAccountObserved&&
            malformedFenceQuarantined&&interruptedMarkerObserved&&
            failedReadQuarantined&&afterRestartStillDenied&&
            unaffectedAccountStillLoads&&allObservationsNoAuthority&&
            noForensicWritesOrLiveGrant,"G21.33 forensic acceptance");

        System.out.println(
            "G2133_MAILBOX_FENCED_RESTART_FORENSICS_PASS"+
            " exactPreparedAndClaimedClassifications=true"+
            " markerChangedDuringFifoVeto=true"+
            " noAccountMutation=true noGrant=true noReplay=true"+
            " originalLoginFencePreserved=true"
        );
    }

    private static boolean vetoed(
        MailboxFencedRestartForensics.Report r
    ){
        return !r.grantAuthorized&&!r.replayAuthorized&&
            !r.rollbackAuthorized&&!r.releaseFenceAuthorized&&
            !r.sessionAdmissionAuthorized&&!r.fileDurabilityConfirmed&&
            !r.automaticRecoveryAuthorized;
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

    private static final class BlockingRepository
        implements PlayerRepository{
        final PlayerRepository delegate;
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
        BlockingRepository(PlayerRepository delegate){
            this.delegate=delegate;
        }
        @Override public Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            entered.countDown();
            try{
                if(!release.await(8,TimeUnit.SECONDS))
                    throw new IOException("G21.33 blocked read timed out");
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException("G21.33 read interrupted",interrupted);
            }
            return delegate.load(username);
        }
        @Override public void save(PlayerSnapshot snapshot)
            throws IOException{
            throw new IOException("G21.33 no writes");
        }
    }

    private static void require(boolean yes,String label){
        if(!yes)throw new AssertionError(label);
    }

    private G2133MailboxFencedRestartForensicsIntegrationTest(){}
}
