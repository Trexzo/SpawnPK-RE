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
 * G21.81: read a real terminal account from fresh repository instances,
 * independently classify restart safety, and reconstruct inert identity.
 * Never creates an actual positive COMMIT/replay/live credit.
 */
public final class G2181MailboxRestartTerminalIdentityIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer owner,MailboxSettlementPostimagePlanner.Proposal p){
            this.owner=owner;
            this.proposal=p;
            terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }
    }

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2181-restart-identity-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository initial=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);
        boolean stableFreshInstance=false;
        boolean correctIdentityFields=false;
        boolean originalTerminalQuarantined=false;
        boolean preparedUnsettled=false;
        boolean staleMixedEvidenceRejected=false;
        boolean checksumTamperRejected=false;
        boolean postimageTamperRejected=false;
        boolean markerDominatesEvenValidTerminal=false;
        boolean markerBytesUnchanged=false;
        boolean foreignAccountRejected=false;
        boolean missingRefused=false;
        boolean unrelatedLegacyUnsettled=false;
        boolean noForensicWrite=true;
        boolean allNoPositiveAuthority=true;
        boolean liveNotCredited=false;
        boolean noLeaks=false;
        String prefix="extension."+
            MailboxAtomicTerminalSnapshot.NAMESPACE+".";
        try(World fixture=World.isolatedForTest(60000L);
            World restarted=World.isolatedForTest(60000L,initial)){
            restarted.start();

            String terminalAccount="g2181-terminal";
            Seed terminal=seed(fixture,terminalAccount);
            strict.saveStrict(terminal.proposal.preparedPreimage);
            FilePlayerRepository.RestartRecoveryEvidence oldPrepared=
                initial.inspectRestartRecoveryReadOnly(terminalAccount);
            strict.saveStrict(terminal.terminal);
            byte[] terminalBefore=Files.readAllBytes(
                paths.resolve(terminalAccount));
            FilePlayerRepository first=new FilePlayerRepository(paths);
            FilePlayerRepository second=new FilePlayerRepository(paths);
            PlayerSnapshot disk1=first.load(terminalAccount).get();
            PlayerSnapshot disk2=second.load(terminalAccount).get();
            MailboxRestartTerminalIdentity.Result identity1=
                MailboxRestartTerminalIdentity.inspect(
                    terminalAccount,disk1,
                    first.inspectRestartRecoveryReadOnly(terminalAccount));
            MailboxRestartTerminalIdentity.Result identity2=
                MailboxRestartTerminalIdentity.inspect(
                    terminalAccount,disk2,
                    second.inspectRestartRecoveryReadOnly(terminalAccount));
            stableFreshInstance=
                identity1.state==MailboxRestartTerminalIdentity.State
                    .VALID_TERMINAL_QUARANTINED&&
                identity2.state==identity1.state&&
                identity1.identityValidated&&identity2.identityValidated&&
                identity1.identityFingerprint!=null&&
                identity1.identityFingerprint.matches("[0-9a-f]{64}")&&
                identity1.identityFingerprint.equals(
                    identity2.identityFingerprint)&&
                noAuthority(identity1)&&noAuthority(identity2);
            correctIdentityFields=
                identity1.account.equals(terminalAccount)&&
                identity1.messageId.equals(terminal.proposal.messageId)&&
                identity1.intentKey.equals(
                    terminal.proposal.idempotencyKey)&&
                identity1.preparedSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(
                            terminal.proposal.preparedPreimage))&&
                identity1.hypotheticalSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(
                            terminal.proposal.hypotheticalPostimage))&&
                identity1.terminalSnapshotSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(terminal.terminal));
            noForensicWrite&=Arrays.equals(terminalBefore,
                Files.readAllBytes(paths.resolve(terminalAccount)));
            originalTerminalQuarantined=
                rejectsAdmission(restarted,terminalAccount);
            MailboxRestartTerminalIdentity.Result stale=
                MailboxRestartTerminalIdentity.inspect(
                    terminalAccount,disk1,oldPrepared);
            staleMixedEvidenceRejected=
                stale.state==MailboxRestartTerminalIdentity.State
                    .INVALID_OR_CONFLICTING_QUARANTINE&&
                !stale.identityValidated&&noAuthority(stale);

            String preparedAccount="g2181-prepared";
            Seed prepared=seed(fixture,preparedAccount);
            strict.saveStrict(prepared.proposal.preparedPreimage);
            MailboxRestartTerminalIdentity.Result pre=
                inspectFresh(paths,preparedAccount);
            preparedUnsettled=
                pre.state==MailboxRestartTerminalIdentity.State
                    .PREPARED_OR_LEGACY_NO_TERMINAL&&
                !pre.identityValidated&&noAuthority(pre)&&
                restarted.persistence().load(preparedAccount).isPresent();

            String checksumAccount="g2181-checksum";
            Seed tamperedChecksum=seed(fixture,checksumAccount);
            TreeMap<String,String> changedChecksum=new TreeMap<>(
                tamperedChecksum.terminal.values());
            changedChecksum.put(prefix+"checksum",
                "0000000000000000000000000000000000000000000000000000000000000000");
            strict.saveStrict(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,checksumAccount,
                changedChecksum));
            MailboxRestartTerminalIdentity.Result checksum=
                inspectFresh(paths,checksumAccount);
            checksumTamperRejected=
                checksum.state==MailboxRestartTerminalIdentity.State
                    .INVALID_OR_CONFLICTING_QUARANTINE&&
                !checksum.identityValidated&&noAuthority(checksum);

            String afterAccount="g2181-after";
            Seed tamperedAfter=seed(fixture,afterAccount);
            TreeMap<String,String> changedAfter=new TreeMap<>(
                tamperedAfter.terminal.values());
            changedAfter.put(prefix+"after",
                "0000000000000000000000000000000000000000000000000000000000000000");
            strict.saveStrict(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,afterAccount,
                changedAfter));
            MailboxRestartTerminalIdentity.Result after=
                inspectFresh(paths,afterAccount);
            postimageTamperRejected=
                after.state==MailboxRestartTerminalIdentity.State
                    .INVALID_OR_CONFLICTING_QUARANTINE&&
                !after.identityValidated&&noAuthority(after);

            String markedAccount="g2181-marker";
            Seed marked=seed(fixture,markedAccount);
            strict.saveStrict(marked.terminal);
            Path marker=paths.resolve(markedAccount).resolveSibling(
                markedAccount+".properties"+
                MailboxStrictWriteIntentFence.SUFFIX);
            byte[] markerContent="G2181_NEGATIVE_MARKER_NO_GRANT".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(marker,markerContent);
            MailboxRestartTerminalIdentity.Result markerResult=
                inspectFresh(paths,markedAccount);
            markerDominatesEvenValidTerminal=
                markerResult.state==MailboxRestartTerminalIdentity.State
                    .NEGATIVE_MARKER_MANUAL_REVIEW&&
                markerResult.identityValidated&&
                markerResult.intentKey.equals(
                    marked.proposal.idempotencyKey)&&
                noAuthority(markerResult)&&
                rejectsAdmission(restarted,markedAccount);
            markerBytesUnchanged=Arrays.equals(markerContent,
                Files.readAllBytes(marker));

            String missingAccount="g2181-missing";
            FilePlayerRepository gone=new FilePlayerRepository(paths);
            MailboxRestartTerminalIdentity.Result missing=
                MailboxRestartTerminalIdentity.inspect(missingAccount,null,
                    gone.inspectRestartRecoveryReadOnly(missingAccount));
            missingRefused=missing.state==
                MailboxRestartTerminalIdentity.State
                    .MISSING_ACCOUNT_NO_IDENTITY&&
                !missing.identityValidated&&noAuthority(missing);

            String legacyAccount="g2181-legacy";
            WorldPlayer legacy=new WorldPlayer();
            legacy.markRegistered(legacyAccount);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                legacyAccount,legacy));
            MailboxRestartTerminalIdentity.Result legacyResult=
                inspectFresh(paths,legacyAccount);
            unrelatedLegacyUnsettled=legacyResult.state==
                MailboxRestartTerminalIdentity.State
                    .PREPARED_OR_LEGACY_NO_TERMINAL&&
                !legacyResult.identityValidated&&noAuthority(legacyResult);

            try{
                MailboxRestartTerminalIdentity.inspect(
                    "g2181-foreign",disk1,
                    first.inspectRestartRecoveryReadOnly(terminalAccount));
            }catch(IllegalArgumentException refused){
                foreignAccountRejected=refused.getMessage().contains(
                    "foreign or noncanonical");
            }

            liveNotCredited=
                terminal.owner.bank().inventorySlots()==0&&
                terminal.owner.mailbox().get(
                    terminal.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                marked.owner.bank().inventorySlots()==0&&
                marked.owner.mailbox().get(
                    marked.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            allNoPositiveAuthority=
                noAuthority(pre)&&noAuthority(checksum)&&
                noAuthority(after)&&noAuthority(markerResult)&&
                noAuthority(missing)&&noAuthority(legacyResult);
            try(Stream<Path> all=Files.walk(dir)){
                noLeaks=all.noneMatch(p->
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
        System.out.println("G2181_RESTART_IDENTITY_DIAGNOSTICS"+
            " stableFreshInstance="+stableFreshInstance+
            " correctIdentityFields="+correctIdentityFields+
            " originalTerminalQuarantined="+originalTerminalQuarantined+
            " preparedUnsettled="+preparedUnsettled+
            " staleMixedEvidenceRejected="+staleMixedEvidenceRejected+
            " checksumTamperRejected="+checksumTamperRejected+
            " postimageTamperRejected="+postimageTamperRejected+
            " markerDominatesEvenValidTerminal="+
                markerDominatesEvenValidTerminal+
            " markerBytesUnchanged="+markerBytesUnchanged+
            " foreignAccountRejected="+foreignAccountRejected+
            " missingRefused="+missingRefused+
            " unrelatedLegacyUnsettled="+unrelatedLegacyUnsettled+
            " noForensicWrite="+noForensicWrite+
            " allNoPositiveAuthority="+allNoPositiveAuthority+
            " liveNotCredited="+liveNotCredited+
            " noLeaks="+noLeaks);
        if(!(stableFreshInstance&&correctIdentityFields&&
             originalTerminalQuarantined&&preparedUnsettled&&
             staleMixedEvidenceRejected&&checksumTamperRejected&&
             postimageTamperRejected&&
             markerDominatesEvenValidTerminal&&markerBytesUnchanged&&
             foreignAccountRejected&&missingRefused&&
             unrelatedLegacyUnsettled&&noForensicWrite&&
             allNoPositiveAuthority&&liveNotCredited&&noLeaks))
            throw new AssertionError(
                "G21.81 restart identity NO_GRANT regression failed");
        System.out.println("G2181_RESTART_IDENTITY_PASS"+
            " persistentTerminalIdentity=true"+
            " markerPrecedence=true tamperVeto=true"+
            " commit=false replay=false admission=false grant=false");
    }

    private static MailboxRestartTerminalIdentity.Result inspectFresh(
        FilePlayerRepository.PathResolver paths,String account
    )throws IOException{
        FilePlayerRepository reopened=new FilePlayerRepository(paths);
        return MailboxRestartTerminalIdentity.inspect(
            account,reopened.load(account).orElse(null),
            reopened.inspectRestartRecoveryReadOnly(account));
    }

    private static boolean rejectsAdmission(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException veto){
            return true;
        }
    }

    private static boolean noAuthority(
        MailboxRestartTerminalIdentity.Result r
    ){
        return r.missingPositiveProofs.size()==4&&
            !r.durabilityConfirmed&&!r.transactionCommitted&&
            !r.liveApplied&&!r.grantAuthorized&&!r.replayAuthorized&&
            !r.rollbackAuthorized&&!r.restartAdmissionAuthorized&&
            !r.releaseAuthorized&&!r.clientAckAuthorized;
    }

    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String message=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            message,"Identity recovery","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2181_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            player.mailbox().get(message);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,selected));
        return new Seed(player,MailboxSettlementPostimagePlanner.plan(
            player,generation,selected));
    }
}
