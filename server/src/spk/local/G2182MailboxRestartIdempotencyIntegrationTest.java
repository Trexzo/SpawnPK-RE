package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * G21.82: independently reloaded real account files spanning two
 * observations. Verifies only inert same-key/conflict/manual-hold
 * classification; NEVER a restart replay or item-grant path.
 */
public final class G2182MailboxRestartIdempotencyIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer owner,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.owner=owner;
            this.proposal=proposal;
            terminal=MailboxAtomicTerminalSnapshot.compose(proposal);
        }
    }

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2182-pairwise-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean repeatedTerminalQuarantined=false;
        boolean conflictingTerminalQuarantined=false;
        boolean preparedToTerminalNoReplay=false;
        boolean terminalToPreparedNoReplay=false;
        boolean repeatedPreparedUnsettled=false;
        boolean legacyUnsettled=false;
        boolean missingRemainsQuarantined=false;
        boolean missingToTerminalQuarantined=false;
        boolean invalidTerminalQuarantined=false;
        boolean markersAlwaysWin=true;
        boolean foreignAccountRejected=false;
        boolean originalLiveNeverCredited=true;
        boolean realWorldRestartVeto=false;
        boolean identicalDiskUntouched=false;
        boolean noTemporaryOrLeaseLeaks=false;
        boolean allDecisionsNoGrant=true;
        int markerCases=0;
        int comparedCases=0;

        try(World firstWorld=World.isolatedForTest(60000L);
            World independentWorld=World.isolatedForTest(60000L);
            World restarted=World.isolatedForTest(
                60000L,new FilePlayerRepository(paths))){
            restarted.start();

            String repeatedAccount="g2182-repeat";
            Seed repeated=seed(firstWorld,repeatedAccount,"first",25);
            strict.saveStrict(repeated.terminal);
            byte[] original=Files.readAllBytes(
                paths.resolve(repeatedAccount));
            MailboxRestartTerminalIdentity.Result repeatA=
                fresh(paths,repeatedAccount);
            MailboxRestartTerminalIdentity.Result repeatB=
                fresh(paths,repeatedAccount);
            MailboxRestartIdempotencyReconciliation.Decision same=
                compare(repeatA,repeatB);
            comparedCases++;
            repeatedTerminalQuarantined=
                same.state==MailboxRestartIdempotencyReconciliation.State
                    .REPEATED_TERMINAL_IDENTITY_QUARANTINED&&
                same.sameTerminalIdentity&&noAuthority(same)&&
                repeatA.identityFingerprint.equals(
                    repeatB.identityFingerprint);
            identicalDiskUntouched=Arrays.equals(original,
                Files.readAllBytes(paths.resolve(repeatedAccount)));
            realWorldRestartVeto=
                rejectsAdmission(restarted,repeatedAccount);

            String conflictAccount="g2182-conflict";
            Seed conflictA=seed(firstWorld,conflictAccount,"first",25);
            Seed conflictB=seed(independentWorld,conflictAccount,"other",40);
            strict.saveStrict(conflictA.terminal);
            MailboxRestartTerminalIdentity.Result conflictBefore=
                fresh(paths,conflictAccount);
            strict.saveStrict(conflictB.terminal);
            MailboxRestartTerminalIdentity.Result conflictAfter=
                fresh(paths,conflictAccount);
            MailboxRestartIdempotencyReconciliation.Decision conflict=
                compare(conflictBefore,conflictAfter);
            comparedCases++;
            conflictingTerminalQuarantined=
                conflict.state==MailboxRestartIdempotencyReconciliation.State
                    .CONFLICTING_TERMINAL_IDENTITIES_QUARANTINED&&
                !conflict.sameTerminalIdentity&&
                !conflictBefore.intentKey.equals(
                    conflictAfter.intentKey)&&noAuthority(conflict);

            String transitionAccount="g2182-transition";
            Seed transition=seed(firstWorld,transitionAccount,"first",25);
            strict.saveStrict(transition.proposal.preparedPreimage);
            MailboxRestartTerminalIdentity.Result preparedBefore=
                fresh(paths,transitionAccount);
            strict.saveStrict(transition.terminal);
            MailboxRestartTerminalIdentity.Result terminalAfter=
                fresh(paths,transitionAccount);
            MailboxRestartIdempotencyReconciliation.Decision progressed=
                compare(preparedBefore,terminalAfter);
            comparedCases++;
            preparedToTerminalNoReplay=
                progressed.state==MailboxRestartIdempotencyReconciliation
                    .State.PREPARED_TO_TERMINAL_UNCONFIRMED&&
                !progressed.sameTerminalIdentity&&
                noAuthority(progressed)&&
                rejectsAdmission(restarted,transitionAccount);

            String regressAccount="g2182-regression";
            Seed regressed=seed(firstWorld,regressAccount,"first",25);
            strict.saveStrict(regressed.terminal);
            MailboxRestartTerminalIdentity.Result terminalBefore=
                fresh(paths,regressAccount);
            // Test-only unguarded raw snapshot writer simulates a stale
            // external rollback. This is NOT a real World save path.
            strict.saveStrict(regressed.proposal.preparedPreimage);
            MailboxRestartTerminalIdentity.Result preparedAfter=
                fresh(paths,regressAccount);
            MailboxRestartIdempotencyReconciliation.Decision backwards=
                compare(terminalBefore,preparedAfter);
            comparedCases++;
            terminalToPreparedNoReplay=
                backwards.state==MailboxRestartIdempotencyReconciliation
                    .State.TERMINAL_TO_PREPARED_REGRESSION_QUARANTINED&&
                !backwards.sameTerminalIdentity&&noAuthority(backwards);

            String preparedAccount="g2182-prepared";
            Seed unsettled=seed(firstWorld,preparedAccount,"first",25);
            strict.saveStrict(unsettled.proposal.preparedPreimage);
            MailboxRestartIdempotencyReconciliation.Decision bothPrepared=
                compare(fresh(paths,preparedAccount),
                    fresh(paths,preparedAccount));
            comparedCases++;
            repeatedPreparedUnsettled=
                bothPrepared.state==MailboxRestartIdempotencyReconciliation
                    .State.UNSETTLED_NO_TERMINAL_RECORD&&
                noAuthority(bothPrepared)&&!bothPrepared.sameTerminalIdentity;

            String legacyAccount="g2182-legacy";
            WorldPlayer legacy=new WorldPlayer();
            legacy.markRegistered(legacyAccount);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                legacyAccount,legacy));
            MailboxRestartIdempotencyReconciliation.Decision bothLegacy=
                compare(fresh(paths,legacyAccount),
                    fresh(paths,legacyAccount));
            comparedCases++;
            legacyUnsettled=
                bothLegacy.state==MailboxRestartIdempotencyReconciliation
                    .State.UNSETTLED_NO_TERMINAL_RECORD&&
                noAuthority(bothLegacy);

            String missingAccount="g2182-missing";
            MailboxRestartTerminalIdentity.Result missing=
                fresh(paths,missingAccount);
            MailboxRestartIdempotencyReconciliation.Decision stillMissing=
                compare(missing,fresh(paths,missingAccount));
            comparedCases++;
            missingRemainsQuarantined=
                stillMissing.state==MailboxRestartIdempotencyReconciliation
                    .State.MISSING_ACCOUNT_QUARANTINE&&
                noAuthority(stillMissing);
            Seed formerlyMissing=seed(firstWorld,missingAccount,"first",25);
            strict.saveStrict(formerlyMissing.terminal);
            MailboxRestartIdempotencyReconciliation.Decision appeared=
                compare(missing,fresh(paths,missingAccount));
            comparedCases++;
            missingToTerminalQuarantined=
                appeared.state==MailboxRestartIdempotencyReconciliation
                    .State.MISSING_ACCOUNT_QUARANTINE&&
                noAuthority(appeared);

            String invalidAccount="g2182-invalid";
            Seed malformed=seed(firstWorld,invalidAccount,"first",25);
            strict.saveStrict(malformed.terminal);
            MailboxRestartTerminalIdentity.Result validBefore=
                fresh(paths,invalidAccount);
            TreeMap<String,String> changed=new TreeMap<>(
                malformed.terminal.values());
            changed.put("extension.mailbox-terminal-snapshot.checksum",
                "0000000000000000000000000000000000000000000000000000000000000000");
            strict.saveStrict(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,invalidAccount,changed));
            MailboxRestartIdempotencyReconciliation.Decision invalid=
                compare(validBefore,fresh(paths,invalidAccount));
            comparedCases++;
            invalidTerminalQuarantined=
                invalid.state==MailboxRestartIdempotencyReconciliation.State
                    .INVALID_OR_CONFLICTING_QUARANTINE&&
                noAuthority(invalid);

            String[] suffixes={
                ".g2132-mailbox-review",
                MailboxStrictUncertainFence.SUFFIX,
                MailboxStrictWriteIntentFence.SUFFIX
            };
            for(int i=0;i<suffixes.length;i++){
                String account="g2182-marker-"+i;
                Seed marked=seed(firstWorld,account,"first",25);
                strict.saveStrict(marked.terminal);
                MailboxRestartTerminalIdentity.Result unmarked=
                    fresh(paths,account);
                Path marker=paths.resolve(account).resolveSibling(
                    account+".properties"+suffixes[i]);
                byte[] bytes=("G2182_MARKER_"+i).getBytes(
                    StandardCharsets.US_ASCII);
                Files.write(marker,bytes);
                MailboxRestartTerminalIdentity.Result withMarker=
                    fresh(paths,account);
                MailboxRestartIdempotencyReconciliation.Decision afterMarker=
                    compare(unmarked,withMarker);
                MailboxRestartIdempotencyReconciliation.Decision repeatMarker=
                    compare(withMarker,withMarker);
                comparedCases+=2;
                markerCases++;
                markersAlwaysWin&=
                    afterMarker.state==
                        MailboxRestartIdempotencyReconciliation.State
                            .NEGATIVE_MARKER_MANUAL_REVIEW&&
                    repeatMarker.state==
                        MailboxRestartIdempotencyReconciliation.State
                            .NEGATIVE_MARKER_MANUAL_REVIEW&&
                    !afterMarker.sameTerminalIdentity&&
                    !repeatMarker.sameTerminalIdentity&&
                    noAuthority(afterMarker)&&noAuthority(repeatMarker)&&
                    Arrays.equals(bytes,Files.readAllBytes(marker))&&
                    rejectsAdmission(restarted,account);
            }

            try{
                compare(repeatA,conflictAfter);
            }catch(IllegalArgumentException refused){
                foreignAccountRejected=refused.getMessage().contains(
                    "foreign or noncanonical");
            }
            originalLiveNeverCredited=
                repeated.owner.bank().inventorySlots()==0&&
                repeated.owner.mailbox().get(
                    repeated.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                conflictA.owner.bank().inventorySlots()==0&&
                conflictB.owner.bank().inventorySlots()==0&&
                transition.owner.bank().inventorySlots()==0&&
                regressed.owner.bank().inventorySlots()==0;
            allDecisionsNoGrant=
                noAuthority(same)&&noAuthority(conflict)&&
                noAuthority(progressed)&&noAuthority(backwards)&&
                noAuthority(bothPrepared)&&noAuthority(bothLegacy)&&
                noAuthority(stillMissing)&&noAuthority(appeared)&&
                noAuthority(invalid);
            try(Stream<Path> all=Files.walk(dir)){
                noTemporaryOrLeaseLeaks=all.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(dir)){
                for(Path file:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
        System.out.println("G2182_RESTART_RECONCILIATION_DIAGNOSTICS"+
            " repeatedTerminalQuarantined="+repeatedTerminalQuarantined+
            " conflictingTerminalQuarantined="+
                conflictingTerminalQuarantined+
            " preparedToTerminalNoReplay="+preparedToTerminalNoReplay+
            " terminalToPreparedNoReplay="+terminalToPreparedNoReplay+
            " repeatedPreparedUnsettled="+repeatedPreparedUnsettled+
            " legacyUnsettled="+legacyUnsettled+
            " missingRemainsQuarantined="+missingRemainsQuarantined+
            " missingToTerminalQuarantined="+
                missingToTerminalQuarantined+
            " invalidTerminalQuarantined="+invalidTerminalQuarantined+
            " markersAlwaysWin="+markersAlwaysWin+
            " markerCases="+markerCases+
            " comparedCases="+comparedCases+
            " foreignAccountRejected="+foreignAccountRejected+
            " originalLiveNeverCredited="+originalLiveNeverCredited+
            " realWorldRestartVeto="+realWorldRestartVeto+
            " identicalDiskUntouched="+identicalDiskUntouched+
            " noTemporaryOrLeaseLeaks="+noTemporaryOrLeaseLeaks+
            " allDecisionsNoGrant="+allDecisionsNoGrant);
        if(!(repeatedTerminalQuarantined&&
             conflictingTerminalQuarantined&&
             preparedToTerminalNoReplay&&
             terminalToPreparedNoReplay&&
             repeatedPreparedUnsettled&&legacyUnsettled&&
             missingRemainsQuarantined&&
             missingToTerminalQuarantined&&
             invalidTerminalQuarantined&&
             markersAlwaysWin&&markerCases==3&&comparedCases==15&&
             foreignAccountRejected&&originalLiveNeverCredited&&
             realWorldRestartVeto&&identicalDiskUntouched&&
             noTemporaryOrLeaseLeaks&&allDecisionsNoGrant))
            throw new AssertionError(
                "G21.82 read-only restart reconciliation regression failed");
        System.out.println("G2182_RESTART_RECONCILIATION_PASS"+
            " repeatIdentity=true changedIdentityQuarantine=true"+
            " prePostTransitionNonGrant=true negativeMarkerWins=true"+
            " commit=false replay=false admission=false"+
            " liveApply=false grant=false release=false ack=false");
    }

    private static MailboxRestartTerminalIdentity.Result fresh(
        FilePlayerRepository.PathResolver paths,String account
    )throws IOException{
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        return MailboxRestartTerminalIdentity.inspect(
            account,repo.load(account).orElse(null),
            repo.inspectRestartRecoveryReadOnly(account));
    }

    private static MailboxRestartIdempotencyReconciliation.Decision compare(
        MailboxRestartTerminalIdentity.Result previous,
        MailboxRestartTerminalIdentity.Result current
    ){
        return MailboxRestartIdempotencyReconciliation.compare(
            previous,current);
    }

    private static boolean rejectsAdmission(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException refused){
            return true;
        }
    }

    private static boolean noAuthority(
        MailboxRestartIdempotencyReconciliation.Decision result
    ){
        return result.missingPositiveProofs.size()==4&&
            !result.durabilityConfirmed&&
            !result.transactionCommitted&&!result.liveApplied&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.rollbackAuthorized&&
            !result.restartAdmissionAuthorized&&
            !result.releaseAuthorized&&!result.clientAckAuthorized;
    }

    private static Seed seed(
        World world,String account,String suffix,int amount
    ){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String messageId=account+":"+suffix;
        player.mailbox().deliver(new RewardDeliveryMessage(
            messageId,"Idempotency fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,amount)),
            "CUSTOM_LOCALLAB_G2182_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=
            player.mailbox().get(messageId);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,row));
        return new Seed(player,MailboxSettlementPostimagePlanner.plan(
            player,generation,row));
    }
}
