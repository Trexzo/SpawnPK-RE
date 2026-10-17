package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.104: opt-in writer operation must reserve BOTH immutable
 * BEGIN+COMPLETE leaves before invoking callback; never prune history.
 */
public final class G21104MailboxBoundedEvidenceRetentionIntegrationTest {
    private static final String FULL="g21104-full";
    private static final String CRASH="g21104-crash";
    private static final String MIXED="g21104-mixed";
    private static final String MALFORMED="g21104-malformed";
    private static final String FOREIGN="g21104-foreign";
    private static final String LEGACY="g21104-legacy";

    private static void check(boolean good,String why){
        if(!good)throw new AssertionError("G21.104 "+why);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return account->root.resolve(account+".properties");
    }
    private static String prefix(String account){
        return account+".properties.g21103-writer-attempt-";
    }
    private static Path[] records(Path root,String account)
        throws IOException{
        try(Stream<Path> list=Files.list(root)){
            return list.filter(p->p.getFileName().toString()
                .startsWith(prefix(account)))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] fingerprint(Path root,String account)
        throws IOException{
        Path[] observed=records(root,account);
        String[] saved=new String[observed.length];
        for(int i=0;i<observed.length;i++)
            saved[i]=observed[i].getFileName().toString()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(observed[i]));
        return saved;
    }
    private static void negative(
        MailboxWriterAttemptEvidenceV1.Report report
    ){
        check(!report.historicalEvidencePruningAllowed&&
            !report.completeWriterPopulationKnown&&
            !report.everyHistoricalGenerationParticipates&&
            !report.crashEpochSealed&&
            !report.uncooperativeWritersExcluded&&
            !report.futureWritersExcluded&&
            !report.unlinkAuthorized&&!report.cleanupAuthorized&&
            !report.accountMutationAuthorized&&!report.grantAuthorized&&
            !report.replayAuthorized&&
            !report.restartAdmissionAuthorized&&
            !report.releaseAuthorized&&!report.clientAckAuthorized,
            "report conferred destructive or reward authority");
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void child(Path root,String mode)
        throws Exception{
        FilePlayerRepository.PathResolver resolver=paths(root);
        if("crash".equals(mode)){
            MailboxWriterAttemptEvidenceV1.runRecorded(
                resolver,CRASH,()->{
                    System.out.println("G21104_V1_BEGIN_BEFORE_HARD_HALT"+
                        " code=126 grant=false");
                    System.out.flush();
                    Runtime.getRuntime().halt(126);
                    throw new AssertionError("Runtime.halt returned");
                });
            throw new AssertionError("writer survived requested crash");
        }
        if("retry".equals(mode)){
            AtomicInteger executed=new AtomicInteger();
            boolean blocked=false;
            try{
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    resolver,CRASH,()->{
                        executed.incrementAndGet();
                        return null;
                    });
            }catch(IOException expected){
                blocked=String.valueOf(expected.getMessage()).contains(
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
            }
            MailboxWriterAttemptEvidenceV1.Report observation=
                MailboxWriterAttemptEvidenceV1.inspect(resolver,CRASH);
            negative(observation);
            check(blocked&&executed.get()==0&&
                observation.started==1&&observation.completed==0&&
                observation.recordCount==1&&
                observation.remainingRecordSlots==
                    MailboxWriterAttemptEvidenceV1.MAX_RECORDS-1&&
                observation.additionalCompleteAttemptSlots==0&&
                observation.status==MailboxWriterAttemptEvidenceV1.Status
                    .INCOMPLETE_ATTEMPT_NO_CLEANUP&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0,
                "crashed BEGIN not blocking subsequent opt-in action");
            System.out.println("G21104_CRASH_RETRY_CHILD_PASS"+
                " previousBeginDurable=true callbackNeverCalled=true"+
                " recordsUnchanged=true cleanup=false grant=false");
            return;
        }
        throw new AssertionError("unknown child mode");
    }
    private static void fork(
        Path root,String mode,int expectedCode,String marker
    )throws Exception{
        Path log=root.resolve("g21104-"+mode+".log");
        ProcessBuilder command=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21104MailboxBoundedEvidenceRetentionIntegrationTest
                .class.getName(),"--child",root.toString(),mode);
        command.redirectErrorStream(true);
        command.redirectOutput(log.toFile());
        Process p=command.start();
        try{
            check(p.waitFor(25,TimeUnit.SECONDS),
                "child timeout mode="+mode);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==expectedCode&&output.contains(marker),
            "child result mismatch "+mode+" code="+p.exitValue()+
            " output="+output);
    }
    private static boolean refusedBeforeAction(
        Path root,String account,String expectedPrefix
    )throws Exception{
        AtomicInteger invoked=new AtomicInteger();
        String[] before=fingerprint(root,account);
        boolean denied=false;
        try{
            MailboxWriterAttemptEvidenceV1.runRecorded(
                paths(root),account,()->{
                    invoked.incrementAndGet();
                    return true;
                });
        }catch(IOException expected){
            denied=String.valueOf(expected.getMessage()).contains(
                expectedPrefix);
        }
        return denied&&invoked.get()==0&&
            Arrays.equals(before,fingerprint(root,account));
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21104-bounded-ledger-");
        boolean fullRejectedBeforeCallback=false;
        boolean partialAfterCrashRejected=false;
        boolean mixedVersionRejected=false;
        boolean malformedRejected=false;
        boolean accountMismatchRejected=false;
        boolean legacyNotRetconned=false;
        boolean recordsUnchanged=false,accountsUnchanged=false;
        boolean noLeases=false;
        int successfulOps=0;
        try{
            FilePlayerRepository.PathResolver resolver=paths(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolver);
            String[] accounts={
                FULL,CRASH,MIXED,MALFORMED,FOREIGN,LEGACY
            };
            byte[][] baseline=new byte[accounts.length][];
            for(int i=0;i<accounts.length;i++){
                writer.saveStrict(PlayerSnapshotCodec.capture(
                    accounts[i],new WorldPlayer()));
                baseline[i]=Files.readAllBytes(
                    resolver.resolve(accounts[i]));
            }
            MailboxWriterAttemptEvidenceV1.Report noEvidence=
                MailboxWriterAttemptEvidenceV1.inspect(
                    resolver,FULL);
            negative(noEvidence);
            check(noEvidence.recordCount==0&&
                noEvidence.remainingRecordSlots==
                    MailboxWriterAttemptEvidenceV1.MAX_RECORDS&&
                noEvidence.additionalCompleteAttemptSlots==
                    MailboxWriterAttemptEvidenceV1.MAX_RECORDS/2,
                "empty legacy/unknown ledger capacity");

            // Fifteen paired attempts consume thirty immutable leaves.
            for(int n=0;n<15;n++){
                final int index=n;
                int value=MailboxWriterAttemptEvidenceV1.runRecorded(
                    resolver,FULL,()->index+1);
                check(value==n+1,"completed action value changed");
                successfulOps++;
            }
            MailboxWriterAttemptEvidenceV1.Report nearlyFull=
                MailboxWriterAttemptEvidenceV1.inspect(resolver,FULL);
            negative(nearlyFull);
            check(nearlyFull.started==15&&nearlyFull.completed==15&&
                nearlyFull.recordCount==30&&
                nearlyFull.remainingRecordSlots==2&&
                nearlyFull.additionalCompleteAttemptSlots==1&&
                nearlyFull.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP,
                "15 paired immutable attempts should reserve exactly 2");
            MailboxWriterAttemptEvidenceV1.runRecorded(
                resolver,FULL,()->{return 16;});
            successfulOps++;
            MailboxWriterAttemptEvidenceV1.Report filled=
                MailboxWriterAttemptEvidenceV1.inspect(resolver,FULL);
            negative(filled);
            String[] immutableFull=fingerprint(root,FULL);
            fullRejectedBeforeCallback=filled.started==16&&
                filled.completed==16&&filled.recordCount==32&&
                filled.remainingRecordSlots==0&&
                filled.additionalCompleteAttemptSlots==0&&
                immutableFull.length==
                    MailboxWriterAttemptEvidenceV1.MAX_RECORDS&&
                refusedBeforeAction(root,FULL,
                    "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL")&&
                Arrays.equals(immutableFull,fingerprint(root,FULL));

            fork(root,"crash",126,
                "G21104_V1_BEGIN_BEFORE_HARD_HALT");
            String[] crashBefore=fingerprint(root,CRASH);
            fork(root,"retry",0,"G21104_CRASH_RETRY_CHILD_PASS");
            partialAfterCrashRejected=crashBefore.length==1&&
                Arrays.equals(crashBefore,fingerprint(root,CRASH))&&
                refusedBeforeAction(root,CRASH,
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");

            // A historical mixed-version file in the exact v1 namespace
            // must veto even another opted-in writer's callback.
            MailboxWriterAttemptEvidenceV1.runRecorded(
                resolver,MIXED,()->{return true;});
            Path[] mixedV1=records(root,MIXED);
            check(mixedV1.length==2,
                "mixed fixture must start from one valid v1 pair");
            String uuid="01234567-89ab-cdef-0123-456789abcdef";
            Path foreignVersion=root.resolve(
                prefix(MIXED)+"v2-"+uuid+".begin");
            Files.write(foreignVersion,new byte[]{1,2,3});
            mixedVersionRejected=refusedBeforeAction(root,MIXED,
                "G21.103 UNKNOWN_WRITER_EVIDENCE_VERSION_NO_UNLINK");

            Path malformed=root.resolve(
                prefix(MALFORMED)+"v1-"+uuid+".begin");
            Files.write(malformed,
                "INVALID_WRITER_RECORD".getBytes(
                    StandardCharsets.US_ASCII));
            malformedRejected=refusedBeforeAction(root,MALFORMED,
                "G21.103 INVALID_OR_CHANGED_WRITER_RECORD_NO_UNLINK");

            // The filename is account-qualified but the payload belongs
            // to someone else. The check must refuse before callback.
            String suffix=mixedV1[0].getFileName().toString()
                .substring(prefix(MIXED).length());
            Path crossed=root.resolve(prefix(FOREIGN)+suffix);
            Files.write(crossed,Files.readAllBytes(mixedV1[0]));
            accountMismatchRejected=refusedBeforeAction(
                root,FOREIGN,
                "G21.103 INVALID_OR_CHANGED_WRITER_RECORD_NO_UNLINK");

            MailboxWriterAttemptEvidenceV1.Report legacyBefore=
                MailboxWriterAttemptEvidenceV1.inspect(
                    resolver,LEGACY);
            negative(legacyBefore);
            // This only proves the NEW action was explicitly opted in;
            // it cannot retroactively cover earlier raw/legacy writers.
            MailboxWriterAttemptEvidenceV1.runRecorded(
                resolver,LEGACY,()->{return true;});
            MailboxWriterAttemptEvidenceV1.Report legacyAfter=
                MailboxWriterAttemptEvidenceV1.inspect(
                    resolver,LEGACY);
            negative(legacyAfter);
            legacyNotRetconned=
                legacyBefore.status==MailboxWriterAttemptEvidenceV1.Status
                    .NO_VERSIONED_EVIDENCE_LEGACY_OR_UNKNOWN&&
                legacyAfter.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP&&
                legacyAfter.started==1&&legacyAfter.completed==1&&
                !legacyAfter.everyHistoricalGenerationParticipates;

            recordsUnchanged=Arrays.equals(
                immutableFull,fingerprint(root,FULL))&&
                Arrays.equals(crashBefore,fingerprint(root,CRASH));
            accountsUnchanged=true;
            for(int i=0;i<accounts.length;i++)
                accountsUnchanged&=Arrays.equals(
                    baseline[i],Files.readAllBytes(
                        resolver.resolve(accounts[i])));
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G21104_BOUNDED_RETENTION_DIAGNOSTICS"+
                " completeOperationsBeforeHardLimit="+successfulOps+
                " fullLedgerBlocksCallback="+fullRejectedBeforeCallback+
                " crashedIncompleteBlocksCallback="+
                    partialAfterCrashRejected+
                " mixedVersionBlocksCallback="+mixedVersionRejected+
                " malformedBlocksCallback="+malformedRejected+
                " foreignAccountBlocksCallback="+
                    accountMismatchRejected+
                " legacyHistoryStillUnknown="+legacyNotRetconned+
                " originalRecordsPreserved="+recordsUnchanged+
                " canonicalAccountsUnchanged="+accountsUnchanged+
                " jvmLeasesZero="+noLeases);
            check(successfulOps==16&&fullRejectedBeforeCallback&&
                partialAfterCrashRejected&&mixedVersionRejected&&
                malformedRejected&&accountMismatchRejected&&
                legacyNotRetconned&&recordsUnchanged&&
                accountsUnchanged&&noLeases,
                "negative-only bounded writer retention regression");
            System.out.println("G21104_BOUNDED_WRITER_RETENTION_PASS"+
                " maximumRecordedPairs=16 maximumLeaves=32"+
                " spaceReservedBeforeAction=true"+
                " noEvidenceCompaction=true mixedVersionsFailClosed=true"+
                " crashIncompleteVeto=true"+
                " cleanup=false unlink=false session=false"+
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
        if(args.length==3&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2]);return;
        }
        check(args.length==0,"unexpected test arguments");
        run();
    }
}
