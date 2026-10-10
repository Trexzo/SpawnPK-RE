package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.96: eight independent fresh-JVM restarts after real filesystem
 * negative-evidence damage. Every observed case must fail closed.
 */
public final class G2196MailboxDamagedEvidenceRestartIntegrationTest {
    private enum Fault {
        CORRUPT_COMMIT(
            MailboxGuardedDiskCommitRecord.Status
                .INVALID_RECORD_QUARANTINE),
        MISSING_COMMIT(
            MailboxGuardedDiskCommitRecord.Status.ABSENT),
        MISSING_JOURNAL(
            MailboxGuardedDiskCommitRecord.Status
                .INTENT_CONFLICT_QUARANTINE),
        CORRUPT_JOURNAL(
            MailboxGuardedDiskCommitRecord.Status
                .INTENT_CONFLICT_QUARANTINE),
        FOREIGN_JOURNAL(
            MailboxGuardedDiskCommitRecord.Status
                .INTENT_CONFLICT_QUARANTINE),
        STALE_PREPARED_ACCOUNT(
            MailboxGuardedDiskCommitRecord.Status
                .PREPARED_NOT_COMMITTED),
        MISSING_ACCOUNT(
            MailboxGuardedDiskCommitRecord.Status
                .MISSING_ACCOUNT_QUARANTINE),
        BOTH_SIDECARS_MISSING(
            MailboxGuardedDiskCommitRecord.Status.ABSENT);

        final MailboxGuardedDiskCommitRecord.Status expected;
        Fault(MailboxGuardedDiskCommitRecord.Status status){
            expected=status;
        }
        String account(){
            return "g2196-"+name().toLowerCase(Locale.ROOT)
                .replace('_','-');
        }
    }

    private static final class Fixture {
        final PlayerSnapshot prepared;
        Fixture(PlayerSnapshot snapshot){prepared=snapshot;}
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.96 "+message);
    }

    private static FilePlayerRepository.PathResolver paths(Path root){
        return a->root.resolve(a+".properties");
    }

    private static Fixture committed(
        World source,String account,
        FilePlayerRepository.PathResolver resolver
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=source.registerPlayer(player,account);
        String messageId=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            messageId,"No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2196_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            player.mailbox().get(messageId);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,selected));
        MailboxSettlementPostimagePlanner.Proposal plan=
            MailboxSettlementPostimagePlanner.plan(
                player,generation,selected);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(resolver);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(resolver);
        MailboxGuardedDiskCommitRecord commit=
            new MailboxGuardedDiskCommitRecord(resolver);
        writer.saveStrict(plan.preparedPreimage);
        journal.publishPreparedIntent(plan);
        PlayerSnapshot terminal=
            MailboxAtomicTerminalSnapshot.compose(plan);
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            writer.saveStrictTerminalForWorld(
                terminal,resolver.resolve(account),
                StrictDurablePlayerSnapshotWriter
                    .canonicalSnapshotSha256(plan.preparedPreimage),
                ()->{},()->{});
        require(receipt.matchesSnapshot(terminal),
            "real strict terminal receipt missing");
        commit.recordConfirmedDiskTerminal(plan,receipt);
        require(selected.claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                player.bank().inventorySlots()==0,
            "original owner never received a reward");
        return new Fixture(plan.preparedPreimage);
    }

    private static void damage(
        Path root,Fault fault,Fixture fixture,
        String donor
    )throws Exception{
        FilePlayerRepository.PathResolver resolver=paths(root);
        String account=fault.account();
        Path file=resolver.resolve(account);
        Path journal=new MailboxDurableIdempotencyIntentJournal(resolver)
            .journalPath(account);
        Path commit=new MailboxGuardedDiskCommitRecord(resolver)
            .recordPath(account);
        switch(fault){
            case CORRUPT_COMMIT:
                Files.write(commit,new byte[]{1,2,3,4});
                return;
            case MISSING_COMMIT:
                Files.delete(commit);
                return;
            case MISSING_JOURNAL:
                Files.delete(journal);
                return;
            case CORRUPT_JOURNAL:
                Files.write(journal,new byte[]{4,3,2,1});
                return;
            case FOREIGN_JOURNAL:
                Files.copy(
                    new MailboxDurableIdempotencyIntentJournal(resolver)
                        .journalPath(donor),
                    journal,java.nio.file.StandardCopyOption
                        .REPLACE_EXISTING);
                return;
            case STALE_PREPARED_ACCOUNT:
                new StrictDurablePlayerSnapshotWriter(resolver)
                    .saveStrict(fixture.prepared);
                return;
            case MISSING_ACCOUNT:
                Files.delete(file);
                return;
            case BOTH_SIDECARS_MISSING:
                Files.delete(commit);
                Files.delete(journal);
                return;
            default:
                throw new AssertionError("G21.96 unknown fault");
        }
    }

