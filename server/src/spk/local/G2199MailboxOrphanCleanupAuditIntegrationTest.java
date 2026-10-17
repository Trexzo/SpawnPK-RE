package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.99: even an aged strict temp under an acquired account
 * publication lock can belong to a LIVE writer paused before the lock.
 */
public final class G2199MailboxOrphanCleanupAuditIntegrationTest {
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.99 "+why);
    }
    private static MailboxSettlementPostimagePlanner.Proposal plan(
        World source,String account
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=source.registerPlayer(player,account);
        player.mailbox().deliver(new RewardDeliveryMessage(
            account+":gift","No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2199_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot gift=
            player.mailbox().get(account+":gift");
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,gift));
        check(player.bank().inventorySlots()==0&&
            gift.claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED,
            "fixture issued reward");
        return MailboxSettlementPostimagePlanner.plan(
            player,generation,gift);
    }

    private static Path temp(Path root,String prefix)
        throws Exception{
        return Files.createTempFile(root,prefix,".tmp");
    }

    private static void assertNegative(
        MailboxOrphanPublicationCleanupAudit.Result r
    ){
        check(!r.cleanupAuthorized&&!r.unlinkAuthorized&&
            !r.accountMutationAuthorized&&!r.grantAuthorized&&
            !r.replayAuthorized&&!r.restartAdmissionAuthorized&&
            !r.clientAckAuthorized,"audit exposed unsafe authority");
        for(MailboxOrphanPublicationCleanupAudit.Candidate c:
                r.candidates)
            check(!c.unlinkAuthorized&&!c.authoritative,
                "candidate minted destructive/positive authority");
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2199-cleanup-audit-");
        FilePlayerRepository.PathResolver resolver=
            a->root.resolve(a+".properties");
        String committed="g2199-committed",active="g2199-active";
        CountDownLatch paused=new CountDownLatch(1);
        CountDownLatch resume=new CountDownLatch(1);
        AtomicReference<Throwable> writerFailure=new AtomicReference<>();
        AtomicReference<StrictDurablePlayerSnapshotWriter.Receipt>
            completedReceipt=new AtomicReference<>();
        boolean aliasesProtected=false,unknownTempsProtected=false;
        boolean symlinkAndDirectoryKept=false;
        boolean unrelatedExcluded=false,capacityFailsClosed=false;
        boolean liveAgedTempProtected=false,liveWriterFinished=false;
        boolean canonicalUnchanged=false,sourceUnclaimed=false;
        boolean noPublicationLeases=false;
        Thread writer=null;
        try(World fixture=World.isolatedForTest(60000L)){
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(resolver);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(resolver);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(resolver);

            MailboxSettlementPostimagePlanner.Proposal committedPlan=
                plan(fixture,committed);
            strict.saveStrict(committedPlan.preparedPreimage);
            journal.publishPreparedIntent(committedPlan);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot.compose(
                committedPlan);
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                strict.saveStrictTerminalForWorld(
                    terminal,resolver.resolve(committed),
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(
                            committedPlan.preparedPreimage),
                    ()->{},()->{});
            check(receipt.matchesSnapshot(terminal),
                "strict terminal receipt mismatch");
            disk.recordConfirmedDiskTerminal(committedPlan,receipt);

            Path account=resolver.resolve(committed);
            Path jpath=journal.journalPath(committed);
            Path cpath=disk.recordPath(committed);
            byte[][] canonicalBefore={
                Files.readAllBytes(account),
                Files.readAllBytes(jpath),
                Files.readAllBytes(cpath)
            };

            String prefix=committed+".properties";
            Path orphanStrict=temp(root,prefix+".g2123-");
            Files.write(orphanStrict,new byte[]{1,2,3});
            Path orphanIntent=temp(root,
                prefix+MailboxDurableIdempotencyIntentJournal.SUFFIX+
                    ".write-");
            Files.write(orphanIntent,new byte[]{4,5,6});
            Path orphanCommit=temp(root,
                prefix+MailboxGuardedDiskCommitRecord.SUFFIX+
                    ".write-");
            Files.write(orphanCommit,new byte[]{7,8,9});

            Path aliasJournal=temp(root,prefix+
                MailboxDurableIdempotencyIntentJournal.SUFFIX+
                ".write-");
            Files.delete(aliasJournal);
            Files.createLink(aliasJournal,jpath);
            Path aliasCommit=temp(root,prefix+
                MailboxGuardedDiskCommitRecord.SUFFIX+".write-");
            Files.delete(aliasCommit);
            Files.createLink(aliasCommit,cpath);

            Path unsafeDir=root.resolve(prefix+
                ".g2123-directory.tmp");
            Files.createDirectory(unsafeDir);
            boolean symlinkCreated=false;
            Path symlink=root.resolve(prefix+".g2123-symlink.tmp");
            try{
                Files.createSymbolicLink(symlink,cpath);
                symlinkCreated=true;
            }catch(UnsupportedOperationException|
                   java.nio.file.FileSystemException unsupported){
                // Windows runners may not permit symlink creation.
            }
            Path foreign=temp(root,
                committed+"-foreign.properties.g2123-");
            Files.write(foreign,new byte[]{10,11});

            MailboxOrphanPublicationCleanupAudit audit=
                new MailboxOrphanPublicationCleanupAudit(resolver);
            MailboxOrphanPublicationCleanupAudit.Result observed=
                audit.inspect(committed);
            assertNegative(observed);
            aliasesProtected=observed.count(
                MailboxOrphanPublicationCleanupAudit.Disposition
                    .CANONICAL_HARDLINK_ALIAS_KEEP)==2;
            unknownTempsProtected=observed.count(
                MailboxOrphanPublicationCleanupAudit.Disposition
                    .POSSIBLY_ACTIVE_OR_ORPHAN_KEEP)==3;
            symlinkAndDirectoryKept=observed.count(
                MailboxOrphanPublicationCleanupAudit.Disposition
                    .NONREGULAR_OR_SYMLINK_KEEP)==(symlinkCreated?2:1);
            unrelatedExcluded=observed.candidates.size()==
                (symlinkCreated?7:6)&&
                Files.readAllBytes(foreign)[0]==10;

            // Bounded, read-only audit must fail closed if many matching
            // candidates exist; no cleanup is performed on overflow.
            for(int n=0;n<MailboxOrphanPublicationCleanupAudit
                    .MAX_TEMP_CANDIDATES;n++)
                temp(root,prefix+".g2123-overflow-");
            try{audit.inspect(committed);}
            catch(java.io.IOException e){
                capacityFailsClosed=String.valueOf(e.getMessage())
                    .contains("G21.99 TEMP_AUDIT_CAPACITY_NO_UNLINK");
            }
            check(capacityFailsClosed,
                "candidate overcapacity silently accepted");
            check(Files.exists(aliasJournal,LinkOption.NOFOLLOW_LINKS)&&
                Files.exists(aliasCommit,LinkOption.NOFOLLOW_LINKS),
                "overload mutated published hardlink aliases");

            MailboxSettlementPostimagePlanner.Proposal activePlan=
                plan(fixture,active);
            strict.saveStrict(activePlan.preparedPreimage);
            journal.publishPreparedIntent(activePlan);
            PlayerSnapshot activeTerminal=
                MailboxAtomicTerminalSnapshot.compose(activePlan);
            String activeSha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(activePlan.preparedPreimage);
            writer=new Thread(()->{
                try{
                    StrictDurablePlayerSnapshotWriter inFlight=
                        new StrictDurablePlayerSnapshotWriter(
                            resolver,phase->{
                                if(phase==
                                        StrictDurablePlayerSnapshotWriter
                                            .Phase.BEFORE_ATOMIC_REPLACE){
                                    paused.countDown();
                                    try{
                                        if(!resume.await(
                                                8,TimeUnit.SECONDS))
                                            throw new java.io.IOException(
                                                "G21.99 writer gate timed out");
                                    }catch(InterruptedException interrupted){
                                        Thread.currentThread().interrupt();
                                        throw new java.io.IOException(
                                            "G21.99 writer interrupted",
                                            interrupted);
                                    }
                                }
                            });
                    completedReceipt.set(
                        inFlight.saveStrictTerminalForWorld(
                            activeTerminal,resolver.resolve(active),
                            activeSha,()->{},()->{}));
                }catch(Throwable failure){
                    writerFailure.set(failure);
                }
            },"g2199-active-strict-prepublication-writer");
            writer.start();
            check(paused.await(5,TimeUnit.SECONDS),
                "active writer never paused before publication lock");
            String activePrefix=active+".properties.g2123-";
            Path liveTemp;
            try(Stream<Path> listing=Files.list(root)){
                Path[] matched=listing.filter(p->p.getFileName()
                    .toString().startsWith(activePrefix)&&p.getFileName()
                    .toString().endsWith(".tmp")).toArray(Path[]::new);
                check(matched.length==1,
                    "active writer temp missing");
                liveTemp=matched[0];
            }
            // Deliberately forge a 60-day-old mtime while the writer is
            // STILL live. Age does not certify abandoned ownership.
            Files.setLastModifiedTime(liveTemp,FileTime.fromMillis(
                System.currentTimeMillis()-
                    TimeUnit.DAYS.toMillis(60)));
            MailboxOrphanPublicationCleanupAudit.Result live=
                audit.inspect(active);
            assertNegative(live);
            liveAgedTempProtected=live.candidates.size()==1&&
                live.candidates.get(0).disposition==
                    MailboxOrphanPublicationCleanupAudit.Disposition
                        .POSSIBLY_ACTIVE_OR_ORPHAN_KEEP&&
                live.candidates.get(0).modifiedMillis<
                    System.currentTimeMillis()-
                    TimeUnit.DAYS.toMillis(59)&&
                Files.exists(liveTemp,LinkOption.NOFOLLOW_LINKS);
            check(liveAgedTempProtected,
                "old-looking ACTIVE temp was treated as disposable");
            resume.countDown();
            writer.join(9000);
            liveWriterFinished=!writer.isAlive()&&
                writerFailure.get()==null&&
                completedReceipt.get()!=null&&
                completedReceipt.get().matchesSnapshot(activeTerminal)&&
                new FilePlayerRepository(resolver).load(active)
                    .orElseThrow(()->new AssertionError(
                        "active terminal account missing"))
                    .values().equals(activeTerminal.values());
            canonicalUnchanged=Arrays.equals(canonicalBefore[0],
                Files.readAllBytes(account))&&
                Arrays.equals(canonicalBefore[1],
                    Files.readAllBytes(jpath))&&
                Arrays.equals(canonicalBefore[2],
                    Files.readAllBytes(cpath))&&
                Files.isSameFile(aliasJournal,jpath)&&
                Files.isSameFile(aliasCommit,cpath)&&
                Files.exists(orphanStrict,LinkOption.NOFOLLOW_LINKS)&&
                Files.exists(orphanIntent,LinkOption.NOFOLLOW_LINKS)&&
                Files.exists(orphanCommit,LinkOption.NOFOLLOW_LINKS)&&
                Files.isDirectory(unsafeDir,LinkOption.NOFOLLOW_LINKS)&&
                (!symlinkCreated||Files.isSymbolicLink(symlink));
            sourceUnclaimed=true;
            for(WorldPlayer p:fixture.players().snapshot())
                sourceUnclaimed&=p.bank().inventorySlots()==0&&
                    p.mailbox().get(p.username()+":gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
        }finally{
            resume.countDown();
            if(writer!=null){
                writer.join(10000);
                check(!writer.isAlive(),
                    "G21.99 active writer still held at cleanup");
            }
            noPublicationLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2199_ORPHAN_CLEANUP_AUDIT_DIAGNOSTICS"+
            " hardlinkedAliasesProtected="+aliasesProtected+
            " unidentifiedTempsNotDeleted="+unknownTempsProtected+
            " nonregularAndSymlinkRefused="+symlinkAndDirectoryKept+
            " unrelatedAccountTempExcluded="+unrelatedExcluded+
            " overCapacityFailsClosed="+capacityFailsClosed+
            " agedInFlightTempProtected="+liveAgedTempProtected+
            " liveWriterCompletedAfterAudit="+liveWriterFinished+
            " canonicalFilesUnchanged="+canonicalUnchanged+
            " originalOwnerRewardsUnclaimed="+sourceUnclaimed+
            " jvmLeasesZero="+noPublicationLeases);
        check(aliasesProtected&&unknownTempsProtected&&
            symlinkAndDirectoryKept&&unrelatedExcluded&&
            capacityFailsClosed&&liveAgedTempProtected&&
            liveWriterFinished&&canonicalUnchanged&&sourceUnclaimed&&
            noPublicationLeases,
            "bounded cleanup preflight regression");
        System.out.println("G2199_ORPHAN_CLEANUP_AUDIT_PASS"+
            " explicitOptIn=true noUnlinkApi=true"+
            " oldTempNotAuthority=true"+
            " publicationLockDoesNotQuiescePrepublication=true"+
            " hardlinksNeverDeleted=true"+
            " grant=false liveApply=false replay=false"+
            " release=false ack=false");
    }
}
