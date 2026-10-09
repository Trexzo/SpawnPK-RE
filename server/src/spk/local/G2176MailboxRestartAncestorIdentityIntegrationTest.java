package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.76: directory ancestry can redirect despite NOFOLLOW leaf checks.
 * The recovery fingerprint must refuse redirected ancestry even if the
 * account and marker continue to resolve to the EXACT SAME file inode.
 */
public final class G2176MailboxRestartAncestorIdentityIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2176-ancestry-");
        Path active=Files.createDirectory(root.resolve("active"));
        Path parked=root.resolve("parked");
        FilePlayerRepository.PathResolver paths=
            account->active.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean terminalBaselineStable=false;
        boolean markerBaselineStable=false;
        boolean terminalAncestorRedirectInjected=false;
        boolean terminalRedirectDenied=false;
        boolean terminalSameFileDuringRedirect=false;
        boolean upfrontSymlinkAncestorDenied=false;
        boolean terminalAfterRestoreStable=false;
        boolean markerAncestorRedirectInjected=false;
        boolean markerRedirectDenied=false;
        boolean markerSameFileDuringRedirect=false;
        boolean markerAfterRestoreStable=false;
        boolean nestedSymlinkDenied=false;
        boolean absentAccountStable=false;
        boolean independentAccountStable=false;
        boolean originalTerminalQuarantine=false;
        boolean originalMarkerQuarantine=false;
        boolean sameRawFileBytes=true;
        boolean noLiveCredit=false;
        boolean noTempOrLeaseLeaks=false;

        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,repo)){
            restart.start();
            String name="g2176-terminal",id=name+":gift";
            WorldPlayer owner=new WorldPlayer();
            long generation=fixture.registerPlayer(owner,name);
            owner.mailbox().deliver(new RewardDeliveryMessage(
                id,"Ancestor inspection","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2176_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(id);
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,row));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    owner,generation,row);
            strict.saveStrict(MailboxAtomicTerminalSnapshot.compose(proposal));
            Path account=paths.resolve(name);
            byte[] exactAccount=Files.readAllBytes(account);
            Object accountKey=Files.readAttributes(account,
                java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
            String first=repo.captureRestartContinuityTokenReadOnly(name);
            terminalBaselineStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(name,first));

            String reviewed="g2176-review";
            WorldPlayer detached=new WorldPlayer();
            detached.markRegistered(reviewed);
            PlayerSnapshot ordinary=PlayerSnapshotCodec.capture(
                reviewed,detached);
            strict.saveStrict(ordinary);
            Path reviewFile=paths.resolve(reviewed);
            MailboxAccountPublicationCoordinator.withExclusivePublication(
                reviewFile,()->{
                    new MailboxStrictWriteIntentFence(paths)
                        .armInsidePublicationLock(
                            reviewed,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(ordinary));
                    return null;
                });
            Path sidecar=new MailboxStrictWriteIntentFence(paths)
                .fencePath(reviewed);
            byte[] exactSidecar=Files.readAllBytes(sidecar);
            Object sidecarKey=Files.readAttributes(sidecar,
                java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
            String markerToken=repo.captureRestartContinuityTokenReadOnly(
                reviewed);
            markerBaselineStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        reviewed,markerToken));

            AtomicBoolean terminalInjected=new AtomicBoolean();
            FilePlayerRepository raced=new FilePlayerRepository(
                paths,a->{},a->{},a->{},a->{
                    if(terminalInjected.compareAndSet(false,true))
                        redirectActiveToOriginal(active,parked);
                });
            try{
                raced.captureRestartContinuityTokenReadOnly(name);
            }catch(IOException refused){
                terminalRedirectDenied=refused.getMessage().contains(
                    "G21.76 RECOVERY_DIRECTORY_ANCESTOR_UNSAFE_NO_GRANT");
            }
            terminalAncestorRedirectInjected=terminalInjected.get();
            terminalSameFileDuringRedirect=
                Files.isSymbolicLink(active)&&
                Arrays.equals(exactAccount,Files.readAllBytes(account))&&
                accountKey.equals(Files.readAttributes(account,
                    java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).fileKey());
            try{
                repo.captureRestartContinuityTokenReadOnly(name);
            }catch(IOException denied){
                upfrontSymlinkAncestorDenied=denied.getMessage().contains(
                    "G21.76 RECOVERY_DIRECTORY_ANCESTOR_UNSAFE_NO_GRANT");
            }
            restoreOriginalAncestry(active,parked);
            terminalAfterRestoreStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(name,first));

            AtomicBoolean markerInjected=new AtomicBoolean();
            FilePlayerRepository markerRaced=new FilePlayerRepository(
                paths,a->{},a->{},a->{},a->{
                    if(markerInjected.compareAndSet(false,true))
                        redirectActiveToOriginal(active,parked);
                });
            try{
                markerRaced.captureRestartContinuityTokenReadOnly(reviewed);
            }catch(IOException refused){
                markerRedirectDenied=refused.getMessage().contains(
                    "G21.76 RECOVERY_DIRECTORY_ANCESTOR_UNSAFE_NO_GRANT");
            }
            markerAncestorRedirectInjected=markerInjected.get();
            markerSameFileDuringRedirect=
                Files.isSymbolicLink(active)&&
                Arrays.equals(exactSidecar,Files.readAllBytes(sidecar))&&
                sidecarKey.equals(Files.readAttributes(sidecar,
                    java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).fileKey());
            restoreOriginalAncestry(active,parked);
            markerAfterRestoreStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        reviewed,markerToken));

            Path nested=root.resolve("nested-link");
            Files.createSymbolicLink(nested,active.getFileName());
            try{
                new FilePlayerRepository(a->
                    nested.resolve(a+".properties"))
                    .captureRestartContinuityTokenReadOnly(name);
            }catch(IOException refused){
                nestedSymlinkDenied=refused.getMessage().contains(
                    "G21.76 RECOVERY_DIRECTORY_ANCESTOR_UNSAFE_NO_GRANT");
            }
            Files.delete(nested);

            String absent="g2176-absent";
            String absence=repo.captureRestartContinuityTokenReadOnly(absent);
            absentAccountStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(absent,absence));
            String independent="g2176-independent";
            WorldPlayer independentOwner=new WorldPlayer();
            independentOwner.markRegistered(independent);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                independent,independentOwner));
            String unrelated=repo.captureRestartContinuityTokenReadOnly(
                independent);
            independentAccountStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(independent,unrelated));

            try{restart.persistence().load(name);}
            catch(IOException denied){
                originalTerminalQuarantine=denied.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }
            try{restart.persistence().load(reviewed);}
            catch(IOException denied){
                originalMarkerQuarantine=denied.getMessage().contains(
                    "MAILBOX_DURABLE_REVIEW_FENCE");
            }
            sameRawFileBytes=
                Arrays.equals(exactAccount,Files.readAllBytes(account))&&
                Arrays.equals(exactSidecar,Files.readAllBytes(sidecar));
            noLiveCredit=owner.bank().inventorySlots()==0&&
                owner.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> entries=Files.walk(root)){
                noTempOrLeaseLeaks=entries.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            if(Files.isSymbolicLink(active)&&Files.exists(parked))
                restoreOriginalAncestry(active,parked);
            try(Stream<Path> entries=Files.walk(root)){
                for(Path p:entries.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2176_ANCESTRY_DIAGNOSTICS"+
            " terminalBaselineStable="+terminalBaselineStable+
            " markerBaselineStable="+markerBaselineStable+
            " terminalAncestorRedirectInjected="+
                terminalAncestorRedirectInjected+
            " terminalRedirectDenied="+terminalRedirectDenied+
            " terminalSameFileDuringRedirect="+
                terminalSameFileDuringRedirect+
            " upfrontSymlinkAncestorDenied="+
                upfrontSymlinkAncestorDenied+
            " terminalAfterRestoreStable="+terminalAfterRestoreStable+
            " markerAncestorRedirectInjected="+
                markerAncestorRedirectInjected+
            " markerRedirectDenied="+markerRedirectDenied+
            " markerSameFileDuringRedirect="+
                markerSameFileDuringRedirect+
            " markerAfterRestoreStable="+markerAfterRestoreStable+
            " nestedSymlinkDenied="+nestedSymlinkDenied+
            " absentAccountStable="+absentAccountStable+
            " independentAccountStable="+independentAccountStable+
            " originalTerminalQuarantine="+originalTerminalQuarantine+
            " originalMarkerQuarantine="+originalMarkerQuarantine+
            " sameRawFileBytes="+sameRawFileBytes+
            " noLiveCredit="+noLiveCredit+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(terminalBaselineStable&&markerBaselineStable&&
             terminalAncestorRedirectInjected&&terminalRedirectDenied&&
             terminalSameFileDuringRedirect&&
             upfrontSymlinkAncestorDenied&&terminalAfterRestoreStable&&
             markerAncestorRedirectInjected&&markerRedirectDenied&&
             markerSameFileDuringRedirect&&markerAfterRestoreStable&&
             nestedSymlinkDenied&&absentAccountStable&&
             independentAccountStable&&originalTerminalQuarantine&&
             originalMarkerQuarantine&&sameRawFileBytes&&noLiveCredit&&
             noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.76 restart ancestor NOFOLLOW race not fenced");
        System.out.println("G2176_ANCESTRY_PASS"+
            " directoryRedirectDenied=true sameLeafIdentity=true"+
            " grant=false replay=false admission=false release=false");
    }

    private static void redirectActiveToOriginal(
        Path original,Path saved
    )throws IOException{
        if(Files.exists(saved)||Files.isSymbolicLink(original))
            throw new IOException("G21.76 fixture already redirected");
        Files.move(original,saved);
        try{
            Files.createSymbolicLink(original,saved.getFileName());
        }catch(IOException|RuntimeException failed){
            Files.move(saved,original);
            throw failed;
        }
    }

    private static void restoreOriginalAncestry(
        Path link,Path saved
    )throws IOException{
        if(!Files.isSymbolicLink(link))
            throw new IOException("G21.76 unexpected fixture link type");
        Files.delete(link);
        Files.move(saved,link);
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

    private G2176MailboxRestartAncestorIdentityIntegrationTest(){}
}
