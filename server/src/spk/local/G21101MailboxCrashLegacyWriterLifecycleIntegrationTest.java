package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.101: hard-JVM death abandons a tracked temp but frees its lease.
 * A real legacy-style writer can publish AFTER successful "quiescence".
 * Negative evidence only; never delete or promote temp files.
 */
public final class G21101MailboxCrashLegacyWriterLifecycleIntegrationTest {
    private enum Cut{
        STRICT(121),JOURNAL(122),COMMIT(123);
        final int haltCode;
        Cut(int code){haltCode=code;}
        String account(){
            return "g21101-"+name().toLowerCase(Locale.ROOT);
        }
        MailboxOrphanPublicationCleanupAudit.Kind kind(){
            switch(this){
                case STRICT: return MailboxOrphanPublicationCleanupAudit
                    .Kind.STRICT_ACCOUNT;
                case JOURNAL: return MailboxOrphanPublicationCleanupAudit
                    .Kind.PREPARED_INTENT;
                case COMMIT: return MailboxOrphanPublicationCleanupAudit
                    .Kind.DISK_COMMIT;
                default: throw new AssertionError("invalid cut");
            }
        }
    }
    private static final String LEGACY="g21101-legacy";

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.101 "+reason);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return username->root.resolve(username+".properties");
    }
    private static boolean present(Path p){
        return Files.exists(p,LinkOption.NOFOLLOW_LINKS);
    }
    private static byte[][] leaves(Path root,String account)
        throws IOException{
        FilePlayerRepository.PathResolver resolver=paths(root);
        Path file=resolver.resolve(account);
        Path journal=new MailboxDurableIdempotencyIntentJournal(resolver)
            .journalPath(account);
        Path commit=new MailboxGuardedDiskCommitRecord(resolver)
            .recordPath(account);
        return new byte[][]{
            present(file)?Files.readAllBytes(file):null,
            present(journal)?Files.readAllBytes(journal):null,
            present(commit)?Files.readAllBytes(commit):null
        };
    }
    private static boolean sameLeaves(byte[][] a,byte[][] b){
        for(int i=0;i<3;i++)
            if(!Arrays.equals(a[i],b[i]))return false;
        return true;
    }
    private static String identity(Path path)throws IOException{
        BasicFileAttributes a=Files.readAttributes(path,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        require(a.isRegularFile()&&a.fileKey()!=null,
            "physical leaf witness unavailable");
        return a.fileKey().toString();
    }
    private static void negative(
        MailboxPublicationWriterLifecycle.Report report
    ){
        require(report.participatingWriterQuiescenceObserved&&
            !report.allWriterGenerationsCovered&&
            !report.uncooperativeWritersExcluded&&
            !report.crashEpochDurabilityVerified&&
            !report.historicalWriterParticipationVerified&&
            report.legacyWriterCanStillPublish&&
            !report.orphanTempDisposalSafe&&
            !report.cleanupAuthorized&&!report.unlinkAuthorized&&
            !report.grantAuthorized&&!report.replayAuthorized&&
            !report.restartAdmissionAuthorized&&
            !report.clientAckAuthorized&&
            !report.tempAudit.unlinkAuthorized&&
            !report.tempAudit.cleanupAuthorized,
            "bounded lock falsely minted deletion or grant authority");
        for(MailboxOrphanPublicationCleanupAudit.Candidate c:
                report.tempAudit.candidates)
            require(!c.unlinkAuthorized&&!c.authoritative,
                "candidate marked safe for deletion");
    }

    private static MailboxSettlementPostimagePlanner.Proposal proposal(
        World w,String account
    )throws Exception{
        WorldPlayer owner=new WorldPlayer();
        long gen=w.registerPlayer(owner,account);
        owner.mailbox().deliver(new RewardDeliveryMessage(
            account+":gift","No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21101_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot reward=
            owner.mailbox().get(account+":gift");
        MailboxPreparedClaimJournal.stageOnly(owner,
            MailboxPreparedClaimJournal.prepare(owner,reward));
        require(owner.bank().inventorySlots()==0&&
            reward.claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED,
            "original owner was rewarded");
        return MailboxSettlementPostimagePlanner.plan(owner,gen,reward);
    }

    private static void hardStop(Cut cut,String stage){
        System.out.println("G21101_HARD_WRITER_CUT"+
            " cut="+cut.name()+" stage="+stage+
            " noCleanup=true grant=false ack=false");
        System.out.flush();
        Runtime.getRuntime().halt(cut.haltCode);
        throw new AssertionError("G21.101 JVM halt returned");
    }
    private static void crashWriter(Path root,Cut cut)
        throws Exception{
        FilePlayerRepository.PathResolver resolver=paths(root);
        String account=cut.account();
        try(World w=World.isolatedForTest(60000L)){
            MailboxSettlementPostimagePlanner.Proposal p=
                proposal(w,account);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(resolver);
            strict.saveStrict(p.preparedPreimage);
            MailboxDurableIdempotencyIntentJournal normalJournal=
                new MailboxDurableIdempotencyIntentJournal(resolver);
            if(cut==Cut.JOURNAL){
                new MailboxDurableIdempotencyIntentJournal(
                    resolver,phase->{
                        if(phase==MailboxDurableIdempotencyIntentJournal
                                .Phase.BEFORE_LINK)
                            hardStop(cut,"forcedJournalBeforeHardLink");
                    }).publishPreparedIntent(p);
                throw new AssertionError("journal hook not reached");
            }
            normalJournal.publishPreparedIntent(p);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot.compose(p);
            String preparedSha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(p.preparedPreimage);
            if(cut==Cut.STRICT){
                new StrictDurablePlayerSnapshotWriter(resolver,
                    phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter
                                .Phase.BEFORE_ATOMIC_REPLACE)
                            hardStop(cut,"forcedStrictBeforeAtomicMove");
                    }).saveStrictTerminalForWorld(
                        terminal,resolver.resolve(account),
                        preparedSha,()->{},()->{});
                throw new AssertionError("strict hook not reached");
            }
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                strict.saveStrictTerminalForWorld(
                    terminal,resolver.resolve(account),
                    preparedSha,()->{},()->{});
            require(receipt.matchesSnapshot(terminal),
                "strict terminal receipt mismatch");
            new MailboxGuardedDiskCommitRecord(resolver,phase->{
                if(phase==MailboxGuardedDiskCommitRecord.Phase.BEFORE_LINK)
                    hardStop(cut,"forcedCommitBeforeHardLink");
            }).recordConfirmedDiskTerminal(p,receipt);
            throw new AssertionError("commit hook not reached");
        }
    }

    /** Independent JVM after a hard writer death; never clean or repair. */
    private static void observeCrashedWriter(Path root,Cut cut)
        throws Exception{
        String account=cut.account();
        FilePlayerRepository.PathResolver resolver=paths(root);
        byte[][] before=leaves(root,account);
        MailboxPublicationWriterLifecycle.Report report=
            MailboxPublicationWriterLifecycle.inspect(
                resolver,account,1500L);
        negative(report);
        require(report.tempAudit.candidates.size()==1&&
            report.tempAudit.candidates.get(0).kind==cut.kind()&&
            report.tempAudit.candidates.get(0).disposition==
                MailboxOrphanPublicationCleanupAudit.Disposition
                    .POSSIBLY_ACTIVE_OR_ORPHAN_KEEP,
            "crashed temp not reported as unsafe");
        MailboxOrphanPublicationCleanupAudit.Candidate c=
            report.tempAudit.candidates.get(0);
        String originalFileKey=identity(c.path);
        byte[] orphanBefore=Files.readAllBytes(c.path);
        require(c.fileKey.equals(originalFileKey)&&
            orphanBefore.length>0,
            "crashed temp physical identity incorrect");
        MailboxGuardedDiskCommitRecord.Observation disk=
            new MailboxGuardedDiskCommitRecord(resolver).inspect(account);
        require(disk.status==
                MailboxGuardedDiskCommitRecord.Status.ABSENT&&
                !disk.grantAuthorized&&!disk.replayAuthorized&&
                !disk.restartAdmissionAuthorized&&!disk.clientAckAuthorized,
            "unlinked COMMIT temp promoted to authoritative state");
        MailboxDurableIdempotencyIntentJournal.Observation journal=
            new MailboxDurableIdempotencyIntentJournal(resolver)
                .inspect(account);
        MailboxDurableIdempotencyIntentJournal.Status expected=
            cut==Cut.JOURNAL
                ?MailboxDurableIdempotencyIntentJournal.Status.ABSENT
                :cut==Cut.STRICT
                    ?MailboxDurableIdempotencyIntentJournal.Status
                        .PREPARED_MATCH_NO_REPLAY
                    :MailboxDurableIdempotencyIntentJournal.Status
                        .TERMINAL_MATCH_NO_COMMIT;
        require(journal.status==expected,
            "post-crash PREPARED journal state unexpected "+journal.status);
        FilePlayerRepository repository=new FilePlayerRepository(resolver);
        boolean terminalDenied=false;
        if(cut==Cut.COMMIT){
            try{
                repository.loadForWorldSession(account);
            }catch(IOException denied){terminalDenied=true;}
            require(terminalDenied,
                "terminal account admitted after COMMIT writer crash");
        }else{
            PlayerSnapshot prepared=repository.loadForWorldSession(account)
                .orElseThrow(()->new AssertionError("prepared account absent"));
            require(MailboxPreparedRestartAdmission.inspect(prepared).state==
                MailboxPreparedRestartAdmission.State
                    .VALID_PREPARED_UNCLAIMED,
                "prepared account lost negative-only status");
        }
        boolean detachedRecoveryRejected=false;
        try{
            new MailboxCommittedDetachedRestartRecovery(resolver)
                .recoverDetached(account);
        }catch(IOException expectedFailure){
            detachedRecoveryRejected=true;
        }
        require(detachedRecoveryRejected,
            "orphan-only evidence allowed detached COMMIT recovery");
        MailboxPublicationWriterLifecycle.Report second=
            MailboxPublicationWriterLifecycle.inspect(
                resolver,account,1500L);
        negative(second);
        require(second.tempAudit.candidates.size()==1&&
            second.tempAudit.candidates.get(0).fileKey.equals(
                originalFileKey)&&
            identity(c.path).equals(originalFileKey)&&
            Arrays.equals(orphanBefore,Files.readAllBytes(c.path))&&
            sameLeaves(before,leaves(root,account))&&
            MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "restart inspected or mutated abandoned temp/canonical leaves");
        System.out.println("G21101_CRASH_RESTART_CHILD_PASS"+
            " cut="+cut.name()+
            " writerLifetimeLockReacquired=true"+
            " unlinkedTempRetained=true"+
            " canonicalUnchanged=true"+
            " orphanNotDeletionAuthority=true"+
            " terminalDenied="+terminalDenied+
            " detachedRecoveryDenied=true"+
            " grant=false replay=false ack=false");
    }

    /**
     * Model a prior-version writer: forced temp outside G21.100 lock,
     * paused, then old-style G21.39 publication lock + atomic account
     * replace. No current lifecycle lock is ever acquired.
     */
    private static void legacyWriter(Path root)throws Exception{
        Path account=paths(root).resolve(LEGACY);
        byte[] original=Files.readAllBytes(account);
        Path temp=Files.createTempFile(root,
            LEGACY+".properties.g2123-",".tmp");
        try(FileChannel channel=FileChannel.open(temp,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)){
            ByteBuffer bytes=ByteBuffer.wrap(original);
            while(bytes.hasRemaining())channel.write(bytes);
            channel.force(true);
        }
        Files.setLastModifiedTime(temp,FileTime.fromMillis(
            System.currentTimeMillis()-TimeUnit.DAYS.toMillis(60)));
        Path ready=root.resolve("legacy.ready");
        Path release=root.resolve("legacy.release");
        Files.createFile(ready);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(!present(release)){
            if(System.nanoTime()>=deadline)
                throw new IOException("G21.101 legacy gate timed out");
            Thread.sleep(15);
        }
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            account,()->{
                Files.move(temp,account,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
                try(FileChannel directory=FileChannel.open(
                        root,StandardOpenOption.READ)){
                    directory.force(true);
                }
                return null;
            });
        require(Arrays.equals(original,Files.readAllBytes(account)),
            "legacy write changed contents unexpectedly");
        System.out.println("G21101_LEGACY_PUBLISH_CHILD_PASS"+
            " legacyLifecycleParticipation=false"+
            " atomicCanonicalReplace=true noGrant=true");
    }

    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static Process start(Path root,String mode,String arg)
        throws IOException{
        Path log=root.resolve("g21101-"+mode+"-"+arg+".log");
        ProcessBuilder b=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21101MailboxCrashLegacyWriterLifecycleIntegrationTest
                .class.getName(),"--child",mode,root.toString(),arg);
        b.redirectErrorStream(true);
        b.redirectOutput(log.toFile());
        return b.start();
    }
    private static void finish(
        Process p,Path root,String mode,String arg,int code,
        String marker
    )throws Exception{
        try{
            require(p.waitFor(20,TimeUnit.SECONDS),
                "child did not terminate mode="+mode+" arg="+arg);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(
            root.resolve("g21101-"+mode+"-"+arg+".log")),
            StandardCharsets.UTF_8);
        require(p.exitValue()==code&&output.contains(marker),
            "child mismatch mode="+mode+" arg="+arg+
            " actualCode="+p.exitValue()+" expected="+code+
            " output="+output);
    }

    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("g21101-lifecycle-death-");
        boolean allCrashedLeavesUnchanged=true,legacyOverlap=false;
        boolean legacyActualPublish=false,legacyOrphanKept=false;
        boolean leaseLeakFree=false;
        int hardHalts=0,independentObservers=0;
        Process legacy=null;
        try{
            for(Cut cut:Cut.values()){
                Process crashed=start(root,"crash",cut.name());
                finish(crashed,root,"crash",cut.name(),cut.haltCode,
                    "G21101_HARD_WRITER_CUT");
                hardHalts++;
                byte[][] before=leaves(root,cut.account());
                Process observed=start(root,"observe",cut.name());
                finish(observed,root,"observe",cut.name(),0,
                    "G21101_CRASH_RESTART_CHILD_PASS");
                independentObservers++;
                allCrashedLeavesUnchanged&=sameLeaves(
                    before,leaves(root,cut.account()));
            }
            String account=LEGACY;
            FilePlayerRepository.PathResolver resolver=paths(root);
            new StrictDurablePlayerSnapshotWriter(resolver)
                .saveStrict(PlayerSnapshotCodec.capture(
                    account,new WorldPlayer()));
            Path canonical=resolver.resolve(account);
            byte[] original=Files.readAllBytes(canonical);
            String oldKey=identity(canonical);
            legacy=start(root,"legacy",account);
            Path ready=root.resolve("legacy.ready");
            long start=System.nanoTime();
            while(!present(ready)){
                require(legacy.isAlive(),
                    "legacy writer exited before temp pause");
                require(System.nanoTime()-start<
                    TimeUnit.SECONDS.toNanos(6),
                    "legacy writer never created paused temp");
                Thread.sleep(15);
            }
            // New quiescence returns SUCCESS despite a live
            // nonparticipating older-version writer.
            MailboxPublicationWriterLifecycle.Report report=
                MailboxPublicationWriterLifecycle.inspect(
                    resolver,account,1500L);
            negative(report);
            legacyOverlap=legacy.isAlive()&&
                report.tempAudit.candidates.size()==1&&
                report.tempAudit.candidates.get(0).kind==
                    MailboxOrphanPublicationCleanupAudit.Kind
                        .STRICT_ACCOUNT&&
                report.tempAudit.candidates.get(0).disposition==
                    MailboxOrphanPublicationCleanupAudit.Disposition
                        .POSSIBLY_ACTIVE_OR_ORPHAN_KEEP&&
                report.tempAudit.candidates.get(0).modifiedMillis<
                    System.currentTimeMillis()-
                    TimeUnit.DAYS.toMillis(59);
            require(legacyOverlap,
                "quiescence could not observe paused legacy temp");
            Path temp=report.tempAudit.candidates.get(0).path;
            String tempKey=identity(temp);
            legacyOrphanKept=present(temp)&&
                tempKey.equals(report.tempAudit.candidates.get(0).fileKey);
            Files.createFile(root.resolve("legacy.release"));
            finish(legacy,root,"legacy",account,0,
                "G21101_LEGACY_PUBLISH_CHILD_PASS");
            legacyActualPublish=
                Arrays.equals(original,Files.readAllBytes(canonical))&&
                !oldKey.equals(identity(canonical))&&
                !present(temp)&&
                MailboxPublicationWriterLifecycle.inspect(
                    resolver,account,1500L)
                    .participatingWriterQuiescenceObserved;
            negative(MailboxPublicationWriterLifecycle.inspect(
                resolver,account,1500L));
            leaseLeakFree=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G21101_CRASH_LEGACY_DIAGNOSTICS"+
                " crashedCooperatingWriters="+hardHalts+
                " freshJvmCrashObservers="+independentObservers+
                " crashedCanonicalBytesUnchanged="+
                    allCrashedLeavesUnchanged+
                " legacyAliveDuringQuiescence="+legacyOverlap+
                " legacyOldTempProtected="+legacyOrphanKept+
                " legacyActuallyReplacedCanonical="+legacyActualPublish+
                " noPublicationLeaseLeaks="+leaseLeakFree);
            require(hardHalts==3&&independentObservers==3&&
                allCrashedLeavesUnchanged&&legacyOverlap&&
                legacyOrphanKept&&legacyActualPublish&&leaseLeakFree,
                "G21.101 crash/legacy negative-only lifecycle proof");
            System.out.println("G21101_CRASH_LEGACY_LIFECYCLE_PASS"+
                " hardHalts=3 freshRestartJvms=3"+
                " abandonedTempsPreserved=true"+
                " staleWriterCanStillPublish=true"+
                " quiescenceIsCooperatingOnly=true"+
                " unlink=false cleanup=false grant=false"+
                " replay=false session=false ack=false");
        }finally{
            Files.createDirectories(root);
            try{Files.createFile(root.resolve("legacy.release"));}
            catch(java.nio.file.FileAlreadyExistsException ignored){}
            if(legacy!=null&&legacy.isAlive()){
                legacy.destroyForcibly();
                legacy.waitFor(5,TimeUnit.SECONDS);
            }
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            Path root=Paths.get(args[2]).toAbsolutePath().normalize();
            if("crash".equals(args[1])){
                crashWriter(root,Cut.valueOf(args[3]));
                throw new AssertionError("writer survived requested hard halt");
            }
            if("observe".equals(args[1])){
                observeCrashedWriter(root,Cut.valueOf(args[3]));
                return;
            }
            if("legacy".equals(args[1])){
                require(LEGACY.equals(args[3]),
                    "unexpected legacy child account");
                legacyWriter(root);
                return;
            }
            throw new AssertionError("invalid child mode");
        }
        require(args.length==0,"invalid parent arguments");
        parent();
    }
}
