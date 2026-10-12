package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.137: fileKey identity veto in opt-in held-lease final scan
 * when RAW writer replaces all bytes with identical data and restores
 * the original mtime. Six original physical planes, no grant/epoch.
 */
public final class G21137MailboxRestoredMtimeFileKeySwapIntegrationTest {
    private static final String ACCOUNT="g21137-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.137 "+message);
    }
    private static FilePlayerRepository.PathResolver resolver(Path dir){
        return a->dir.resolve(a+".properties");
    }
    private static String sha(Path file)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder text=new StringBuilder(64);
        for(byte b:bytes)
            text.append(String.format(Locale.ROOT,"%02x",b&255));
        return text.toString();
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxWriterKindEvidenceV2.Snapshot v2;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r)throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,ACCOUNT);
            v2=MailboxWriterKindEvidenceV2.capture(r,ACCOUNT);
            v3=MailboxFourBoundResultLeavesArchiveV1.capture(r,ACCOUNT);
            account=sha(r.resolve(ACCOUNT));
            prepared=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(ACCOUNT));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(ACCOUNT));
        }
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        physical(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneContinuousPhysicalWitnessV1
            .compareWithContinuousLease(r,ACCOUNT,p.v3.sha256,
                p.v1.portable,p.v1.sha256,p.account,p.prepared,
                p.commit,p.v2.sha256,LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneImmutableUuidCohortV1.Report
        strict(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneImmutableUuidCohortV1
            .comparePinnedImmutableUuidCohort(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static boolean denied(
        FilePlayerRepository.PathResolver r,Pins p,String expected
    ){
        try{strict(r,p);return false;}
        catch(IOException refused){
            return String.valueOf(refused.getMessage())
                .contains(expected);
        }
    }
    private static void negative(
        MailboxSevenPlaneImmutableUuidCohortV1.Report result
    ){
        check(result.exactFourV1V2V3UuidCohortObserved&&
            result.physical.sevenPhysicalPinsMatched&&
            result.physical.threePhysicalIdentitiesStableAcrossCaptures&&
            result.physical.evidenceLeafMetadataStableAcrossCaptures&&
            !result.v3ResultPayloadAuthorAuthenticated&&
            !result.realCallbackExecutionProved&&
            !result.rawOrDefaultWritersExcluded&&
            !result.historicalAndFutureWritersFenced&&
            !result.independentPinAuthenticationProved&&
            !result.atomicRecoveryEpochProved&&
            !result.richSemanticCohortWithinLeaseProved&&
            !result.rolloverAuthorized&&!result.pruningAuthorized&&
            !result.cleanupAuthorized&&!result.unlinkAuthorized&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.sessionAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized,"physical cohort promoted grant");
    }
    private static MailboxSevenPlaneV3PayloadCohortV1.Report payload(
        FilePlayerRepository.PathResolver r,Pins p
    )throws IOException{
        return MailboxSevenPlaneV3PayloadCohortV1
            .comparePinnedFourPayloads(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneContinuousSemanticCohortV1.Report
        continuous(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneContinuousSemanticCohortV1
            .comparePinnedContinuousSemantic(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        withObserver(
            FilePlayerRepository.PathResolver r,Pins p,
            MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between,
            MailboxSevenPlaneContinuousPhysicalWitnessV1.InsideLeaseObserver
                visitor
        )throws IOException{
        return MailboxSevenPlaneContinuousPhysicalWitnessV1
            .compareWithContinuousLease(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,between,visitor);
    }
    private static void healthy(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        check(physical(r,p).sevenPhysicalPinsMatched,
            "original physical witness did not recover");
        check(continuous(r,p).originalFourSemanticInspectorsInsideSameLease,
            "original semantic witness did not recover");
        check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "G21.100 lock retained after healthy re-entry");
    }
    private static Path evidence(Path account,String glob)
        throws IOException{
        try(DirectoryStream<Path> found=Files.newDirectoryStream(
                account.getParent(),
                account.getFileName().toString()+glob)){
            for(Path p:found)return p;
        }
        throw new IOException("G21.137 missing original "+glob);
    }
    private static Object physicalKey(Path file)throws IOException{
        BasicFileAttributes a=Files.readAttributes(file,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(a.isRegularFile()&&!a.isSymbolicLink()&&a.fileKey()!=null,
            "expected nofollow regular fileKey on source");
        return a.fileKey();
    }
    /**
     * An UNCOOPERATIVE test-only actor swaps the original inode to a
     * benign shadow name outside all watched account/evidence globs,
     * creates a new file with identical bytes and restores mtime
     * AFTER the SECOND observer. Tail physical witness must catch
     * fileKey change even though bytes/mtime are identical.
     * Finally restore the actual original inode, not just its data.
     */
    private static boolean swappedFileKeyVeto(
        FilePlayerRepository.PathResolver r,Pins pinned,
        Path target,Path shadow
    )throws Exception{
        check(!Files.exists(shadow,LinkOption.NOFOLLOW_LINKS),
            "shadow must not exist before identity test");
        byte[] initial=Files.readAllBytes(target);
        FileTime originalTime=Files.getLastModifiedTime(target);
        Object originalKey=physicalKey(target);
        int[] seen={0};
        int[] between={0};
        boolean[] changedKey={false};
        boolean[] sameBytes={false};
        boolean[] restoredTime={false};
        boolean vetoed=false;
        try{
            try{
                withObserver(r,pinned,()->between[0]++,
                    (selected,name,sample)->{
                        if(++seen[0]==2){
                            Files.move(target,shadow);
                            Files.copy(shadow,target,
                                StandardCopyOption.COPY_ATTRIBUTES);
                            Files.setLastModifiedTime(target,originalTime);
                            changedKey[0]=!Objects.equals(
                                originalKey,physicalKey(target));
                            sameBytes[0]=Arrays.equals(
                                initial,Files.readAllBytes(target));
                            restoredTime[0]=originalTime.equals(
                                Files.getLastModifiedTime(target));
                            check(changedKey[0]&&sameBytes[0]&&
                                restoredTime[0],
                                "swap fixture did not preserve bytes "+
                                "and mtime while changing fileKey");
                        }
                    });
            }catch(IOException failedClosed){
                vetoed=String.valueOf(failedClosed.getMessage())
                    .contains("AFTER_FINAL_OBSERVER_METADATA_CHANGED");
            }
            check(seen[0]==2&&between[0]==1,
                "wrong observer/callback count during inode swap");
            return vetoed&&changedKey[0]&&sameBytes[0]&&restoredTime[0];
        }finally{
            if(Files.exists(shadow,LinkOption.NOFOLLOW_LINKS)){
                Files.deleteIfExists(target);
                Files.move(shadow,target);
            }
            check(Arrays.equals(initial,Files.readAllBytes(target))&&
                originalTime.equals(Files.getLastModifiedTime(target))&&
                Objects.equals(originalKey,physicalKey(target)),
                "original inode/mTime/bytes must be fully restored");
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21137-cohort-");
        boolean genuine=false,allSixPhysicalFileKeyVetos=false;
        boolean originalLegacyApiPreserved=false;
        boolean healthyRetryAfterEverySwap=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.137 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21137_TEST_ONLY"));
            MailboxRewardDeliveryService.Snapshot selected=
                player.mailbox().get(id);
            MailboxPreparedClaimJournal.stageOnly(player,
                MailboxPreparedClaimJournal.prepare(player,selected));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    player,generation,selected);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot
                .compose(proposal);
            StrictDurablePlayerSnapshotWriter strictWriter=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(r);
            strictWriter.saveStrictWithResultBinding(
                proposal.preparedPreimage);
            journal.publishPreparedIntentWithResultBinding(proposal);
            WorldPlayerPersistence.PreparedAccountReservation ticket=
                world.persistence().reservePreparedAccount(
                    player,generation,proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence saved=
                world.persistence()
                    .publishReservedTerminalStrictlyWithResultBinding(
                        ticket,proposal,terminal,strictWriter)
                    .get(15L,TimeUnit.SECONDS);
            check(saved.strictReceipt.matchesSnapshot(terminal),
                "real World strict Receipt");
            disk.recordConfirmedDiskTerminalWithResultBinding(
                proposal,saved.strictReceipt);
            Pins original=new Pins(r);
            negative(strict(r,original));
            check(payload(r,original).canonicalFourV3PayloadsObserved,
                "original G21.133 canonical payloads");
            MailboxSevenPlaneContinuousSemanticCohortV1.Report real=
                continuous(r,original);
            check(real.originalFourSemanticInspectorsInsideSameLease&&
                real.strictUuidAndPayloadChecksOnCapturedSnapshots&&
                real.semanticHashesMatchV3Payloads&&
                !real.externallyAuthenticatedWriterProvenance&&
                !real.rawOrDefaultWriterExclusionProved&&
                !real.historicalAndFutureWritersFenced&&
                !real.independentPinAuthenticationProved&&
                !real.atomicCrossFileEpochProved&&
                !real.hardwarePowerLossProved&&
                !real.cleanupAuthorized&&!real.unlinkAuthorized&&
                !real.pruningAuthorized&&!real.rolloverAuthorized&&
                !real.grantAuthorized&&!real.replayAuthorized&&
                !real.sessionAuthorized&&!real.releaseAuthorized&&
                !real.clientAckAuthorized,
                "semantic witness improperly promoted authority");
            genuine=true;

            Path account=r.resolve(ACCOUNT);
            Path[] targets={
                account,
                new MailboxDurableIdempotencyIntentJournal(r)
                    .journalPath(ACCOUNT),
                new MailboxGuardedDiskCommitRecord(r)
                    .recordPath(ACCOUNT),
                evidence(account,".g21103-writer-attempt-v1-*.begin"),
                evidence(account,".g21114-writer-kind-v2-*.begin"),
                evidence(account,".g21120-commit-result-v3-*.result")
            };
            String[] planes={
                "ACCOUNT","PREPARED_JOURNAL","COMMIT","V1","V2","V3"
            };
            int vetoCount=0;
            for(int i=0;i<targets.length;i++){
                // This neutral shadow name is intentionally OUTSIDE
                // the account-scoped G21.103/114/12x evidence globs.
                Path backup=root.resolve("g21137-shadow-inode-"+i+".bak");
                check(swappedFileKeyVeto(r,original,targets[i],backup),
                    "identical-byte + restored-mtime replacement "+
                    "not rejected plane="+planes[i]);
                vetoCount++;
                // Prove exact original inode restored and no G21.100
                // lease was leaked by the rejected final scan.
                healthy(r,original);
            }
            allSixPhysicalFileKeyVetos=vetoCount==6;
            healthyRetryAfterEverySwap=true;
            originalLegacyApiPreserved=
                physical(r,original).sevenPhysicalPinsMatched;
            int[] visited={0};
            withObserver(r,original,()->{},
                (observedPath,who,sample)->visited[0]++);
            check(visited[0]==2,"two opt-in observer visits changed");
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21137_RESTORED_MTIME_FILEKEY_DIAGNOSTICS"+
                " realFourBoundWriterBaseline="+genuine+
                " identicalBytesRestoredMtimeFileKeyVetos="+vetoCount+
                " legacyTwoCaptureApiPreserved="+
                    originalLegacyApiPreserved+
                " healthyRetryAfterEveryInodeRestore="+
                    healthyRetryAfterEverySwap+
                " observerVisits="+visited[0]+
                " noLiveRewards="+noGrant+
                " zeroJvmLeases="+zeroLeases);
            check(genuine&&allSixPhysicalFileKeyVetos&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEverySwap&&noGrant&&zeroLeases,
                "fileKey swap detection");
            System.out.println("G21137_RESTORED_MTIME_FILEKEY_PASS"+
                " accountJournalCommitV1V2V3FileKeysChecked=true"+
                " identicalBytesAndMtimeDifferentInodeRejected=true"+
                " originalRealInodesRestored=true"+
                " futureRawWriterExclusion=false"+
                " identitySpoofResistanceProved=false"+
                " atomicEpoch=false rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
