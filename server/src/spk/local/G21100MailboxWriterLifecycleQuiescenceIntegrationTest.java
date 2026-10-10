package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.100 real writers and bounded cross-JVM lifecycle observation. */
public final class G21100MailboxWriterLifecycleQuiescenceIntegrationTest {
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.100 "+why);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return account->root.resolve(account+".properties");
    }
    private static MailboxSettlementPostimagePlanner.Proposal plan(
        World fixture,String account)throws Exception{
        WorldPlayer p=new WorldPlayer();
        long generation=fixture.registerPlayer(p,account);
        p.mailbox().deliver(new RewardDeliveryMessage(
            account+":gift","No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21100_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=
            p.mailbox().get(account+":gift");
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,row));
        check(p.bank().inventorySlots()==0&&
            row.claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED,
            "fixture issued reward");
        return MailboxSettlementPostimagePlanner.plan(
            p,generation,row);
    }
    private static boolean isBusy(
        FilePlayerRepository.PathResolver paths,String account
    )throws Exception{
        try{
            MailboxPublicationWriterLifecycle.inspect(
                paths,account,150L);
            return false;
        }catch(IOException expected){
            return String.valueOf(expected.getMessage())
                .contains("G21.73 RECOVERY_PUBLICATION_BUSY_NO_GRANT");
        }
    }
    private static void negative(
        MailboxPublicationWriterLifecycle.Report report
    ){
        check(report.participatingWriterQuiescenceObserved&&
            !report.allWriterGenerationsCovered&&
            !report.uncooperativeWritersExcluded&&
            !report.unlinkAuthorized&&!report.cleanupAuthorized&&
            !report.grantAuthorized&&!report.replayAuthorized&&
            !report.restartAdmissionAuthorized&&
            !report.clientAckAuthorized&&
            !report.tempAudit.unlinkAuthorized&&
            !report.tempAudit.cleanupAuthorized,
            "false positive cleanup authority");
    }

    private static boolean separateJvmBusy(
        Path root,String account)throws Exception{
        Path log=root.resolve("g21100-independent-observer.log");
        String java=Paths.get(System.getProperty("java.home"),
            "bin",System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).contains("win")
                    ?"java.exe":"java").toString();
        ProcessBuilder builder=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21100MailboxWriterLifecycleQuiescenceIntegrationTest
                .class.getName(),"--child",root.toString(),account);
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        Process process=builder.start();
        try{
            check(process.waitFor(8,TimeUnit.SECONDS),
                "independent JVM probe timed out");
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                process.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String text=new String(Files.readAllBytes(log),
            java.nio.charset.StandardCharsets.UTF_8);
        return process.exitValue()==0&&text.contains(
            "G21100_INDEPENDENT_JVM_BUSY_PASS");
    }

    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            check(isBusy(paths(Paths.get(args[1])),args[2]),
                "independent JVM missed active writer");
            System.out.println("G21100_INDEPENDENT_JVM_BUSY_PASS");
            return;
        }
        check(args.length==0,"invalid test arguments");
        Path root=Files.createTempDirectory("g21100-writer-life-");
        FilePlayerRepository.PathResolver resolver=paths(root);
        final String strictName="g21100-strict";
        final String journalName="g21100-journal";
        final String commitName="g21100-commit";
        boolean strictBusy=false,strictAgeProtected=false;
        boolean remoteStrictBusy=false,strictFinished=false;
        boolean journalBusy=false,journalFinished=false;
        boolean commitBusy=false,commitFinished=false;
        boolean quietAfter=false,sourceUnclaimed=false;
        boolean noLeases=false;
        CountDownLatch paused=new CountDownLatch(1);
        CountDownLatch resume=new CountDownLatch(1);
        AtomicReference<Throwable> fail=new AtomicReference<>();
        AtomicReference<StrictDurablePlayerSnapshotWriter.Receipt>
            finalStrictReceipt=new AtomicReference<>();
        Thread active=null;
        try(World fixture=World.isolatedForTest(60000L)){
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolver);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(resolver);
            MailboxGuardedDiskCommitRecord commit=
                new MailboxGuardedDiskCommitRecord(resolver);

            MailboxSettlementPostimagePlanner.Proposal strictP=
                plan(fixture,strictName);
            writer.saveStrict(strictP.preparedPreimage);
            journal.publishPreparedIntent(strictP);
            PlayerSnapshot strictTerminal=
                MailboxAtomicTerminalSnapshot.compose(strictP);
            String strictSha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(strictP.preparedPreimage);
            active=new Thread(()->{
                try{
                    StrictDurablePlayerSnapshotWriter pausedWriter=
                        new StrictDurablePlayerSnapshotWriter(
                            resolver,phase->{
                                if(phase==
                                        StrictDurablePlayerSnapshotWriter
                                            .Phase.BEFORE_ATOMIC_REPLACE){
                                    paused.countDown();
                                    try{
                                        if(!resume.await(
                                                12,TimeUnit.SECONDS))
                                            throw new IOException(
                                                "G21.100 pause timeout");
                                    }catch(InterruptedException e){
                                        Thread.currentThread().interrupt();
                                        throw new IOException(
                                            "G21.100 interrupted",e);
                                    }
                                }
                            });
                    finalStrictReceipt.set(
                        pausedWriter.saveStrictTerminalForWorld(
                            strictTerminal,resolver.resolve(strictName),
                            strictSha,()->{},()->{}));
                }catch(Throwable e){fail.set(e);}
            },"g21100-strict-lifecycle-writer");
            active.start();
            check(paused.await(5,TimeUnit.SECONDS),
                "strict writer did not pause");
            Path liveTemp;
            try(Stream<Path> list=Files.list(root)){
                Path[] found=list.filter(p->p.getFileName().toString()
                    .startsWith(strictName+".properties.g2123-")&&
                    p.getFileName().toString().endsWith(".tmp"))
                    .toArray(Path[]::new);
                check(found.length==1,"strict temp not present");
                liveTemp=found[0];
            }
            Files.setLastModifiedTime(liveTemp,FileTime.fromMillis(
                System.currentTimeMillis()-
                    TimeUnit.DAYS.toMillis(60)));
            // G21.99 audit still sees this file under publication lock.
            MailboxOrphanPublicationCleanupAudit.Result old=
                new MailboxOrphanPublicationCleanupAudit(resolver)
                    .inspect(strictName);
            strictAgeProtected=old.candidates.size()==1&&
                !old.candidates.get(0).unlinkAuthorized&&
                Files.exists(liveTemp,LinkOption.NOFOLLOW_LINKS);
            strictBusy=isBusy(resolver,strictName);
            remoteStrictBusy=separateJvmBusy(root,strictName);
            resume.countDown();
            active.join(13000);
            strictFinished=!active.isAlive()&&fail.get()==null&&
                finalStrictReceipt.get()!=null&&
                finalStrictReceipt.get().matchesSnapshot(strictTerminal);
            check(strictFinished,
                "writer after quiescence check failed="+fail.get());
            negative(MailboxPublicationWriterLifecycle.inspect(
                resolver,strictName,1000L));

            MailboxSettlementPostimagePlanner.Proposal journalP=
                plan(fixture,journalName);
            writer.saveStrict(journalP.preparedPreimage);
            CountDownLatch journalPaused=new CountDownLatch(1);
            CountDownLatch journalResume=new CountDownLatch(1);
            AtomicReference<Throwable> journalError=new AtomicReference<>();
            Thread jw=new Thread(()->{
                try{
                    new MailboxDurableIdempotencyIntentJournal(
                        resolver,phase->{
                            if(phase==
                                    MailboxDurableIdempotencyIntentJournal
                                        .Phase.BEFORE_LINK){
                                journalPaused.countDown();
                                try{
                                    if(!journalResume.await(
                                            6,TimeUnit.SECONDS))
                                        throw new IOException(
                                            "journal pause timeout");
                                }catch(InterruptedException e){
                                    Thread.currentThread().interrupt();
                                    throw new IOException(e);
                                }
                            }
                        }).publishPreparedIntent(journalP);
                }catch(Throwable e){journalError.set(e);}
            },"g21100-journal-writer");
            jw.start();
            check(journalPaused.await(4,TimeUnit.SECONDS),
                "journal writer not paused");
            journalBusy=isBusy(resolver,journalName);
            journalResume.countDown();
            jw.join(8000);
            journalFinished=!jw.isAlive()&&journalError.get()==null&&
                journal.inspect(journalName).status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .PREPARED_MATCH_NO_REPLAY;
            check(journalFinished,
                "journal writer did not finish="+journalError.get());
            negative(MailboxPublicationWriterLifecycle.inspect(
                resolver,journalName,1000L));

            MailboxSettlementPostimagePlanner.Proposal commitP=
                plan(fixture,commitName);
            writer.saveStrict(commitP.preparedPreimage);
            journal.publishPreparedIntent(commitP);
            PlayerSnapshot terminal=
                MailboxAtomicTerminalSnapshot.compose(commitP);
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                writer.saveStrictTerminalForWorld(
                    terminal,resolver.resolve(commitName),
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(commitP.preparedPreimage),
                    ()->{},()->{});
            check(receipt.matchesSnapshot(terminal),
                "COMMIT terminal strict receipt");
            CountDownLatch commitPaused=new CountDownLatch(1);
            CountDownLatch commitResume=new CountDownLatch(1);
            AtomicReference<Throwable> commitError=new AtomicReference<>();
            Thread cw=new Thread(()->{
                try{
                    new MailboxGuardedDiskCommitRecord(resolver,
                        phase->{
                            if(phase==
                                    MailboxGuardedDiskCommitRecord.Phase
                                        .BEFORE_LINK){
                                commitPaused.countDown();
                                try{
                                    if(!commitResume.await(
                                            6,TimeUnit.SECONDS))
                                        throw new IOException(
                                            "COMMIT pause timeout");
                                }catch(InterruptedException e){
                                    Thread.currentThread().interrupt();
                                    throw new IOException(e);
                                }
                            }
                        }).recordConfirmedDiskTerminal(commitP,receipt);
                }catch(Throwable e){commitError.set(e);}
            },"g21100-commit-writer");
            cw.start();
            check(commitPaused.await(4,TimeUnit.SECONDS),
                "COMMIT writer not paused");
            commitBusy=isBusy(resolver,commitName);
            commitResume.countDown();
            cw.join(8000);
            commitFinished=!cw.isAlive()&&commitError.get()==null&&
                commit.inspect(commitName).status==
                    MailboxGuardedDiskCommitRecord.Status
                        .DISK_COMMIT_MATCH_NO_LIVE_APPLY;
            check(commitFinished,
                "COMMIT writer did not finish="+commitError.get());
            negative(MailboxPublicationWriterLifecycle.inspect(
                resolver,commitName,1000L));
            quietAfter=true;

            sourceUnclaimed=true;
            for(WorldPlayer p:fixture.players().snapshot())
                sourceUnclaimed&=
                    p.bank().inventorySlots()==0&&
                    p.mailbox().get(p.username()+":gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
        }finally{
            resume.countDown();
            if(active!=null)active.join(14000);
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G21100_WRITER_LIFECYCLE_DIAGNOSTICS"+
            " strictPrepublicationBusy="+strictBusy+
            " agedTempRetained="+strictAgeProtected+
            " separateJvmBusy="+remoteStrictBusy+
            " resumedStrictCommit="+strictFinished+
            " journalPublicationBusy="+journalBusy+
            " journalPublished="+journalFinished+
            " diskCommitPublicationBusy="+commitBusy+
            " diskCommitPublished="+commitFinished+
            " cooperatingQuietAfter="+quietAfter+
            " originalRewardsUnclaimed="+sourceUnclaimed+
            " noJvmLeaseLeaks="+noLeases);
        check(strictBusy&&strictAgeProtected&&remoteStrictBusy&&
            strictFinished&&journalBusy&&journalFinished&&
            commitBusy&&commitFinished&&quietAfter&&
            sourceUnclaimed&&noLeases,
            "writer lifecycle lock regression");
        System.out.println("G21100_WRITER_LIFECYCLE_PASS"+
            " allThreeWritersTracked=true"+
            " actualCrossJvmContention=true"+
            " ageNotCleanupAuthority=true"+
            " noUnlink=true grant=false replay=false"+
            " session=false release=false ack=false");
    }
}
