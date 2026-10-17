package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** G21.105: portable, bounded, negative-only archive with real JVM verify. */
public final class G21105MailboxPortableEvidenceArchiveIntegrationTest {
    private static final String FULL="g21105-full";
    private static final String CRASH="g21105-crash";
    private static final String LEGACY="g21105-legacy";
    private static final String MIXED="g21105-mixed";
    private static final String TAMPER="g21105-tamper";

    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.105 "+message);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return a->root.resolve(a+".properties");
    }
    private static void negative(
        MailboxWriterEvidencePortableArchiveV1.Verified v
    ){
        check(!v.hardwareDurabilityProved&&
            !v.completeWriterPopulationProved&&
            !v.sourceStillUnchangedProved&&
            !v.cleanupAuthorized&&!v.unlinkAuthorized&&
            !v.recordPruningAuthorized&&!v.rolloverAuthorized&&
            !v.grantAuthorized&&!v.replayAuthorized&&
            !v.restartAdmissionAuthorized&&!v.clientAckAuthorized,
            "archive should have zero authority");
    }
    private static String[] ledger(Path root,String account)
        throws Exception{
        String prefix=account+".properties.g21103-writer-attempt-";
        try(Stream<Path> all=Files.list(root)){
            return all.filter(p->p.getFileName().toString()
                .startsWith(prefix))
                .sorted(Comparator.comparing(Path::toString))
                .map(p->{
                    try{
                        return p.getFileName()+"|"+
                            Base64.getEncoder().encodeToString(
                                Files.readAllBytes(p));
                    }catch(IOException e){
                        throw new IllegalStateException(e);
                    }
                }).toArray(String[]::new);
        }
    }
    private static boolean refusesCapture(
        Path root,String account,String marker
    ){
        try{
            MailboxWriterEvidencePortableArchiveV1.capture(
                paths(root),account);
            return false;
        }catch(IOException expected){
            return String.valueOf(expected.getMessage())
                .contains(marker);
        }
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void fork(
        Path root,String mode,int code,String marker
    )throws Exception{
        Path logfile=root.resolve("g21105-"+mode+".log");
        ProcessBuilder b=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21105MailboxPortableEvidenceArchiveIntegrationTest
                .class.getName(),"--child",root.toString(),mode);
        b.redirectErrorStream(true);
        b.redirectOutput(logfile.toFile());
        Process process=b.start();
        try{
            check(process.waitFor(20,TimeUnit.SECONDS),
                "child timed out "+mode);
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                process.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(logfile),
            StandardCharsets.UTF_8);
        check(process.exitValue()==code&&output.contains(marker),
            "child result mismatch "+mode+
            " actual="+process.exitValue()+
            " expected="+code+" output="+output);
    }
    private static void child(Path root,String mode)
        throws Exception{
        FilePlayerRepository.PathResolver resolver=paths(root);
        if("crash".equals(mode)){
            MailboxWriterAttemptEvidenceV1.runRecorded(
                resolver,CRASH,()->{
                    System.out.println("G21105_OPTIN_BEGIN_HARD_HALT");
                    System.out.flush();
                    Runtime.getRuntime().halt(127);
                    throw new AssertionError("hard halt returned");
                });
            throw new AssertionError("crash writer returned");
        }
        if("verify-full".equals(mode)){
            String portable=new String(Files.readAllBytes(
                root.resolve("manual-full.archive")),
                StandardCharsets.US_ASCII);
            String expected=new String(Files.readAllBytes(
                root.resolve("manual-full.sha256")),
                StandardCharsets.US_ASCII).trim();
            MailboxWriterEvidencePortableArchiveV1.Verified checked=
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    portable,expected);
            negative(checked);
            MailboxWriterEvidencePortableArchiveV1.Snapshot current=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,FULL);
            negative(current.verified);
            check(current.sha256.equals(expected)&&
                current.portable.equals(portable)&&
                checked.records==32&&checked.started==16&&
                checked.completed==16&&
                !current.savedToExternalArchive&&
                !current.ledgerMutationAuthorized&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0,
                "independent JVM portable/full ledger mismatch");
            System.out.println("G21105_INDEPENDENT_FULL_ARCHIVE_VERIFY_PASS"+
                " records=32 sha256Verified=true noPruning=true");
            return;
        }
        if("verify-crash".equals(mode)){
            MailboxWriterEvidencePortableArchiveV1.Snapshot a=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,CRASH);
            negative(a.verified);
            check(a.verified.records==1&&a.verified.started==1&&
                a.verified.completed==0&&
                a.verified.status==MailboxWriterAttemptEvidenceV1
                    .Status.INCOMPLETE_ATTEMPT_NO_CLEANUP&&
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    a.portable,a.sha256).records==1&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0,
                "hard crash BEGIN not preserved as incomplete archive");
            System.out.println("G21105_INDEPENDENT_CRASH_ARCHIVE_VERIFY_PASS"+
                " incompleteRetained=true noGrant=true noUnlink=true");
            return;
        }
        throw new AssertionError("unknown child mode");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21105-archive-");
        boolean fullArchived=false,independentFull=false;
        boolean incompleteArchived=false,digestTamperDenied=false;
        boolean forgedDigestStructureDenied=false,legacyDenied=false;
        boolean mixedDenied=false,diskTamperDenied=false;
        boolean noRollover=true,originalUnchanged=false;
        boolean noLeaks=false;
        try{
            FilePlayerRepository.PathResolver resolver=paths(root);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(resolver);
            for(String account:new String[]{
                    FULL,CRASH,LEGACY,MIXED,TAMPER})
                strict.saveStrict(PlayerSnapshotCodec.capture(
                    account,new WorldPlayer()));
            byte[] fullAccount=Files.readAllBytes(resolver.resolve(FULL));
            byte[] crashAccount=Files.readAllBytes(resolver.resolve(CRASH));
            for(int n=0;n<16;n++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    resolver,FULL,()->true);
            String[] before=ledger(root,FULL);
            MailboxWriterEvidencePortableArchiveV1.Snapshot exported=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    resolver,FULL);
            negative(exported.verified);
            fullArchived=before.length==32&&
                exported.verified.records==32&&
                exported.verified.started==16&&
                exported.verified.completed==16&&
                exported.verified.status==MailboxWriterAttemptEvidenceV1
                    .Status.ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP&&
                Arrays.equals(before,ledger(root,FULL))&&
                !exported.savedToExternalArchive&&
                !exported.ledgerMutationAuthorized;
            check(fullArchived,"full ledger read-only capture failed");
            // Explicit export is test-harness output only. Production
            // capture has NO filesystem-writing method.
            Files.write(root.resolve("manual-full.archive"),
                exported.portable.getBytes(StandardCharsets.US_ASCII));
            Files.write(root.resolve("manual-full.sha256"),
                exported.sha256.getBytes(StandardCharsets.US_ASCII));
            fork(root,"verify-full",0,
                "G21105_INDEPENDENT_FULL_ARCHIVE_VERIFY_PASS");
            independentFull=true;
            noRollover=Arrays.equals(before,ledger(root,FULL));
            try{
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    resolver,FULL,()->false);
                noRollover=false;
            }catch(IOException expected){
                noRollover&=String.valueOf(expected.getMessage())
                    .contains(
                        "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL");
            }
            noRollover&=Arrays.equals(before,ledger(root,FULL));

            fork(root,"crash",127,"G21105_OPTIN_BEGIN_HARD_HALT");
            String[] crashBefore=ledger(root,CRASH);
            fork(root,"verify-crash",0,
                "G21105_INDEPENDENT_CRASH_ARCHIVE_VERIFY_PASS");
            incompleteArchived=crashBefore.length==1&&
                Arrays.equals(crashBefore,ledger(root,CRASH));

            String changed=exported.portable.replace(
                "COUNTS|16|16|32","COUNTS|16|15|32");
            check(!changed.equals(exported.portable),
                "counter mutation fixture not constructed");
            try{
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    changed,exported.sha256);
            }catch(IOException expected){
                digestTamperDenied=String.valueOf(
                    expected.getMessage()).contains("SHA256_MISMATCH");
            }
            // Malicious/self-rehashed export must also fail STRUCTURE,
            // rather than trusting only an attacker-supplied hash.
            String rehash=changed;
            String rehashSha=sha256ForTest(rehash);
            try{
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    rehash,rehashSha);
            }catch(IOException expected){
                forgedDigestStructureDenied=String.valueOf(
                    expected.getMessage()).contains(
                        "G21.105 INVALID_PORTABLE_WRITER_ARCHIVE");
            }
            legacyDenied=refusesCapture(root,LEGACY,
                "G21.105 NO_V1_ARCHIVE_EVIDENCE_NO_UNLINK");

            String uuid="01234567-89ab-cdef-0123-456789abcdef";
            Files.write(root.resolve(MIXED+
                    ".properties.g21103-writer-attempt-v2-"+uuid+".begin"),
                new byte[]{7});
            mixedDenied=refusesCapture(root,MIXED,
                "G21.103 UNKNOWN_WRITER_EVIDENCE_VERSION_NO_UNLINK");

            MailboxWriterAttemptEvidenceV1.runRecorded(
                resolver,TAMPER,()->true);
            Path altered;
            try(Stream<Path> files=Files.list(root)){
                altered=files.filter(p->p.getFileName().toString()
                    .startsWith(TAMPER+
                        ".properties.g21103-writer-attempt-v1-"))
                    .findFirst().orElseThrow(
                        ()->new AssertionError("tamper fixture absent"));
            }
            FileTime originalTime=Files.getLastModifiedTime(
                altered,LinkOption.NOFOLLOW_LINKS);
            byte[] content=Files.readAllBytes(altered);
            // Equal-length payload alteration with restored mtime:
            // name, physical identity, length, timestamp unchanged.
            byte[] corrupt=Arrays.copyOf(content,content.length);
            corrupt[0]=(byte)(corrupt[0]^1);
            Files.write(altered,corrupt);
            Files.setLastModifiedTime(altered,originalTime);
            diskTamperDenied=refusesCapture(root,TAMPER,
                "G21.103 INVALID_OR_CHANGED_WRITER_RECORD_NO_UNLINK");
            originalUnchanged=
                Arrays.equals(before,ledger(root,FULL))&&
                Arrays.equals(crashBefore,ledger(root,CRASH))&&
                Arrays.equals(fullAccount,
                    Files.readAllBytes(resolver.resolve(FULL)))&&
                Arrays.equals(crashAccount,
                    Files.readAllBytes(resolver.resolve(CRASH)));
            noLeaks=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G21105_ARCHIVE_DIAGNOSTICS"+
                " fullBoundedLedgerCaptured="+fullArchived+
                " independentJvmVerified="+independentFull+
                " crashBeginArchiveRetained="+incompleteArchived+
                " originalSHARejectsTampering="+digestTamperDenied+
                " forgedSHAStillStructureRejected="+
                    forgedDigestStructureDenied+
                " legacyAbsentRejected="+legacyDenied+
                " unknownVersionRejected="+mixedDenied+
                " diskPayloadTamperRejected="+diskTamperDenied+
                " noRolloverOrPruning="+noRollover+
                " canonicalAndSourceEvidenceUnchanged="+
                    originalUnchanged+" jvmLeaseZero="+noLeaks);
            check(fullArchived&&independentFull&&incompleteArchived&&
                digestTamperDenied&&forgedDigestStructureDenied&&
                legacyDenied&&mixedDenied&&diskTamperDenied&&
                noRollover&&originalUnchanged&&noLeaks,
                "bounded portable evidence archival regression");
            System.out.println("G21105_READONLY_PORTABLE_ARCHIVE_PASS"+
                " records=32 freshJvmIndependentVerify=true"+
                " incompleteCrashEvidencePreserved=true"+
                " hashAndSchemaTamperingDenied=true"+
                " retentionRollover=false"+
                " pruning=false unlink=false cleanup=false"+
                " grant=false replay=false session=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    private static String sha256ForTest(String text)
        throws Exception{
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(StandardCharsets.US_ASCII));
        StringBuilder result=new StringBuilder();
        for(byte b:digest)
            result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2]);return;
        }
        check(args.length==0,"unknown args");
        run();
    }
}
