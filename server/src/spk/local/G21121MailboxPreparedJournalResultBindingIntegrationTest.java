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
 * G21.121: opt-in genuine PREPARED journal and its post-confirmed
 * immutable result on the same v1/v2 UUID. Existing journal writers
 * stay unbound. This provides no live reward/cleanup/epoch authority.
 */
public final class G21121MailboxPreparedJournalResultBindingIntegrationTest {
    private static final String BOUND="g21121-bound";
    private static final String OLD="g21121-old";
    private static final String FAULT="g21121-fault";
    private static final String FULL="g21121-full";
    private static final String TAMPER="g21121-tamper";
    private static final String ORPHAN="g21121-orphan";
    private static final String VERSION="g21121-version";

    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.121 "+message);
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
        long generation=world.registerPlayer(player,account);
        String message=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            message,"G21.121 journal binding fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21121_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot s=
            player.mailbox().get(message);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,s));
        return new Seed(player,generation,
            MailboxSettlementPostimagePlanner.plan(
                player,generation,s));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(Path path)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path));
        StringBuilder result=new StringBuilder(64);
        for(byte b:bytes)result.append(
            String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    @FunctionalInterface private interface Action{
        void call()throws IOException;
    }
    private static boolean denied(Action a,String marker)throws IOException{
        try{a.call();return false;}
        catch(IOException e){
            for(Throwable x=e;x!=null;x=x.getCause())
                if(String.valueOf(x.getMessage()).contains(marker))
                    return true;
            return false;
        }
    }
    private static Path[] leaves(Path root,String account)
        throws IOException{
        try(Stream<Path> stream=Files.list(root)){
            return stream.filter(p->p.getFileName().toString()
                .startsWith(account+
                    ".properties.g21121-prepared-result-v3-"))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final String v2,account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r,String name)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,name);
            v2=MailboxWriterKindEvidenceV2.capture(r,name).sha256;
            account=sha(r.resolve(name));
            prepared=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(name));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(name));
        }
    }
    private static MailboxPreparedJournalResultBindingV3.Report report(
        FilePlayerRepository.PathResolver r,String name
    )throws IOException{
        MailboxPreparedJournalResultBindingV3.Report report=
            MailboxPreparedJournalResultBindingV3.inspect(r,name);
        check(!report.authenticatedWriterExecution&&
            !report.fullWriterPopulationKnown&&
            !report.futureWritersExcluded&&!report.atomicEpochProved&&
            !report.archiveRolloverAuthorized&&
            !report.recordPruningAuthorized&&!report.unlinkAuthorized&&
            !report.cleanupAuthorized&&!report.grantAuthorized&&
            !report.replayAuthorized&&!report.sessionAuthorized&&
            !report.releaseAuthorized&&!report.clientAckAuthorized,
            "result verifier manufactured authority");
        return report;
    }
    private static MailboxPreparedJournalResultBindingV3.Report strict(
        FilePlayerRepository.PathResolver r,String name,Pins pins
    )throws IOException{
        return MailboxPreparedJournalResultBindingV3
            .requirePinnedRealBoundPreparedJournal(
                r,name,pins.v1.portable,pins.v1.sha256,
                pins.account,pins.prepared,pins.commit,pins.v2);
    }
    private static StrictDurablePlayerSnapshotWriter.Receipt terminal(
        World world,Seed s,StrictDurablePlayerSnapshotWriter writer
    )throws Exception{
        WorldPlayerPersistence.PreparedAccountReservation token=
            world.persistence().reservePreparedAccount(
                s.player,s.generation,s.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence e=
            world.persistence()
                .publishReservedTerminalStrictlyWithWriterEvidence(
                    token,s.proposal,s.terminal,writer)
                .get(15L,TimeUnit.SECONDS);
        check(e.strictReceipt.matchesSnapshot(s.terminal),
            "terminal Receipt mismatch");
        return e.strictReceipt;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21121-prepared-binding-");
        boolean preparedBoundBeforeTerminal=false;
        boolean survivesTerminalTransition=false;
        boolean fullFourRealAndCommitBound=false;
        boolean oldG21110StillUnbound=false;
        boolean oldG21110StrictlyDenied=false;
        boolean forcedJournalUncertaintyBeginOnly=false;
        boolean retentionFullBeforeJournal=false;
        boolean alteredV3Denied=false,alteredJournalDenied=false;
        boolean orphanDenied=false,unknownVersionDenied=false;
        boolean originalPortableCompatible=false;
        boolean noLiveRewards=false,zeroLeaseLeaks=false;
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
            writer.saveStrictWithWriterEvidence(
                good.proposal.preparedPreimage);
            journal.publishPreparedIntentWithResultBinding(good.proposal);
            MailboxPreparedJournalResultBindingV3.Report pre=
                report(r,BOUND);
            preparedBoundBeforeTerminal=pre.status==
                MailboxPreparedJournalResultBindingV3.Status
                    .BOUND_PREPARED_JOURNAL_MATCH_NO_AUTHORITY&&
                pre.boundAttempts==1&&
                pre.preparedSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(
                            good.proposal.preparedPreimage))&&
                pre.journalBytesSha256.equals(
                    sha(journal.journalPath(BOUND)))&&
                leaves(root,BOUND).length==1;
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                terminal(world,good,writer);
            commit.recordConfirmedDiskTerminalWithResultBinding(
                good.proposal,receipt);
            Pins pins=new Pins(r,BOUND);
            MailboxPreparedJournalResultBindingV3.Report observed=
                report(r,BOUND);
            MailboxPreparedJournalResultBindingV3.Report matched=
                strict(r,BOUND,pins);
            MailboxCommitResultBindingV3.Report commitBound=
                MailboxCommitResultBindingV3.requirePinnedRealBoundCommit(
                    r,BOUND,pins.v1.portable,pins.v1.sha256,
                    pins.account,pins.prepared,pins.commit,pins.v2);
            survivesTerminalTransition=observed.status==
                MailboxPreparedJournalResultBindingV3.Status
                    .BOUND_PREPARED_JOURNAL_MATCH_NO_AUTHORITY&&
                observed.journalBytesSha256.equals(pins.prepared)&&
                matched.terminalSha256.equals(receipt.snapshotSha256)&&
                journal.inspect(BOUND).status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .TERMINAL_MATCH_NO_COMMIT;
            MailboxWriterAttemptEvidenceV1.Report v1=
                MailboxWriterAttemptEvidenceV1.inspect(r,BOUND);
            MailboxWriterKindEvidenceV2.Report v2=
                MailboxWriterKindEvidenceV2.inspect(r,BOUND);
            fullFourRealAndCommitBound=
                v1.recordCount==8&&v1.started==4&&v1.completed==4&&
                v2.typedBegins==4&&v2.typedCompletes==4&&
                commitBound.status==MailboxCommitResultBindingV3.Status
                    .BOUND_COMMIT_DISK_MATCH_NO_AUTHORITY&&
                commit.inspect(BOUND).diskCommitRecordMatched&&
                noGrant(good);

            // G21.110 opt-in remains v1/v2 only; no retroactive v3.
            Seed old=seed(world,OLD);
            writer.saveStrictWithWriterEvidence(
                old.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(
                old.proposal);
            MailboxPreparedJournalResultBindingV3.Report legacy=
                report(r,OLD);
            oldG21110StillUnbound=legacy.status==
                MailboxPreparedJournalResultBindingV3.Status
                    .NO_RESULT_BINDING_LEGACY_OR_UNKNOWN&&
                legacy.boundAttempts==0&&leaves(root,OLD).length==0;
            StrictDurablePlayerSnapshotWriter.Receipt oldReceipt=
                terminal(world,old,writer);
            commit.recordConfirmedDiskTerminalWithResultBinding(
                old.proposal,oldReceipt);
            Pins oldPins=new Pins(r,OLD);
            oldG21110StrictlyDenied=denied(
                ()->strict(r,OLD,oldPins),
                "G21.121 PREPARED_RESULT_BINDING_UNSAFE_NO_AUTHORITY");

            // Genuine G21.83 after-directory-force fault is not
            // confirmed. Journal may exist, but v1/v2 BEGIN only.
            Seed uncertain=seed(world,FAULT);
            writer.saveStrict(uncertain.proposal.preparedPreimage);
            MailboxDurableIdempotencyIntentJournal fault=
                new MailboxDurableIdempotencyIntentJournal(r,phase->{
                    if(phase==
                       MailboxDurableIdempotencyIntentJournal.Phase
                            .AFTER_DIRECTORY_FORCE)
                        throw new IOException(
                            "G21121_FORCED_POST_JOURNAL_DIRECTORY");
                });
            boolean failed=denied(
                ()->fault.publishPreparedIntentWithResultBinding(
                    uncertain.proposal),
                "G21.83 JOURNAL_PUBLICATION_UNCONFIRMED_NO_GRANT");
            MailboxWriterAttemptEvidenceV1.Report badV1=
                MailboxWriterAttemptEvidenceV1.inspect(r,FAULT);
            MailboxWriterKindEvidenceV2.Report badV2=
                MailboxWriterKindEvidenceV2.inspect(r,FAULT);
            forcedJournalUncertaintyBeginOnly=failed&&
                Files.exists(journal.journalPath(FAULT),
                    LinkOption.NOFOLLOW_LINKS)&&
                badV1.recordCount==1&&badV1.started==1&&
                badV1.completed==0&&
                badV2.typedBegins==1&&badV2.typedCompletes==0&&
                leaves(root,FAULT).length==0&&
                denied(()->report(r,FAULT),
                    "G21.121 PREPARED_RESULT_BINDING_UNSAFE_NO_AUTHORITY");

            // Shared 32-leaf v1 cap refuses before touching journal.
            Seed full=seed(world,FULL);
            writer.saveStrict(full.proposal.preparedPreimage);
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            retentionFullBeforeJournal=denied(
                ()->journal.publishPreparedIntentWithResultBinding(
                    full.proposal),
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL")&&
                !Files.exists(journal.journalPath(FULL),
                    LinkOption.NOFOLLOW_LINKS)&&
                leaves(root,FULL).length==0&&noGrant(full);

            Seed tamper=seed(world,TAMPER);
            writer.saveStrict(tamper.proposal.preparedPreimage);
            journal.publishPreparedIntentWithResultBinding(
                tamper.proposal);
            Path resultFile=leaves(root,TAMPER)[0];
            byte[] before=Files.readAllBytes(resultFile);
            byte[] damaged=before.clone();
            damaged[0]^=1;
            Files.write(resultFile,damaged);
            alteredV3Denied=denied(()->report(r,TAMPER),
                "G21.121 PREPARED_RESULT_BINDING_UNSAFE_NO_AUTHORITY");
            Files.write(resultFile,before);
            Path realJournal=journal.journalPath(TAMPER);
            byte[] originalJournal=Files.readAllBytes(realJournal);
            byte[] altered=originalJournal.clone();
            altered[0]^=1;
            Files.write(realJournal,altered);
            alteredJournalDenied=denied(()->report(r,TAMPER),
                "G21.121 PREPARED_RESULT_BINDING_UNSAFE_NO_AUTHORITY");
            Files.write(realJournal,originalJournal);

            Seed orphan=seed(world,ORPHAN);
            writer.saveStrict(orphan.proposal.preparedPreimage);
            Files.write(root.resolve(ORPHAN+
                ".properties.g21121-prepared-result-v3-"+
                "00000000-0000-0000-0000-000000000000.result"),
                "STRAY_RESULT".getBytes(StandardCharsets.US_ASCII));
            orphanDenied=denied(()->report(r,ORPHAN),
                "G21.121 PREPARED_RESULT_BINDING_UNSAFE_NO_AUTHORITY");

            Seed version=seed(world,VERSION);
            writer.saveStrict(version.proposal.preparedPreimage);
            Files.write(root.resolve(VERSION+
                ".properties.g21121-prepared-result-v3-v4-unknown"),
                new byte[]{1});
            unknownVersionDenied=denied(()->report(r,VERSION),
                "G21.121 PREPARED_RESULT_BINDING_UNSAFE_NO_AUTHORITY");

            MailboxWriterEvidencePortableArchiveV1.Verified archive=
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    pins.v1.portable,pins.v1.sha256);
            originalPortableCompatible=archive.records==8&&
                archive.started==4&&archive.completed==4&&
                !archive.cleanupAuthorized&&!archive.rolloverAuthorized;
            noLiveRewards=noGrant(good)&&noGrant(old)&&
                noGrant(uncertain)&&noGrant(full)&&noGrant(tamper)&&
                noGrant(orphan)&&noGrant(version);
            zeroLeaseLeaks=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21121_PREPARED_RESULT_DIAGNOSTICS"+
                " realBoundPreparedBeforeTerminal="+
                    preparedBoundBeforeTerminal+
                " boundJournalSurvivesCoherentTerminal="+
                    survivesTerminalTransition+
                " fourRealAndBoundCommitStillNoGrant="+
                    fullFourRealAndCommitBound+
                " existingG21110Unbound="+oldG21110StillUnbound+
                " oldUnboundNegativeGate="+oldG21110StrictlyDenied+
                " afterDirectoryForceBeginOnly="+
                    forcedJournalUncertaintyBeginOnly+
                " full32LeafPreflightVeto="+retentionFullBeforeJournal+
                " alteredV3Denied="+alteredV3Denied+
                " alteredJournalDenied="+alteredJournalDenied+
                " orphanResultDenied="+orphanDenied+
                " unknownVersionDenied="+unknownVersionDenied+
                " originalV1ArchiveCompatible="+
                    originalPortableCompatible+
                " noLiveRewards="+noLiveRewards+
                " zeroJvmLeases="+zeroLeaseLeaks);
            check(preparedBoundBeforeTerminal&&
                survivesTerminalTransition&&
                fullFourRealAndCommitBound&&
                oldG21110StillUnbound&&oldG21110StrictlyDenied&&
                forcedJournalUncertaintyBeginOnly&&
                retentionFullBeforeJournal&&alteredV3Denied&&
                alteredJournalDenied&&orphanDenied&&
                unknownVersionDenied&&originalPortableCompatible&&
                noLiveRewards&&zeroLeaseLeaks,
                "genuine G21.83 result UUID binding regression");
            System.out.println("G21121_BOUND_PREPARED_JOURNAL_PASS"+
                " realG2183Journal=true sameV1V2Uuid=true"+
                " realJournalSha256=true preparedAndTerminalHashes=true"+
                " genuineBoundCommitCoexists=true"+
                " formerDefaultAndG21110Unchanged=true"+
                " historicalWritersFenced=false atomicEpoch=false"+
                " hardwarePowerLossProved=false rollover=false"+
                " pruning=false cleanup=false unlink=false"+
                " grant=false replay=false session=false"+
                " release=false ack=false");
        }finally{
            try(Stream<Path> stream=Files.walk(root)){
                for(Path p:stream.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected test arguments");
        run();
    }
}
