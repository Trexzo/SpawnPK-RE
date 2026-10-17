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
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.102: legacy same-byte inode change *between* two bounded audit
 * passes is rejected, while changes AFTER two stable passes remain
 * possible and never create cleanup permissions.
 */
public final class G21102MailboxCrossVersionTwoPassEvidenceIntegrationTest {
    private static final String BETWEEN="g21102-between";
    private static final String AFTER="g21102-after";
    private static final String CRASH="g21102-crash";
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.102 "+why);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return account->root.resolve(account+".properties");
    }
    private static boolean present(Path p){
        return Files.exists(p,LinkOption.NOFOLLOW_LINKS);
    }
    private static String physicalKey(Path file)throws IOException{
        BasicFileAttributes a=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(a.isRegularFile()&&a.fileKey()!=null,
            "physical file key unavailable");
        return a.fileKey().toString();
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static Process fork(Path root,String mode,String name)
        throws IOException{
        Path log=root.resolve("g21102-"+mode+"-"+name+".log");
        ProcessBuilder b=new ProcessBuilder(javaBin(),"-cp",
            System.getProperty("java.class.path"),
            G21102MailboxCrossVersionTwoPassEvidenceIntegrationTest
                .class.getName(),"--child",mode,root.toString(),name);
        b.redirectErrorStream(true);
        b.redirectOutput(log.toFile());
        return b.start();
    }
    private static void finish(
        Process p,Path root,String mode,String name,
        int exit,String marker
    )throws Exception{
        try{
            check(p.waitFor(15,TimeUnit.SECONDS),
                "child timeout mode="+mode+" account="+name);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(
            Files.readAllBytes(root.resolve(
                "g21102-"+mode+"-"+name+".log")),
            StandardCharsets.UTF_8);
        check(p.exitValue()==exit&&output.contains(marker),
            "child failure mode="+mode+" account="+name+
            " expected="+exit+" actual="+p.exitValue()+
            " output="+output);
    }
    private static void waitReady(
        Path root,String account,Process process
    )throws Exception{
        Path ready=root.resolve(account+".ready");
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(!present(ready)){
            check(process.isAlive(),"legacy process exited before ready");
            check(System.nanoTime()<deadline,
                "legacy process never produced ready marker");
            Thread.sleep(15);
        }
    }
    private static void release(Path root,String account)
        throws IOException{
        Files.createFile(root.resolve(account+".release"));
    }

    // Real previous-version style writer: temp created/forced OUTSIDE
    // G21.100, waits while new quiescence may see it, then takes ONLY
    // the historical G21.39 publication lock for atomic replacement.
    private static void legacy(Path root,String account)
        throws Exception{
        Path canonical=resolver(root).resolve(account);
        byte[] previous=Files.readAllBytes(canonical);
        Path temp=Files.createTempFile(root,
            account+".properties.g2123-",".tmp");
        try(FileChannel ch=FileChannel.open(temp,StandardOpenOption.WRITE)){
            ByteBuffer buf=ByteBuffer.wrap(previous);
            while(buf.hasRemaining())ch.write(buf);
            ch.force(true);
        }
        Files.setLastModifiedTime(temp,FileTime.fromMillis(
            System.currentTimeMillis()-TimeUnit.DAYS.toMillis(60)));
        Files.createFile(root.resolve(account+".ready"));
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(13);
        while(!present(root.resolve(account+".release"))){
            if(System.nanoTime()>=end)
                throw new IOException("legacy release timeout");
            Thread.sleep(15);
        }
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            canonical,()->{
                Files.move(temp,canonical,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
                try(FileChannel dir=FileChannel.open(
                        root,StandardOpenOption.READ)){
                    dir.force(true);
                }
                return null;
            });
        check(Arrays.equals(previous,Files.readAllBytes(canonical)),
            "legacy publisher changed canonical bytes");
        System.out.println("G21102_LEGACY_WRITER_FINISHED account="+account+
            " noLifecycleLock=true sameByteNewInode=true");
    }

    private static void crash(Path root)throws Exception{
        FilePlayerRepository.PathResolver paths=resolver(root);
        PlayerSnapshot snapshot=new FilePlayerRepository(paths)
            .load(CRASH).orElseThrow(
                ()->new AssertionError("crash baseline absent"));
        new StrictDurablePlayerSnapshotWriter(paths,phase->{
            if(phase==StrictDurablePlayerSnapshotWriter.Phase
                    .BEFORE_ATOMIC_REPLACE){
                System.out.println("G21102_TRACKED_CRASH_AT_TEMP"+
                    " lockHeld=true noGrant=true");
                System.out.flush();
                Runtime.getRuntime().halt(124);
            }
        }).saveStrict(snapshot);
        throw new AssertionError("crash writer did not halt");
    }
    private static void postCrashObserve(Path root)throws Exception{
        FilePlayerRepository.PathResolver paths=resolver(root);
        Path canonical=paths.resolve(CRASH);
        byte[] before=Files.readAllBytes(canonical);
        String physical=physicalKey(canonical);
        MailboxCrossVersionPublicationEvidenceAudit.Result stable=
            MailboxCrossVersionPublicationEvidenceAudit
                .inspectTwice(paths,CRASH,1500L);
        negative(stable);
        check(stable.first.tempAudit.candidates.size()==1&&
            stable.second.tempAudit.candidates.size()==1&&
            stable.first.tempAudit.candidates.get(0).kind==
                MailboxOrphanPublicationCleanupAudit.Kind.STRICT_ACCOUNT,
            "crashed tracked writer did not leave orphan temp");
        Path temp=stable.first.tempAudit.candidates.get(0).path;
        String tempKey=physicalKey(temp);
        byte[] orphan=Files.readAllBytes(temp);
        check(stable.second.tempAudit.candidates.get(0).fileKey
                .equals(tempKey)&&orphan.length>0&&
            physicalKey(canonical).equals(physical)&&
            Arrays.equals(before,Files.readAllBytes(canonical))&&
            Arrays.equals(orphan,Files.readAllBytes(temp))&&
            MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "crash restart changed canonical or orphan identity");
        System.out.println("G21102_CRASH_RESTART_OBSERVER_PASS"+
            " crossJvmLifecycleLockReacquired=true"+
            " exactOrphanRetained=true canonicalUnchanged=true"+
            " delete=false grant=false replay=false ack=false");
    }
    private static void negative(
        MailboxCrossVersionPublicationEvidenceAudit.Result r
    ){
        check(r.sameObservedPhysicalIdentities&&
            r.participatingWritersQuietDuringPasses&&
            !r.legacyWritersExcluded&&
            !r.futureWritersExcluded&&
            !r.allWriterGenerationsCovered&&
            !r.crashEpochPersistentlySealed&&
            !r.cleanupAuthorized&&!r.unlinkAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.restartAdmissionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,
            "two-pass snapshot granted impermissible authority");
        check(!r.first.unlinkAuthorized&&!r.second.unlinkAuthorized&&
            !r.first.tempAudit.unlinkAuthorized&&
            !r.second.tempAudit.unlinkAuthorized,
            "nested temp witnesses may not authorize unlink");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21102-two-pass-");
        boolean driftRejected=false,lateWriteStillPossible=false;
        boolean crashRestartValid=false,noLeases=false;
        boolean exactBytesPreserved=false;
        int writerJvms=0,restartJvms=0;
        Process between=null,after=null;
        try{
            FilePlayerRepository.PathResolver paths=resolver(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(paths);
            for(String account:new String[]{BETWEEN,AFTER,CRASH})
                writer.saveStrict(PlayerSnapshotCodec.capture(
                    account,new WorldPlayer()));
            byte[] firstBytes=Files.readAllBytes(paths.resolve(BETWEEN));
            byte[] secondBytes=Files.readAllBytes(paths.resolve(AFTER));
            byte[] thirdBytes=Files.readAllBytes(paths.resolve(CRASH));
            String beforeBetweenKey=physicalKey(paths.resolve(BETWEEN));

            between=fork(root,"legacy",BETWEEN);
            waitReady(root,BETWEEN,between);
            writerJvms++;
            final Process inGap=between;
            try{
                MailboxCrossVersionPublicationEvidenceAudit.inspectTwice(
                    paths,BETWEEN,1500L,()->{
                        release(root,BETWEEN);
                        try{
                            if(!inGap.waitFor(8,TimeUnit.SECONDS))
                                throw new IOException(
                                    "interpass legacy writer timed out");
                        }catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();
                            throw new IOException(interrupted);
                        }
                    });
            }catch(IOException expected){
                driftRejected=String.valueOf(expected.getMessage())
                    .contains("G21.102 CROSS_VERSION_DISK_WITNESS_DRIFT");
            }
            finish(between,root,"legacy",BETWEEN,0,
                "G21102_LEGACY_WRITER_FINISHED");
            check(driftRejected&&
                !physicalKey(paths.resolve(BETWEEN))
                    .equals(beforeBetweenKey)&&
                Arrays.equals(firstBytes,
                    Files.readAllBytes(paths.resolve(BETWEEN))),
                "interpass same-byte physical replacement not detected");

            after=fork(root,"legacy",AFTER);
            waitReady(root,AFTER,after);
            writerJvms++;
            String beforeAfterKey=physicalKey(paths.resolve(AFTER));
            MailboxCrossVersionPublicationEvidenceAudit.Result stable=
                MailboxCrossVersionPublicationEvidenceAudit.inspectTwice(
                    paths,AFTER,1500L);
            negative(stable);
            release(root,AFTER);
            finish(after,root,"legacy",AFTER,0,
                "G21102_LEGACY_WRITER_FINISHED");
            lateWriteStillPossible=!beforeAfterKey.equals(
                    physicalKey(paths.resolve(AFTER)))&&
                Arrays.equals(secondBytes,
                    Files.readAllBytes(paths.resolve(AFTER)))&&
                !stable.unlinkAuthorized&&!stable.futureWritersExcluded;
            check(lateWriteStillPossible,
                "successful two-pass witness falsely excluded future legacy write");

            Process crashed=fork(root,"crash",CRASH);
            finish(crashed,root,"crash",CRASH,124,
                "G21102_TRACKED_CRASH_AT_TEMP");
            writerJvms++;
            Process observed=fork(root,"observe",CRASH);
            finish(observed,root,"observe",CRASH,0,
                "G21102_CRASH_RESTART_OBSERVER_PASS");
            restartJvms++;
            crashRestartValid=true;
            exactBytesPreserved=
                Arrays.equals(firstBytes,
                    Files.readAllBytes(paths.resolve(BETWEEN)))&&
                Arrays.equals(secondBytes,
                    Files.readAllBytes(paths.resolve(AFTER)))&&
                Arrays.equals(thirdBytes,
                    Files.readAllBytes(paths.resolve(CRASH)));
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G21102_CROSS_VERSION_DIAGNOSTICS"+
                " interpassSameByteInodeDriftRejected="+driftRejected+
                " legacyCanPublishAfterStableTwoPass="+lateWriteStillPossible+
                " independentCrashRestartValid="+crashRestartValid+
                " originalCanonicalBytesUnchanged="+exactBytesPreserved+
                " participatingWriterJvms="+writerJvms+
                " newRestartJvms="+restartJvms+
                " jvmLeasesZero="+noLeases);
            check(driftRejected&&lateWriteStillPossible&&
                crashRestartValid&&exactBytesPreserved&&noLeases&&
                writerJvms==3&&restartJvms==1,
                "two-pass legacy/crash negative-only proof");
            System.out.println("G21102_CROSS_VERSION_TWO_PASS_PASS"+
                " boundedPhysicalWitnesses=true"+
                " legacyDriftFailClosed=true"+
                " futureLegacyUnfenced=true"+
                " crashOrphanNotDeletionAuthority=true"+
                " cleanup=false unlink=false grant=false"+
                " replay=false session=false ack=false");
        }finally{
            for(String account:new String[]{BETWEEN,AFTER})
                try{Files.createFile(root.resolve(account+".release"));}
                catch(java.nio.file.FileAlreadyExistsException ignored){}
            for(Process p:new Process[]{between,after})
                if(p!=null&&p.isAlive()){
                    p.destroyForcibly();
                    p.waitFor(5,TimeUnit.SECONDS);
                }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            Path root=Paths.get(args[2]).toAbsolutePath().normalize();
            if("legacy".equals(args[1])){
                legacy(root,args[3]);return;
            }
            if("crash".equals(args[1])){
                check(CRASH.equals(args[3]),"foreign crash account");
                crash(root);return;
            }
            if("observe".equals(args[1])){
                check(CRASH.equals(args[3]),"foreign observer account");
                postCrashObserve(root);return;
            }
            throw new AssertionError("unknown child mode");
        }
        check(args.length==0,"unexpected test arguments");
        run();
    }
}
