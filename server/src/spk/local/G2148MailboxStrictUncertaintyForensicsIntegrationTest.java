package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.48: G21.47 strict-postpublication markers must be inspectable
 * as negative uncertainty, NOT misclassified as corrupted G21.32
 * proposal records and never used to grant or recover a Mailbox item.
 */
public final class G2148MailboxStrictUncertaintyForensicsIntegrationTest {
    private static final class Seed {
        final String account;
        final String message;
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(String account,String message,WorldPlayer player,
             long generation,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.account=account;
            this.message=message;
            this.player=player;
            this.generation=generation;
            this.proposal=proposal;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean realUnconfirmedWriteOccurred=false;
        boolean strictExactClassified=false;
        boolean strictDigestBoundToDisk=false;
        boolean strictDivergenceClassified=false;
        boolean strictMissingAccountClassified=false;
        boolean strictBadAccountRejected=false;
        boolean strictCorruptMarkerClassified=false;
        boolean invalidStrictMarkerStillPresent=false;
        boolean legacyProposalClassificationPreserved=false;
        boolean dualMarkersPrioritizeStrictUncertainty=false;
        boolean unfencedAccountRetainsNoAuthority=false;
        boolean freshRestartStrictAccountDenied=false;
        boolean freshRestartLegacyAccountDenied=false;
        boolean freshRestartDualAccountDenied=false;
        boolean freshRestartOrdinaryAccountLoads=false;
        boolean allReportsNonAuthorizing=true;
        boolean noItemCreditOrMailboxClaim=true;
        boolean noAutomaticMarkerRemoval=true;
        boolean noUnpublishedTemps=true;

        Path root=Files.createTempDirectory(
            "g2148-strict-uncertain-forensics-"
        );
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        MailboxDurableReviewFence fence=new MailboxDurableReviewFence(paths);
        CountDownLatch postForced=new CountDownLatch(1);
        CountDownLatch allowFinish=new CountDownLatch(1);

        try{
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                Seed uncertain=seed(
                    world,"g2148-strict","g2148:strict"
                );
                Seed legacy=seed(
                    world,"g2148-legacy","g2148:legacy"
                );
                Seed dual=seed(world,"g2148-dual","g2148:dual");
                Seed normal=seed(
                    world,"g2148-normal","g2148:normal"
                );

                for(Seed seed:new Seed[]{uncertain,legacy,dual,normal})
                    repository.save(seed.proposal.preparedPreimage);

                // Ground the first marker in ACTUAL G21.46/G21.47
                // production behavior, not a synthetic test-only
                // call to the marker publisher.
                StrictDurablePlayerSnapshotWriter strictWriter=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .AFTER_DIRECTORY_FORCE){
                            postForced.countDown();
                            await(allowFinish,
                                "G21.48 resume strict publication");
                        }
                    });
                CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt>
                    pending=world.persistence().submitPreparedStrictBarrier(
                        uncertain.player,uncertain.generation,
                        uncertain.proposal.preparedPreimage,strictWriter
                    );
                if(!postForced.await(8,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.48 strict writer never published account"
                    );
                world.submitAndWait(
                    uncertain.player,uncertain.generation,
                    ()->uncertain.player.movement().setRunEnergy(32),
                    5000L
                );
                allowFinish.countDown();
                realUnconfirmedWriteOccurred=unconfirmed(pending)&&
                    MailboxStrictUnconfirmedReviewFence.present(
                        paths.resolve(uncertain.account)
                    );
                MailboxFencedRestartForensics.Report exact=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,uncertain.account
                    );
                strictExactClassified=exact.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_EXACT_SNAPSHOT;
                strictDigestBoundToDisk=
                    exact.observedSnapshotSha256!=null&&
                    exact.observedSnapshotSha256.equals(
                        StrictDurablePlayerSnapshotWriter
                            .canonicalSnapshotSha256(
                                uncertain.proposal.preparedPreimage
                            )
                    );

