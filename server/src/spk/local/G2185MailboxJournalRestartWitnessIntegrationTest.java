package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Real file-backed G21.85 six-object forensic continuity matrix. */
public final class G2185MailboxJournalRestartWitnessIntegrationTest {
    private static final class Seed {
        final WorldPlayer player;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer player,MailboxSettlementPostimagePlanner.Proposal p){
            this.player=player;proposal=p;
            terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }
    }

    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("g2185-restart-witness-");
        FilePlayerRepository.PathResolver paths=
            name->folder.resolve(name+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        boolean missingUnchanged=false,legacyUnchanged=false;
        boolean oldTokenVersionRefused=false;
        boolean preparedWithoutJournalUnchanged=false;
        boolean newJournalPresenceDetected=false;
        boolean stableAcrossInstances=false;
        boolean journalBytesUnchanged=false;
        boolean terminalTransitionDetected=false;
        boolean terminalStillQuarantined=false;
        boolean journalTamperChangedWitness=false;
        boolean tamperedJournalSessionVeto=false;
        boolean journalDeletionChangesWitness=false;
        boolean oversizedJournalRejected=false;
        boolean oversizedJournalNeverParsed=false;
        boolean symlinkJournalRefused=false;
        boolean negativeMarkerPrecedence=false;
        boolean midWitnessSameBytesObjectReplacementRefused=false;
        boolean afterReplacementWitnessStable=false;
        boolean noLiveGrant=true,noTempOrLeaseLeaks=false;
        int cases=0;
        try(World fixture=World.isolatedForTest(60000L);
            World restarted=World.isolatedForTest(60000L,repo)){
            restarted.start();
            String absent="g2185-absent";
            String absentToken=repo.captureRestartContinuityTokenReadOnly(absent);
            missingUnchanged=absentToken.startsWith("G2185|")&&
                unchanged(new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(absent,absentToken));
            cases++;
            try{
                repo.compareRestartContinuityReadOnly(absent,
                    "G2172|"+absent+"|MISSING_ACCOUNT_NO_REPLAY|"+
                    absentToken.substring(absentToken.lastIndexOf('|')+1));
            }catch(IllegalArgumentException incompatible){
                oldTokenVersionRefused=true;
            }

            String legacy="g2185-legacy";
            WorldPlayer old=new WorldPlayer();
            old.markRegistered(legacy);
            writer.saveStrict(PlayerSnapshotCodec.capture(legacy,old));
            String legacyToken=repo.captureRestartContinuityTokenReadOnly(legacy);
            legacyUnchanged=unchanged(new FilePlayerRepository(paths)
                .compareRestartContinuityReadOnly(legacy,legacyToken));
            cases++;

            String account="g2185-primary";
            Seed main=seed(fixture,account);
            writer.saveStrict(main.proposal.preparedPreimage);
            String beforeJournal=repo.captureRestartContinuityTokenReadOnly(
                account);
            preparedWithoutJournalUnchanged=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(account,beforeJournal));
            journal.publishPreparedIntent(main.proposal);
            byte[] firstBytes=Files.readAllBytes(journal.journalPath(account));
            FilePlayerRepository fresh=new FilePlayerRepository(paths);
            FilePlayerRepository.RestartContinuityComparison published=
                fresh.compareRestartContinuityReadOnly(
                    account,beforeJournal);
            newJournalPresenceDetected=changed(published);
            String withJournal=fresh.captureRestartContinuityTokenReadOnly(
                account);
            stableAcrossInstances=unchanged(
                repo.compareRestartContinuityReadOnly(account,withJournal))&&
                withJournal.startsWith("G2185|")&&
                journal.inspect(account).status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .PREPARED_MATCH_NO_REPLAY&&
                restarted.persistence().load(account).isPresent();
            journalBytesUnchanged=Arrays.equals(
                firstBytes,Files.readAllBytes(journal.journalPath(account)));
            cases+=2;

            writer.saveStrict(main.terminal);
            terminalTransitionDetected=changed(
                fresh.compareRestartContinuityReadOnly(
                    account,withJournal));
            String terminalToken=fresh.captureRestartContinuityTokenReadOnly(
                account);
            terminalStillQuarantined=unchanged(
                repo.compareRestartContinuityReadOnly(
                    account,terminalToken))&&
                journal.inspect(account).status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .TERMINAL_MATCH_NO_COMMIT&&
                rejectsSession(restarted,account);
            cases++;

            String editName="g2185-tamper";
            Seed tamper=seed(fixture,editName);
            writer.saveStrict(tamper.proposal.preparedPreimage);
            journal.publishPreparedIntent(tamper.proposal);
            String cleanToken=repo.captureRestartContinuityTokenReadOnly(
                editName);
            Path edit=journal.journalPath(editName);
            byte[] corrupt=Files.readAllBytes(edit);
            corrupt[3]^=1;
            Files.write(edit,corrupt);
            journalTamperChangedWitness=changed(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        editName,cleanToken))&&
                unchanged(repo.compareRestartContinuityReadOnly(
                    editName,repo.captureRestartContinuityTokenReadOnly(
                        editName)))&&
                journal.inspect(editName).status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .INVALID_RECORD_QUARANTINE;
            tamperedJournalSessionVeto=rejectsSession(restarted,editName);
            cases++;

            String removed="g2185-removed";
            Seed delete=seed(fixture,removed);
            writer.saveStrict(delete.proposal.preparedPreimage);
            journal.publishPreparedIntent(delete.proposal);
            String tokenBeforeDelete=
                repo.captureRestartContinuityTokenReadOnly(removed);
            Files.delete(journal.journalPath(removed));
            journalDeletionChangesWitness=changed(
                repo.compareRestartContinuityReadOnly(
                    removed,tokenBeforeDelete))&&
                unchanged(repo.compareRestartContinuityReadOnly(
                    removed,repo.captureRestartContinuityTokenReadOnly(
                        removed)));
            cases++;

            String oversize="g2185-oversize";
            Seed large=seed(fixture,oversize);
            writer.saveStrict(large.proposal.preparedPreimage);
            journal.publishPreparedIntent(large.proposal);
            Files.write(journal.journalPath(oversize),new byte[1025]);
            try{
                repo.captureRestartContinuityTokenReadOnly(oversize);
            }catch(IOException bounded){
                oversizedJournalRejected=bounded.getMessage().contains(
                    "G21.85 RECOVERY_JOURNAL_OVERSIZE_NO_GRANT");
                oversizedJournalNeverParsed=Files.size(
                    journal.journalPath(oversize))==1025L;
            }
            cases++;

            String markerAccount="g2185-negative";
            Seed marker=seed(fixture,markerAccount);
            writer.saveStrict(marker.proposal.preparedPreimage);
            journal.publishPreparedIntent(marker.proposal);
            Path fence=paths.resolve(markerAccount).resolveSibling(
                markerAccount+".properties"+
                MailboxStrictUncertainFence.SUFFIX);
            byte[] fenceBytes="G2185_NEGATIVE_HOLD".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(fence,fenceBytes);
            String markerToken=repo.captureRestartContinuityTokenReadOnly(
                markerAccount);
            negativeMarkerPrecedence=
                unchanged(new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        markerAccount,markerToken))&&
                repo.inspectRestartRecoveryReadOnly(markerAccount).state==
                    FilePlayerRepository.RestartRecoveryEvidence.State
                        .UNCERTAIN_COMMIT_MARKER&&
                journal.inspect(markerAccount).status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .NEGATIVE_MARKER_MANUAL_HOLD&&
                rejectsSession(restarted,markerAccount)&&
                Arrays.equals(fenceBytes,Files.readAllBytes(fence));
            cases++;

            String symlink="g2185-symlink";
            Seed link=seed(fixture,symlink);
            writer.saveStrict(link.proposal.preparedPreimage);
            journal.publishPreparedIntent(link.proposal);
            Path symlinkPath=journal.journalPath(symlink);
            try{
                Files.delete(symlinkPath);
                Files.createSymbolicLink(symlinkPath,
                    journal.journalPath(markerAccount));
                try{
                    repo.captureRestartContinuityTokenReadOnly(symlink);
                }catch(IOException veto){
                    symlinkJournalRefused=true;
                }
            }catch(java.nio.file.FileSystemException|
                    UnsupportedOperationException notSupported){
                symlinkJournalRefused=!Files.isSymbolicLink(symlinkPath);
            }
            cases++;

            String swapName="g2185-samebytes-swap";
            Seed swap=seed(fixture,swapName);
            writer.saveStrict(swap.proposal.preparedPreimage);
            journal.publishPreparedIntent(swap.proposal);
            Path swapFile=journal.journalPath(swapName);
            byte[] original=Files.readAllBytes(swapFile);
            String snapshot=repo.captureRestartContinuityTokenReadOnly(
                swapName);
            AtomicBoolean replaced=new AtomicBoolean();
            FilePlayerRepository adversary=new FilePlayerRepository(
                paths,accountName->{},accountName->{},
                accountName->{},accountName->{
                    if(accountName.equals(swapName)&&
                       replaced.compareAndSet(false,true)){
                        Path temp=Files.createTempFile(folder,
                            "g2185-swap-", ".tmp");
                        Files.write(temp,original);
                        Files.move(temp,swapFile,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    }
                });
            try{
                adversary.captureRestartContinuityTokenReadOnly(swapName);
            }catch(IOException denied){
                midWitnessSameBytesObjectReplacementRefused=
                    denied.getMessage().contains(
                        "RECOVERY_OBJECT_REPLACED_NO_GRANT")||
                    denied.getMessage().contains(
                        "RECOVERY_INPLACE_CHANGE_NO_GRANT");
            }
            afterReplacementWitnessStable=
                replaced.get()&&
                Arrays.equals(original,Files.readAllBytes(swapFile))&&
                unchanged(new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(swapName,snapshot));
            cases++;

            noLiveGrant=main.player.bank().inventorySlots()==0&&
                main.player.mailbox().get(main.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                marker.player.bank().inventorySlots()==0&&
                tamper.player.bank().inventorySlots()==0&&
                swap.player.bank().inventorySlots()==0;
            try(Stream<Path> all=Files.walk(folder)){
                noTempOrLeaseLeaks=all.noneMatch(f->
                    f.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(folder)){
                for(Path f:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }
        System.out.println("G2185_RESTART_WITNESS_DIAGNOSTICS"+
            " missingUnchanged="+missingUnchanged+
            " legacyUnchanged="+legacyUnchanged+
            " oldTokenVersionRefused="+oldTokenVersionRefused+
            " preparedWithoutJournalUnchanged="+
                preparedWithoutJournalUnchanged+
            " newJournalPresenceDetected="+newJournalPresenceDetected+
            " stableAcrossInstances="+stableAcrossInstances+
            " journalBytesUnchanged="+journalBytesUnchanged+
            " terminalTransitionDetected="+terminalTransitionDetected+
            " terminalStillQuarantined="+terminalStillQuarantined+
            " journalTamperChangedWitness="+journalTamperChangedWitness+
            " tamperedJournalSessionVeto="+tamperedJournalSessionVeto+
            " journalDeletionChangesWitness="+
                journalDeletionChangesWitness+
            " oversizedJournalRejected="+oversizedJournalRejected+
            " oversizedJournalNeverParsed="+
                oversizedJournalNeverParsed+
            " symlinkJournalRefused="+symlinkJournalRefused+
            " negativeMarkerPrecedence="+negativeMarkerPrecedence+
            " midWitnessSameBytesObjectReplacementRefused="+
                midWitnessSameBytesObjectReplacementRefused+
            " afterReplacementWitnessStable="+
                afterReplacementWitnessStable+
            " noLiveGrant="+noLiveGrant+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks+
            " cases="+cases);
        if(!(missingUnchanged&&legacyUnchanged&&oldTokenVersionRefused&&
             preparedWithoutJournalUnchanged&&
             newJournalPresenceDetected&&stableAcrossInstances&&
             journalBytesUnchanged&&terminalTransitionDetected&&
             terminalStillQuarantined&&journalTamperChangedWitness&&
             tamperedJournalSessionVeto&&
             journalDeletionChangesWitness&&oversizedJournalRejected&&
             oversizedJournalNeverParsed&&symlinkJournalRefused&&
             negativeMarkerPrecedence&&
             midWitnessSameBytesObjectReplacementRefused&&
             afterReplacementWitnessStable&&noLiveGrant&&
             noTempOrLeaseLeaks&&cases==11))
            throw new AssertionError(
                "G21.85 journal-inclusive restart witness failed");
        System.out.println("G2185_JOURNAL_RESTART_WITNESS_PASS"+
            " sixObjects=true journalCap=1024"+
            " tokenVersion=G2185 journalChangeDetected=true"+
            " commit=false replay=false grant=false admission=false");
    }

    private static boolean rejectsSession(World world,String name){
        try{
            world.persistence().load(name);
            return false;
        }catch(IOException rejected){
            return true;
        }
    }

    private static boolean unchanged(
        FilePlayerRepository.RestartContinuityComparison result
    ){
        return result.state==FilePlayerRepository
                .RestartContinuityComparison.State
                    .UNCHANGED_FORENSICS_NO_GRANT&&
            !result.restartAdmissionAuthorized&&
            !result.transactionCommitted&&!result.grantAuthorized&&
            !result.replayAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized;
    }

    private static boolean changed(
        FilePlayerRepository.RestartContinuityComparison result
    ){
        return result.state==FilePlayerRepository
                .RestartContinuityComparison.State
                    .CHANGED_FORENSICS_QUARANTINE&&
            !result.restartAdmissionAuthorized&&
            !result.transactionCommitted&&!result.grantAuthorized&&
            !result.replayAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized;
    }

    private static Seed seed(World world,String name){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,name);
        String messageId=name+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            messageId,"Restart journal witness","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2185_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            player.mailbox().get(messageId);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,selected));
        return new Seed(player,MailboxSettlementPostimagePlanner.plan(
            player,generation,selected));
    }
}
