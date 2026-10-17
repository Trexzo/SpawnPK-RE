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

/** Real JVM archive-vs-live negative reconciliation with legacy writers. */
public final class G21106MailboxPinnedArchiveLiveReconciliationIntegrationTest {
    private static final String BETWEEN="g21106-between";
    private static final String AFTER="g21106-after";
    private static final String TAMPER="g21106-tamper";
    private static final String MISSING="g21106-missing";
    private static final String VERSION="g21106-version";
    private static final String FOREIGN="g21106-foreign";

    private static void check(boolean ok,String s){
        if(!ok)throw new AssertionError("G21.106 "+s);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return a->root.resolve(a+".properties");
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static boolean exists(Path p){
        return Files.exists(p,LinkOption.NOFOLLOW_LINKS);
    }
    private static String physical(Path p)throws IOException{
        BasicFileAttributes a=Files.readAttributes(
            p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(a.isRegularFile()&&a.fileKey()!=null,
            "physical identity not observable");
        return a.fileKey().toString();
    }
    private static void negative(
        MailboxWriterArchiveLiveReconciliationV1.Report r
    ){
        check(r.sourceMatchedAcrossTwoPasses&&
            r.participatingWriterLeasesObservedQuiet&&
            !r.authoritativeAccountContentProved&&
            !r.allWriterVersionsFenced&&
            !r.futureLegacyWritersExcluded&&
            !r.ledgerLifetimeArchived&&
            !r.sourceFutureImmutabilityProved&&
            !r.rolloverAuthorized&&!r.recordPruningAuthorized&&
            !r.cleanupAuthorized&&!r.unlinkAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.sessionAdmissionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,
            "live archive match became cleanup/grant authority");
    }
    private static Path name(Path root,String account,String suffix){
        return root.resolve(account+
            ".properties.g21103-writer-attempt-"+suffix);
    }
    private static String[] recordBytes(Path root,String account)
        throws Exception{
        String pre=account+".properties.g21103-writer-attempt-";
        try(Stream<Path> files=Files.list(root)){
            Path[] all=files.filter(p->p.getFileName().toString()
                .startsWith(pre))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
            String[] values=new String[all.length];
            for(int i=0;i<all.length;i++)
                values[i]=all[i].getFileName()+"|"+
                    java.util.Base64.getEncoder().encodeToString(
                        Files.readAllBytes(all[i]));
            return values;
        }
    }
    private static Path firstRecord(Path root,String account)
        throws Exception{
        String prefix=account+".properties.g21103-writer-attempt-v1-";
        try(Stream<Path> files=Files.list(root)){
            return files.filter(p->p.getFileName().toString()
                .startsWith(prefix))
                .findFirst().orElseThrow(
                    ()->new AssertionError("missing v1 test record"));
        }
    }
    private static boolean sourceRejected(
        Path root,String account,
        MailboxWriterEvidencePortableArchiveV1.Snapshot archive,
        String message
    ){
        try{
            MailboxWriterArchiveLiveReconciliationV1.compare(
                paths(root),account,archive.portable,archive.sha256);
            return false;
        }catch(IOException expected){
            return String.valueOf(expected.getMessage()).contains(message);
        }
    }
    private static Process launch(
        Path root,String mode,String account
    )throws IOException{
        Path log=root.resolve("g21106-"+mode+"-"+account+".log");
        ProcessBuilder pb=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21106MailboxPinnedArchiveLiveReconciliationIntegrationTest
                .class.getName(),"--child",root.toString(),mode,account);
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        return pb.start();
    }
    private static void completed(
        Process p,Path root,String mode,String account,String marker
    )throws Exception{
        try{
            check(p.waitFor(16,TimeUnit.SECONDS),
                "child timeout "+mode+"/"+account);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String log=new String(Files.readAllBytes(
            root.resolve("g21106-"+mode+"-"+account+".log")),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0&&log.contains(marker),
            "child failed "+mode+"/"+account+
            " exit="+p.exitValue()+" "+log);
    }
    private static void ready(
        Process p,Path root,String account
    )throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);
        Path signal=root.resolve(account+".ready");
        while(!exists(signal)){
            check(p.isAlive(),"legacy writer already exited");
            check(System.nanoTime()<deadline,
                "legacy writer readiness timeout");
            Thread.sleep(15);
        }
    }
    private static void unlock(Path root,String account)
        throws IOException{
        Files.createFile(root.resolve(account+".release"));
    }
    private static void legacyPublisher(Path root,String account)
        throws Exception{
        Path canonical=paths(root).resolve(account);
        byte[] original=Files.readAllBytes(canonical);
        Path temp=Files.createTempFile(root,
            account+".properties.g2123-",".tmp");
        try(FileChannel ch=FileChannel.open(temp,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)){
            ByteBuffer b=ByteBuffer.wrap(original);
            while(b.hasRemaining())ch.write(b);
            ch.force(true);
        }
        Files.setLastModifiedTime(temp,FileTime.fromMillis(
            System.currentTimeMillis()-
                TimeUnit.DAYS.toMillis(60)));
        Files.createFile(root.resolve(account+".ready"));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(13);
        while(!exists(root.resolve(account+".release"))){
            if(System.nanoTime()>=deadline)
                throw new IOException("legacy handoff timeout");
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
        check(Arrays.equals(original,Files.readAllBytes(canonical)),
            "same-byte legacy account replacement changed bytes");
        System.out.println("G21106_LEGACY_SAME_BYTE_REPLACE_PASS"+
            " account="+account+" lifecycleParticipating=false");
    }

    private static void child(
        Path root,String mode,String account
    )throws Exception{
        if("legacy".equals(mode)){
            legacyPublisher(root,account);
            return;
        }
        if("verify".equals(mode)){
            String manifest=new String(Files.readAllBytes(
                root.resolve("independent.archive")),StandardCharsets.US_ASCII);
            String hash=new String(Files.readAllBytes(
                root.resolve("independent.sha256")),StandardCharsets.US_ASCII)
                    .trim();
            MailboxWriterArchiveLiveReconciliationV1.Report r=
                MailboxWriterArchiveLiveReconciliationV1.compare(
                    paths(root),account,manifest,hash);
            negative(r);
            check(r.matchedRecords==32&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0,
                "independent JVM archive/source comparison");
            System.out.println("G21106_INDEPENDENT_LIVE_COMPARE_PASS"+
                " records=32 noRollover=true noGrant=true");
            return;
        }
        throw new AssertionError("invalid child mode");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21106-source-match-");
        Process between=null,after=null;
        boolean independentSuccess=false,interpassDriftRejected=false;
        boolean afterSuccessStillUnfenced=false,sourceTamperDenied=false;
        boolean missingSourceDenied=false,mixedVersionDenied=false;
        boolean foreignAccountDenied=false,allEvidenceUnchanged=false;
        boolean noLeases=false,sourceExactBytes=true;
        try{
            FilePlayerRepository.PathResolver resolver=paths(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolver);
            String[] accounts={
                BETWEEN,AFTER,TAMPER,MISSING,VERSION,FOREIGN
            };
            for(String account:accounts){
                writer.saveStrict(PlayerSnapshotCodec.capture(
                    account,new WorldPlayer()));
                for(int n=0;n<(BETWEEN.equals(account)?16:1);n++)
                    MailboxWriterAttemptEvidenceV1.runRecorded(
                        resolver,account,()->true);
            }
            byte[] beforeAccount=Files.readAllBytes(
                resolver.resolve(BETWEEN));
            MailboxWriterEvidencePortableArchiveV1.Snapshot archive=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,BETWEEN);
            String[] ledgerBefore=recordBytes(root,BETWEEN);
            check(archive.verified.records==32,
                "expected full bounded v1 archive");

            // Test harness only: explicitly export already returned
            // in-memory data so another real JVM can independently read.
            Files.write(root.resolve("independent.archive"),
                archive.portable.getBytes(StandardCharsets.US_ASCII));
            Files.write(root.resolve("independent.sha256"),
                archive.sha256.getBytes(StandardCharsets.US_ASCII));
            Process independent=launch(root,"verify",BETWEEN);
            completed(independent,root,"verify",BETWEEN,
                "G21106_INDEPENDENT_LIVE_COMPARE_PASS");
            independentSuccess=true;

            // Same-byte but new inode legacy publication BETWEEN two
            // distinct cooperating audit passes must be rejected.
            between=launch(root,"legacy",BETWEEN);
            ready(between,root,BETWEEN);
            String beforeKey=physical(resolver.resolve(BETWEEN));
            final Process interpass=between;
            try{
                MailboxWriterArchiveLiveReconciliationV1.compare(
                    resolver,BETWEEN,archive.portable,archive.sha256,
                    ()->{
                        unlock(root,BETWEEN);
                        try{
                            if(!interpass.waitFor(9,TimeUnit.SECONDS))
                                throw new IOException(
                                    "interpass legacy writer timeout");
                        }catch(InterruptedException e){
                            Thread.currentThread().interrupt();
                            throw new IOException(e);
                        }
                    });
            }catch(IOException expected){
                interpassDriftRejected=String.valueOf(
                    expected.getMessage()).contains(
                        "G21.106 LIVE_ARCHIVE_WITNESS_DRIFT_NO_ROLLOVER");
            }
            completed(between,root,"legacy",BETWEEN,
                "G21106_LEGACY_SAME_BYTE_REPLACE_PASS");
            check(interpassDriftRejected&&
                !beforeKey.equals(physical(resolver.resolve(BETWEEN))),
                "legacy same-byte inode drift unrecognized");

            MailboxWriterEvidencePortableArchiveV1.Snapshot laterArchive=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,AFTER);
            after=launch(root,"legacy",AFTER);
            ready(after,root,AFTER);
            String afterKey=physical(resolver.resolve(AFTER));
            MailboxWriterArchiveLiveReconciliationV1.Report quiet=
                MailboxWriterArchiveLiveReconciliationV1.compare(
                    resolver,AFTER,laterArchive.portable,
                    laterArchive.sha256);
            negative(quiet);
            unlock(root,AFTER);
            completed(after,root,"legacy",AFTER,
                "G21106_LEGACY_SAME_BYTE_REPLACE_PASS");
            afterSuccessStillUnfenced=!afterKey.equals(
                    physical(resolver.resolve(AFTER)))&&
                !quiet.futureLegacyWritersExcluded&&
                !quiet.rolloverAuthorized;

            MailboxWriterEvidencePortableArchiveV1.Snapshot tamperArchive=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,TAMPER);
            Path tamper=firstRecord(root,TAMPER);
            byte[] bytes=Files.readAllBytes(tamper);
            FileTime oldTime=Files.getLastModifiedTime(
                tamper,LinkOption.NOFOLLOW_LINKS);
            byte[] damaged=Arrays.copyOf(bytes,bytes.length);
            damaged[0]=(byte)(damaged[0]^1);
            Files.write(tamper,damaged);
            Files.setLastModifiedTime(tamper,oldTime);
            sourceTamperDenied=sourceRejected(
                root,TAMPER,tamperArchive,
                "G21.103 INVALID_OR_CHANGED_WRITER_RECORD_NO_UNLINK");