                // Manual/offline forensic mutation is deliberately
                // outside cooperating guarded World save authority.
                PlayerSnapshot current=PlayerSnapshotCodec.capture(
                    uncertain.account,uncertain.player
                );
                repository.save(current);
                MailboxFencedRestartForensics.Report divergent=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,uncertain.account
                    );
                strictDivergenceClassified=divergent.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_DIVERGENT_SNAPSHOT;

                Files.delete(paths.resolve(uncertain.account));
                MailboxFencedRestartForensics.Report missing=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,uncertain.account
                    );
                strictMissingAccountClassified=missing.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_MISSING_ACCOUNT;

                // Corrupt direct account file: do not parse corrupted
                // account bytes as an exact durable snapshot.
                Files.writeString(
                    paths.resolve(uncertain.account),
                    "g2148-forensic-corrupt-account",
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW
                );
                MailboxFencedRestartForensics.Report badAccount=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,uncertain.account
                    );
                strictBadAccountRejected=
                    badAccount.state==
                        MailboxFencedRestartForensics.State
                            .STRICT_UNCERTAIN_ACCOUNT_READ_FAILED||
                    badAccount.state==
                        MailboxFencedRestartForensics.State
                            .STRICT_UNCERTAIN_INVALID_ACCOUNT;

                Path strictFence=
                    MailboxStrictUnconfirmedReviewFence.fencePath(
                        paths.resolve(uncertain.account)
                    );
                Files.writeString(
                    strictFence,"corrupt-untrusted-negative-sidecar",
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.TRUNCATE_EXISTING
                );
                MailboxFencedRestartForensics.Report corrupt=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,uncertain.account
                    );
                strictCorruptMarkerClassified=corrupt.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_INVALID_MARKER;
                invalidStrictMarkerStillPresent=
                    fence.present(uncertain.account);

                // Historical G21.32 proposal-format marker is still
                // classified under the previously certified G21.33
                // EXACT_PREPARED_UNCLAIMED path.
                MailboxDurableReviewFence.Receipt legacyReceipt=
                    fence.arm(legacy.proposal);
                MailboxFencedRestartForensics.Report legacyReport=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,legacy.account
                    );
                legacyProposalClassificationPreserved=
                    legacyReport.state==
                        MailboxFencedRestartForensics.State
                            .EXACT_PREPARED_UNCLAIMED&&
                    legacyReceipt.record.matches(legacy.proposal);

                // Both negative formats may coexist. New G21.47
                // uncertainty is never silently reduced to a
                // G21.32 "exact prepared" label.
                fence.arm(dual.proposal);
                Path dualFile=paths.resolve(dual.account);
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(dualFile,()->{
                        MailboxStrictUnconfirmedReviewFence
                            .armInsidePublicationLock(
                                dualFile,dual.account,
                                StrictDurablePlayerSnapshotWriter
                                    .canonicalSnapshotSha256(
                                        dual.proposal.preparedPreimage
                                    )
                            );
                        return null;
                    });
                MailboxFencedRestartForensics.Report dualReport=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,dual.account
                    );
                dualMarkersPrioritizeStrictUncertainty=
                    dualReport.state==
                        MailboxFencedRestartForensics.State
                            .STRICT_UNCERTAIN_EXACT_SNAPSHOT;

                MailboxFencedRestartForensics.Report clean=
                    MailboxFencedRestartForensics.inspect(
                        world.persistence(),fence,normal.account
                    );
                unfencedAccountRetainsNoAuthority=
                    clean.state==MailboxFencedRestartForensics.State
                        .NO_FENCE_NO_AUTHORITY;

                for(MailboxFencedRestartForensics.Report report:
                    new MailboxFencedRestartForensics.Report[]{
                        exact,divergent,missing,badAccount,corrupt,
                        legacyReport,dualReport,clean
                    }){
                    allReportsNonAuthorizing &=noAuthority(report);
                }

                for(Seed seed:new Seed[]{
                    uncertain,legacy,dual,normal
                }){
                    noItemCreditOrMailboxClaim &=
                        seed.player.bank().inventorySlots()==0&&
                        seed.player.mailbox().get(seed.message)
                            .claimState==
                                MailboxRewardDeliveryService.ClaimState
                                    .UNCLAIMED;
                }
                noAutomaticMarkerRemoval=
                    fence.present(uncertain.account)&&
                    fence.present(legacy.account)&&
                    fence.present(dual.account)&&
                    !fence.present(normal.account);
                try(Stream<Path> entries=Files.list(root)){
                    noUnpublishedTemps=entries.noneMatch(
                        path->path.getFileName().toString()
                            .endsWith(".tmp")
                    );
                }
            }

            try(World restarted=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                restarted.start();
                freshRestartStrictAccountDenied=
                    blocked(restarted,"g2148-strict");
                freshRestartLegacyAccountDenied=
                    blocked(restarted,"g2148-legacy");
                freshRestartDualAccountDenied=
                    blocked(restarted,"g2148-dual");
                freshRestartOrdinaryAccountLoads=
                    restarted.persistence().load(
                        "g2148-normal"
                    ).isPresent();
            }
        }finally{
            allowFinish.countDown();
            try(Stream<Path> entries=Files.walk(root)){
                for(Path path:entries.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2148_MAILBOX_STRICT_FORENSICS_DIAGNOSTICS"+
            " realUnconfirmedMarker="+realUnconfirmedWriteOccurred+
            " exactStrictSnapshot="+strictExactClassified+
            " exactWholeSnapshotDigest="+strictDigestBoundToDisk+
            " divergentStrictSnapshot="+strictDivergenceClassified+
            " missingStrictAccount="+strictMissingAccountClassified+
            " corruptAccountFailClosed="+strictBadAccountRejected+
            " corruptStrictMarkerInvalid="+strictCorruptMarkerClassified+
            " corruptMarkerStillVetoes="+invalidStrictMarkerStillPresent+
            " originalG2132Classification="+
                legacyProposalClassificationPreserved+
            " dualMarkerStrictWins="+
                dualMarkersPrioritizeStrictUncertainty+
            " normalUnfencedNoAuthority="+
                unfencedAccountRetainsNoAuthority+
            " restartStrictDenied="+freshRestartStrictAccountDenied+
            " restartOriginalDenied="+freshRestartLegacyAccountDenied+
            " restartDualDenied="+freshRestartDualAccountDenied+
            " restartNormalAllowed="+freshRestartOrdinaryAccountLoads+
            " allForensicsNoAuthority="+allReportsNonAuthorizing+
            " inventoryAndMailboxUnchanged="+
                noItemCreditOrMailboxClaim+
            " noAutoMarkerRelease="+noAutomaticMarkerRemoval+
            " noTempArtifacts="+noUnpublishedTemps
        );
        require(
            realUnconfirmedWriteOccurred&&strictExactClassified&&
            strictDigestBoundToDisk&&strictDivergenceClassified&&
            strictMissingAccountClassified&&strictBadAccountRejected&&
            strictCorruptMarkerClassified&&
            invalidStrictMarkerStillPresent&&
            legacyProposalClassificationPreserved&&
            dualMarkersPrioritizeStrictUncertainty&&
            unfencedAccountRetainsNoAuthority&&
            freshRestartStrictAccountDenied&&
            freshRestartLegacyAccountDenied&&
            freshRestartDualAccountDenied&&
            freshRestartOrdinaryAccountLoads&&
            allReportsNonAuthorizing&&
            noItemCreditOrMailboxClaim&&
            noAutomaticMarkerRemoval&&noUnpublishedTemps,
            "G21.48 strict uncertainty read-only forensics"
        );
        System.out.println(
            "G2148_MAILBOX_STRICT_UNCERTAINTY_FORENSICS_PASS"+
            " validG2147MarkerClearlyClassified=true"+
            " originalG2132ForensicsUnaffected=true"+
            " allReportsNonAuthorizing=true"+
            " restartVetoPreserved=true"+
            " grant=false replay=false rollback=false release=false"
        );
    }

    private static Seed seed(
        World world,String account,String message
    )throws Exception{
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            result=new AtomicReference<>();
        world.submitAndWait(owner,generation,()->{
            owner.mailbox().deliver(new RewardDeliveryMessage(
                message,"Strict uncertainty forensic","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2148_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(
                owner,MailboxPreparedClaimJournal.prepare(
                    owner,row
                )
            );
            result.set(MailboxSettlementPostimagePlanner.plan(
                owner,generation,row
            ));
        },5000L);
        return new Seed(account,message,owner,generation,result.get());
    }

    private static boolean noAuthority(
        MailboxFencedRestartForensics.Report report
    ){
        return !report.grantAuthorized&&
            !report.replayAuthorized&&
            !report.rollbackAuthorized&&
            !report.releaseFenceAuthorized&&
            !report.sessionAdmissionAuthorized&&
            !report.fileDurabilityConfirmed&&
            !report.automaticRecoveryAuthorized;
    }

    private static boolean unconfirmed(
        CompletableFuture<StrictDurablePlayerSnapshotWriter.Receipt> task
    )throws Exception{
        try{
            task.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException failure){
            Throwable cause=failure.getCause();
            return cause instanceof
                StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException&&
                cause.getMessage()!=null&&
                cause.getMessage().contains(
                    "G21.46 STRICT_PREPARED_POSTPUBLICATION_UNCONFIRMED"
                );
        }
    }

    private static boolean blocked(
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

    private static void await(CountDownLatch gate,String step)
        throws IOException{
        try{
            if(!gate.await(8,TimeUnit.SECONDS))
                throw new IOException(step+" timed out");
        }catch(InterruptedException stop){
            Thread.currentThread().interrupt();
            throw new IOException(step+" interrupted",stop);
        }
    }

    private static void require(boolean passed,String gate){
        if(!passed)throw new AssertionError(gate);
    }

    private G2148MailboxStrictUncertaintyForensicsIntegrationTest(){}
}
