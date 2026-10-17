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
 * G21.123: genuine reserved G21.66 World FIFO publication with real
 * result binding on its SAME forced v1/v2 WORLD_TERMINAL UUID. Only
 * forensic structural evidence, not World live apply or grant.
 */
public final class G21123MailboxWorldTerminalResultBindingIntegrationTest {
    private static final String BOUND="g21123-bound";
    private static final String OLD="g21123-old";
    private static final String FAULT="g21123-fault";
    private static final String FULL="g21123-full";
    private static final String TAMPER="g21123-tamper";
    private static final String ORPHAN="g21123-orphan";
    private static final String VERSION="g21123-version";

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.123 "+reason);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long gen,
             MailboxSettlementPostimagePlanner.Proposal pr){
            player=p;generation=gen;proposal=pr;
            terminal=MailboxAtomicTerminalSnapshot.compose(pr);
        }
    }
    private static Seed seed(World world,String name){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,name);
        String id=name+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.123 terminal result","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21123_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot selected=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,selected));
        return new Seed(player,generation,
            MailboxSettlementPostimagePlanner.plan(
                player,generation,selected));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(Path path)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path));
        StringBuilder out=new StringBuilder(64);
        for(byte b:hash)
            out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    @FunctionalInterface
    private interface Action {void run()throws Exception;}
    private static boolean veto(Action a,String marker)throws Exception{
        try{a.run();return false;}
        catch(Exception e){
            for(Throwable t=e;t!=null;t=t.getCause())
                if(String.valueOf(t.getMessage()).contains(marker))
                    return true;
            return false;
        }
    }
    private static Path[] leaves(Path root,String account)
        throws IOException{
        try(Stream<Path> paths=Files.list(root)){
            return paths.filter(p->p.getFileName().toString()
                .startsWith(account+
                    ".properties.g21123-world-terminal-result-v3-"))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static class Pins {
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
    private static MailboxWorldTerminalResultBindingV3.Report read(
        FilePlayerRepository.PathResolver r,String name
    )throws IOException{
        MailboxWorldTerminalResultBindingV3.Report report=
            MailboxWorldTerminalResultBindingV3.inspect(r,name);
        check(!report.independentWriterSignatureProved&&
            !report.historicalWriterCensusProved&&
            !report.defaultAndRawWritersExcluded&&
            !report.futureWriterGenerationsFenced&&
            !report.crossJvmAtomicEpochProved&&
            !report.hardwarePowerLossDurabilityProved&&
            !report.cleanupAuthorized&&!report.unlinkAuthorized&&
            !report.pruningAuthorized&&!report.archiveRolloverAuthorized&&
            !report.grantAuthorized&&!report.replayAuthorized&&
            !report.sessionAuthorized&&!report.releaseAuthorized&&
            !report.clientAckAuthorized,
            "result manufactured forbidden authority");
        return report;
    }
    private static MailboxWorldTerminalResultBindingV3.Report strict(
        FilePlayerRepository.PathResolver r,String name,Pins pins
    )throws IOException{
        return MailboxWorldTerminalResultBindingV3
            .requirePinnedRealBoundWorldTerminal(
                r,name,pins.v1.portable,pins.v1.sha256,pins.account,
                pins.prepared,pins.commit,pins.v2);
    }
    private static StrictDurablePlayerSnapshotWriter.Receipt terminal(
        World world,Seed seed,StrictDurablePlayerSnapshotWriter writer,
        boolean bound
    )throws Exception{
        WorldPlayerPersistence.PreparedAccountReservation ticket=
            world.persistence().reservePreparedAccount(
                seed.player,seed.generation,seed.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence completed=
            (bound
              ?world.persistence().publishReservedTerminalStrictlyWithResultBinding(
                  ticket,seed.proposal,seed.terminal,writer)
              :world.persistence().publishReservedTerminalStrictlyWithWriterEvidence(
                  ticket,seed.proposal,seed.terminal,writer))
                .get(15,TimeUnit.SECONDS);
        check(completed.strictReceipt.matchesSnapshot(seed.terminal)&&
            !completed.grantAuthorized&&!completed.replayAuthorized&&
            !completed.releaseAuthorized&&!completed.clientAckAuthorized,
            "terminal Receipt is not real or grants reward");
        return completed.strictReceipt;
    }
    private static void prepare(
        Seed s,StrictDurablePlayerSnapshotWriter writer,
        MailboxDurableIdempotencyIntentJournal journal
    )throws Exception{
        writer.saveStrictWithResultBinding(s.proposal.preparedPreimage);
        journal.publishPreparedIntentWithResultBinding(s.proposal);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21123-world-result-");
        boolean boundBeforeCommit=false,fourBoundWriters=false;
        boolean oldG21113Unbound=false,oldStrictGateDenied=false;
        boolean uncertainBeginOnly=false,capacityVetoBeforeTerminal=false;
        boolean modifiedV3Denied=false,modifiedTerminalDenied=false;
        boolean orphanDenied=false,versionDenied=false;
        boolean v1ArchiveUnchanged=false,noRewards=false,zeroLocks=false;
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
            prepare(good,writer,journal);
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                terminal(world,good,writer,true);
            MailboxWorldTerminalResultBindingV3.Report early=
                read(r,BOUND);
            boundBeforeCommit=early.status==
                MailboxWorldTerminalResultBindingV3.Status
                    .BOUND_REAL_TERMINAL_DISK_MATCH_NO_AUTHORITY&&
                early.boundAttempts==1&&
                early.preparedSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(
                            good.proposal.preparedPreimage))&&
                early.terminalSha256.equals(receipt.snapshotSha256)&&
                early.terminalFileBytesSha256.equals(
                    sha(r.resolve(BOUND)))&&
                leaves(root,BOUND).length==1&&noGrant(good);
            disk.recordConfirmedDiskTerminalWithResultBinding(
                good.proposal,receipt);
            Pins pins=new Pins(r,BOUND);
            MailboxWorldTerminalResultBindingV3.Report observed=
                strict(r,BOUND,pins);
            MailboxStrictPreparedResultBindingV3.Report strictPrepared=
                MailboxStrictPreparedResultBindingV3
                    .requirePinnedRealBoundStrictPrepared(
                        r,BOUND,pins.v1.portable,pins.v1.sha256,
                        pins.account,pins.prepared,pins.commit,pins.v2);
            MailboxPreparedJournalResultBindingV3.Report boundJournal=
                MailboxPreparedJournalResultBindingV3
                    .requirePinnedRealBoundPreparedJournal(
                        r,BOUND,pins.v1.portable,pins.v1.sha256,
                        pins.account,pins.prepared,pins.commit,pins.v2);
            MailboxCommitResultBindingV3.Report boundCommit=
                MailboxCommitResultBindingV3
                    .requirePinnedRealBoundCommit(
                        r,BOUND,pins.v1.portable,pins.v1.sha256,
                        pins.account,pins.prepared,pins.commit,pins.v2);
            MailboxWriterAttemptEvidenceV1.Report v1=
                MailboxWriterAttemptEvidenceV1.inspect(r,BOUND);
            MailboxWriterKindEvidenceV2.Report v2=
                MailboxWriterKindEvidenceV2.inspect(r,BOUND);
            fourBoundWriters=v1.recordCount==8&&v1.started==4&&
                v1.completed==4&&v2.typedBegins==4&&
                v2.typedCompletes==4&&
                observed.boundAttempts==1&&
                observed.terminalSha256.equals(
                    boundCommit.terminalSha256)&&
                observed.terminalFileBytesSha256.equals(pins.account)&&
                strictPrepared.boundAttempts==1&&
                boundJournal.boundAttempts==1&&
                boundCommit.boundAttempts==1&&
                disk.inspect(BOUND).diskCommitRecordMatched&&noGrant(good);

            // Old G21.113 remains typed-only even with a genuine
            // terminal Receipt and a G21.120-bound COMMIT afterward.
            Seed legacy=seed(world,OLD);
            prepare(legacy,writer,journal);
            StrictDurablePlayerSnapshotWriter.Receipt oldReceipt=
                terminal(world,legacy,writer,false);
            disk.recordConfirmedDiskTerminalWithResultBinding(
                legacy.proposal,oldReceipt);
            Pins oldPins=new Pins(r,OLD);
            oldG21113Unbound=read(r,OLD).status==
                MailboxWorldTerminalResultBindingV3.Status
                    .NO_RESULT_BINDING_LEGACY_OR_UNKNOWN&&
                leaves(root,OLD).length==0;
            oldStrictGateDenied=veto(
                ()->strict(r,OLD,oldPins),
                "G21.123 WORLD_TERMINAL_RESULT_UNSAFE_NO_AUTHORITY");

            // Actual World FIFO strict write may atomically REPLACE
            // the account before directory-force fault. Never
            // manufacture v3 or COMPLETE; real v1/v2 BEGIN persists.
            Seed fault=seed(world,FAULT);
            writer.saveStrict(fault.proposal.preparedPreimage);
            journal.publishPreparedIntent(fault.proposal);
            StrictDurablePlayerSnapshotWriter faultWriter=
                new StrictDurablePlayerSnapshotWriter(r,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                        .AFTER_DIRECTORY_FORCE)
                        throw new IOException(
                            "G21123_WORLD_TERMINAL_POSTFORCE_FAULT");
                });
            WorldPlayerPersistence.PreparedAccountReservation token=
                world.persistence().reservePreparedAccount(
                    fault.player,fault.generation,fault.proposal);
            boolean injected=veto(
                ()->world.persistence()
                    .publishReservedTerminalStrictlyWithResultBinding(
                        token,fault.proposal,fault.terminal,faultWriter)
                    .get(15,TimeUnit.SECONDS),
                "atomic replacement occurred; durability unconfirmed");
            MailboxWriterAttemptEvidenceV1.Report failedV1=
                MailboxWriterAttemptEvidenceV1.inspect(r,FAULT);
            MailboxWriterKindEvidenceV2.Report failedV2=
                MailboxWriterKindEvidenceV2.inspect(r,FAULT);
            uncertainBeginOnly=injected&&
                Files.exists(r.resolve(FAULT),LinkOption.NOFOLLOW_LINKS)&&
                failedV1.recordCount==1&&
                failedV1.started==1&&failedV1.completed==0&&
                failedV2.typedBegins==1&&failedV2.typedCompletes==0&&
                leaves(root,FAULT).length==0&&noGrant(fault);

            Seed full=seed(world,FULL);
            writer.saveStrict(full.proposal.preparedPreimage);
            journal.publishPreparedIntent(full.proposal);
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            String before=sha(r.resolve(FULL));
            WorldPlayerPersistence.PreparedAccountReservation cap=
                world.persistence().reservePreparedAccount(
                    full.player,full.generation,full.proposal);
            capacityVetoBeforeTerminal=veto(
                ()->world.persistence()
                    .publishReservedTerminalStrictlyWithResultBinding(
                        cap,full.proposal,full.terminal,writer)
                    .get(15,TimeUnit.SECONDS),
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL")&&
                before.equals(sha(r.resolve(FULL)))&&
                leaves(root,FULL).length==0&&noGrant(full);

            Seed tamper=seed(world,TAMPER);
            writer.saveStrict(tamper.proposal.preparedPreimage);
            journal.publishPreparedIntent(tamper.proposal);
            terminal(world,tamper,writer,true);
            Path result=leaves(root,TAMPER)[0];
            byte[] original=Files.readAllBytes(result);
            byte[] changed=original.clone();changed[0]^=1;
            Files.write(result,changed);
            modifiedV3Denied=veto(()->read(r,TAMPER),
                "G21.123 WORLD_TERMINAL_RESULT_UNSAFE_NO_AUTHORITY");
            Files.write(result,original);
            Path actual=r.resolve(TAMPER);
            byte[] terminalRaw=Files.readAllBytes(actual);
            byte[] different=terminalRaw.clone();different[0]^=1;
            Files.write(actual,different);
            modifiedTerminalDenied=veto(()->read(r,TAMPER),
                "G21.123 WORLD_TERMINAL_RESULT_UNSAFE_NO_AUTHORITY");
            Files.write(actual,terminalRaw);

            Seed orphan=seed(world,ORPHAN);
            writer.saveStrict(orphan.proposal.preparedPreimage);
            Files.write(root.resolve(ORPHAN+
                ".properties.g21123-world-terminal-result-v3-"+
                "00000000-0000-0000-0000-000000000000.result"),
                "ORPHAN".getBytes(StandardCharsets.US_ASCII));
            orphanDenied=veto(()->read(r,ORPHAN),
                "G21.123 WORLD_TERMINAL_RESULT_UNSAFE_NO_AUTHORITY");
            Seed version=seed(world,VERSION);
            writer.saveStrict(version.proposal.preparedPreimage);
            Files.write(root.resolve(VERSION+
                ".properties.g21123-world-terminal-result-v3-unknown"),
                new byte[]{3});
            versionDenied=veto(()->read(r,VERSION),
                "G21.123 WORLD_TERMINAL_RESULT_UNSAFE_NO_AUTHORITY");

            MailboxWriterEvidencePortableArchiveV1.Verified archive=
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    pins.v1.portable,pins.v1.sha256);
            v1ArchiveUnchanged=archive.records==8&&
                archive.started==4&&archive.completed==4&&
                !archive.cleanupAuthorized&&!archive.rolloverAuthorized;
            noRewards=noGrant(good)&&noGrant(legacy)&&noGrant(fault)&&
                noGrant(full)&&noGrant(tamper)&&noGrant(orphan)&&
                noGrant(version);
            zeroLocks=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21123_TERMINAL_RESULT_DIAGNOSTICS"+
                " terminalFileBoundBeforeCommit="+boundBeforeCommit+
                " fourRealWriterResultsBound="+fourBoundWriters+
                " oldG21113TerminalStillUnbound="+oldG21113Unbound+
                " oldUnboundStrictlyDenied="+oldStrictGateDenied+
                " worldTerminalPostforceBeginOnly="+uncertainBeginOnly+
                " full32LeafVetoBeforeTerminal="+capacityVetoBeforeTerminal+
                " modifiedV3Denied="+modifiedV3Denied+
                " modifiedTerminalFileDenied="+modifiedTerminalDenied+
                " orphanResultDenied="+orphanDenied+
                " unknownVersionDenied="+versionDenied+
                " originalV1ArchiveCompatible="+v1ArchiveUnchanged+
                " noLiveRewards="+noRewards+" zeroJvmLeases="+zeroLocks);
            check(boundBeforeCommit&&fourBoundWriters&&
                oldG21113Unbound&&oldStrictGateDenied&&
                uncertainBeginOnly&&capacityVetoBeforeTerminal&&
                modifiedV3Denied&&modifiedTerminalDenied&&
                orphanDenied&&versionDenied&&v1ArchiveUnchanged&&
                noRewards&&zeroLocks,
                "real World terminal UUID/result binding regression");
            System.out.println("G21123_BOUND_WORLD_TERMINAL_PASS"+
                " reservedWorldFifoPreserved=true"+
                " fourOptinWriterResultsObserved=true"+
                " exactTerminalReceiptAndDiskSha=true"+
                " originalV1V2SameUuid=true"+
                " oldWorldTerminalMethodsUnchanged=true"+
                " historicalDefaultRawWriterFence=false"+
                " atomicEpoch=false hardwarePowerLossProved=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false"+
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
        check(args.length==0,"unexpected arguments");
        run();
    }
}
