package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.74: byte-identical file replacement *during* a single read-only
 * forensic witness capture must fail on NOFOLLOW filesystem identity.
 */
public final class G2174MailboxRestartIdentityReplacementIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2174-object-identity-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean baselineAcrossInstances=false;
        boolean terminalReplacementInjected=false;
        boolean terminalSameBytesAfterSwap=false;
        boolean terminalObjectChangeDenied=false;
        boolean stableTokenAfterAccountSwap=false;
        boolean preparedReplacementDenied=false;
        boolean markerReplacementInjected=false;
        boolean markerSameBytesAfterSwap=false;
        boolean markerObjectChangeDenied=false;
        boolean stableTokenAfterMarkerSwap=false;
        boolean deleteAndRecreateDenied=false;
        boolean markerStillBlocksRestart=false;
        boolean originalTerminalStillBlocksRestart=false;
        boolean absentStable=false;
        boolean anotherAccountIndependent=false;
        boolean callerCaptureOnlyReads=true;
        boolean noLiveRewardsCredited=false;
        boolean noTmpOrLockLeaks=false;

        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,repository)){
            restart.start();

            String first="g2174-terminal";
            WorldPlayer owner=new WorldPlayer();
            long gen=fixture.registerPlayer(owner,first);
            String id=first+":gift";
            owner.mailbox().deliver(new RewardDeliveryMessage(
                id,"No-grant object identity fixture","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2174_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(id);
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,row));
            MailboxSettlementPostimagePlanner.Proposal plan=
                MailboxSettlementPostimagePlanner.plan(owner,gen,row);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot.compose(plan);
            strict.saveStrict(terminal);
            Path accountPath=paths.resolve(first);
            byte[] exactAccount=Files.readAllBytes(accountPath);
            String before=repository.captureRestartContinuityTokenReadOnly(
                first);
            baselineAcrossInstances=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(first,before));

            AtomicBoolean didAccountSwap=new AtomicBoolean();
            FilePlayerRepository accountRace=new FilePlayerRepository(
                paths,a->{},a->{},a->{},account->{
                    if(didAccountSwap.compareAndSet(false,true))
                        replaceWithSameBytes(accountPath);
                }
            );
            try{
                accountRace.captureRestartContinuityTokenReadOnly(first);
            }catch(IOException veto){
                terminalObjectChangeDenied=veto.getMessage().contains(
                    "G21.74 RECOVERY_OBJECT_REPLACED_NO_GRANT");
            }
            terminalReplacementInjected=didAccountSwap.get();
            terminalSameBytesAfterSwap=Arrays.equals(exactAccount,
                Files.readAllBytes(accountPath));
            stableTokenAfterAccountSwap=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(first,before));

            // Exercise a genuine PREPARED journal preimage rather than
            // synthesizing a non-canonical identity under another name.
            strict.saveStrict(plan.preparedPreimage);
            AtomicBoolean didPreparedSwap=new AtomicBoolean();
            FilePlayerRepository preparedRace=new FilePlayerRepository(
                paths,a->{},a->{},a->{},account->{
                    if(didPreparedSwap.compareAndSet(false,true))
                        replaceWithSameBytes(accountPath);
                }
            );
            try{
                preparedRace.captureRestartContinuityTokenReadOnly(
                    first);
            }catch(IOException veto){
                preparedReplacementDenied=didPreparedSwap.get()&&
                    veto.getMessage().contains(
                        "G21.74 RECOVERY_OBJECT_REPLACED_NO_GRANT");
            }
            strict.saveStrict(terminal);
            // The strict writer intentionally refreshed saved.at when
            // restoring the terminal after the PREPARED fixture.
            // Compare later read-only captures to THIS new baseline,
            // not to serialization bytes from before that write.
            byte[] restoredTerminalBytes=Files.readAllBytes(accountPath);

            // A negative review marker has stable PATH/PRESENCE/BYTES
            // but a different filesystem inode after raw replacement.
            String reviewed="g2174-marker";
            WorldPlayer plain=new WorldPlayer();
            plain.markRegistered(reviewed);
            PlayerSnapshot prepared=PlayerSnapshotCodec.capture(
                reviewed,plain
            );
            strict.saveStrict(prepared);
            Path reviewAccount=paths.resolve(reviewed);
            MailboxAccountPublicationCoordinator
                .withExclusivePublication(reviewAccount,()->{
                    new MailboxStrictWriteIntentFence(paths)
                        .armInsidePublicationLock(
                            reviewed,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(prepared));
                    return null;
                });
            Path marker=new MailboxStrictWriteIntentFence(paths)
                .fencePath(reviewed);
            byte[] exactMarker=Files.readAllBytes(marker);
            String markerBefore=
                repository.captureRestartContinuityTokenReadOnly(reviewed);
            AtomicBoolean didMarkerSwap=new AtomicBoolean();
            FilePlayerRepository markerRace=new FilePlayerRepository(
                paths,a->{},a->{},a->{},account->{
                    if(didMarkerSwap.compareAndSet(false,true))
                        replaceWithSameBytes(marker);
                }
            );
            try{
                markerRace.captureRestartContinuityTokenReadOnly(reviewed);
            }catch(IOException veto){
                markerObjectChangeDenied=veto.getMessage().contains(
                    "G21.74 RECOVERY_OBJECT_REPLACED_NO_GRANT");
            }
            markerReplacementInjected=didMarkerSwap.get();
            markerSameBytesAfterSwap=Arrays.equals(exactMarker,
                Files.readAllBytes(marker));
            stableTokenAfterMarkerSwap=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        reviewed,markerBefore));

            // Delete/recreate without changing marker content, name,
            // size, timestamp or selected G21.71 review classification.
            AtomicBoolean recreated=new AtomicBoolean();
            FilePlayerRepository deleteRace=new FilePlayerRepository(
                paths,a->{},a->{},a->{},account->{
                    if(recreated.compareAndSet(false,true)){
                        byte[] saved=Files.readAllBytes(marker);
                        FileTime mtime=Files.getLastModifiedTime(
                            marker,LinkOption.NOFOLLOW_LINKS);
                        Files.delete(marker);
                        Files.write(marker,saved);
                        Files.setLastModifiedTime(marker,mtime);
                    }
                }
            );
            try{
                deleteRace.captureRestartContinuityTokenReadOnly(reviewed);
            }catch(IOException veto){
                deleteAndRecreateDenied=recreated.get()&&
                    veto.getMessage().contains(
                        "G21.74 RECOVERY_OBJECT_REPLACED_NO_GRANT");
            }

            try{restart.persistence().load(first);}
            catch(IOException denial){
                originalTerminalStillBlocksRestart=
                    denial.getMessage().contains(
                        "QUARANTINE_TERMINAL_NO_GRANT");
            }
            try{restart.persistence().load(reviewed);}
            catch(IOException denial){
                markerStillBlocksRestart=denial.getMessage().contains(
                    "MAILBOX_DURABLE_REVIEW_FENCE");
            }

            String missing="g2174-absent";
            String missingToken=repository
                .captureRestartContinuityTokenReadOnly(missing);
            absentStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        missing,missingToken));

            String independent="g2174-independent";
            WorldPlayer other=new WorldPlayer();
            other.markRegistered(independent);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                independent,other));
            String unrelated=repository.captureRestartContinuityTokenReadOnly(
                independent);
            anotherAccountIndependent=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        independent,unrelated));

            callerCaptureOnlyReads=callerCaptureOnlyReads&&
                Arrays.equals(restoredTerminalBytes,
                    Files.readAllBytes(accountPath))&&
                Arrays.equals(exactMarker,
                    Files.readAllBytes(marker));
            noLiveRewardsCredited=owner.bank().inventorySlots()==0&&
                owner.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> all=Files.walk(root)){
                noTmpOrLockLeaks=all.noneMatch(p->
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
        System.out.println("G2174_OBJECT_REPLACEMENT_DIAGNOSTICS"+
            " baselineAcrossInstances="+baselineAcrossInstances+
            " terminalReplacementInjected="+terminalReplacementInjected+
            " terminalSameBytesAfterSwap="+terminalSameBytesAfterSwap+
            " terminalObjectChangeDenied="+terminalObjectChangeDenied+
            " stableTokenAfterAccountSwap="+stableTokenAfterAccountSwap+
            " preparedReplacementDenied="+preparedReplacementDenied+
            " markerReplacementInjected="+markerReplacementInjected+
            " markerSameBytesAfterSwap="+markerSameBytesAfterSwap+
            " markerObjectChangeDenied="+markerObjectChangeDenied+
            " stableTokenAfterMarkerSwap="+stableTokenAfterMarkerSwap+
            " deleteAndRecreateDenied="+deleteAndRecreateDenied+
            " markerStillBlocksRestart="+markerStillBlocksRestart+
            " originalTerminalStillBlocksRestart="+
                originalTerminalStillBlocksRestart+
            " absentStable="+absentStable+
            " anotherAccountIndependent="+anotherAccountIndependent+
            " callerCaptureOnlyReads="+callerCaptureOnlyReads+
            " noLiveRewardsCredited="+noLiveRewardsCredited+
            " noTmpOrLockLeaks="+noTmpOrLockLeaks);
        if(!(baselineAcrossInstances&&terminalReplacementInjected&&
             terminalSameBytesAfterSwap&&terminalObjectChangeDenied&&
             stableTokenAfterAccountSwap&&preparedReplacementDenied&&
             markerReplacementInjected&&markerSameBytesAfterSwap&&
             markerObjectChangeDenied&&stableTokenAfterMarkerSwap&&
             deleteAndRecreateDenied&&markerStillBlocksRestart&&
             originalTerminalStillBlocksRestart&&absentStable&&
             anotherAccountIndependent&&callerCaptureOnlyReads&&
             noLiveRewardsCredited&&noTmpOrLockLeaks))
            throw new AssertionError(
                "G21.74 replacement within recovery capture must fail");
        System.out.println("G2174_OBJECT_REPLACEMENT_PASS"+
            " accountIdentity=true markerIdentity=true"+
            " byteIdenticalNoBypass=true grant=false replay=false"+
            " admission=false release=false");
    }

    private static void replaceWithSameBytes(Path destination)
        throws IOException{
        byte[] before=Files.readAllBytes(destination);
        FileTime mtime=Files.getLastModifiedTime(
            destination,LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes original=Files.readAttributes(
            destination,BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        Path temp=Files.createTempFile(
            destination.getParent(),destination.getFileName().toString()+
                ".g2174-replaced-",".new");
        try{
            Files.write(temp,before);
            Files.setLastModifiedTime(temp,mtime);
            Files.move(temp,destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }finally{
            Files.deleteIfExists(temp);
        }
        BasicFileAttributes after=Files.readAttributes(
            destination,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(Arrays.equals(before,Files.readAllBytes(destination))&&
           original.fileKey()!=null&&
           original.fileKey().equals(after.fileKey()))
            throw new IOException(
                "G21.74 fixture atomic replacement did not change identity");
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

    private G2174MailboxRestartIdentityReplacementIntegrationTest(){}
}
