package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.75: same-inode write-and-restore with identical bytes and mtime.
 * Optional POSIX ctime rejects that change within ONE forensic capture.
 */
public final class G2175MailboxRestartInPlaceChangeStampIntegrationTest {
    private static final class Mutation {
        final boolean identicalBytes;
        final boolean identicalBasicIdentity;
        final boolean ctimeAvailable;
        final boolean ctimeAdvanced;
        Mutation(boolean bytes,boolean identity,boolean available,
                 boolean changed){
            identicalBytes=bytes;identicalBasicIdentity=identity;
            ctimeAvailable=available;ctimeAdvanced=changed;
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2175-inplace-recovery-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean terminalStableAcrossInstances=false;
        boolean terminalMutationInjected=false;
        boolean terminalSameInodeBytesAndMtime=false;
        boolean terminalCtimeAdvancedIfSupported=false;
        boolean terminalMutationRejectedIfSupported=false;
        boolean terminalStableAfterMutation=false;
        boolean markerStableAcrossInstances=false;
        boolean markerMutationInjected=false;
        boolean markerSameInodeBytesAndMtime=false;
        boolean markerCtimeAdvancedIfSupported=false;
        boolean markerMutationRejectedIfSupported=false;
        boolean markerStableAfterMutation=false;
        boolean absentAccountStable=false;
        boolean independentAccountUnaffected=false;
        boolean restartTerminalQuarantined=false;
        boolean restartMarkerQuarantined=false;
        boolean liveNeverGranted=false;
        boolean noTempOrLeaseLeaks=false;
        boolean posixCtimeAccountAvailable=false;
        boolean posixCtimeMarkerAvailable=false;

        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,repo)){
            restart.start();
            String terminalAccount="g2175-terminal";
            WorldPlayer owner=new WorldPlayer();
            long generation=fixture.registerPlayer(owner,terminalAccount);
            String message=terminalAccount+":gift";
            owner.mailbox().deliver(new RewardDeliveryMessage(
                message,"Restored byte mutation","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2175_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot selected=
                owner.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(
                owner,MailboxPreparedClaimJournal.prepare(owner,selected));
            MailboxSettlementPostimagePlanner.Proposal plan=
                MailboxSettlementPostimagePlanner.plan(
                    owner,generation,selected);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot.compose(plan);
            strict.saveStrict(terminal);
            Path accountFile=paths.resolve(terminalAccount);
            byte[] terminalOriginal=Files.readAllBytes(accountFile);
            String terminalToken=
                repo.captureRestartContinuityTokenReadOnly(terminalAccount);
            terminalStableAcrossInstances=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        terminalAccount,terminalToken));

            AtomicBoolean terminalInjected=new AtomicBoolean();
            Mutation[] terminalMutation={null};
            FilePlayerRepository terminalRace=new FilePlayerRepository(
                paths,a->{},a->{},a->{},account->{
                    if(terminalInjected.compareAndSet(false,true))
                        terminalMutation[0]=mutateRestore(accountFile);
                });
            boolean terminalRejected=false;
            try{
                terminalRace.captureRestartContinuityTokenReadOnly(
                    terminalAccount);
            }catch(IOException veto){
                terminalRejected=veto.getMessage().contains(
                    "G21.75 RECOVERY_INPLACE_CHANGE_NO_GRANT");
            }
            terminalMutationInjected=terminalInjected.get();
            Mutation tm=terminalMutation[0];
            posixCtimeAccountAvailable=tm!=null&&tm.ctimeAvailable;
            terminalSameInodeBytesAndMtime=tm!=null&&
                tm.identicalBytes&&tm.identicalBasicIdentity&&
                Arrays.equals(terminalOriginal,
                    Files.readAllBytes(accountFile));
            terminalCtimeAdvancedIfSupported=tm!=null&&
                (!tm.ctimeAvailable||tm.ctimeAdvanced);
            terminalMutationRejectedIfSupported=tm!=null&&
                (tm.ctimeAvailable?terminalRejected:!terminalRejected);
            terminalStableAfterMutation=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        terminalAccount,terminalToken));

            String reviewAccount="g2175-review";
            WorldPlayer other=new WorldPlayer();
            other.markRegistered(reviewAccount);
            PlayerSnapshot safe=PlayerSnapshotCodec.capture(
                reviewAccount,other);
            strict.saveStrict(safe);
            Path reviewFile=paths.resolve(reviewAccount);
            MailboxAccountPublicationCoordinator
                .withExclusivePublication(reviewFile,()->{
                    new MailboxStrictWriteIntentFence(paths)
                        .armInsidePublicationLock(
                            reviewAccount,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(safe));
                    return null;
                });
            Path marker=new MailboxStrictWriteIntentFence(paths)
                .fencePath(reviewAccount);
            byte[] markerOriginal=Files.readAllBytes(marker);
            String markerToken=repo.captureRestartContinuityTokenReadOnly(
                reviewAccount);
            markerStableAcrossInstances=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        reviewAccount,markerToken));
            AtomicBoolean markerInjected=new AtomicBoolean();
            Mutation[] markerMutation={null};
            FilePlayerRepository markerRace=new FilePlayerRepository(
                paths,a->{},a->{},a->{},account->{
                    if(markerInjected.compareAndSet(false,true))
                        markerMutation[0]=mutateRestore(marker);
                });
            boolean markerRejected=false;
            try{
                markerRace.captureRestartContinuityTokenReadOnly(
                    reviewAccount);
            }catch(IOException veto){
                markerRejected=veto.getMessage().contains(
                    "G21.75 RECOVERY_INPLACE_CHANGE_NO_GRANT");
            }
            markerMutationInjected=markerInjected.get();
            Mutation mm=markerMutation[0];
            posixCtimeMarkerAvailable=mm!=null&&mm.ctimeAvailable;
            markerSameInodeBytesAndMtime=mm!=null&&
                mm.identicalBytes&&mm.identicalBasicIdentity&&
                Arrays.equals(markerOriginal,Files.readAllBytes(marker));
            markerCtimeAdvancedIfSupported=mm!=null&&
                (!mm.ctimeAvailable||mm.ctimeAdvanced);
            markerMutationRejectedIfSupported=mm!=null&&
                (mm.ctimeAvailable?markerRejected:!markerRejected);
            markerStableAfterMutation=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        reviewAccount,markerToken));

            String missing="g2175-absent";
            String absence=repo.captureRestartContinuityTokenReadOnly(missing);
            absentAccountStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(missing,absence));
            String independent="g2175-independent";
            WorldPlayer detached=new WorldPlayer();
            detached.markRegistered(independent);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                independent,detached));
            String independentToken=repo.captureRestartContinuityTokenReadOnly(
                independent);
            independentAccountUnaffected=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        independent,independentToken));

            try{
                restart.persistence().load(terminalAccount);
            }catch(IOException denied){
                restartTerminalQuarantined=denied.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }
            try{
                restart.persistence().load(reviewAccount);
            }catch(IOException denied){
                restartMarkerQuarantined=denied.getMessage().contains(
                    "MAILBOX_DURABLE_REVIEW_FENCE");
            }
            liveNeverGranted=owner.bank().inventorySlots()==0&&
                owner.mailbox().get(message).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> disk=Files.walk(root)){
                noTempOrLeaseLeaks=disk.noneMatch(p->
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
        System.out.println("G2175_INPLACE_CHANGE_DIAGNOSTICS"+
            " terminalStableAcrossInstances="+terminalStableAcrossInstances+
            " terminalMutationInjected="+terminalMutationInjected+
            " terminalSameInodeBytesAndMtime="+
                terminalSameInodeBytesAndMtime+
            " terminalCtimeAdvancedIfSupported="+
                terminalCtimeAdvancedIfSupported+
            " terminalMutationRejectedIfSupported="+
                terminalMutationRejectedIfSupported+
            " terminalStableAfterMutation="+terminalStableAfterMutation+
            " markerStableAcrossInstances="+markerStableAcrossInstances+
            " markerMutationInjected="+markerMutationInjected+
            " markerSameInodeBytesAndMtime="+
                markerSameInodeBytesAndMtime+
            " markerCtimeAdvancedIfSupported="+
                markerCtimeAdvancedIfSupported+
            " markerMutationRejectedIfSupported="+
                markerMutationRejectedIfSupported+
            " markerStableAfterMutation="+markerStableAfterMutation+
            " absentAccountStable="+absentAccountStable+
            " independentAccountUnaffected="+
                independentAccountUnaffected+
            " restartTerminalQuarantined="+restartTerminalQuarantined+
            " restartMarkerQuarantined="+restartMarkerQuarantined+
            " liveNeverGranted="+liveNeverGranted+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks+
            " posixCtimeAccountAvailable="+posixCtimeAccountAvailable+
            " posixCtimeMarkerAvailable="+posixCtimeMarkerAvailable);
        if(!(terminalStableAcrossInstances&&terminalMutationInjected&&
             terminalSameInodeBytesAndMtime&&
             terminalCtimeAdvancedIfSupported&&
             terminalMutationRejectedIfSupported&&
             terminalStableAfterMutation&&markerStableAcrossInstances&&
             markerMutationInjected&&markerSameInodeBytesAndMtime&&
             markerCtimeAdvancedIfSupported&&
             markerMutationRejectedIfSupported&&markerStableAfterMutation&&
             absentAccountStable&&independentAccountUnaffected&&
             restartTerminalQuarantined&&restartMarkerQuarantined&&
             liveNeverGranted&&noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.75 POSIX change-time in-place recovery veto NO_GRANT");
        System.out.println("G2175_INPLACE_CHANGE_PASS"+
            " ctimeAvailableAccount="+posixCtimeAccountAvailable+
            " ctimeAvailableMarker="+posixCtimeMarkerAvailable+
            " inPlaceWithRestoredContentRejectedWhenSupported=true"+
            " grant=false replay=false admission=false release=false");
    }

    private static Mutation mutateRestore(Path file)throws IOException{
        BasicFileAttributes first=attrs(file);
        FileTime originalTime=first.lastModifiedTime();
        FileTime originalChange=changeTime(file);
        byte[] original=Files.readAllBytes(file);
        // Distinguish ctime at coarse-resolution local filesystems.
        try{
            Thread.sleep(1100L);
        }catch(InterruptedException interruption){
            Thread.currentThread().interrupt();
            throw new IOException(
                "G21.75 interrupted mutation fixture",interruption);
        }
        byte[] altered=original.clone();
        if(altered.length==0)
            throw new IOException("G21.75 empty fixture file");
        altered[0]=(byte)(altered[0]=='X'?'Y':'X');
        Files.write(file,altered,StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        Files.write(file,original,StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        Files.setLastModifiedTime(file,originalTime);
        BasicFileAttributes last=attrs(file);
        FileTime afterChange=changeTime(file);
        return new Mutation(
            Arrays.equals(original,Files.readAllBytes(file)),
            java.util.Objects.equals(first.fileKey(),last.fileKey())&&
            first.size()==last.size()&&
            java.util.Objects.equals(first.lastModifiedTime(),
                last.lastModifiedTime())&&
            java.util.Objects.equals(first.creationTime(),
                last.creationTime()),
            originalChange!=null,
            originalChange!=null&&!originalChange.equals(afterChange)
        );
    }
    private static BasicFileAttributes attrs(Path path)throws IOException{
        return Files.readAttributes(path,BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
    }
    private static FileTime changeTime(Path path)throws IOException{
        try{
            return (FileTime)Files.getAttribute(path,"unix:ctime",
                LinkOption.NOFOLLOW_LINKS);
        }catch(UnsupportedOperationException|
                IllegalArgumentException unsupported){return null;}
    }
    private static boolean unchanged(
        FilePlayerRepository.RestartContinuityComparison result
    ){
        return result.state==
            FilePlayerRepository.RestartContinuityComparison.State
                .UNCHANGED_FORENSICS_NO_GRANT&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.releaseAuthorized&&!result.restartAdmissionAuthorized;
    }
    private G2175MailboxRestartInPlaceChangeStampIntegrationTest(){}
}
