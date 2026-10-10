package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * G21.79: real file-backed restart-state cutover coverage.
 * Nothing here authorizes COMMIT, live credit, restart replay, or ACK.
 */
public final class G2179MailboxSettlementCutoverContractIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final MailboxSettlementPostimagePlanner.Proposal plan;
        Seed(WorldPlayer player,MailboxSettlementPostimagePlanner.Proposal p){
            owner=player;
            plan=p;
        }
    }

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2179-cutover-contract-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);

        EnumSet<FilePlayerRepository.RestartRecoveryEvidence.State> seen=
            EnumSet.noneOf(
                FilePlayerRepository.RestartRecoveryEvidence.State.class);
        boolean preparedCorrect=false,terminalCorrect=false;
        boolean invalidCorrect=false,legacyCorrect=false;
        boolean missingCorrect=false,markerCorrect=true;
        boolean terminalStillQuarantined=false;
        boolean preparedStillNonGrant=false;
        boolean foreignRejected=false;
        boolean noPositiveAuthority=true;
        boolean liveInventoryUnchanged=true;
        boolean noForensicWrites=true;
        boolean noTempOrLeaseLeaks=false;
        try(World world=World.isolatedForTest(60000L)){
            String preparedName="g2179-prepared";
            Seed prepared=seed(world,preparedName);
            strict.saveStrict(prepared.plan.preparedPreimage);
            FilePlayerRepository.RestartRecoveryEvidence prep=
                repo.inspectRestartRecoveryReadOnly(preparedName);
            preparedCorrect=assertDecision(preparedName,prep,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .PREPARED_UNCLAIMED_NO_REPLAY,
                MailboxSettlementCutoverReadiness.Disposition
                    .PREPARED_ONLY_NO_SETTLEMENT,seen);
            preparedStillNonGrant=
                repo.loadForWorldSession(preparedName).isPresent()&&
                prepared.owner.bank().inventorySlots()==0&&
                prepared.owner.mailbox().get(prepared.plan.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            String terminalName="g2179-terminal";
            Seed terminal=seed(world,terminalName);
            PlayerSnapshot terminalImage=
                MailboxAtomicTerminalSnapshot.compose(terminal.plan);
            strict.saveStrict(terminalImage);
            byte[] stored=Files.readAllBytes(paths.resolve(terminalName));
            FilePlayerRepository.RestartRecoveryEvidence end=
                repo.inspectRestartRecoveryReadOnly(terminalName);
            terminalCorrect=assertDecision(terminalName,end,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .COHERENT_TERMINAL_QUARANTINE,
                MailboxSettlementCutoverReadiness.Disposition
                    .COHERENT_TERMINAL_NOT_COMMITTED,seen);
            noForensicWrites&=Arrays.equals(stored,
                Files.readAllBytes(paths.resolve(terminalName)));
            try{
                repo.loadForWorldSession(terminalName);
            }catch(IOException veto){
                terminalStillQuarantined=veto.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }

            String missingName="g2179-missing";
            missingCorrect=assertDecision(missingName,
                repo.inspectRestartRecoveryReadOnly(missingName),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .MISSING_ACCOUNT_NO_REPLAY,
                MailboxSettlementCutoverReadiness.Disposition
                    .MISSING_ACCOUNT_QUARANTINE,seen);

            String legacyName="g2179-legacy";
            WorldPlayer legacy=new WorldPlayer();
            legacy.markRegistered(legacyName);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                legacyName,legacy));
            legacyCorrect=assertDecision(legacyName,
                repo.inspectRestartRecoveryReadOnly(legacyName),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .LEGACY_NO_JOURNAL_NON_ADMITTING,
                MailboxSettlementCutoverReadiness.Disposition
                    .LEGACY_NOT_MAILBOX_TRANSACTION,seen);

            String invalidTerminal="g2179-badterminal";
            Seed corruptedTerminal=seed(world,invalidTerminal);
            TreeMap<String,String> damaged=new TreeMap<>(
                MailboxAtomicTerminalSnapshot.compose(
                    corruptedTerminal.plan).values());
            damaged.remove(
                "extension.mailbox-terminal-snapshot.checksum");
            repo.save(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,invalidTerminal,damaged));
            boolean badTerminal=assertDecision(invalidTerminal,
                repo.inspectRestartRecoveryReadOnly(invalidTerminal),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .INVALID_TERMINAL_QUARANTINE,
                MailboxSettlementCutoverReadiness.Disposition
                    .INVALID_ACCOUNT_QUARANTINE,seen);

            String invalidPrepared="g2179-badprepared";
            Seed corruptedPrepared=seed(world,invalidPrepared);
            TreeMap<String,String> invalid=new TreeMap<>(
                corruptedPrepared.plan.preparedPreimage.values());
            invalid.put("extension.mailbox-claim-intent.version","invalid");
            repo.save(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,invalidPrepared,invalid));
            boolean badPrepared=assertDecision(invalidPrepared,
                repo.inspectRestartRecoveryReadOnly(invalidPrepared),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .INVALID_PREPARED_QUARANTINE,
                MailboxSettlementCutoverReadiness.Disposition
                    .INVALID_ACCOUNT_QUARANTINE,seen);
            invalidCorrect=badTerminal&&badPrepared;

            String[] suffixes={
                ".g2132-mailbox-review",
                MailboxStrictUncertainFence.SUFFIX,
                MailboxStrictWriteIntentFence.SUFFIX
            };
            FilePlayerRepository.RestartRecoveryEvidence.State[] states={
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .DURABLE_REVIEW_MARKER,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .UNCERTAIN_COMMIT_MARKER,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .STRANDED_WRITE_INTENT_MARKER
            };
            for(int i=0;i<suffixes.length;i++){
                String name="g2179-marker-"+i;
                Seed marked=seed(world,name);
                strict.saveStrict(marked.plan.preparedPreimage);
                Path marker=paths.resolve(name).resolveSibling(
                    name+".properties"+suffixes[i]);
                byte[] body=("NO_GRANT_MARKER_"+i).getBytes(
                    StandardCharsets.US_ASCII);
                Files.write(marker,body);
                markerCorrect&=assertDecision(name,
                    repo.inspectRestartRecoveryReadOnly(name),states[i],
                    MailboxSettlementCutoverReadiness.Disposition
                        .REVIEW_MARKER_MANUAL_HOLD,seen);
                markerCorrect&=Arrays.equals(body,
                    Files.readAllBytes(marker));
            }

            try{
                MailboxSettlementCutoverReadiness.assess(
                    "g2179-foreign",end);
            }catch(IllegalArgumentException rejected){
                foreignRejected=true;
            }

            // Every G21.71 state has one explicit, fail-closed G21.79
            // policy. A newly added recovery state must update the matrix.
            for(FilePlayerRepository.RestartRecoveryEvidence.State state:
                FilePlayerRepository.RestartRecoveryEvidence.State.values())
                if(!seen.contains(state))noPositiveAuthority=false;

            liveInventoryUnchanged=
                terminal.owner.bank().inventorySlots()==0&&
                terminal.owner.mailbox().get(terminal.plan.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> pathsOnDisk=Files.walk(dir)){
                noTempOrLeaseLeaks=pathsOnDisk.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> pathsOnDisk=Files.walk(dir)){
                for(Path path:pathsOnDisk.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("G2179_CUTOVER_DIAGNOSTICS"+
            " statesCovered="+seen.size()+
            " preparedCorrect="+preparedCorrect+
            " terminalCorrect="+terminalCorrect+
            " invalidCorrect="+invalidCorrect+
            " legacyCorrect="+legacyCorrect+
            " missingCorrect="+missingCorrect+
            " markerCorrect="+markerCorrect+
            " terminalStillQuarantined="+terminalStillQuarantined+
            " preparedStillNonGrant="+preparedStillNonGrant+
            " foreignRejected="+foreignRejected+
            " noPositiveAuthority="+noPositiveAuthority+
            " liveInventoryUnchanged="+liveInventoryUnchanged+
            " noForensicWrites="+noForensicWrites+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(seen.size()==FilePlayerRepository
                 .RestartRecoveryEvidence.State.values().length&&
             preparedCorrect&&terminalCorrect&&invalidCorrect&&
             legacyCorrect&&missingCorrect&&markerCorrect&&
             terminalStillQuarantined&&preparedStillNonGrant&&
             foreignRejected&&noPositiveAuthority&&
             liveInventoryUnchanged&&noForensicWrites&&
             noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.79 terminal cutover contract regression failed");
        System.out.println("G2179_CUTOVER_CONTRACT_PASS"+
            " positiveCommit=false liveApply=false replay=false"+
            " release=false ack=false");
    }

    private static boolean assertDecision(
        String account,
        FilePlayerRepository.RestartRecoveryEvidence evidence,
        FilePlayerRepository.RestartRecoveryEvidence.State state,
        MailboxSettlementCutoverReadiness.Disposition expected,
        EnumSet<FilePlayerRepository.RestartRecoveryEvidence.State> seen
    ){
        seen.add(evidence.state);
        MailboxSettlementCutoverReadiness.Decision result=
            MailboxSettlementCutoverReadiness.assess(account,evidence);
        return result.account.equals(account)&&
            result.observed==state&&result.disposition==expected&&
            result.missingProofs.size()==4&&
            result.missingProofs.containsAll(EnumSet.allOf(
                MailboxSettlementCutoverReadiness.MissingProof.class))&&
            !result.snapshotAdmitted&&!result.durabilityConfirmed&&
            !result.transactionCommitted&&!result.liveApplied&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.rollbackAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized;
    }

    private static Seed seed(World world,String account){
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        String messageId=account+":gift";
        owner.mailbox().deliver(new RewardDeliveryMessage(
            messageId,"G21.79 test Mailbox","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2179_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=
            owner.mailbox().get(messageId);
        MailboxPreparedClaimJournal.stageOnly(owner,
            MailboxPreparedClaimJournal.prepare(owner,row));
        return new Seed(owner,MailboxSettlementPostimagePlanner.plan(
            owner,generation,row));
    }
}
