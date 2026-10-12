package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.122: strict PREPARED save, exact actual replaced file binding,
 * and later terminal/journal corroboration. Old default/G21.109
 * remain unbound and none of these observations authorizes rewards.
 */
public final class G21122MailboxStrictPreparedResultBindingIntegrationTest {
    private static final String BOUND="g21122-bound";
    private static final String OLD="g21122-old";
    private static final String FAULT="g21122-fault";
    private static final String FULL="g21122-full";
    private static final String TAMPER="g21122-tamper";
    private static final String ORPHAN="g21122-orphan";
    private static final String VERSION="g21122-version";

    private static void check(boolean b,String why){
        if(!b)throw new AssertionError("G21.122 "+why);
    }
    private static FilePlayerRepository.PathResolver resolver(Path d){
        return a->d.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long gen,
            MailboxSettlementPostimagePlanner.Proposal plan){
            player=p;generation=gen;proposal=plan;
            terminal=MailboxAtomicTerminalSnapshot.compose(plan);
        }
    }
    private static Seed seed(World world,String name){
        WorldPlayer p=new WorldPlayer();
        long g=world.registerPlayer(p,name);
        String id=name+":gift";
        p.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.122 strict result","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21122_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot s=p.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,s));
        return new Seed(p,g,MailboxSettlementPostimagePlanner
            .plan(p,g,s));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String hash(Path file)throws Exception{
        byte[] data=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder out=new StringBuilder(64);
        for(byte b:data)
            out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    @FunctionalInterface interface Action{void call()throws IOException;}
    private static boolean veto(Action action,String expected)
        throws IOException{
        try{action.call();return false;}
        catch(IOException err){
            for(Throwable cause=err;cause!=null;cause=cause.getCause())
                if(String.valueOf(cause.getMessage()).contains(expected))
                    return true;
            return false;
        }
    }
    private static Path[] leaves(Path dir,String name)throws IOException{
        try(Stream<Path> ls=Files.list(dir)){
            return ls.filter(p->p.getFileName().toString().startsWith(
                name+".properties.g21122-strict-prepared-result-v3-"))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static final class Pins {
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final String v2,account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r,String name)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,name);
            v2=MailboxWriterKindEvidenceV2.capture(r,name).sha256;
            account=hash(r.resolve(name));
            prepared=hash(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(name));
            commit=hash(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(name));
        }
    }
    private static MailboxStrictPreparedResultBindingV3.Report observed(
        FilePlayerRepository.PathResolver r,String name
    )throws IOException{
        MailboxStrictPreparedResultBindingV3.Report result=
            MailboxStrictPreparedResultBindingV3.inspect(r,name);
        check(!result.authenticatedWriterExecution&&
            !result.completeHistoricalWriterPopulation&&
            !result.allFutureWritersFenced&&
            !result.atomicMultiFileEpochProved&&
            !result.archiveRolloverAuthorized&&
            !result.pruningAuthorized&&
            !result.cleanupAuthorized&&!result.unlinkAuthorized&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.sessionAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized,
            "result promoted into authority");
        return result;
    }
    private static StrictDurablePlayerSnapshotWriter.Receipt terminal(
        World world,Seed seed,StrictDurablePlayerSnapshotWriter writer
    )throws Exception{
        WorldPlayerPersistence.PreparedAccountReservation token=
            world.persistence().reservePreparedAccount(
                seed.player,seed.generation,seed.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence e=
            world.persistence()
                .publishReservedTerminalStrictlyWithWriterEvidence(
                    token,seed.proposal,seed.terminal,writer)
                .get(15L,TimeUnit.SECONDS);
        check(e.strictReceipt.matchesSnapshot(seed.terminal),
            "actual terminal receipt mismatch");
        return e.strictReceipt;
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21122-prepared-result-");
        boolean exactPreparedBound=false,laterTerminalBound=false;
        boolean realFourWithTwoBoundResults=false;
        boolean oldG21109Unbound=false,oldStrictGateRejects=false;
        boolean forceFaultBeginOnly=false,fullRetentionVeto=false;
        boolean resultTamperDenied=false,sourceTamperDenied=false;
        boolean orphanDenied=false,versionDenied=false;
        boolean originalArchiveCompatible=false,noRewards=false;
        boolean noLeaseLeaks=false;
        try(World world=World.isolatedForTest(
             60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord commit=
                new MailboxGuardedDiskCommitRecord(r);

            Seed good=seed(world,BOUND);
            StrictDurablePlayerSnapshotWriter.Receipt saved=
                writer.saveStrictWithResultBinding(
                    good.proposal.preparedPreimage);
            MailboxStrictPreparedResultBindingV3.Report before=
                observed(r,BOUND);
            exactPreparedBound=before.status==
                MailboxStrictPreparedResultBindingV3.Status
                    .BOUND_CURRENT_PREPARED_BYTES_NO_AUTHORITY&&
                before.originalPreparedFileStillPresent&&
                before.boundAttempts==1&&
                before.preparedSha256.equals(saved.snapshotSha256)&&
                before.strictFileSha256.equals(hash(r.resolve(BOUND)))&&
                leaves(root,BOUND).length==1;
            journal.publishPreparedIntentWithResultBinding(
                good.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt terminalReceipt=
                terminal(world,good,writer);
            commit.recordConfirmedDiskTerminalWithResultBinding(
                good.proposal,terminalReceipt);
            Pins pins=new Pins(r,BOUND);
            MailboxStrictPreparedResultBindingV3.Report after=
                observed(r,BOUND);
            MailboxStrictPreparedResultBindingV3.Report strict=
                MailboxStrictPreparedResultBindingV3
                    .requirePinnedRealBoundStrictPrepared(
                        r,BOUND,pins.v1.portable,pins.v1.sha256,
                        pins.account,pins.prepared,pins.commit,pins.v2);
            laterTerminalBound=after.status==
                MailboxStrictPreparedResultBindingV3.Status
                    .BOUND_HISTORICAL_PREPARED_WITH_TERMINAL_JOURNAL_NO_AUTHORITY&&
                !after.originalPreparedFileStillPresent&&
                strict.preparedSha256.equals(saved.snapshotSha256)&&
                strict.strictFileSha256.equals(
                    before.strictFileSha256)&&
                !strict.strictFileSha256.equals(
                    hash(r.resolve(BOUND)));
            MailboxPreparedJournalResultBindingV3.Report prepared=
                MailboxPreparedJournalResultBindingV3
                    .requirePinnedRealBoundPreparedJournal(
                        r,BOUND,pins.v1.portable,pins.v1.sha256,
                        pins.account,pins.prepared,pins.commit,pins.v2);
            MailboxCommitResultBindingV3.Report committed=
                MailboxCommitResultBindingV3.requirePinnedRealBoundCommit(
                    r,BOUND,pins.v1.portable,pins.v1.sha256,
                    pins.account,pins.prepared,pins.commit,pins.v2);
            MailboxWriterAttemptEvidenceV1.Report v1=
                MailboxWriterAttemptEvidenceV1.inspect(r,BOUND);
            realFourWithTwoBoundResults=v1.recordCount==8&&
                v1.started==4&&v1.completed==4&&
                prepared.boundAttempts==1&&committed.boundAttempts==1&&
                commit.inspect(BOUND).diskCommitRecordMatched&&noGrant(good);

            Seed old=seed(world,OLD);
            writer.saveStrictWithWriterEvidence(
                old.proposal.preparedPreimage);
            oldG21109Unbound=observed(r,OLD).status==
                MailboxStrictPreparedResultBindingV3.Status
                    .NO_RESULT_BINDING_LEGACY_OR_UNKNOWN&&
                leaves(root,OLD).length==0;
            journal.publishPreparedIntentWithResultBinding(
                old.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt oldReceipt=
                terminal(world,old,writer);
            commit.recordConfirmedDiskTerminalWithResultBinding(
                old.proposal,oldReceipt);
            Pins oldPins=new Pins(r,OLD);
            oldStrictGateRejects=veto(()->
                MailboxStrictPreparedResultBindingV3
                    .requirePinnedRealBoundStrictPrepared(
                        r,OLD,oldPins.v1.portable,oldPins.v1.sha256,
                        oldPins.account,oldPins.prepared,oldPins.commit,
                        oldPins.v2),
                "G21.122 STRICT_PREPARED_RESULT_UNSAFE_NO_AUTHORITY");

            Seed faulty=seed(world,FAULT);
            StrictDurablePlayerSnapshotWriter injected=
                new StrictDurablePlayerSnapshotWriter(r,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .AFTER_DIRECTORY_FORCE)
                        throw new IOException(
                            "G21122_UNCERTAIN_AFTER_DIRECTORY_FORCE");
                });
            boolean threw=veto(
                ()->injected.saveStrictWithResultBinding(
                    faulty.proposal.preparedPreimage),
                "atomic replacement occurred; durability unconfirmed");
            MailboxWriterAttemptEvidenceV1.Report bad=
                MailboxWriterAttemptEvidenceV1.inspect(r,FAULT);
            MailboxWriterKindEvidenceV2.Report badTyped=
                MailboxWriterKindEvidenceV2.inspect(r,FAULT);
            forceFaultBeginOnly=threw&&
                Files.exists(r.resolve(FAULT),LinkOption.NOFOLLOW_LINKS)&&
                bad.recordCount==1&&bad.started==1&&bad.completed==0&&
                badTyped.typedBegins==1&&badTyped.typedCompletes==0&&
                leaves(root,FAULT).length==0&&
                veto(()->observed(r,FAULT),
                    "G21.122 STRICT_PREPARED_RESULT_UNSAFE_NO_AUTHORITY");

            Seed full=seed(world,FULL);
            writer.saveStrict(full.proposal.preparedPreimage);
            String oldHash=hash(r.resolve(FULL));
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            fullRetentionVeto=veto(()->
                writer.saveStrictWithResultBinding(
                    full.proposal.preparedPreimage),
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL")&&
                oldHash.equals(hash(r.resolve(FULL)))&&
                leaves(root,FULL).length==0;

            Seed tamper=seed(world,TAMPER);
            writer.saveStrictWithResultBinding(
                tamper.proposal.preparedPreimage);
            Path leaf=leaves(root,TAMPER)[0];
            byte[] correct=Files.readAllBytes(leaf);
            byte[] wrong=correct.clone();
            wrong[0]^=1;
            Files.write(leaf,wrong);
            resultTamperDenied=veto(()->observed(r,TAMPER),
                "G21.122 STRICT_PREPARED_RESULT_UNSAFE_NO_AUTHORITY");
            Files.write(leaf,correct);
            Path canonical=r.resolve(TAMPER);
            byte[] actual=Files.readAllBytes(canonical);
            byte[] altered=actual.clone();
            altered[0]^=1;
            Files.write(canonical,altered);
            sourceTamperDenied=veto(()->observed(r,TAMPER),
                "G21.122 STRICT_PREPARED_RESULT_UNSAFE_NO_AUTHORITY");
            Files.write(canonical,actual);

            Seed orphan=seed(world,ORPHAN);
            writer.saveStrict(orphan.proposal.preparedPreimage);
            Files.write(root.resolve(ORPHAN+
                ".properties.g21122-strict-prepared-result-v3-"+
                "00000000-0000-0000-0000-000000000000.result"),
                "ORPHAN".getBytes(StandardCharsets.US_ASCII));
            orphanDenied=veto(()->observed(r,ORPHAN),
                "G21.122 STRICT_PREPARED_RESULT_UNSAFE_NO_AUTHORITY");
            Seed version=seed(world,VERSION);
            writer.saveStrict(version.proposal.preparedPreimage);
            Files.write(root.resolve(VERSION+
                ".properties.g21122-strict-prepared-result-v3-unknown"),
                new byte[]{3});
            versionDenied=veto(()->observed(r,VERSION),
                "G21.122 STRICT_PREPARED_RESULT_UNSAFE_NO_AUTHORITY");

            MailboxWriterEvidencePortableArchiveV1.Verified archived=
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    pins.v1.portable,pins.v1.sha256);
            originalArchiveCompatible=archived.records==8&&
                archived.started==4&&archived.completed==4&&
                !archived.cleanupAuthorized&&!archived.rolloverAuthorized;
            noRewards=noGrant(good)&&noGrant(old)&&noGrant(faulty)&&
                noGrant(full)&&noGrant(tamper)&&noGrant(orphan)&&
                noGrant(version);
            noLeaseLeaks=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21122_STRICT_RESULT_DIAGNOSTICS"+
                " realPreparedRawBytesBound="+exactPreparedBound+
                " originalPreparedCorroboratedAfterTerminal="+
                    laterTerminalBound+
                " realFourWritersPlusJournalCommitBindings="+
                    realFourWithTwoBoundResults+
                " oldG21109Unbound="+oldG21109Unbound+
                " oldUnboundStrictGateDenied="+oldStrictGateRejects+
                " postforceFaultBeginOnly="+forceFaultBeginOnly+
                " shared32LeafRetentionVeto="+fullRetentionVeto+
                " modifiedV3Denied="+resultTamperDenied+
                " modifiedPreparedFileDenied="+sourceTamperDenied+
                " orphanV3Denied="+orphanDenied+
                " unknownV3VersionDenied="+versionDenied+
                " originalV1PortableCompatible="+originalArchiveCompatible+
                " noLiveRewards="+noRewards+
                " zeroJvmLeases="+noLeaseLeaks);
            check(exactPreparedBound&&laterTerminalBound&&
                realFourWithTwoBoundResults&&oldG21109Unbound&&
                oldStrictGateRejects&&forceFaultBeginOnly&&
                fullRetentionVeto&&resultTamperDenied&&
                sourceTamperDenied&&orphanDenied&&versionDenied&&
                originalArchiveCompatible&&noRewards&&noLeaseLeaks,
                "real strict result evidence acceptance");
            System.out.println("G21122_BOUND_STRICT_PREPARED_PASS"+
                " genuineStrictReceipt=true actualPreparedDiskSha256=true"+
                " sameV1V2Uuid=true laterTerminalJournalCorroborated=true"+
                " journalAndCommitBindingsCompatible=true"+
                " historicalWriterFence=false atomicEpoch=false"+
                " hardwarePowerLossProved=false rollover=false"+
                " pruning=false cleanup=false unlink=false grant=false"+
                " replay=false session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path file:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
