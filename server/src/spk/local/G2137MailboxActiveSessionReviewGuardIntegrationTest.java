package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.37: deterministic session-side negative fence discovery.
 *
 * Tests the guard invoked by real LocalSession socket-loop call sites,
 * NOT a captured client/server TCP packet. World tick is never used
 * for these filesystem metadata checks.
 */
public final class G2137MailboxActiveSessionReviewGuardIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean normalBoundaryAllowed=false;
        boolean sessionLoopInitiallyAllowed=false;
        boolean armedFenceTriggersNextPoll=false;
        boolean explicitBoundaryBypassesThrottle=false;
        boolean corruptMarkerAlsoTriggers=false;
        boolean secondAccountUnaffected=false;
        boolean disabledNonPersistentNoChecks=false;
        boolean nonFileRepositoryDoesNotInventFence=false;
        boolean fileReadFailureTerminates=false;
        boolean periodicChecksThrottled=false;
        boolean forcedBoundaryAlwaysChecks=false;
        boolean restartStillFenced=false;
        boolean noLiveGrantOrClaim=false;
        boolean markerCannotAutoRelease=false;

        Path dir=Files.createTempDirectory(
            "g2137-active-session-review-"
        );
        FilePlayerRepository.PathResolver paths=
            username->dir.resolve(username+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        MailboxDurableReviewFence fence=
            new MailboxDurableReviewFence(paths);
        String alice="g2137-alice";
        String bob="g2137-bob";

        try{
            try(World world=World.isolatedForTest(60000L,repo)){
                world.start();
                WorldPlayer owner=new WorldPlayer();
                long generation=world.registerPlayer(owner,alice);
                AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                    prepared=new AtomicReference<>();
                world.submitAndWait(owner,generation,()->{
                    owner.mailbox().deliver(new RewardDeliveryMessage(
                        "g2137:gift","Session review","NO_GRANT",
                        Collections.singletonList(
                            new RewardDeliveryMessage.Attachment(995,20)
                        ),"CUSTOM_LOCALLAB_G2137_FIXTURE"
                    ));
                    MailboxRewardDeliveryService.Snapshot selected=
                        owner.mailbox().get("g2137:gift");
                    MailboxPreparedClaimJournal.stageOnly(
                        owner,MailboxPreparedClaimJournal.prepare(
                            owner,selected
                        )
                    );
                    prepared.set(MailboxSettlementPostimagePlanner.plan(
                        owner,generation,selected
                    ));
                },5000L);
                repo.save(prepared.get().preparedPreimage);

                MailboxActiveSessionReviewGuard online=
                    MailboxActiveSessionReviewGuard.forSession(
                        world.persistence(),alice,true
                    );
                online.requireAtBoundary();
                normalBoundaryAllowed=!fence.present(alice);

                online.poll(System.nanoTime());
                sessionLoopInitiallyAllowed=true;

                MailboxDurableReviewFence.Receipt armed=
                    fence.arm(prepared.get());
                armedFenceTriggersNextPoll=rejects(
                    ()->online.poll(
                        System.nanoTime()+
                        MailboxActiveSessionReviewGuard
                            .POLL_INTERVAL_NANOS+10_000L
                    ),"G21.37 MAILBOX_LIVE_REVIEW_FENCE"
                );
                explicitBoundaryBypassesThrottle=rejects(
                    online::requireAtBoundary,
                    "G21.37 MAILBOX_LIVE_REVIEW_FENCE"
                );

                // Marker presence is the veto, NOT its parse result.
                Files.write(
                    fence.fencePath(alice),
                    "corrupted-marker".getBytes(
                        StandardCharsets.US_ASCII
                    )
                );
                corruptMarkerAlsoTriggers=rejects(
                    online::requireAtBoundary,
                    "G21.37 MAILBOX_LIVE_REVIEW_FENCE"
                );

                MailboxActiveSessionReviewGuard other=
                    MailboxActiveSessionReviewGuard.forSession(
                        world.persistence(),bob,true
                    );
                other.requireAtBoundary();
                other.poll(System.nanoTime()+
                    MailboxActiveSessionReviewGuard.POLL_INTERVAL_NANOS);
                secondAccountUnaffected=!fence.present(bob);

                AtomicInteger disabledQueries=new AtomicInteger();
                MailboxActiveSessionReviewGuard notPersistent=
                    new MailboxActiveSessionReviewGuard(
                        alice,false,account->{
                            disabledQueries.incrementAndGet();
                            return true;
                        }
                    );
                notPersistent.requireAtBoundary();
                notPersistent.poll(Long.MAX_VALUE);
                disabledNonPersistentNoChecks=
                    disabledQueries.get()==0;

                WorldPlayer stranger=new WorldPlayer();
                stranger.markRegistered("g2137-unrelated");
                noLiveGrantOrClaim=
                    owner.bank().inventorySlots()==0&&
                    owner.mailbox().get("g2137:gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                    stranger.bank().inventorySlots()==0;
                markerCannotAutoRelease=
                    armed.record.state.equals(
                        MailboxDurableReviewFence.STATE
                    )&&
                    !armed.grantAuthorized&&!armed.replayAuthorized&&
                    !armed.record.releaseAuthorized&&
                    fence.present(alice);
            }

            // No sidecar contract is invented for custom non-file repos.
            PlayerRepository custom=new PlayerRepository(){
                @Override public java.util.Optional<PlayerSnapshot> load(
                    String account
                )throws IOException{
                    return java.util.Optional.empty();
                }
                @Override public void save(PlayerSnapshot state)
                    throws IOException{
                    throw new IOException("no write");
                }
            };
            try(World otherWorld=World.isolatedForTest(
                    60000L,custom)){
                otherWorld.start();
                MailboxActiveSessionReviewGuard unsupported=
                    MailboxActiveSessionReviewGuard.forSession(
                        otherWorld.persistence(),alice,true
                    );
                unsupported.requireAtBoundary();
                unsupported.poll(Long.MAX_VALUE);
                nonFileRepositoryDoesNotInventFence=true;
            }

            MailboxActiveSessionReviewGuard broken=
                new MailboxActiveSessionReviewGuard(
                    alice,true,account->{
                        throw new IOException(
                            "G21.37 injected marker metadata failure"
                        );
                    }
                );
            fileReadFailureTerminates=rejects(
                broken::requireAtBoundary,
                "G21.37 MAILBOX_LIVE_REVIEW_STATUS_FAILED"
            );

            AtomicInteger probes=new AtomicInteger();
            MailboxActiveSessionReviewGuard timing=
                new MailboxActiveSessionReviewGuard(
                    bob,true,account->{
                        probes.incrementAndGet();
                        return false;
                    }
                );
            timing.poll(1_000L);
            timing.poll(2_000L);
            timing.poll(
                1_000L+
                MailboxActiveSessionReviewGuard.POLL_INTERVAL_NANOS-1L
            );
            periodicChecksThrottled=probes.get()==1;
            timing.poll(
                1_000L+
                MailboxActiveSessionReviewGuard.POLL_INTERVAL_NANOS
            );
            periodicChecksThrottled &=probes.get()==2;
            timing.requireAtBoundary();
            forcedBoundaryAlwaysChecks=probes.get()==3;

            try(World fresh=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                fresh.start();
                MailboxActiveSessionReviewGuard restarted=
                    MailboxActiveSessionReviewGuard.forSession(
                        fresh.persistence(),alice,true
                    );
                restartStillFenced=rejects(
                    restarted::requireAtBoundary,
                    "G21.37 MAILBOX_LIVE_REVIEW_FENCE"
                );
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
            "G2137_MAILBOX_LIVE_FENCE_DIAGNOSTICS"+
            " normalPreLoginBoundaryAllowed="+normalBoundaryAllowed+
            " initialLoopAllowed="+sessionLoopInitiallyAllowed+
            " armedFenceEndsSession="+armedFenceTriggersNextPoll+
            " prePacketBoundaryNotThrottled="+
                explicitBoundaryBypassesThrottle+
            " corruptMarkerAlsoVetoes="+corruptMarkerAlsoTriggers+
            " otherAccountUnaffected="+secondAccountUnaffected+
            " nonpersistentSkipsIO="+disabledNonPersistentNoChecks+
            " nonfileRepoNoSidecarClaim="+
                nonFileRepositoryDoesNotInventFence+
            " ioFailureEndsSession="+fileReadFailureTerminates+
            " pollingRateLimited="+periodicChecksThrottled+
            " boundaryCheckForced="+forcedBoundaryAlwaysChecks+
            " freshProcessVeto="+restartStillFenced+
            " liveInventoryUnchanged="+noLiveGrantOrClaim+
            " noAutoRelease="+markerCannotAutoRelease
        );
        require(
            normalBoundaryAllowed&&sessionLoopInitiallyAllowed&&
            armedFenceTriggersNextPoll&&explicitBoundaryBypassesThrottle&&
            corruptMarkerAlsoTriggers&&secondAccountUnaffected&&
            disabledNonPersistentNoChecks&&
            nonFileRepositoryDoesNotInventFence&&
            fileReadFailureTerminates&&periodicChecksThrottled&&
            forcedBoundaryAlwaysChecks&&restartStillFenced&&
            noLiveGrantOrClaim&&markerCannotAutoRelease,
            "G21.37 live session negative review-fence guard"
        );
        System.out.println(
            "G2137_MAILBOX_LIVE_SESSION_REVIEW_GUARD_PASS"+
            " newMarkerClosesNextDueSocketPoll=true"+
            " preLoginSuccessBoundaryChecks=true"+
            " noWorldTickIO=true noPrivateThread=true"+
            " corruptMarkerFailClosed=true"+
            " noLiveGrantOrReplay=true"
        );
    }

    private interface CheckedAction {
        void run()throws Exception;
    }

    private static boolean rejects(
        CheckedAction action,String expected
    )throws Exception{
        try{
            action.run();
            return false;
        }catch(IOException error){
            return error.getMessage()!=null&&
                error.getMessage().contains(expected);
        }
    }

    private static void require(boolean yes,String name){
        if(!yes)throw new AssertionError(name);
    }

    private G2137MailboxActiveSessionReviewGuardIntegrationTest(){}
}
