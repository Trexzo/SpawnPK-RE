package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.110: opt-in G21.83 real PREPARED journal publication evidence,
 * not a reward, session, historical writer, or retention authority.
 */
public final class G21110MailboxRealPreparedJournalRecordedWriterIntegrationTest {
    private static final String COMPLETE="g21110-complete";
    private static final String LEGACY="g21110-legacy";
    private static final String FAILED="g21110-failed";
    private static final String FULL="g21110-full";
    private static final String CRASH="g21110-crash";

    private static void check(boolean ok,String s){
        if(!ok)throw new AssertionError("G21.110 "+s);
    }
    private static FilePlayerRepository.PathResolver resolver(Path dir){
        return account->dir.resolve(account+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(WorldPlayer w,MailboxSettlementPostimagePlanner.Proposal p){
            player=w;proposal=p;
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        String id=account+":gift";
        owner.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.110 recorded intent","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21110_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            owner.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(owner,
            MailboxPreparedClaimJournal.prepare(owner,selected));
        return new Seed(owner,MailboxSettlementPostimagePlanner.plan(
            owner,generation,selected));
    }
    private static Path[] records(Path root,String account)
        throws IOException{
        try(Stream<Path> stream=Files.list(root)){
            String prefix=account+
                ".properties.g21103-writer-attempt-v1-";
            return stream.filter(p->p.getFileName().toString()
                .startsWith(prefix))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] fingerprint(Path root,String account)
        throws IOException{
        Path[] files=records(root,account);
        String[] out=new String[files.length];
        for(int i=0;i<files.length;i++)
            out[i]=files[i].getFileName().toString()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(files[i]));
        return out;
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
            !r.clientAckAuthorized,"v1 evidence minted authority");
    }
    private static void negative(
        MailboxDurableIdempotencyIntentJournal.Observation r
    ){
        check(!r.durabilityConfirmed&&!r.transactionCommitted&&
            !r.liveApplied&&!r.grantAuthorized&&!r.replayAuthorized&&
            !r.rollbackAuthorized&&!r.restartAdmissionAuthorized&&
            !r.releaseAuthorized&&!r.clientAckAuthorized,
            "journal observation minted settlement authority");
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static boolean denied(
        MailboxDurableIdempotencyIntentJournal j,
        MailboxSettlementPostimagePlanner.Proposal p,String marker
    )throws IOException{
        try{
            j.publishPreparedIntentWithWriterEvidence(p);
            return false;
        }catch(IOException rejected){
            return String.valueOf(rejected.getMessage()).contains(marker);
        }
    }
    private static void child(Path root,String mode)throws Exception{
        FilePlayerRepository.PathResolver paths=resolver(root);
        try(World world=World.isolatedForTest(60000L)){
            Seed s=seed(world,CRASH);
            MailboxDurableIdempotencyIntentJournal plain=
                new MailboxDurableIdempotencyIntentJournal(paths);
            if("halt".equals(mode)){
                new StrictDurablePlayerSnapshotWriter(paths)
                    .saveStrict(s.proposal.preparedPreimage);
                MailboxDurableIdempotencyIntentJournal cut=
                    new MailboxDurableIdempotencyIntentJournal(
                        paths,phase->{
                            if(phase==MailboxDurableIdempotencyIntentJournal
                                    .Phase.AFTER_DIRECTORY_FORCE){
                                System.out.println(
                                    "G21110_REAL_JOURNAL_DIRFORCE_BEFORE_HALT"+
                                    " durableBegin=true code=130");
                                System.out.flush();
                                Runtime.getRuntime().halt(130);
                                throw new AssertionError("halt returned");
                            }
                        });
                cut.publishPreparedIntentWithWriterEvidence(s.proposal);
                throw new AssertionError("hard halt did not fire");
            }
            if("retry".equals(mode)){
                Path file=plain.journalPath(CRASH);
                byte[] original=Files.readAllBytes(file);
                String[] leaves=fingerprint(root,CRASH);
                boolean noRetry=denied(plain,s.proposal,
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
                MailboxWriterAttemptEvidenceV1.Report v1=
                    MailboxWriterAttemptEvidenceV1.inspect(paths,CRASH);
                MailboxDurableIdempotencyIntentJournal.Observation seen=
                    plain.inspect(CRASH);
                negative(v1);
                negative(seen);
                check(noRetry&&v1.recordCount==1&&v1.started==1&&
                    v1.completed==0&&
                    v1.status==MailboxWriterAttemptEvidenceV1.Status
                        .INCOMPLETE_ATTEMPT_NO_CLEANUP&&
                    seen.status==MailboxDurableIdempotencyIntentJournal
                        .Status.PREPARED_MATCH_NO_REPLAY&&
                    Arrays.equals(original,Files.readAllBytes(file))&&
                    Arrays.equals(leaves,fingerprint(root,CRASH))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0,
                    "fresh JVM accepted uncertain journal retry");
                System.out.println(
                    "G21110_FRESH_JVM_JOURNAL_RETRY_DENIED_PASS"+
                    " beginOnly=true journalUnchanged=true grant=false");
                return;
            }
            throw new AssertionError("unknown child mode");
        }
    }
    private static void fork(
        Path root,String mode,int code,String marker
    )throws Exception{
        Path log=root.resolve("g21110-"+mode+".log");
        Process p=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21110MailboxRealPreparedJournalRecordedWriterIntegrationTest
                .class.getName(),"--child",root.toString(),mode)
            .redirectErrorStream(true).redirectOutput(log.toFile())
            .start();
        try{
            check(p.waitFor(25,TimeUnit.SECONDS),
                "child timeout "+mode);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String logText=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==code&&logText.contains(marker),
            "child "+mode+" exit="+p.exitValue()+" "+logText);
    }

    private static void run()throws Exception{
        Path dir=Files.createTempDirectory("g21110-journal-");
        boolean legacyUnrecorded=false,realPaired=false;
        boolean pairedSemanticMatch=false,prelinkFailureBeginOnly=false;
        boolean fullDenied=false,crashJournalBeginOnly=false;
        boolean retryDenied=false,accountsUnchangedOnVeto=false;
        boolean noLiveReward=false,noLeases=false;
        try(World world=World.isolatedForTest(60000L)){
            FilePlayerRepository.PathResolver paths=resolver(dir);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(paths);
            MailboxDurableIdempotencyIntentJournal publisher=
                new MailboxDurableIdempotencyIntentJournal(paths);

            Seed legacy=seed(world,LEGACY);
            strict.saveStrict(legacy.proposal.preparedPreimage);
            publisher.publishPreparedIntent(legacy.proposal);
            MailboxWriterAttemptEvidenceV1.Report legacyLog=
                MailboxWriterAttemptEvidenceV1.inspect(paths,LEGACY);
            negative(legacyLog);
            legacyUnrecorded=legacyLog.recordCount==0&&
                legacyLog.status==MailboxWriterAttemptEvidenceV1.Status
                    .NO_VERSIONED_EVIDENCE_LEGACY_OR_UNKNOWN;

            Seed good=seed(world,COMPLETE);
            strict.saveStrict(good.proposal.preparedPreimage);
            publisher.publishPreparedIntentWithWriterEvidence(good.proposal);
            MailboxWriterAttemptEvidenceV1.Report record=
                MailboxWriterAttemptEvidenceV1.inspect(paths,COMPLETE);
            MailboxDurableIdempotencyIntentJournal.Observation intent=
                publisher.inspect(COMPLETE);
            negative(record);
            negative(intent);
            realPaired=record.started==1&&record.completed==1&&
                record.recordCount==2&&
                record.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP;
            pairedSemanticMatch=intent.status==
                MailboxDurableIdempotencyIntentJournal.Status
                    .PREPARED_MATCH_NO_REPLAY&&intent.recordValidated&&
                intent.messageId.equals(good.proposal.messageId)&&
                intent.intentKey.equals(good.proposal.idempotencyKey)&&
                Files.size(publisher.journalPath(COMPLETE))>0L;

            Seed fail=seed(world,FAILED);
            strict.saveStrict(fail.proposal.preparedPreimage);
            byte[] originalFail=Files.readAllBytes(paths.resolve(FAILED));
            MailboxDurableIdempotencyIntentJournal faulted=
                new MailboxDurableIdempotencyIntentJournal(paths,phase->{
                    if(phase==MailboxDurableIdempotencyIntentJournal.Phase
                            .BEFORE_LINK)
                        throw new IOException("G21110_INJECTED_PRELINK");
                });
            boolean failed=false;
            try{
                faulted.publishPreparedIntentWithWriterEvidence(
                    fail.proposal);
            }catch(IOException expected){
                failed=String.valueOf(expected.getMessage())
                    .contains("G21110_INJECTED_PRELINK");
            }
            MailboxWriterAttemptEvidenceV1.Report incomplete=
                MailboxWriterAttemptEvidenceV1.inspect(paths,FAILED);
            negative(incomplete);
            prelinkFailureBeginOnly=failed&&incomplete.started==1&&
                incomplete.completed==0&&incomplete.recordCount==1&&
                !Files.exists(publisher.journalPath(FAILED),
                    LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(originalFail,
                    Files.readAllBytes(paths.resolve(FAILED)))&&
                denied(publisher,fail.proposal,
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");

            Seed full=seed(world,FULL);
            strict.saveStrict(full.proposal.preparedPreimage);
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    paths,FULL,()->Boolean.TRUE);
            String[] originalFullRecords=fingerprint(dir,FULL);
            byte[] originalFullAccount=
                Files.readAllBytes(paths.resolve(FULL));
            fullDenied=denied(publisher,full.proposal,
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL");
            accountsUnchangedOnVeto=Arrays.equals(
                    originalFullRecords,fingerprint(dir,FULL))&&
                Arrays.equals(originalFullAccount,
                    Files.readAllBytes(paths.resolve(FULL)))&&
                !Files.exists(publisher.journalPath(FULL),
                    LinkOption.NOFOLLOW_LINKS);

            fork(dir,"halt",130,
                "G21110_REAL_JOURNAL_DIRFORCE_BEFORE_HALT");
            MailboxWriterAttemptEvidenceV1.Report afterHalt=
                MailboxWriterAttemptEvidenceV1.inspect(paths,CRASH);
            negative(afterHalt);
            byte[] firstJournal=Files.readAllBytes(
                publisher.journalPath(CRASH));
            String[] firstRecords=fingerprint(dir,CRASH);
            fork(dir,"retry",0,
                "G21110_FRESH_JVM_JOURNAL_RETRY_DENIED_PASS");
            crashJournalBeginOnly=afterHalt.started==1&&
                afterHalt.completed==0&&afterHalt.recordCount==1&&
                firstRecords.length==1&&
                firstRecords[0].contains(".begin|")&&
                Arrays.equals(firstJournal,Files.readAllBytes(
                    publisher.journalPath(CRASH)))&&
                Arrays.equals(firstRecords,fingerprint(dir,CRASH));
            retryDenied=crashJournalBeginOnly;
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            noLiveReward=legacy.player.bank().inventorySlots()==0&&
                good.player.bank().inventorySlots()==0&&
                fail.player.bank().inventorySlots()==0&&
                full.player.bank().inventorySlots()==0&&
                good.player.mailbox().get(good.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            check(legacyUnrecorded&&realPaired&&pairedSemanticMatch&&
                prelinkFailureBeginOnly&&fullDenied&&
                accountsUnchangedOnVeto&&crashJournalBeginOnly&&
                retryDenied&&noLiveReward&&noLeases,
                "real opt-in G21.83 journal contract");
            System.out.println("G21110_REAL_PREPARED_JOURNAL_DIAGNOSTICS"+
                " legacyJournalUnrecorded="+legacyUnrecorded+
                " optInRealJournalPaired="+realPaired+
                " semanticPreparedMatch="+pairedSemanticMatch+
                " prelinkFailureLeavesBeginOnly="+prelinkFailureBeginOnly+
                " fullV1LedgerBlocksBeforeJournal="+fullDenied+
                " existingAccountUnchangedOnVeto="+
                    accountsUnchangedOnVeto+
                " hardJvmAfterJournalForceLeavesBegin="+
                    crashJournalBeginOnly+
                " independentJvmRetryDenied="+retryDenied+
                " noLiveGrant="+noLiveReward+
                " zeroJvmLeases="+noLeases);
            System.out.println("G21110_OPTIN_REAL_JOURNAL_WRITER_PASS"+
                " writer=G2183 preparedOnly=true"+
                " defaultHotpathsUnchanged=true v1EvidenceNoPrune=true"+
                " legacyWritersFenced=false cleanup=false unlink=false"+
                " rollover=false grant=false replay=false session=false"+
                " ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(dir)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
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
