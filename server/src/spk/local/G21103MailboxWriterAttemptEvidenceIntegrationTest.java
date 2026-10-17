package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.103: versioned durable BEGIN / COMPLETE are negative-only and
 * do not extend historical writer coverage or allow cleanup.
 */
public final class G21103MailboxWriterAttemptEvidenceIntegrationTest {
    private static final String CRASH="g21103-crash";
    private static final String COMPLETE="g21103-complete";
    private static final String LEGACY="g21103-legacy";
    private static final String BAD="g21103-corrupt";
    private static final String VERSION="g21103-version";
    private static final String OVER="g21103-over";
    private static final String ORPHAN="g21103-orphancomplete";

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.103 "+reason);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return account->root.resolve(account+".properties");
    }
    private static void negative(
        MailboxWriterAttemptEvidenceV1.Report r
    ){
        check(!r.completeWriterPopulationKnown&&
            !r.everyHistoricalGenerationParticipates&&
            !r.crashEpochSealed&&!r.uncooperativeWritersExcluded&&
            !r.futureWritersExcluded&&!r.unlinkAuthorized&&
            !r.cleanupAuthorized&&!r.accountMutationAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.restartAdmissionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,"evidence minted authority");
    }
    private static Path name(Path root,String account,String tail){
        return root.resolve(account+".properties.g21103-writer-attempt-"+
            tail);
    }
    private static Path[] records(Path root,String account)
        throws IOException{
        try(Stream<Path> all=Files.list(root)){
            return all.filter(p->p.getFileName().toString()
                .startsWith(account+
                    ".properties.g21103-writer-attempt-"))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] contents(Path[] ps)throws IOException{
        String[] result=new String[ps.length];
        for(int i=0;i<ps.length;i++)
            result[i]=ps[i].getFileName()+"="+
                new String(Files.readAllBytes(ps[i]),
                    StandardCharsets.US_ASCII);
        return result;
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void child(Path root,String mode,String account)
        throws Exception{
        FilePlayerRepository.PathResolver r=paths(root);
        switch(mode){
            case "crash":
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,account,()->{
                        System.out.println(
                            "G21103_WRITER_BEGIN_FORCED_BEFORE_HALT");
                        System.out.flush();
                        Runtime.getRuntime().halt(125);
                        throw new AssertionError("halt returned");
                    });
                throw new AssertionError("crash wrapper returned");
            case "complete":
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,account,()->{
                        check(Files.exists(r.resolve(account),
                            LinkOption.NOFOLLOW_LINKS),
                            "canonical account missing");
                        return true;
                    });
                System.out.println("G21103_WRITER_PAIRED_DONE");
                return;
            case "inspect":
                MailboxWriterAttemptEvidenceV1.Report report=
                    MailboxWriterAttemptEvidenceV1.inspect(r,account);
                negative(report);
                if(CRASH.equals(account)){
                    check(report.started==1&&report.completed==0&&
                        report.status==MailboxWriterAttemptEvidenceV1
                            .Status.INCOMPLETE_ATTEMPT_NO_CLEANUP,
                        "restart failed to retain crash BEGIN");
                }else if(COMPLETE.equals(account)){
                    check(report.started==1&&report.completed==1&&
                        report.status==MailboxWriterAttemptEvidenceV1
                            .Status.ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP,
                        "full paired attempt not observable");
                }else{
                    check(report.started==0&&report.completed==0&&
                        report.status==MailboxWriterAttemptEvidenceV1
                            .Status.NO_VERSIONED_EVIDENCE_LEGACY_OR_UNKNOWN,
                        "legacy without v1 evidence wrongly enrolled");
                }
                check(MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0,
                    "observer retained a lifecycle lease");
                System.out.println("G21103_FRESH_JVM_OBSERVER_PASS"+
                    " account="+account+" status="+report.status+
                    " noUnlink=true noGrant=true");
                return;
            default:
                throw new AssertionError("bad mode");
        }
    }
    private static void fork(
        Path root,String mode,String account,int expected,
        String marker
    )throws Exception{
        Path log=root.resolve("g21103-"+mode+"-"+account+".log");
        ProcessBuilder process=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21103MailboxWriterAttemptEvidenceIntegrationTest
                .class.getName(),"--child",root.toString(),mode,account);
        process.redirectErrorStream(true);
        process.redirectOutput(log.toFile());
        Process p=process.start();
        try{
            check(p.waitFor(20,TimeUnit.SECONDS),
                "child timeout mode="+mode);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==expected&&output.contains(marker),
            "child result mismatch mode="+mode+
            " code="+p.exitValue()+" output="+output);
    }
    private static boolean rejected(
        Path root,String account,String marker
    ){
        try{
            MailboxWriterAttemptEvidenceV1.inspect(paths(root),account);
            return false;
        }catch(IOException expected){
            return String.valueOf(expected.getMessage())
                .contains(marker);
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21103-writer-records-");
        boolean crashRetained=false,pairedRetained=false;
        boolean legacyUnknown=false,corruptDenied=false;
        boolean versionDenied=false,overCapacityDenied=false;
        boolean completeWithoutBeginDenied=false,noMutation=false;
        boolean locksReleased=false;
        try{
            FilePlayerRepository.PathResolver r=paths(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(r);
            for(String account:new String[]{
                    CRASH,COMPLETE,LEGACY,BAD,VERSION,OVER,ORPHAN})
                writer.saveStrict(PlayerSnapshotCodec.capture(
                    account,new WorldPlayer()));
            byte[][] original=new byte[][]{
                Files.readAllBytes(r.resolve(CRASH)),
                Files.readAllBytes(r.resolve(COMPLETE)),
                Files.readAllBytes(r.resolve(LEGACY))
            };
            fork(root,"crash",CRASH,125,
                "G21103_WRITER_BEGIN_FORCED_BEFORE_HALT");
            Path[] crashRecords=records(root,CRASH);
            String[] crashContents=contents(crashRecords);
            fork(root,"inspect",CRASH,0,"G21103_FRESH_JVM_OBSERVER_PASS");
            crashRetained=crashRecords.length==1&&
                crashRecords[0].getFileName().toString().endsWith(".begin")&&
                Arrays.equals(crashContents,contents(records(root,CRASH)));

            fork(root,"complete",COMPLETE,0,"G21103_WRITER_PAIRED_DONE");
            Path[] completeRecords=records(root,COMPLETE);
            String[] completeContents=contents(completeRecords);
            fork(root,"inspect",COMPLETE,0,
                "G21103_FRESH_JVM_OBSERVER_PASS");
            pairedRetained=completeRecords.length==2&&
                Arrays.equals(completeContents,
                    contents(records(root,COMPLETE)));
            fork(root,"inspect",LEGACY,0,
                "G21103_FRESH_JVM_OBSERVER_PASS");
            legacyUnknown=records(root,LEGACY).length==0;

            // Corrupted previously well-formed v1 record: exact
            // payload and account identity must be checked.
            String uuid="01234567-89ab-cdef-0123-456789abcdef";
            Files.write(name(root,BAD,"v1-"+uuid+".begin"),
                "UNTRUSTED".getBytes(StandardCharsets.US_ASCII));
            corruptDenied=rejected(root,BAD,
                "G21.103 INVALID_OR_CHANGED_WRITER_RECORD_NO_UNLINK");

            Files.write(name(root,VERSION,"v2-"+uuid+".begin"),
                new byte[]{1,2,3});
            versionDenied=rejected(root,VERSION,
                "G21.103 UNKNOWN_WRITER_EVIDENCE_VERSION_NO_UNLINK");

            Files.write(name(root,ORPHAN,"v1-"+uuid+".complete"),
                ("SPK-MAILBOX-WRITER-ATTEMPT-V1\n"+
                 "ACCOUNT="+ORPHAN+"\nATTEMPT="+uuid+
                 "\nSTAGE=COMPLETE\n").getBytes(
                    StandardCharsets.US_ASCII));
            completeWithoutBeginDenied=rejected(root,ORPHAN,
                "G21.103 COMPLETE_WITHOUT_BEGIN_NO_UNLINK");

            for(int n=0;n<MailboxWriterAttemptEvidenceV1.MAX_RECORDS+1;n++)
                Files.write(name(root,OVER,
                    "v1-"+String.format(java.util.Locale.ROOT,
                        "%08x",n)+"-89ab-cdef-0123-456789abcdef.begin"),
                    new byte[]{7});
            overCapacityDenied=rejected(root,OVER,
                "G21.103 WRITER_EVIDENCE_OVER_CAPACITY_NO_UNLINK");
            noMutation=Arrays.equals(original[0],
                    Files.readAllBytes(r.resolve(CRASH)))&&
                Arrays.equals(original[1],
                    Files.readAllBytes(r.resolve(COMPLETE)))&&
                Arrays.equals(original[2],
                    Files.readAllBytes(r.resolve(LEGACY)))&&
                Arrays.equals(crashContents,contents(records(root,CRASH)))&&
                Arrays.equals(completeContents,
                    contents(records(root,COMPLETE)));
            locksReleased=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G21103_DURABLE_EVIDENCE_DIAGNOSTICS"+
                " forcedBeginSurvivesHardHalt="+crashRetained+
                " successfulPairPersists="+pairedRetained+
                " legacyWithoutVersionUnknown="+legacyUnknown+
                " corruptionDenied="+corruptDenied+
                " unknownVersionDenied="+versionDenied+
                " orphanCompletionDenied="+completeWithoutBeginDenied+
                " boundedOverflowDenied="+overCapacityDenied+
                " canonicalAndEvidenceUnmodified="+noMutation+
                " jvmLeasesZero="+locksReleased);
            check(crashRetained&&pairedRetained&&legacyUnknown&&
                corruptDenied&&versionDenied&&
                completeWithoutBeginDenied&&overCapacityDenied&&
                noMutation&&locksReleased,
                "durable v1 negative-only evidence regression");
            System.out.println("G21103_DURABLE_WRITER_EVIDENCE_PASS"+
                " independentJvms=5 version=1"+
                " beginForced=true completeCreateOnly=true"+
                " incompleteAfterCrash=true"+
                " legacyCannotProveParticipation=true"+
                " noDeletion=true noSession=true"+
                " grant=false replay=false ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2],args[3]);
            return;
        }
        check(args.length==0,"unknown test arguments");
        run();
    }
}
