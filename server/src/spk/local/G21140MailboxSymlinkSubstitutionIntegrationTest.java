package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
 * G21.140: late original source symlink substitution must be
 * rejected by G21.136's NOFOLLOW final held-lease physical scan,
 * despite unchanged pathname and bytewise readable content.
 * Original inode restored; no grants or atomic-epoch claim.

 */
public final class G21140MailboxSymlinkSubstitutionIntegrationTest {
    private static final String ACCOUNT="g21140-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.140 "+message);
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
    private static Path firstLeaf(Path account,String suffix)
        throws IOException{
        try(DirectoryStream<Path> files=Files.newDirectoryStream(
                account.getParent(),
                account.getFileName().toString()+suffix)){
            for(Path p:files)return p;
        }
        throw new IOException("G21.140 missing real leaf "+suffix);
    }
    private static Object nofollowKey(Path path)throws IOException{
        BasicFileAttributes attr=Files.readAttributes(path,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(attr.isRegularFile()&&!attr.isSymbolicLink()&&
            attr.fileKey()!=null,"missing original regular file key");
        return attr.fileKey();
    }
    /**
     * Second observer moves exact original regular inode to a neutral
     * shadow, leaving a symlink at SAME original pathname pointing
     * back to the stored original inode. FOLLOW bytes thus match all
     * original trusted hashes, while NOFOLLOW file-type/identity
     * check must fail closed on final in-lease physical rescan.
     */
    private static boolean symlinkOriginalDenied(
        FilePlayerRepository.PathResolver r,Pins pins,
        Path target,Path backup
    )throws Exception{
        check(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&
            !Files.isSymbolicLink(target),
            "original source is not a regular file");
        check(!Files.exists(backup,LinkOption.NOFOLLOW_LINKS),
            "shadow path already exists");
        byte[] bytes=Files.readAllBytes(target);
        java.nio.file.attribute.FileTime originalMtime=
            Files.getLastModifiedTime(target);
        Object originalKey=nofollowKey(target);
        int[] visits={0},callbacks={0};
        boolean refused=false;
        boolean[] sameReadableBytes={false};
        boolean[] replacedWithSymlink={false};
        try{
            try{
                withObserver(r,pins,()->callbacks[0]++,
                    (observedPath,account,snapshot)->{
                        if(++visits[0]==2){
                            Files.move(target,backup);
                            // Relative link target keeps the test
                            // isolated to this temporary directory.
                            Files.createSymbolicLink(target,
                                backup.getFileName());
                            replacedWithSymlink[0]=
                                Files.isSymbolicLink(target);
                            sameReadableBytes[0]=java.util.Arrays.equals(
                                bytes,Files.readAllBytes(target));
                            check(replacedWithSymlink[0]&&sameReadableBytes[0]&&
                                Files.exists(target)&&
                                !Files.isRegularFile(target,
                                    LinkOption.NOFOLLOW_LINKS),
                                "late symlink replacement fixture invalid");
                        }
                    });
            }catch(IOException failedClosed){
                refused=true;
            }
            check(visits[0]==2&&callbacks[0]==1,
                "late symlink must follow two physical captures");
            check(Files.isSymbolicLink(target)&&
                Files.isRegularFile(backup,
                    LinkOption.NOFOLLOW_LINKS),
                "original symlink path and preserved inode missing");
            return refused&&replacedWithSymlink[0]&&
                sameReadableBytes[0];
        }finally{
            if(Files.isSymbolicLink(target)){
                Files.delete(target);
            }
            if(Files.exists(backup,LinkOption.NOFOLLOW_LINKS)){
                check(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),
                    "unexpected replacement before inode restoration");
                Files.move(backup,target);
            }
            check(java.util.Arrays.equals(
                    bytes,Files.readAllBytes(target))&&
                java.util.Objects.equals(
                    originalKey,nofollowKey(target))&&
                originalMtime.equals(Files.getLastModifiedTime(target))&&
                Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&
                !Files.isSymbolicLink(target),
                "restoration must preserve same original real inode");
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21140-cohort-");
        boolean genuine=false,allSixLateSymlinksRejected=false;
        boolean originalLegacyApiPreserved=false;
        boolean healthyRetryAfterEveryRestoration=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.140 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21140_TEST_ONLY"));
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
                firstLeaf(account,".g21103-writer-attempt-v1-*.begin"),
                firstLeaf(account,".g21114-writer-kind-v2-*.begin"),
                firstLeaf(account,".g21120-commit-result-v3-*.result")
            };
            String[] names={
                "ACCOUNT","PREPARED_JOURNAL","COMMIT","V1","V2","V3"
            };
            int vetoed=0;
            for(int i=0;i<targets.length;i++){
                Path neutral=root.resolve(
                    "g21140-original-symlink-shadow-"+i+".bak");
                check(symlinkOriginalDenied(
                    r,original,targets[i],neutral),
                    "NOFOLLOW late symlink was accepted plane="+names[i]);
                vetoed++;
                // After returning from the failed scan, the same
                // inode and independently pinned content remain.
                healthy(r,original);
            }
            allSixLateSymlinksRejected=vetoed==6;
            healthyRetryAfterEveryRestoration=true;
            originalLegacyApiPreserved=
                physical(r,original).sevenPhysicalPinsMatched;
            int[] visited={0};
            withObserver(r,original,()->{},
                (observedPath,accountName,sample)->visited[0]++);
            check(visited[0]==2,
                "two opt-in observer visits must remain unchanged");
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21140_LATE_SYMLINK_DIAGNOSTICS"+
                " genuineFourBoundWriterBaseline="+genuine+
                " identicalReadableBytesSymlinkVetos="+vetoed+
                " restoredOriginalInodes=true"+
                " legacyTwoCaptureApiPreserved="+
                    originalLegacyApiPreserved+
                " healthyRetryAfterEach="+
                    healthyRetryAfterEveryRestoration+
                " optInObserverVisits="+visited[0]+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&allSixLateSymlinksRejected&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEveryRestoration&&noGrant&&zeroLeases,
                "late original source symlink refusal");
            System.out.println("G21140_LATE_SYMLINK_SUBSTITUTION_PASS"+
                " sixOriginalPathsRefusedSymlink=true"+
                " originalContentsStillReadableByFollow=true"+
                " finalNOFOLLOWCheckVetoed=true"+
                " realInodesAndHashesRestored=true"+
                " rawWriterExclusion=false signedProvenance=false"+
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
