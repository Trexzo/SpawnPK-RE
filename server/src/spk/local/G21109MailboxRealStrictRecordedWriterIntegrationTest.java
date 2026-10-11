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

/** G21.109: real strict file save, explicit versioned evidence only. */
public final class G21109MailboxRealStrictRecordedWriterIntegrationTest {
    private static final String COMPLETE="g21109-complete";
    private static final String CRASH="g21109-crash";
    private static final String FULL="g21109-full";
    private static final String FAILED="g21109-failed";
    private static final String LEGACY="g21109-legacy";

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.109 "+reason);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static PlayerSnapshot snapshot(String a){
        return PlayerSnapshotCodec.capture(a,new WorldPlayer());
    }
    private static void negative(MailboxWriterAttemptEvidenceV1.Report r){
        check(!r.historicalEvidencePruningAllowed&&
            !r.completeWriterPopulationKnown&&
            !r.everyHistoricalGenerationParticipates&&
            !r.crashEpochSealed&&!r.uncooperativeWritersExcluded&&
            !r.futureWritersExcluded&&!r.unlinkAuthorized&&
            !r.cleanupAuthorized&&!r.accountMutationAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.restartAdmissionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,"attempt evidence granted authority");
    }
    private static Path[] records(Path root,String account)
        throws IOException{
        try(Stream<Path> list=Files.list(root)){
            String prefix=account+
                ".properties.g21103-writer-attempt-v1-";
            return list.filter(p->p.getFileName().toString()
                .startsWith(prefix))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] fingerprint(Path root,String account)
        throws IOException{
        Path[] ps=records(root,account);
        String[] result=new String[ps.length];
        for(int i=0;i<ps.length;i++)
            result[i]=ps[i].getFileName().toString()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(ps[i]));
        return result;
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static boolean refused(
        StrictDurablePlayerSnapshotWriter writer,String a,String prefix
    )throws Exception{
        try{
            writer.saveStrictWithWriterEvidence(snapshot(a));
            return false;
        }catch(IOException expected){
            return String.valueOf(expected.getMessage()).contains(prefix);
        }
    }
    private static void child(Path root,String mode)throws Exception{
        FilePlayerRepository.PathResolver r=resolver(root);
        if("halt".equals(mode)){
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(r,point->{
                    if(point==StrictDurablePlayerSnapshotWriter
                            .Phase.AFTER_DIRECTORY_FORCE){
                        System.out.println(
                            "G21109_REAL_STRICT_ACCOUNT_FORCE_BEFORE_HALT"+
                            " durableBegin=true code=129");
                        System.out.flush();
                        Runtime.getRuntime().halt(129);
                        throw new AssertionError("Runtime.halt returned");
                    }
                });
            writer.saveStrictWithWriterEvidence(snapshot(CRASH));
            throw new AssertionError("strict write survived hard halt");
        }
        if("retry".equals(mode)){
            Path original=r.resolve(CRASH);
            byte[] before=Files.readAllBytes(original);
            String[] evidence=fingerprint(root,CRASH);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(r);
            boolean denied=refused(writer,CRASH,
                "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
            MailboxWriterAttemptEvidenceV1.Report report=
                MailboxWriterAttemptEvidenceV1.inspect(r,CRASH);
            negative(report);
            check(denied&&report.started==1&&report.completed==0&&
                report.status==MailboxWriterAttemptEvidenceV1.Status
                    .INCOMPLETE_ATTEMPT_NO_CLEANUP&&
                Arrays.equals(before,Files.readAllBytes(original))&&
                Arrays.equals(evidence,fingerprint(root,CRASH))&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0,
                "fresh JVM permitted uncertain strict retry");
            System.out.println(
                "G21109_FRESH_JVM_STRICT_RETRY_DENIED_PASS"+
                " beginOnly=true originalBytesUnchanged=true"+
                " cleanup=false grant=false");
            return;
        }
        throw new AssertionError("unknown child mode");
    }
    private static void fork(
        Path root,String mode,int expectedCode,String marker
    )throws Exception{
        Path log=root.resolve("g21109-"+mode+".log");
        Process p=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21109MailboxRealStrictRecordedWriterIntegrationTest.class
                .getName(),"--child",root.toString(),mode)
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
        try{
            check(p.waitFor(20,TimeUnit.SECONDS),
                "child timeout "+mode);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==expectedCode&&output.contains(marker),
            "child failed "+mode+" exit="+p.exitValue()+" "+output);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21109-strict-");
        boolean paired=false,failedBegin=false,legacyUnknown=false;
        boolean fullRejected=false,crashedRetained=false;
        boolean realFileChangedOnOptIn=false,accountPreservedOnVeto=false;
        boolean noLeases=false;
        try{
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter ordinary=
                new StrictDurablePlayerSnapshotWriter(r);
            // Ordinary non-World strict save remains unrecorded.
            ordinary.saveStrict(snapshot(LEGACY));
            MailboxWriterAttemptEvidenceV1.Report legacy=
                MailboxWriterAttemptEvidenceV1.inspect(r,LEGACY);
            negative(legacy);
            legacyUnknown=legacy.recordCount==0&&
                legacy.status==MailboxWriterAttemptEvidenceV1.Status
                    .NO_VERSIONED_EVIDENCE_LEGACY_OR_UNKNOWN;

            PlayerSnapshot saved=snapshot(COMPLETE);
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                ordinary.saveStrictWithWriterEvidence(saved);
            MailboxWriterAttemptEvidenceV1.Report complete=
                MailboxWriterAttemptEvidenceV1.inspect(r,COMPLETE);
            negative(complete);
            paired=receipt.matchesSnapshot(saved)&&
                complete.started==1&&complete.completed==1&&
                complete.recordCount==2&&
                complete.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP;
            realFileChangedOnOptIn=Files.size(r.resolve(COMPLETE))>0L;
            String[] pairEvidence=fingerprint(root,COMPLETE);
            ordinary.saveStrict(snapshot(COMPLETE));
            check(Arrays.equals(pairEvidence,
                fingerprint(root,COMPLETE)),
                "untracked strict save unexpectedly enrolled");

            // A real strict writer fault BEFORE the atomic replacement
            // leaves durable BEGIN, no COMPLETE and no new account bytes.
            ordinary.saveStrict(snapshot(FAILED));
            byte[] failedBefore=Files.readAllBytes(r.resolve(FAILED));
            StrictDurablePlayerSnapshotWriter bad=
                new StrictDurablePlayerSnapshotWriter(r,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_ATOMIC_REPLACE)
                        throw new IOException("G21109_INJECTED_FAILURE");
                });
            try{bad.saveStrictWithWriterEvidence(snapshot(FAILED));}
            catch(IOException expected){
                failedBegin=String.valueOf(expected.getMessage())
                    .contains("G21109_INJECTED_FAILURE");
            }
            MailboxWriterAttemptEvidenceV1.Report partial=
                MailboxWriterAttemptEvidenceV1.inspect(r,FAILED);
            negative(partial);
            failedBegin&=partial.recordCount==1&&
                partial.started==1&&partial.completed==0&&
                refused(ordinary,FAILED,
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL")&&
                Arrays.equals(failedBefore,
                    Files.readAllBytes(r.resolve(FAILED)));

            // Full 32 immutable leaves refuse the opt-in strict save
            // BEFORE serializing or replacing an existing account.
            ordinary.saveStrict(snapshot(FULL));
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            String[] beforeFull=fingerprint(root,FULL);
            byte[] fileBefore=Files.readAllBytes(r.resolve(FULL));
            fullRejected=refused(ordinary,FULL,
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL");
            accountPreservedOnVeto=Arrays.equals(fileBefore,
                Files.readAllBytes(r.resolve(FULL)))&&
                Arrays.equals(beforeFull,fingerprint(root,FULL))&&
                beforeFull.length==32;

            ordinary.saveStrict(snapshot(CRASH));
            fork(root,"halt",129,
                "G21109_REAL_STRICT_ACCOUNT_FORCE_BEFORE_HALT");
            MailboxWriterAttemptEvidenceV1.Report afterCrash=
                MailboxWriterAttemptEvidenceV1.inspect(r,CRASH);
            negative(afterCrash);
            byte[] crashBytes=Files.readAllBytes(r.resolve(CRASH));
            String[] crashEvidence=fingerprint(root,CRASH);
            fork(root,"retry",0,
                "G21109_FRESH_JVM_STRICT_RETRY_DENIED_PASS");
            crashedRetained=afterCrash.started==1&&
                afterCrash.completed==0&&
                crashEvidence.length==1&&
                crashEvidence[0].contains(".begin|")&&
                Arrays.equals(crashBytes,Files.readAllBytes(r.resolve(CRASH)))&&
                Arrays.equals(crashEvidence,fingerprint(root,CRASH));
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;

            System.out.println("G21109_REAL_STRICT_DIAGNOSTICS"+
                " ordinarySaveUnrecorded="+legacyUnknown+
                " realOptInReceiptPaired="+paired+
                " realCanonicalPublished="+realFileChangedOnOptIn+
                " failedBeforeMoveRetainsBegin="+failedBegin+
                " fullLedgerVetoesBeforeSave="+fullRejected+
                " canonicalAndRecordsPreservedOnVeto="+accountPreservedOnVeto+
                " hardJvmAfterFileForceRetainsBegin="+crashedRetained+
                " independentJvmRetryRefused="+crashedRetained+
                " noJvmLeases="+noLeases);
            check(paired&&failedBegin&&legacyUnknown&&fullRejected&&
                crashedRetained&&realFileChangedOnOptIn&&
                accountPreservedOnVeto&&noLeases,
                "negative-only strict writer enrollment regression");
            System.out.println("G21109_OPTIN_REAL_STRICT_WRITER_PASS"+
                " focusedWriter=G2123 nonWorldOnly=true"+
                " defaultHotpathsUnchanged=true realJvmRestart=true"+
                " v1EvidenceNoPrune=true"+
                " cleanup=false unlink=false rollover=false"+
                " grant=false replay=false session=false ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2]);return;
        }
        check(args.length==0,"unknown arguments");
        run();
    }
}
