package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.114: separately versioned, write-once opt-in writer-kind
 * companions bound to original v1 attempts, never a cleanup grant.
 */
public final class G21114MailboxTypedWriterKindEvidenceIntegrationTest {
    private static final String FOUR="g21114-four";
    private static final String MIXED="g21114-mixed";
    private static final String EMPTY="g21114-empty";
    private static final String FAILED="g21114-failed";
    private static final String TAMPER="g21114-tamper";
    private static final String ORPHAN="g21114-orphan";
    private static final String FOREIGN="g21114-foreign";
    private static final String FULL="g21114-full";
    private static final String VERSION="g21114-version";

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.114 "+reason);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long g,MailboxSettlementPostimagePlanner.Proposal q){
            player=p;generation=g;proposal=q;
            terminal=MailboxAtomicTerminalSnapshot.compose(q);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long gen=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.114 typed evidence fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21114_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot current=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,current));
        return new Seed(player,gen,MailboxSettlementPostimagePlanner
            .plan(player,gen,current));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static Path[] leaves(Path root,String account,String suffix)
        throws IOException{
        try(Stream<Path> all=Files.list(root)){
            String prefix=account+".properties"+suffix;
            return all.filter(p->p.getFileName().toString()
                    .startsWith(prefix))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] fingerprint(
        Path root,String account,String suffix
    )throws IOException{
        Path[] ls=leaves(root,account,suffix);
        String[] data=new String[ls.length];
        for(int i=0;i<ls.length;i++)
            data[i]=ls[i].getFileName()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(ls[i]));
        return data;
    }
    private static void negative(MailboxWriterKindEvidenceV2.Report r){
        check(!r.sourceAuthenticityVerified&&
            !r.completeHistoricalWriterPopulation&&
            !r.defaultWritersEnrolled&&!r.allFutureWritersExcluded&&
            !r.crashEpochProved&&!r.cleanupAuthorized&&
            !r.unlinkAuthorized&&!r.rolloverAuthorized&&
            !r.pruningAuthorized&&!r.grantAuthorized&&
            !r.replayAuthorized&&!r.sessionAuthorized&&
            !r.releaseAuthorized&&!r.clientAckAuthorized,
            "typed evidence conferred authority");
    }
    private static MailboxWriterKindEvidenceV2.Report inspect(
        FilePlayerRepository.PathResolver r,String account
    )throws IOException{
        MailboxWriterKindEvidenceV2.Report v=
            MailboxWriterKindEvidenceV2.inspect(r,account);
        negative(v);
        return v;
    }
    private static boolean fails(
        RunnableIO action,String marker
    )throws Exception{
        try{
            action.run();
            return false;
        }catch(IOException x){
            return String.valueOf(x.getMessage()).contains(marker);
        }
    }
    @FunctionalInterface
    private interface RunnableIO { void run()throws IOException; }
    private static String stageName(Path root,String name)throws IOException{
        Path[] ps=leaves(root,name,".g21114-writer-kind-");
        check(ps.length==2,"expected one v2 kind pair");
        for(Path p:ps)
            if(p.getFileName().toString().endsWith(".complete"))
                return p.toString();
        throw new AssertionError("missing typed COMPLETE");
    }
    private static String sha(String account,String uuid,String type,
                              String stage){
        return MailboxWriterKindEvidenceV2.PROTOCOL+"\nACCOUNT="+account+
            "\nATTEMPT="+uuid+"\nKIND="+type+
            "\nSTAGE="+stage+"\n";
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21114-kind-evidence-");
        boolean fourTyped=false,v1ArchiveUnmodified=false;
        boolean mixedMarkedUnknown=false,defaultUnrecorded=false;
        boolean strictCrashConservative=false;
        boolean tamperedPairRejected=false,orphanRejected=false;
        boolean unknownVersionRejected=false,foreignDenied=false;
        boolean fullLedgerDenied=false,noLiveGrant=false;
        boolean zeroLeases=false;
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(r);

            Seed zero=seed(world,EMPTY);
            strict.saveStrict(zero.proposal.preparedPreimage);
            defaultUnrecorded=
                inspect(r,EMPTY).status==
                    MailboxWriterKindEvidenceV2.Status
                        .NO_TYPED_EVIDENCE_UNKNOWN&&
                MailboxWriterAttemptEvidenceV1.inspect(r,EMPTY)
                    .recordCount==0;

            Seed four=seed(world,FOUR);
            strict.saveStrictWithWriterEvidence(
                four.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(four.proposal);
            WorldPlayerPersistence.PreparedAccountReservation reserved=
                world.persistence().reservePreparedAccount(
                    four.player,four.generation,four.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence done=
                world.persistence()
                    .publishReservedTerminalStrictlyWithWriterEvidence(
                        reserved,four.proposal,four.terminal,strict)
                    .get(15,TimeUnit.SECONDS);
            disk.recordConfirmedDiskTerminalWithWriterEvidence(
                four.proposal,done.strictReceipt);
            MailboxWriterKindEvidenceV2.Report typed=inspect(r,FOUR);
            MailboxWriterAttemptEvidenceV1.Report legacy=
                MailboxWriterAttemptEvidenceV1.inspect(r,FOUR);
            MailboxGuardedDiskCommitRecord.Observation diskState=
                disk.inspect(FOUR);
            fourTyped=typed.status==
                    MailboxWriterKindEvidenceV2.Status
                        .OBSERVED_TYPED_PAIRED_NO_AUTHORITY&&
                typed.typedBegins==4&&typed.typedCompletes==4&&
                typed.untypedV1Leaves==0&&
                typed.pairedByKind.size()==4&&
                typed.pairedByKind.getOrDefault(
                    MailboxWriterKindEvidenceV2.Kind.STRICT_PREPARED,0)==1&&
                typed.pairedByKind.getOrDefault(
                    MailboxWriterKindEvidenceV2.Kind.PREPARED_JOURNAL,0)==1&&
                typed.pairedByKind.getOrDefault(
                    MailboxWriterKindEvidenceV2.Kind.WORLD_TERMINAL,0)==1&&
                typed.pairedByKind.getOrDefault(
                    MailboxWriterKindEvidenceV2.Kind.DISK_COMMIT,0)==1&&
                legacy.started==4&&legacy.completed==4&&
                legacy.recordCount==8&&
                leaves(root,FOUR,".g21114-writer-kind-").length==8&&
                diskState.status==MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY&&
                !diskState.transactionCommitted&&
                !diskState.grantAuthorized&&noGrant(four);
            MailboxWriterEvidencePortableArchiveV1.Snapshot archive=
                MailboxWriterEvidencePortableArchiveV1.capture(r,FOUR);
            MailboxWriterEvidencePortableArchiveV1.Verified checked=
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    archive.portable,archive.sha256);
            v1ArchiveUnmodified=checked.started==4&&
                checked.completed==4&&checked.records==8&&
                !checked.cleanupAuthorized&&!checked.rolloverAuthorized;

            Seed mixed=seed(world,MIXED);
            strict.saveStrictWithWriterEvidence(
                mixed.proposal.preparedPreimage);
            MailboxWriterAttemptEvidenceV1.runRecorded(
                r,MIXED,()->Boolean.TRUE);
            MailboxWriterKindEvidenceV2.Report hybrid=inspect(r,MIXED);
            mixedMarkedUnknown=hybrid.status==
                    MailboxWriterKindEvidenceV2.Status
                        .MIXED_TYPED_AND_UNTYPED_UNKNOWN&&
                hybrid.typedBegins==1&&hybrid.typedCompletes==1&&
                hybrid.untypedV1Leaves==2&&
                MailboxWriterAttemptEvidenceV1.inspect(r,MIXED).started==2;

            Seed crash=seed(world,FAILED);
            strict.saveStrict(crash.proposal.preparedPreimage);
            StrictDurablePlayerSnapshotWriter faulty=
                new StrictDurablePlayerSnapshotWriter(r,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_ATOMIC_REPLACE)
                        throw new IOException("G21114_FAULT_BEFORE_MOVE");
                });
            byte[] before=Files.readAllBytes(r.resolve(FAILED));
            boolean faulted=fails(()->faulty.saveStrictWithWriterEvidence(
                crash.proposal.preparedPreimage),"G21114_FAULT_BEFORE_MOVE");
            MailboxWriterKindEvidenceV2.Report interrupted=
                inspect(r,FAILED);
            strictCrashConservative=faulted&&
                interrupted.status==MailboxWriterKindEvidenceV2.Status
                    .INCOMPLETE_TYPED_NO_AUTHORITY&&
                interrupted.typedBegins==1&&
                interrupted.typedCompletes==0&&
                MailboxWriterAttemptEvidenceV1.inspect(r,FAILED)
                    .status==MailboxWriterAttemptEvidenceV1.Status
                        .INCOMPLETE_ATTEMPT_NO_CLEANUP&&
                fails(()->strict.saveStrictWithWriterEvidence(
                    crash.proposal.preparedPreimage),
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL")&&
                Arrays.equals(before,Files.readAllBytes(r.resolve(FAILED)));

            Seed tamper=seed(world,TAMPER);
            strict.saveStrictWithWriterEvidence(
                tamper.proposal.preparedPreimage);
            Path complete=java.nio.file.Paths.get(
                stageName(root,TAMPER));
            String old=new String(Files.readAllBytes(complete),
                StandardCharsets.US_ASCII);
            Files.write(complete,old.replace(
                "KIND=STRICT_PREPARED","KIND=DISK_COMMIT")
                    .getBytes(StandardCharsets.US_ASCII));
            tamperedPairRejected=fails(()->inspect(r,TAMPER),
                "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP")&&
                fails(()->strict.saveStrictWithWriterEvidence(
                    tamper.proposal.preparedPreimage),
                    "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP");

            Seed orphan=seed(world,ORPHAN);
            strict.saveStrict(orphan.proposal.preparedPreimage);
            String uuid="00000000-0000-0000-0000-000000000000";
            Path orphanLeaf=root.resolve(ORPHAN+
                ".properties.g21114-writer-kind-v2-"+uuid+".begin");
            Files.write(orphanLeaf,sha(ORPHAN,uuid,"STRICT_PREPARED",
                "BEGIN").getBytes(StandardCharsets.US_ASCII));
            orphanRejected=fails(()->inspect(r,ORPHAN),
                "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP")&&
                fails(()->strict.saveStrictWithWriterEvidence(
                    orphan.proposal.preparedPreimage),
                    "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP");

            Seed foreign=seed(world,FOREIGN);
            strict.saveStrict(foreign.proposal.preparedPreimage);
            MailboxWriterAttemptEvidenceV1.runRecorded(
                r,FOREIGN,()->Boolean.TRUE);
            Path foreignOriginal=leaves(root,FOREIGN,
                ".g21103-writer-attempt-")[0];
            String fn=foreignOriginal.getFileName().toString();
            String fid=fn.substring(
                (FOREIGN+".properties.g21103-writer-attempt-v1-").length(),
                fn.lastIndexOf('.'));
            Path foreignLeaf=root.resolve(FOREIGN+
                ".properties.g21114-writer-kind-v2-"+fid+".begin");
            Files.write(foreignLeaf,sha("other-user",fid,
                "STRICT_PREPARED","BEGIN")
                    .getBytes(StandardCharsets.US_ASCII));
            foreignDenied=fails(()->inspect(r,FOREIGN),
                "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP");

            Seed version=seed(world,VERSION);
            strict.saveStrict(version.proposal.preparedPreimage);
            Files.write(root.resolve(VERSION+
                ".properties.g21114-writer-kind-v3-"+
                uuid+".begin"),new byte[]{7});
            unknownVersionRejected=fails(()->inspect(r,VERSION),
                "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP");

            Seed full=seed(world,FULL);
            strict.saveStrict(full.proposal.preparedPreimage);
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            byte[] fullBefore=Files.readAllBytes(r.resolve(FULL));
            String[] fullBeforeV1=fingerprint(
                root,FULL,".g21103-writer-attempt-");
            fullLedgerDenied=fails(
                ()->strict.saveStrictWithWriterEvidence(
                    full.proposal.preparedPreimage),
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL")&&
                Arrays.equals(fullBefore,Files.readAllBytes(r.resolve(FULL)))&&
                Arrays.equals(fullBeforeV1,fingerprint(
                    root,FULL,".g21103-writer-attempt-"))&&
                leaves(root,FULL,".g21114-writer-kind-").length==0;

            noLiveGrant=noGrant(zero)&&noGrant(four)&&noGrant(mixed)&&
                noGrant(crash)&&noGrant(tamper)&&noGrant(orphan)&&
                noGrant(foreign)&&noGrant(version)&&noGrant(full);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G21114_TYPED_KIND_DIAGNOSTICS"+
                " fourKindsPaired="+fourTyped+
                " unchangedPortableV1Archive="+v1ArchiveUnmodified+
                " mixedUntypedUnknown="+mixedMarkedUnknown+
                " oldDefaultUnrecorded="+defaultUnrecorded+
                " interruptedTypedBeginQuarantined="+
                    strictCrashConservative+
                " changedKindPairDenied="+tamperedPairRejected+
                " orphanKindDenied="+orphanRejected+
                " foreignAccountKindDenied="+foreignDenied+
                " unknownKindVersionDenied="+unknownVersionRejected+
                " fullV1LedgerStillVetoes="+fullLedgerDenied+
                " noLiveGrant="+noLiveGrant+
                " zeroJvmLeases="+zeroLeases);
            check(fourTyped&&v1ArchiveUnmodified&&mixedMarkedUnknown&&
                defaultUnrecorded&&strictCrashConservative&&
                tamperedPairRejected&&orphanRejected&&
                foreignDenied&&unknownVersionRejected&&
                fullLedgerDenied&&noLiveGrant&&zeroLeases,
                "real typed kind evidence regression");
            System.out.println("G21114_TYPED_V2_WRITER_KIND_PASS"+
                " fourRealKinds=true v1Unchanged=true"+
                " oldArchiveCompatible=true mixedLegacyUnknown=true"+
                " defaultWritersUnenrolled=true"+
                " typedSourceNotAuthenticated=true"+
                " historicalWritersNotFenced=true"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false session=false"+
                " release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unknown args");
        run();
    }
}