    private static byte[][] readLeaves(
        Path root,String account
    )throws IOException{
        FilePlayerRepository.PathResolver resolver=paths(root);
        Path file=resolver.resolve(account);
        return new byte[][]{
            Files.exists(file)?
                Files.readAllBytes(file):null,
            Files.exists(new MailboxDurableIdempotencyIntentJournal(resolver)
                        .journalPath(account))?
                Files.readAllBytes(
                    new MailboxDurableIdempotencyIntentJournal(resolver)
                        .journalPath(account)):null,
            Files.exists(new MailboxGuardedDiskCommitRecord(resolver)
                        .recordPath(account))?
                Files.readAllBytes(
                    new MailboxGuardedDiskCommitRecord(resolver)
                        .recordPath(account)):null
        };
    }

    private static boolean sameLeaves(byte[][] first,byte[][] second){
        for(int i=0;i<3;i++)
            if(!Arrays.equals(first[i],second[i]))
                return false;
        return true;
    }

    private static void inspectFreshJVM(
        Path root,Fault fault
    )throws Exception{
        final String account=fault.account();
        final FilePlayerRepository.PathResolver resolver=paths(root);
        FilePlayerRepository repository=new FilePlayerRepository(resolver);
        MailboxGuardedDiskCommitRecord disk=
            new MailboxGuardedDiskCommitRecord(resolver);
        byte[][] before=readLeaves(root,account);
        MailboxGuardedDiskCommitRecord.Observation status=
            disk.inspect(account);
        require(status.status==fault.expected,
            "wrong negative forensic status expected="+fault.expected+
            " actual="+status.status+" account="+account);
        require(!status.transactionCommitted&&
                !status.liveApplied&&!status.grantAuthorized&&
                !status.replayAuthorized&&!status.restartAdmissionAuthorized&&
                !status.releaseAuthorized&&!status.clientAckAuthorized,
            "damaged COMMIT never confers positive authority");

        boolean detachedRejected=false;
        try{
            new MailboxCommittedDetachedRestartRecovery(resolver)
                .recoverDetached(account);
        }catch(IOException expected){
            detachedRejected=true;
        }
        require(detachedRejected,
            "G21.89 detached recovery must reject compromised evidence");

        boolean directWorldReadDenied=false;
        try{
            repository.loadForWorldSession(account);
        }catch(IOException denial){
            directWorldReadDenied=true;
        }
        require(directWorldReadDenied,
            "low-level guarded World-session read must reject all cases");

        boolean actualSessionDenied=false;
        try(World w=World.isolatedForTest(60000L,repository)){
            try{
                w.persistence().load(account);
            }catch(IOException denied){
                actualSessionDenied=true;
            }
            require(w.players().snapshot().isEmpty(),
                "fresh World has no automatically registered players");
        }
        require(actualSessionDenied,
            "actual World persistence session must reject damaged evidence");

        boolean guardedSaveDenied=false;
        try{
            repository.saveForWorld(PlayerSnapshotCodec.capture(
                account,new WorldPlayer()));
        }catch(IOException denied){
            guardedSaveDenied=true;
        }
        require(guardedSaveDenied,
            "guarded World save must refuse damaged evidence");

        require(sameLeaves(before,readLeaves(root,account)),
            "new process must not repair/remove/change damaged evidence");
        require(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0,
            "no leaked advisory publication lease");
        System.out.println("G2196_RESTART_CHILD_CASE_PASS"+
            " fault="+fault.name()+
            " status="+status.status+
            " detachedDenied=true"+
            " directSessionReadDenied=true"+
            " actualWorldSessionDenied=true"+
            " guardedWorldSaveDenied=true"+
            " diskUnchanged=true grant=false replay=false ack=false");
    }

