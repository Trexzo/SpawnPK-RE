package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.71: pinned, read-only postcrash/restart recovery evidence only. */
public final class G2171MailboxRestartRecoveryInspectorIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer owner,MailboxSettlementPostimagePlanner.Proposal p){
            this.owner=owner;this.proposal=p;
            this.terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }
    }

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2171-restart-recovery-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);
        boolean missingAccountNoReplay=false;
        boolean legacyNotAutoAdmitted=false;
        boolean exactPreparedOnly=false;
        boolean terminalQuarantined=false;
        boolean terminalRestartDenied=false;
        boolean invalidTerminalQuarantined=false;
        boolean invalidPreparedQuarantined=false;
        boolean durableMarkerDominates=false;
        boolean strandedIntentVeto=false;
        boolean uncertainPostMoveVeto=false;
        boolean uncertainRestartDenied=false;
        boolean markerRaceFailsClosed=false;
        boolean sameMetadataByteRewriteDetected=false;
        boolean symlinkNonregularDenied=false;
        boolean repeatedObservationStable=false;
        boolean noForensicWrites=false;
        boolean unrelatedSessionAdmitted=false;
        boolean allResultsNoAuthority=true;
        boolean liveNeverCredited=false;
        boolean noTempOrLeaseLeaks=false;

        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,repo)){
            restart.start();
            FilePlayerRepository.RestartRecoveryEvidence missing=
                repo.inspectRestartRecoveryReadOnly("g2171-missing");
            missingAccountNoReplay=state(missing,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .MISSING_ACCOUNT_NO_REPLAY)&&
                !missing.exactObjectAndBytesObserved;

            WorldPlayer legacy=new WorldPlayer();
            legacy.markRegistered("g2171-legacy");
            strict.saveStrict(PlayerSnapshotCodec.capture(
                "g2171-legacy",legacy));
            FilePlayerRepository.RestartRecoveryEvidence old=
                repo.inspectRestartRecoveryReadOnly("g2171-legacy");
            legacyNotAutoAdmitted=state(old,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .LEGACY_NO_JOURNAL_NON_ADMITTING);
            unrelatedSessionAdmitted=restart.persistence()
                .load("g2171-legacy").isPresent();

            Seed prepared=seed(fixture,"g2171-prepared");
            strict.saveStrict(prepared.proposal.preparedPreimage);
            FilePlayerRepository.RestartRecoveryEvidence pre=
                repo.inspectRestartRecoveryReadOnly(
                    prepared.proposal.account);
            exactPreparedOnly=state(pre,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .PREPARED_UNCLAIMED_NO_REPLAY)&&
                restart.persistence().load(
                    prepared.proposal.account).isPresent();

            Seed terminal=seed(fixture,"g2171-terminal");
            strict.saveStrict(terminal.terminal);
            Path terminalFile=paths.resolve(terminal.proposal.account);
            byte[] terminalBefore=Files.readAllBytes(terminalFile);
            FilePlayerRepository.RestartRecoveryEvidence exact=
                repo.inspectRestartRecoveryReadOnly(
                    terminal.proposal.account);
            terminalQuarantined=state(exact,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .COHERENT_TERMINAL_QUARANTINE)&&
                exact.exactObjectAndBytesObserved;
            try{
                restart.persistence().load(terminal.proposal.account);
            }catch(IOException denied){
                terminalRestartDenied=denied.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }
            FilePlayerRepository reloaded=new FilePlayerRepository(paths);
            repeatedObservationStable=state(
                reloaded.inspectRestartRecoveryReadOnly(
                    terminal.proposal.account),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .COHERENT_TERMINAL_QUARANTINE);
            noForensicWrites=Arrays.equals(
                terminalBefore,Files.readAllBytes(terminalFile));

            Seed malformed=seed(fixture,"g2171-badterminal");
            TreeMap<String,String> bad=new TreeMap<>(
                malformed.terminal.values());
            bad.remove("extension.mailbox-terminal-snapshot.checksum");
            repo.save(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                malformed.proposal.account,bad));
            invalidTerminalQuarantined=state(
                repo.inspectRestartRecoveryReadOnly(
                    malformed.proposal.account),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .INVALID_TERMINAL_QUARANTINE);
            Seed badPrepared=seed(fixture,"g2171-badprepared");
            repo.save(badPrepared.proposal.hypotheticalPostimage);
            invalidPreparedQuarantined=state(
                repo.inspectRestartRecoveryReadOnly(
                    badPrepared.proposal.account),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .INVALID_PREPARED_QUARANTINE);

            Seed durable=seed(fixture,"g2171-review");
            strict.saveStrict(durable.proposal.preparedPreimage);
            new MailboxDurableReviewFence(paths).arm(durable.proposal);
            Path durablePath=paths.resolve(durable.proposal.account);
            // Two simultaneous marker types: durable review dominates.
            MailboxAccountPublicationCoordinator
                .withExclusivePublication(durablePath,()->{
                    new MailboxStrictWriteIntentFence(paths)
                        .armInsidePublicationLock(
                            durable.proposal.account,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(
                                    durable.proposal.preparedPreimage));
                    return null;
                });
            durableMarkerDominates=state(
                repo.inspectRestartRecoveryReadOnly(
                    durable.proposal.account),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .DURABLE_REVIEW_MARKER);

            Seed intent=seed(fixture,"g2171-stranded");
            strict.saveStrict(intent.proposal.preparedPreimage);
            Path intentPath=paths.resolve(intent.proposal.account);
            MailboxAccountPublicationCoordinator
                .withExclusivePublication(intentPath,()->{
                    new MailboxStrictWriteIntentFence(paths)
                        .armInsidePublicationLock(
                            intent.proposal.account,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(
                                    intent.proposal.preparedPreimage));
                    return null;
                });
            strandedIntentVeto=state(
                repo.inspectRestartRecoveryReadOnly(intent.proposal.account),
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .STRANDED_WRITE_INTENT_MARKER);

            Seed uncertain=seed(fixture,"g2171-uncertain");
            strict.saveStrict(uncertain.proposal.preparedPreimage);
            StrictDurablePlayerSnapshotWriter fault=
                new StrictDurablePlayerSnapshotWriter(paths,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_DIRECTORY_FORCE)
                        throw new IOException(
                            "G21.71 injected postmove crash boundary");
                });
            boolean writerUnconfirmed=false;
            try{
                fault.saveStrictTerminalForWorld(
                    uncertain.terminal,
                    paths.resolve(uncertain.proposal.account),
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(
                            uncertain.proposal.preparedPreimage),
                    ()->{},()->{});
            }catch(StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException outcome){
                writerUnconfirmed=true;
            }
            FilePlayerRepository.RestartRecoveryEvidence uncertainReview=
                repo.inspectRestartRecoveryReadOnly(
                    uncertain.proposal.account);
            uncertainPostMoveVeto=writerUnconfirmed&&state(
                uncertainReview,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .UNCERTAIN_COMMIT_MARKER)&&
                new MailboxStrictUncertainFence(paths)
                    .present(uncertain.proposal.account);
            try{
                restart.persistence().load(uncertain.proposal.account);
            }catch(IOException blocked){
                uncertainRestartDenied=blocked.getMessage().contains(
                    "MAILBOX_DURABLE_REVIEW_FENCE");
            }

            Seed markerRace=seed(fixture,"g2171-markerrace");
            strict.saveStrict(markerRace.proposal.preparedPreimage);
            AtomicBoolean injected=new AtomicBoolean();
            FilePlayerRepository markerInjected=
                new FilePlayerRepository(paths,account->{},
                    account->{},account->{
                        if(injected.compareAndSet(false,true)){
                            new MailboxStrictWriteIntentFence(paths)
                                .armInsidePublicationLock(
                                    account,
                                    StrictDurablePlayerSnapshotWriter
                                        .canonicalSnapshotSha256(
                                            markerRace.proposal
                                                .preparedPreimage));
                        }
                    });
            try{
                markerInjected.inspectRestartRecoveryReadOnly(
                    markerRace.proposal.account);
            }catch(IOException changed){
                markerRaceFailsClosed=injected.get()&&
                    changed.getMessage().contains(
                        "RECOVERY_MARKER_OR_PATH_CHANGED_NO_GRANT");
            }

            Seed byteRace=seed(fixture,"g2171-byterace");
            strict.saveStrict(byteRace.proposal.preparedPreimage);
            Path bytePath=paths.resolve(byteRace.proposal.account);
            AtomicBoolean rewritten=new AtomicBoolean();
            FilePlayerRepository byteInjected=
                new FilePlayerRepository(paths,account->{},
                    account->{},account->{
                        if(rewritten.compareAndSet(false,true)){
                            FileTime savedTime=
                                Files.getLastModifiedTime(bytePath,
                                    LinkOption.NOFOLLOW_LINKS);
                            byte[] all=Files.readAllBytes(bytePath);
                            byte[] tag="saved.at=".getBytes(
                                StandardCharsets.US_ASCII);
                            int index=indexOf(all,tag);
                            if(index<0||index+tag.length>=all.length)
                                throw new IOException(
                                    "G21.71 fixture saved.at missing");
                            int at=index+tag.length;
                            all[at]=(byte)(all[at]=='2'?'3':'2');
                            Files.write(bytePath,all);
                            Files.setLastModifiedTime(bytePath,savedTime);
                        }
                    });
            try{
                byteInjected.inspectRestartRecoveryReadOnly(
                    byteRace.proposal.account);
            }catch(IOException changed){
                sameMetadataByteRewriteDetected=rewritten.get()&&
                    changed.getMessage().contains(
                        "RECOVERY_ACCOUNT_BYTES_CHANGED_NO_GRANT");
            }

            Path symlink=paths.resolve("g2171-symlink");
            try{
                Files.createSymbolicLink(symlink,
                    paths.resolve("g2171-legacy").getFileName());
                try{
                    repo.inspectRestartRecoveryReadOnly("g2171-symlink");
                }catch(IOException denied){
                    symlinkNonregularDenied=denied.getMessage().contains(
                        "MAILBOX_SESSION_ACCOUNT_NONREGULAR");
                }
            }catch(UnsupportedOperationException|
                    java.nio.file.FileSystemException unsupported){
                // Filesystem lacks symbolic links; never claim a
                // symlink rejection was tested on that platform.
                symlinkNonregularDenied=!Files.exists(symlink,
                    LinkOption.NOFOLLOW_LINKS);
            }

            for(FilePlayerRepository.RestartRecoveryEvidence proof:
                    Arrays.asList(missing,old,pre,exact,uncertainReview)){
                if(proof.restartAdmissionAuthorized||
                   proof.durabilityConfirmed||
                   proof.transactionCommitted||proof.liveApplied||
                   proof.grantAuthorized||proof.replayAuthorized||
                   proof.rollbackAuthorized||proof.releaseAuthorized||
                   proof.clientAckAuthorized)
                    allResultsNoAuthority=false;
            }
            liveNeverCredited=terminal.owner.bank().inventorySlots()==0&&
                terminal.owner.mailbox().get(
                    terminal.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                uncertain.owner.bank().inventorySlots()==0&&
                uncertain.owner.mailbox().get(
                    uncertain.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> files=Files.walk(dir)){
                noTempOrLeaseLeaks=files.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(dir)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2171_RESTART_RECOVERY_DIAGNOSTICS"+
            " missingAccountNoReplay="+missingAccountNoReplay+
            " legacyNotAutoAdmitted="+legacyNotAutoAdmitted+
            " exactPreparedOnly="+exactPreparedOnly+
            " terminalQuarantined="+terminalQuarantined+
            " terminalRestartDenied="+terminalRestartDenied+
            " invalidTerminalQuarantined="+invalidTerminalQuarantined+
            " invalidPreparedQuarantined="+invalidPreparedQuarantined+
            " durableMarkerDominates="+durableMarkerDominates+
            " strandedIntentVeto="+strandedIntentVeto+
            " uncertainPostMoveVeto="+uncertainPostMoveVeto+
            " uncertainRestartDenied="+uncertainRestartDenied+
            " markerRaceFailsClosed="+markerRaceFailsClosed+
            " sameMetadataByteRewriteDetected="+
                sameMetadataByteRewriteDetected+
            " symlinkNonregularDenied="+symlinkNonregularDenied+
            " repeatedObservationStable="+repeatedObservationStable+
            " noForensicWrites="+noForensicWrites+
            " unrelatedSessionAdmitted="+unrelatedSessionAdmitted+
            " allResultsNoAuthority="+allResultsNoAuthority+
            " liveNeverCredited="+liveNeverCredited+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(missingAccountNoReplay&&legacyNotAutoAdmitted&&
             exactPreparedOnly&&terminalQuarantined&&
             terminalRestartDenied&&invalidTerminalQuarantined&&
             invalidPreparedQuarantined&&durableMarkerDominates&&
             strandedIntentVeto&&uncertainPostMoveVeto&&
             uncertainRestartDenied&&markerRaceFailsClosed&&
             sameMetadataByteRewriteDetected&&symlinkNonregularDenied&&
             repeatedObservationStable&&noForensicWrites&&
             unrelatedSessionAdmitted&&allResultsNoAuthority&&
             liveNeverCredited&&noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.71 restart inspection fail-closed NO_GRANT");
        System.out.println("G2171_RESTART_RECOVERY_PASS"+
            " restartAdmission=false grant=false replay=false release=false");
    }

    private static int indexOf(byte[] haystack,byte[] needle){
        for(int i=0;i<=haystack.length-needle.length;i++){
            int j=0;
            while(j<needle.length&&haystack[i+j]==needle[j])j++;
            if(j==needle.length)return i;
        }
        return -1;
    }

    private static boolean state(
        FilePlayerRepository.RestartRecoveryEvidence result,
        FilePlayerRepository.RestartRecoveryEvidence.State expected
    ){
        return result.state==expected&&
            !result.restartAdmissionAuthorized&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.releaseAuthorized;
    }

    private static Seed seed(World world,String account){
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        String message=account+":gift";
        owner.mailbox().deliver(new RewardDeliveryMessage(
            message,"Restart recovery","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2171_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=
            owner.mailbox().get(message);
        MailboxPreparedClaimJournal.stageOnly(owner,
            MailboxPreparedClaimJournal.prepare(owner,row));
        return new Seed(owner,MailboxSettlementPostimagePlanner.plan(
            owner,generation,row));
    }

    private G2171MailboxRestartRecoveryInspectorIntegrationTest(){}
}
