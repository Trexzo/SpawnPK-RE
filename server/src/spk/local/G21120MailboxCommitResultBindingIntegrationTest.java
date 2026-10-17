package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.120: real G21.86 disk COMMIT's forced post-result bytes
 * bound to the exact same opt-in G21.103/v1 and G21.114/v2 UUID.
 * Caller-supplied kind labels and matching digests alone remain
 * non-authoritative and no historical/other-writer epoch is implied.
 */
public final class G21120MailboxCommitResultBindingIntegrationTest {
    private static final String BOUND="g21120-bound";
    private static final String LEGACY="g21120-old";
    private static final String FAULT="g21120-fault";
    private static final String FULL="g21120-full";
    private static final String TAMPER="g21120-tamper";
    private static final String ORPHAN="g21120-orphan";
    private static final String VERSION="g21120-version";

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.120 "+reason);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long gen,MailboxSettlementPostimagePlanner.Proposal q){
            player=p;generation=gen;proposal=q;
            terminal=MailboxAtomicTerminalSnapshot.compose(q);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long gen=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.120 bound real COMMIT test","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21120_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot s=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,s));
        return new Seed(player,gen,
            MailboxSettlementPostimagePlanner.plan(player,gen,s));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(byte[] input)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(input);
        StringBuilder hex=new StringBuilder(64);
        for(byte b:hash)
            hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private static String sha(Path p)throws Exception{
        return sha(Files.readAllBytes(p));
    }
    @FunctionalInterface
    private interface Action {void execute()throws IOException;}
    private static boolean denied(Action a,String expected)
        throws IOException{
        try{a.execute();return false;}
        catch(IOException e){
            for(Throwable x=e;x!=null;x=x.getCause())
                if(String.valueOf(x.getMessage()).contains(expected))
                    return true;
            return false;
        }
    }
    private static Path[] resultLeaves(Path root,String account)
        throws IOException{
        try(Stream<Path> listing=Files.list(root)){
            return listing.filter(p->p.getFileName().toString()
                .startsWith(account+
                    ".properties.g21120-commit-result-v3-"))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static final class Pins {
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final String v2,account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r,String a)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,a);
            v2=MailboxWriterKindEvidenceV2.capture(r,a).sha256;
            account=sha(r.resolve(a));
            prepared=sha(new
                MailboxDurableIdempotencyIntentJournal(r)
                    .journalPath(a));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                    .recordPath(a));
        }
    }
    private static MailboxCommitResultBindingV3.Report strict(
        FilePlayerRepository.PathResolver r,String a,Pins pins
    )throws IOException{
        return MailboxCommitResultBindingV3
            .requirePinnedRealBoundCommit(
                r,a,pins.v1.portable,pins.v1.sha256,
                pins.account,pins.prepared,pins.commit,pins.v2);
    }
    private static MailboxCommitResultBindingV3.Report report(
        FilePlayerRepository.PathResolver r,String account
    )throws IOException{
        MailboxCommitResultBindingV3.Report v=
            MailboxCommitResultBindingV3.inspect(r,account);
        check(!v.authenticatedWriterExecution&&
            !v.historicalWriterPopulationProved&&
            !v.allFutureWritersFenced&&
            !v.multiFileAtomicEpochProved&&
            !v.archiveRolloverAuthorized&&
            !v.recordPruningAuthorized&&!v.cleanupAuthorized&&
            !v.unlinkAuthorized&&!v.grantAuthorized&&
            !v.replayAuthorized&&!v.sessionAuthorized&&
            !v.releaseAuthorized&&!v.clientAckAuthorized,
            "result record promoted to authority");
        return v;
    }
    private static StrictDurablePlayerSnapshotWriter.Receipt
        terminal(
            World world,Seed s,
            StrictDurablePlayerSnapshotWriter writer,
            boolean opted
        )throws Exception{
        WorldPlayerPersistence.PreparedAccountReservation token=
            world.persistence().reservePreparedAccount(
                s.player,s.generation,s.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence terminal=
            opted
                ?world.persistence()
                    .publishReservedTerminalStrictlyWithWriterEvidence(
                        token,s.proposal,s.terminal,writer)
                    .get(15L,TimeUnit.SECONDS)
                :world.persistence().publishReservedTerminalStrictly(
                    token,s.proposal,s.terminal,writer)
                    .get(15L,TimeUnit.SECONDS);
        check(terminal.strictReceipt.matchesSnapshot(s.terminal),
            "terminal wrong strict Receipt");
        return terminal.strictReceipt;
    }
    private static void ready(
        World world,Seed s,StrictDurablePlayerSnapshotWriter writer,
        MailboxDurableIdempotencyIntentJournal journal,
        boolean opted
    )throws Exception{
        if(opted){
            writer.saveStrictWithWriterEvidence(
                s.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(s.proposal);
        }else{
            writer.saveStrict(s.proposal.preparedPreimage);
            journal.publishPreparedIntent(s.proposal);
        }
    }
    private static String[] fingerprint(Path root,String a)
        throws IOException{
        Path[] leaves=resultLeaves(root,a);
        String[] out=new String[leaves.length];
        for(int i=0;i<leaves.length;i++)
            out[i]=leaves[i].getFileName()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(leaves[i]));
        return out;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21120-commit-binding-");
        boolean boundCorrectly=false,priorGateStillNoGrant=false;
        boolean oldG21111Unbound=false,oldG21111StrictVeto=false;
        boolean realPostforceUncertainBeginOnly=false;
        boolean fullLedgerDeniedBeforePublish=false;
        boolean originalBindingTamperVeto=false;
        boolean actualCommitBytesTamperVeto=false;
        boolean orphanBindingVeto=false,versionVeto=false;
        boolean unchangedLegacySemantics=false;
        boolean noLiveGrants=false,zeroLeases=false;
        try(World world=World.isolatedForTest(
            60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(r);

            Seed good=seed(world,BOUND);
            ready(world,good,writer,journal,true);
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                terminal(world,good,writer,true);
            disk.recordConfirmedDiskTerminalWithResultBinding(
                good.proposal,receipt);
            Pins pins=new Pins(r,BOUND);
            MailboxCommitResultBindingV3.Report bound=report(r,BOUND);
            MailboxCommitResultBindingV3.Report accepted=
                strict(r,BOUND,pins);
            Path[] v3=resultLeaves(root,BOUND);
            MailboxWriterAttemptEvidenceV1.Report v1=
                MailboxWriterAttemptEvidenceV1.inspect(r,BOUND);
            MailboxWriterKindEvidenceV2.Report v2=
                MailboxWriterKindEvidenceV2.inspect(r,BOUND);
            boundCorrectly=bound.status==
                    MailboxCommitResultBindingV3.Status
                        .BOUND_COMMIT_DISK_MATCH_NO_AUTHORITY&&
                bound.boundAttempts==1&&
                v3.length==1&&
                v1.recordCount==8&&v1.started==4&&
                v1.completed==4&&v2.typedBegins==4&&
                v2.typedCompletes==4&&
                v2.pairedByKind.getOrDefault(
                    MailboxWriterKindEvidenceV2.Kind.DISK_COMMIT,0)==1&&
                pins.commit.equals(bound.commitBytesSha256)&&
                receipt.snapshotSha256.equals(bound.terminalSha256)&&
                accepted.commitBytesSha256.equals(pins.commit)&&
                noGrant(good);
            MailboxRealWriterSemanticBoundaryV1.Report oldGate=
                MailboxRealWriterSemanticBoundaryV1
                    .requirePinnedRealWriterCandidate(
                        r,BOUND,pins.v1.portable,pins.v1.sha256,
                        pins.account,pins.prepared,pins.commit,pins.v2);
            priorGateStillNoGrant=
                oldGate.status==
                    MailboxRealWriterSemanticBoundaryV1.Status
                        .FOUR_REAL_KIND_LABELS_AND_DISK_MATCH_NO_GRANT&&
                !oldGate.grantAuthorized&&
                !oldGate.diskCommit.transactionCommitted&&
                !oldGate.diskCommit.liveApplied;

            // Existing G21.111 opt-in still creates v1+v2, but it
            // MUST NOT retroactively acquire a G21.120 v3 result.
            Seed old=seed(world,LEGACY);
            ready(world,old,writer,journal,true);
            StrictDurablePlayerSnapshotWriter.Receipt oldReceipt=
                terminal(world,old,writer,true);
            disk.recordConfirmedDiskTerminalWithWriterEvidence(
                old.proposal,oldReceipt);
            Pins oldPins=new Pins(r,LEGACY);
            MailboxCommitResultBindingV3.Report legacy=report(r,LEGACY);
            oldG21111Unbound=legacy.status==
                MailboxCommitResultBindingV3.Status
                    .NO_RESULT_BINDING_LEGACY_OR_UNKNOWN&&
                legacy.boundAttempts==0&&
                resultLeaves(root,LEGACY).length==0&&
                MailboxWriterAttemptEvidenceV1.inspect(r,LEGACY)
                    .recordCount==8;
            oldG21111StrictVeto=denied(
                ()->strict(r,LEGACY,oldPins),
                "G21.120 COMMIT_RESULT_BINDING_UNSAFE_NO_AUTHORITY");

            // Real G21.86 COMMIT is forcibly linked BEFORE a fault
            // at AFTER_DIRECTORY_FORCE. No v3, v1/v2 BEGIN only:
            // neither a fake success nor a rollback is allowed.
            Seed faulted=seed(world,FAULT);
            ready(world,faulted,writer,journal,false);
            StrictDurablePlayerSnapshotWriter.Receipt faultReceipt=
                terminal(world,faulted,writer,false);
            MailboxGuardedDiskCommitRecord injected=
                new MailboxGuardedDiskCommitRecord(r,phase->{
                    if(phase==MailboxGuardedDiskCommitRecord.Phase
                            .AFTER_DIRECTORY_FORCE)
                        throw new IOException(
                            "G21120_FORCED_AFTER_DISK_COMMIT");
                });
            boolean faultOccurred=denied(
                ()->injected.recordConfirmedDiskTerminalWithResultBinding(
                    faulted.proposal,faultReceipt),
                "G21.86 DISK_COMMIT_RECORD_UNCONFIRMED_NO_GRANT");
            MailboxWriterAttemptEvidenceV1.Report incomplete=
                MailboxWriterAttemptEvidenceV1.inspect(r,FAULT);
            MailboxWriterKindEvidenceV2.Report incompleteV2=
                MailboxWriterKindEvidenceV2.inspect(r,FAULT);
            realPostforceUncertainBeginOnly=faultOccurred&&
                Files.exists(disk.recordPath(FAULT),
                    LinkOption.NOFOLLOW_LINKS)&&
                incomplete.recordCount==1&&
                incomplete.started==1&&incomplete.completed==0&&
                incompleteV2.typedBegins==1&&
                incompleteV2.typedCompletes==0&&
                resultLeaves(root,FAULT).length==0&&
                denied(()->report(r,FAULT),
                    "G21.120 COMMIT_RESULT_BINDING_UNSAFE_NO_AUTHORITY")&&
                noGrant(faulted);

            // Full v1 retention prevents even opening the real G21.86
            // publication callback. No COMMIT/v3 may be manufactured.
            Seed full=seed(world,FULL);
            ready(world,full,writer,journal,false);
            StrictDurablePlayerSnapshotWriter.Receipt fullReceipt=
                terminal(world,full,writer,false);
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            boolean[] callbackExecuted={false};
            String[] beforeResult=fingerprint(root,FULL);
            fullLedgerDeniedBeforePublish=denied(
                ()->disk.recordConfirmedDiskTerminalWithResultBinding(
                    full.proposal,fullReceipt),
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL")&&
                !Files.exists(disk.recordPath(FULL),
                    LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(beforeResult,fingerprint(root,FULL))&&
                resultLeaves(root,FULL).length==0&&
                !callbackExecuted[0];

            // A bitwise altered v3 file, even with the same filename
            // and matching old five-plane pins, must be quarantined.
            Seed tamper=seed(world,TAMPER);
            ready(world,tamper,writer,journal,true);
            StrictDurablePlayerSnapshotWriter.Receipt tamperReceipt=
                terminal(world,tamper,writer,true);
            disk.recordConfirmedDiskTerminalWithResultBinding(
                tamper.proposal,tamperReceipt);
            Path leaf=resultLeaves(root,TAMPER)[0];
            byte[] before=Files.readAllBytes(leaf);
            byte[] changed=before.clone();
            changed[0]^=1;
            Files.write(leaf,changed);
            originalBindingTamperVeto=denied(
                ()->report(r,TAMPER),
                "G21.120 COMMIT_RESULT_BINDING_UNSAFE_NO_AUTHORITY");
            Files.write(leaf,before);
            Path canonicalCommit=disk.recordPath(TAMPER);
            byte[] originalCommit=Files.readAllBytes(canonicalCommit);
            byte[] modified=originalCommit.clone();
            modified[0]^=1;
            Files.write(canonicalCommit,modified);
            actualCommitBytesTamperVeto=denied(
                ()->report(r,TAMPER),
                "G21.120 COMMIT_RESULT_BINDING_UNSAFE_NO_AUTHORITY");
            Files.write(canonicalCommit,originalCommit);

            // An orphan/unknown version v3 name is NEVER recognized as
            // a safely-bound result, even if v1/v2 are otherwise empty.
            Seed orphan=seed(world,ORPHAN);
            writer.saveStrict(orphan.proposal.preparedPreimage);
            Path orphanFile=root.resolve(ORPHAN+
                ".properties.g21120-commit-result-v3-"+
                "00000000-0000-0000-0000-000000000000.result");
            Files.write(orphanFile,
                "BAD_ORPHAN_RESULT".getBytes(StandardCharsets.US_ASCII));
            orphanBindingVeto=denied(
                ()->report(r,ORPHAN),
                "G21.120 COMMIT_RESULT_BINDING_UNSAFE_NO_AUTHORITY");
            Seed version=seed(world,VERSION);
            writer.saveStrict(version.proposal.preparedPreimage);
            Path unknownFile=root.resolve(VERSION+
                ".properties.g21120-commit-result-v3-wrongformat");
            Files.write(unknownFile,new byte[]{2});
            versionVeto=denied(
                ()->report(r,VERSION),
                "G21.120 COMMIT_RESULT_BINDING_UNSAFE_NO_AUTHORITY");

            // Old G21.105 portable verifier remains byte-identical;
            // no attempt kind or v1/v2 portable format is modified.
            MailboxWriterEvidencePortableArchiveV1.Verified portable=
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    pins.v1.portable,pins.v1.sha256);
            unchangedLegacySemantics=portable.records==8&&
                portable.started==4&&portable.completed==4&&
                !portable.cleanupAuthorized&&!portable.rolloverAuthorized&&
                disk.inspect(BOUND).diskCommitRecordMatched&&
                oldG21111Unbound;
            noLiveGrants=noGrant(good)&&noGrant(old)&&
                noGrant(faulted)&&noGrant(full)&&noGrant(tamper)&&
                noGrant(orphan)&&noGrant(version);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21120_COMMIT_RESULT_DIAGNOSTICS"+
                " realBoundSameUuidAndDiskSha="+boundCorrectly+
                " priorSemanticGateNoLiveGrant="+priorGateStillNoGrant+
                " oldG21111ExplicitlyUnbound="+oldG21111Unbound+
                " oldUnboundStrictlyRejected="+oldG21111StrictVeto+
                " postCommitForceFaultLeavesBeginOnly="+
                    realPostforceUncertainBeginOnly+
                " fullEvidenceBeforeActionVeto="+
                    fullLedgerDeniedBeforePublish+
                " alteredV3ResultDenied="+originalBindingTamperVeto+
                " alteredActualCommitDenied="+
                    actualCommitBytesTamperVeto+
                " orphanV3Denied="+orphanBindingVeto+
                " unknownV3VersionDenied="+versionVeto+
                " v1PortableAndOldDefaultsCompatible="+
                    unchangedLegacySemantics+
                " noLiveRewards="+noLiveGrants+
                " zeroJvmLeases="+zeroLeases);
            check(boundCorrectly&&priorGateStillNoGrant&&
                oldG21111Unbound&&oldG21111StrictVeto&&
                realPostforceUncertainBeginOnly&&
                fullLedgerDeniedBeforePublish&&
                originalBindingTamperVeto&&
                actualCommitBytesTamperVeto&&
                orphanBindingVeto&&versionVeto&&
                unchangedLegacySemantics&&noLiveGrants&&zeroLeases,
                "opt-in real COMMIT result evidence regression");
            System.out.println("G21120_BOUND_REAL_DISK_COMMIT_PASS"+
                " realG2186Result=true sameV1V2Uuid=true"+
                " actualCommitSha256=true strictReceiptTerminal=true"+
                " fourWriterKindsOnlyObserved=true"+
                " formerDefaultAndG21111Unchanged=true"+
                " historicalWritersFenced=false"+
                " atomicEpoch=false hardwarePowerLossProved=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false session=false"+
                " release=false ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path file:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected test arguments");
        run();
    }
}