    private static void freshProcess(
        Path root,Fault fault
    )throws Exception{
        String executable=System.getProperty("os.name")
            .toLowerCase(Locale.ROOT).contains("win")
            ?"java.exe":"java";
        Path java=Paths.get(System.getProperty("java.home"),
            "bin",executable);
        require(Files.isRegularFile(java),"child java binary missing");
        String classPath=System.getProperty("java.class.path");
        require(classPath!=null&&!classPath.isEmpty(),
            "test classpath unavailable");
        Path log=root.resolve("g2196-"+fault.name()+".log");
        ProcessBuilder builder=new ProcessBuilder(
            java.toString(),"-cp",classPath,
            G2196MailboxDamagedEvidenceRestartIntegrationTest
                .class.getName(),
            "--child",root.toString(),fault.name());
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        Process p=builder.start();
        try{
            if(!p.waitFor(20,TimeUnit.SECONDS))
                throw new AssertionError(
                    "G21.96 independent child timeout "+fault.name());
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        require(p.exitValue()==0&&
            output.contains("G2196_RESTART_CHILD_CASE_PASS")&&
            output.contains("fault="+fault.name()),
            "fresh JVM case failed "+fault+" code="+p.exitValue()+
            " output="+output);
    }

    private static void runParent()throws Exception{
        Path root=Files.createTempDirectory("g2196-damaged-restart-");
        boolean allCases=false,allLeavesUnchanged=false;
        boolean originalOwnersUnclaimed=false,noLeases=false;
        int cases=0;
        try(World source=World.isolatedForTest(60000L)){
            final String donor="g2196-donor";
            committed(source,donor,paths(root));
            final java.util.EnumMap<Fault,Fixture> fixture=
                new java.util.EnumMap<>(Fault.class);
            for(Fault fault:Fault.values())
                fixture.put(fault,committed(
                    source,fault.account(),paths(root)));
            for(Fault fault:Fault.values())
                damage(root,fault,fixture.get(fault),donor);
            final java.util.EnumMap<Fault,byte[][]> before=
                new java.util.EnumMap<>(Fault.class);
            for(Fault fault:Fault.values())
                before.put(fault,readLeaves(root,fault.account()));
            for(Fault fault:Fault.values()){
                freshProcess(root,fault);
                cases++;
            }
            allCases=cases==Fault.values().length;
            allLeavesUnchanged=true;
            originalOwnersUnclaimed=true;
            for(Fault fault:Fault.values()){
                allLeavesUnchanged&=sameLeaves(
                    before.get(fault),readLeaves(root,fault.account()));
                String account=fault.account();
                WorldPlayer owner=null;
                for(WorldPlayer candidate:source.players().snapshot())
                    if(account.equals(candidate.username()))
                        owner=candidate;
                originalOwnersUnclaimed&=owner!=null&&
                    owner.bank().inventorySlots()==0&&
                    owner.mailbox().get(account+":gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            }
        }finally{
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2196_DAMAGED_RESTART_DIAGNOSTICS"+
            " independentJvmCases="+cases+
            " allExpectedStatuses="+allCases+
            " evidenceBytesAndAbsenceUnchanged="+allLeavesUnchanged+
            " originalOwnerRewardsUnclaimed="+originalOwnersUnclaimed+
            " noJvmLeases="+noLeases);
        require(allCases&&allLeavesUnchanged&&
            originalOwnersUnclaimed&&noLeases,
            "damaged/missing/conflicting restart matrix");
        System.out.println("G2196_DAMAGED_EVIDENCE_RESTART_PASS"+
            " cases="+cases+
            " realIndependentJvms=true"+
            " detached=false session=false save=false"+
            " liveApply=false grant=false replay=false"+
            " release=false ack=false");
    }

    public static void main(String[] args)throws Exception{
        if(args.length>0){
            require(args.length==3&&"--child".equals(args[0]),
                "unexpected child arguments");
            Fault fault=Fault.valueOf(args[2]);
            inspectFreshJVM(Paths.get(args[1])
                .toAbsolutePath().normalize(),fault);
            return;
        }
        runParent();
    }
}
