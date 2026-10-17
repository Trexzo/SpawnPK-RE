package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.72: exact account+negative marker bytes across fresh instances,
 * without restoring the in-memory reservation or enabling claim replay.
 */
public final class G2172MailboxRestartContinuityWitnessIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final MailboxSettlementPostimagePlanner.Proposal plan;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer owner,MailboxSettlementPostimagePlanner.Proposal plan){
            this.owner=owner;
            this.plan=plan;
            terminal=MailboxAtomicTerminalSnapshot.compose(plan);
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2172-continuity-");
        Path original=Files.createDirectories(root.resolve("primary"));
        Path relocated=Files.createDirectories(root.resolve("relocated"));
        FilePlayerRepository.PathResolver paths=
            a->original.resolve(a+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        boolean absentStable=false;
        boolean absentToLegacyChanged=false;
        boolean legacyStableOnFreshInstance=false;
        boolean terminalStableOnFreshInstance=false;
        boolean terminalClassificationStillQuarantined=false;
        boolean sameClassAccountByteChangeDetected=false;
        boolean restoredMtimeCannotHideRewrite=false;
        boolean newPostrewriteWitnessStable=false;
        boolean unrelatedAccountCannotAffectWitness=false;
        boolean reviewBodyRewriteDetected=false;
        boolean unchangedMarkerPresenceStillQuarantine=false;
        boolean newMarkerWitnessStable=false;
        boolean durableReviewPrecedenceChanged=false;
        boolean symlinkMarkerRefused=false;
        boolean relocatedIdenticalBytesDenied=false;
        boolean malformedTokenRejected=false;
        boolean foreignAccountTokenRejected=false;
        boolean repeatedReadNeverWritesFiles=false;
        boolean allComparisonResultsNoGrant=true;
        boolean actualRestartStillDenied=false;
        boolean livePlayerNeverCredited=false;
        boolean noTempOrLeaseLeaks=false;

        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,repo)){
            restart.start();
            String absent="g2172-missing";
            String missingToken=repo.captureRestartContinuityTokenReadOnly(absent);
            FilePlayerRepository fresh=new FilePlayerRepository(paths);
            absentStable=unchanged(
                fresh.compareRestartContinuityReadOnly(absent,missingToken));

            WorldPlayer oldOwner=new WorldPlayer();
            oldOwner.markRegistered(absent);
            writer.saveStrict(PlayerSnapshotCodec.capture(absent,oldOwner));
            absentToLegacyChanged=changed(
                fresh.compareRestartContinuityReadOnly(absent,missingToken));
            String legacyToken=fresh.captureRestartContinuityTokenReadOnly(absent);
            legacyStableOnFreshInstance=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(absent,legacyToken));

            Seed terminal=seed(fixture,"g2172-terminal",25);
            writer.saveStrict(terminal.terminal);
            String account=terminal.plan.account;
            Path accountFile=paths.resolve(account);
            byte[] firstBytes=Files.readAllBytes(accountFile);
            String terminalToken=repo.captureRestartContinuityTokenReadOnly(
                account);
            FilePlayerRepository fromRestart=new FilePlayerRepository(paths);
            terminalStableOnFreshInstance=unchanged(
                fromRestart.compareRestartContinuityReadOnly(
                    account,terminalToken));
            terminalClassificationStillQuarantined=
                repo.inspectRestartRecoveryReadOnly(account).state==
                    FilePlayerRepository.RestartRecoveryEvidence.State
                        .COHERENT_TERMINAL_QUARANTINE;
            try{
                restart.persistence().load(account);
            }catch(IOException denied){
                actualRestartStillDenied=denied.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }
            repeatedReadNeverWritesFiles=Arrays.equals(
                firstBytes,Files.readAllBytes(accountFile));

            // Raw uncooperative same-length overwrite with restored
            // original file timestamp: all semantic terminal fields
            // remain unchanged, but exact bytes must differ.
            FileTime stamp=Files.getLastModifiedTime(accountFile,
                LinkOption.NOFOLLOW_LINKS);
            byte[] edited=firstBytes.clone();
            rewriteSavedAtOneByte(edited);
            Files.write(accountFile,edited);
            Files.setLastModifiedTime(accountFile,stamp);
            sameClassAccountByteChangeDetected=changed(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        account,terminalToken))&&
                repo.inspectRestartRecoveryReadOnly(account).state==
                    FilePlayerRepository.RestartRecoveryEvidence.State
                        .COHERENT_TERMINAL_QUARANTINE;
            restoredMtimeCannotHideRewrite=
                Files.getLastModifiedTime(accountFile,
                    LinkOption.NOFOLLOW_LINKS).equals(stamp)&&
                edited.length==firstBytes.length;
            String currentToken=
                new FilePlayerRepository(paths)
                    .captureRestartContinuityTokenReadOnly(account);
            newPostrewriteWitnessStable=unchanged(
                repo.compareRestartContinuityReadOnly(
                    account,currentToken));
            Seed other=seed(fixture,"g2172-independent",5);
            writer.saveStrict(other.plan.preparedPreimage);
            unrelatedAccountCannotAffectWitness=unchanged(
                repo.compareRestartContinuityReadOnly(
                    account,currentToken));

            Seed review=seed(fixture,"g2172-review",25);
            writer.saveStrict(review.plan.preparedPreimage);
            Path reviewFile=paths.resolve(review.plan.account);
            MailboxAccountPublicationCoordinator
                .withExclusivePublication(reviewFile,()->{
                    new MailboxStrictWriteIntentFence(paths)
                        .armInsidePublicationLock(
                            review.plan.account,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(
                                    review.plan.preparedPreimage));
                    return null;
                });
            String intentToken=
                fresh.captureRestartContinuityTokenReadOnly(
                    review.plan.account);
            Path intentSidecar=
                new MailboxStrictWriteIntentFence(paths)
                    .fencePath(review.plan.account);
            byte[] intentBefore=Files.readAllBytes(intentSidecar);
            FileTime intentStamp=Files.getLastModifiedTime(intentSidecar);
            byte[] tampered=intentBefore.clone();
            int lastLine=indexOf(tampered,
                "IN_PROGRESS_NO_GRANT".getBytes(StandardCharsets.US_ASCII));
            if(lastLine<0)throw new AssertionError(
                "G21.72 fixture intent body missing");
            tampered[lastLine]=(byte)'X';
            Files.write(intentSidecar,tampered);
            Files.setLastModifiedTime(intentSidecar,intentStamp);
            reviewBodyRewriteDetected=changed(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        review.plan.account,intentToken));
            unchangedMarkerPresenceStillQuarantine=
                repo.inspectRestartRecoveryReadOnly(
                    review.plan.account).state==
                    FilePlayerRepository.RestartRecoveryEvidence.State
                        .STRANDED_WRITE_INTENT_MARKER&&
                intentBefore.length==tampered.length&&
                Files.getLastModifiedTime(intentSidecar)
                    .equals(intentStamp);
            String alteredMarkerToken=repo
                .captureRestartContinuityTokenReadOnly(
                    review.plan.account);
            newMarkerWitnessStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        review.plan.account,alteredMarkerToken));
            new MailboxDurableReviewFence(paths).arm(review.plan);
            durableReviewPrecedenceChanged=changed(
                repo.compareRestartContinuityReadOnly(
                    review.plan.account,alteredMarkerToken))&&
                repo.inspectRestartRecoveryReadOnly(
                    review.plan.account).state==
                    FilePlayerRepository.RestartRecoveryEvidence.State
                        .DURABLE_REVIEW_MARKER;

            Seed link=seed(fixture,"g2172-symlink",1);
            writer.saveStrict(link.plan.preparedPreimage);
            Path maliciousMarker=new MailboxStrictWriteIntentFence(paths)
                .fencePath(link.plan.account);
            try{
                Files.createSymbolicLink(maliciousMarker,
                    intentSidecar);
                try{
                    repo.captureRestartContinuityTokenReadOnly(
                        link.plan.account);
                }catch(IOException denied){
                    symlinkMarkerRefused=denied.getMessage().contains(
                        "MAILBOX_SESSION_ACCOUNT_NONREGULAR");
                }
            }catch(UnsupportedOperationException|
                    java.nio.file.FileSystemException unsupported){
                symlinkMarkerRefused=!Files.exists(maliciousMarker,
                    LinkOption.NOFOLLOW_LINKS);
            }

            FilePlayerRepository relocatedRepo=new FilePlayerRepository(
                a->relocated.resolve(a+".properties"));
            Files.copy(accountFile,
                relocated.resolve(account+".properties"));
            relocatedIdenticalBytesDenied=changed(
                relocatedRepo.compareRestartContinuityReadOnly(
                    account,currentToken));
            malformedTokenRejected=rejects(()->{
                try{
                    repo.compareRestartContinuityReadOnly(
                        account,"G2172|"+account+"|WRONG_STATE|abc");
                }catch(IOException checked){
                    throw new IllegalStateException(checked);
                }
            });
            foreignAccountTokenRejected=rejects(()->{
                try{
                    repo.compareRestartContinuityReadOnly(
                        "g2172-other",currentToken);
                }catch(IOException checked){
                    throw new IllegalStateException(checked);
                }
            });

            for(FilePlayerRepository.RestartContinuityComparison value:
                Arrays.asList(
                    fresh.compareRestartContinuityReadOnly(
                        absent,legacyToken),
                    repo.compareRestartContinuityReadOnly(
                        account,currentToken),
                    relocatedRepo.compareRestartContinuityReadOnly(
                        account,currentToken))){
                if(value.restartAdmissionAuthorized||
                   value.transactionCommitted||value.grantAuthorized||
                   value.replayAuthorized||value.releaseAuthorized||
                   value.clientAckAuthorized)
                    allComparisonResultsNoGrant=false;
            }
            livePlayerNeverCredited=
                terminal.owner.bank().inventorySlots()==0&&
                terminal.owner.mailbox().get(
                    terminal.plan.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                review.owner.bank().inventorySlots()==0&&
                review.owner.mailbox().get(
                    review.plan.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> pathsOnDisk=Files.walk(root)){
                noTempOrLeaseLeaks=pathsOnDisk.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2172_RESTART_CONTINUITY_DIAGNOSTICS"+
            " absentStable="+absentStable+
            " absentToLegacyChanged="+absentToLegacyChanged+
            " legacyStableOnFreshInstance="+legacyStableOnFreshInstance+
            " terminalStableOnFreshInstance="+terminalStableOnFreshInstance+
            " terminalClassificationStillQuarantined="+
                terminalClassificationStillQuarantined+
            " sameClassAccountByteChangeDetected="+
                sameClassAccountByteChangeDetected+
            " restoredMtimeCannotHideRewrite="+
                restoredMtimeCannotHideRewrite+
            " newPostrewriteWitnessStable="+newPostrewriteWitnessStable+
            " unrelatedAccountCannotAffectWitness="+
                unrelatedAccountCannotAffectWitness+
            " reviewBodyRewriteDetected="+reviewBodyRewriteDetected+
            " unchangedMarkerPresenceStillQuarantine="+
                unchangedMarkerPresenceStillQuarantine+
            " newMarkerWitnessStable="+newMarkerWitnessStable+
            " durableReviewPrecedenceChanged="+
                durableReviewPrecedenceChanged+
            " symlinkMarkerRefused="+symlinkMarkerRefused+
            " relocatedIdenticalBytesDenied="+
                relocatedIdenticalBytesDenied+
            " malformedTokenRejected="+malformedTokenRejected+
            " foreignAccountTokenRejected="+foreignAccountTokenRejected+
            " repeatedReadNeverWritesFiles="+
                repeatedReadNeverWritesFiles+
            " allComparisonResultsNoGrant="+allComparisonResultsNoGrant+
            " actualRestartStillDenied="+actualRestartStillDenied+
            " livePlayerNeverCredited="+livePlayerNeverCredited+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(absentStable&&absentToLegacyChanged&&
             legacyStableOnFreshInstance&&
             terminalStableOnFreshInstance&&
             terminalClassificationStillQuarantined&&
             sameClassAccountByteChangeDetected&&
             restoredMtimeCannotHideRewrite&&newPostrewriteWitnessStable&&
             unrelatedAccountCannotAffectWitness&&
             reviewBodyRewriteDetected&&
             unchangedMarkerPresenceStillQuarantine&&
             newMarkerWitnessStable&&durableReviewPrecedenceChanged&&
             symlinkMarkerRefused&&relocatedIdenticalBytesDenied&&
             malformedTokenRejected&&foreignAccountTokenRejected&&
             repeatedReadNeverWritesFiles&&allComparisonResultsNoGrant&&
             actualRestartStillDenied&&livePlayerNeverCredited&&
             noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.72 portable restart witness fail-closed NO_GRANT");
        System.out.println("G2172_RESTART_CONTINUITY_PASS"+
            " freshProcess=true exactFiles=true grant=false"+
            " replay=false admission=false release=false");
    }

    private static void rewriteSavedAtOneByte(byte[] content){
        byte[] search="saved.at=".getBytes(StandardCharsets.US_ASCII);
        int at=indexOf(content,search);
        if(at<0||at+search.length>=content.length)
            throw new AssertionError("G21.72 no saved.at field");
        int pos=at+search.length;
        content[pos]=(byte)(content[pos]=='2'?'3':'2');
    }

    private static int indexOf(byte[] haystack,byte[] needle){
        for(int i=0;i<=haystack.length-needle.length;i++){
            int j=0;
            while(j<needle.length&&haystack[i+j]==needle[j])j++;
            if(j==needle.length)return i;
        }
        return -1;
    }

    private static boolean unchanged(
        FilePlayerRepository.RestartContinuityComparison comparison
    ){
        return comparison.state==
            FilePlayerRepository.RestartContinuityComparison.State
                .UNCHANGED_FORENSICS_NO_GRANT&&
            !comparison.grantAuthorized&&!comparison.replayAuthorized;
    }
    private static boolean changed(
        FilePlayerRepository.RestartContinuityComparison comparison
    ){
        return comparison.state==
            FilePlayerRepository.RestartContinuityComparison.State
                .CHANGED_FORENSICS_QUARANTINE&&
            !comparison.grantAuthorized&&!comparison.replayAuthorized;
    }
    private static boolean rejects(Runnable action){
        try{action.run();return false;}
        catch(IllegalArgumentException|IllegalStateException expected){
            return true;
        }
    }
    private static Seed seed(World world,String account,int amount){
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        String msg=account+":gift";
        owner.mailbox().deliver(new RewardDeliveryMessage(
            msg,"Recovery continuation witness","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,amount)),
            "CUSTOM_LOCALLAB_G2172_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            owner.mailbox().get(msg);
        MailboxPreparedClaimJournal.stageOnly(owner,
            MailboxPreparedClaimJournal.prepare(owner,selected));
        return new Seed(owner,MailboxSettlementPostimagePlanner.plan(
            owner,generation,selected));
    }
    private G2172MailboxRestartContinuityWitnessIntegrationTest(){}
}