            MailboxWriterEvidencePortableArchiveV1.Snapshot missingArchive=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,MISSING);
            Files.delete(firstRecord(root,MISSING));
            // G21.106: directory enumeration order is not guaranteed.
            // Removing COMPLETE leaves an incomplete BEGIN (archive drift);
            // removing BEGIN leaves orphan COMPLETE (G21.103 veto).
            // Both are negative-only rejections, never valid adoption.
            missingSourceDenied=sourceRejected(
                root,MISSING,missingArchive,
                "G21.106 LIVE_ARCHIVE_WITNESS_DRIFT_NO_ROLLOVER")||
                sourceRejected(root,MISSING,missingArchive,
                    "G21.103 COMPLETE_WITHOUT_BEGIN_NO_UNLINK");

            MailboxWriterEvidencePortableArchiveV1.Snapshot versionArchive=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,VERSION);
            Files.write(name(root,VERSION,
                    "v2-01234567-89ab-cdef-0123-456789abcdef.begin"),
                new byte[]{1,2,3});
            mixedVersionDenied=sourceRejected(
                root,VERSION,versionArchive,
                "G21.103 UNKNOWN_WRITER_EVIDENCE_VERSION_NO_UNLINK");

            MailboxWriterEvidencePortableArchiveV1.Snapshot foreignArchive=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,FOREIGN);
            foreignAccountDenied=sourceRejected(
                root,AFTER,foreignArchive,
                "G21.106 LIVE_ARCHIVE_WITNESS_DRIFT_NO_ROLLOVER");

            allEvidenceUnchanged=Arrays.equals(
                ledgerBefore,recordBytes(root,BETWEEN));
            sourceExactBytes=Arrays.equals(
                beforeAccount,Files.readAllBytes(
                    resolver.resolve(BETWEEN)));
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G21106_PINNED_ARCHIVE_DIAGNOSTICS"+
                " independentJvmSourceMatch="+independentSuccess+
                " sameByteInterpassInodeDriftDenied="+
                    interpassDriftRejected+
                " legacyAfterSuccessStillPossible="+
                    afterSuccessStillUnfenced+
                " sameLengthTamperingDenied="+sourceTamperDenied+
                " missingRecordDenied="+missingSourceDenied+
                " unexpectedV2Denied="+mixedVersionDenied+
                " foreignAccountArchiveDenied="+foreignAccountDenied+
                " originalArchiveLeavesUnchanged="+allEvidenceUnchanged+
                " originalAccountBytesUnchanged="+sourceExactBytes+
                " jvmLeasesZero="+noLeases);
            check(independentSuccess&&interpassDriftRejected&&
                afterSuccessStillUnfenced&&sourceTamperDenied&&
                missingSourceDenied&&mixedVersionDenied&&
                foreignAccountDenied&&allEvidenceUnchanged&&
                sourceExactBytes&&noLeases,
                "negative-only live archive reconciliation");
            System.out.println("G21106_LIVE_ARCHIVE_RECONCILIATION_PASS"+
                " records=32 freshJvmIndependent=true"+
                " legacyInterpassDriftFailClosed=true"+
                " lateLegacyUnfenced=true"+
                " recordMismatchFailClosed=true"+
                " cleanup=false unlink=false rollover=false"+
                " grant=false replay=false session=false ack=false");
        }finally{
            for(String account:new String[]{BETWEEN,AFTER})
                try{unlock(root,account);}
                catch(java.nio.file.FileAlreadyExistsException ignored){}
            for(Process p:new Process[]{between,after}){
                if(p!=null&&p.isAlive()){
                    p.destroyForcibly();
                    p.waitFor(5,TimeUnit.SECONDS);
                }
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
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2],args[3]);return;
        }
        check(args.length==0,"invalid arguments");
        run();
    }
}
