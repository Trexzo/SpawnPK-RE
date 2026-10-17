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
 * G21.118: true child-JVM, forced v1/v2 diagnostic evidence.
 * Explicit opt-in only. No durable activity evidence arises from
 * normal G21.100 writers unless they elect this API.
 */
public final class G21118MailboxDurableCrossJvmDiagnosticIntegrationTest {
    private static final String DONE="g21118-done";
    private static final String CRASH="g21118-crash";
    private static final String PLAIN="g21118-plain";
    private static final String FULL="g21118-full";
    private static final String OTHER="g21118-other";

    private static void check(boolean b,String s){
        if(!b)throw new AssertionError("G21.118 "+s);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        Seed(WorldPlayer p,MailboxSettlementPostimagePlanner.Proposal q){
            player=p;proposal=q;
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.118 cross-JVM durable diagnostic","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21118_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot selected=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,selected));
        return new Seed(player,MailboxSettlementPostimagePlanner.plan(
            player,generation,selected));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String hash(Path path)throws Exception{
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path));
        StringBuilder hex=new StringBuilder(64);
        for(byte b:bytes)
            hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private static Path[] leaves(Path root,String account,String suffix)
        throws IOException{
        try(Stream<Path> stream=Files.list(root)){
            return stream.filter(p->p.getFileName().toString()
                .startsWith(account+".properties"+suffix))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] fingerprint(
        Path root,String account,String prefix
    )throws IOException{
        Path[] ps=leaves(root,account,prefix);
        String[] result=new String[ps.length];
        for(int i=0;i<ps.length;i++)
            result[i]=ps[i].getFileName()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(ps[i]));
        return result;
    }
    @FunctionalInterface
    private interface Action {void run()throws IOException;}
    private static boolean rejected(Action act,String expected)
        throws IOException{
        try{act.run();return false;}
        catch(IOException e){
            return String.valueOf(e.getMessage()).contains(expected);
        }
    }
    private static MailboxWriterKindEvidenceV2.Report kind(
        FilePlayerRepository.PathResolver resolver,String account
    )throws IOException{
        MailboxWriterKindEvidenceV2.Report r=
            MailboxWriterKindEvidenceV2.inspect(resolver,account);
        check(!r.completeHistoricalWriterPopulation&&
            !r.defaultWritersEnrolled&&!r.sourceAuthenticityVerified&&
            !r.allFutureWritersExcluded&&
            !r.cleanupAuthorized&&!r.unlinkAuthorized&&
            !r.rolloverAuthorized&&!r.pruningAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.sessionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,
            "kind report manufactured authorization");
        return r;
    }
    private static String javaBinary(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void child(Path root,String account,String mode)
        throws Exception{
        FilePlayerRepository.PathResolver r=resolver(root);
        if("normal".equals(mode)){
            MailboxPublicationWriterLifecycle.withWriter(
                r.resolve(account),()->Boolean.TRUE);
            System.out.println("G21118_CHILD_DEFAULT_UNRECORDED");
            return;
        }
        if("success".equals(mode)){
            String before=hash(r.resolve(account));
            Boolean completed=MailboxCrossJvmDurableWriterDiagnosticV1
                .runRecorded(r,account,()->Boolean.TRUE);
            check(completed&&before.equals(hash(r.resolve(account))),
                "child diagnostic wrote account");
            System.out.println("G21118_CHILD_FORCED_PAIR_NO_CONTENT_MUTATION");
            return;
        }
        if("halt".equals(mode)){
            MailboxCrossJvmDurableWriterDiagnosticV1.runRecorded(
                r,account,()->{
                    System.out.println(
                        "G21118_CHILD_ENTERED_AFTER_FORCED_BEGIN");
                    System.out.flush();
                    Runtime.getRuntime().halt(138);
                    throw new AssertionError("hard halt returned");
                });
            throw new AssertionError("expected JVM halt");
        }
        if("observe".equals(mode)){
            String[] v1before=fingerprint(
                root,account,".g21103-writer-attempt-");
            String[] v2before=fingerprint(
                root,account,".g21114-writer-kind-");
            String canonical=hash(r.resolve(account));
            MailboxWriterAttemptEvidenceV1.Report v1=
                MailboxWriterAttemptEvidenceV1.inspect(r,account);
            MailboxWriterKindEvidenceV2.Report typed=kind(r,account);
            boolean[] actionCalled={false};
            boolean veto=rejected(
                ()->MailboxCrossJvmDurableWriterDiagnosticV1
                    .runRecorded(r,account,()->{
                        actionCalled[0]=true;
                        return Boolean.TRUE;
                    }),
                "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
            check(veto&&!actionCalled[0]&&
                v1.recordCount==1&&v1.started==1&&v1.completed==0&&
                v1.status==MailboxWriterAttemptEvidenceV1.Status
                    .INCOMPLETE_ATTEMPT_NO_CLEANUP&&
                typed.status==MailboxWriterKindEvidenceV2.Status
                    .INCOMPLETE_TYPED_NO_AUTHORITY&&
                typed.typedBegins==1&&typed.typedCompletes==0&&
                typed.untypedV1Leaves==0&&
                Arrays.equals(v1before,fingerprint(
                    root,account,".g21103-writer-attempt-"))&&
                Arrays.equals(v2before,fingerprint(
                    root,account,".g21114-writer-kind-"))&&
                canonical.equals(hash(r.resolve(account)))&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0,
                "independent JVM mutated/retried incomplete diagnostic");
            System.out.println("G21118_FRESH_JVM_INCOMPLETE_VETO_PASS"+
                " beginOnly=true retryActionExecuted=false"+
                " bytesPreserved=true pruning=false grant=false");
            return;
        }
        throw new AssertionError("unknown child mode: "+mode);
    }
    private static String fork(
        Path root,String account,String mode,int exit,String marker
    )throws Exception{
        Path log=root.resolve("g21118-"+account+"-"+mode+".log");
        Process process=new ProcessBuilder(
            javaBinary(),"-cp",System.getProperty("java.class.path"),
            G21118MailboxDurableCrossJvmDiagnosticIntegrationTest
                .class.getName(),
            "--child",root.toString(),account,mode)
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
        try{
            check(process.waitFor(25L,TimeUnit.SECONDS),
                "child process timeout mode="+mode);
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                process.waitFor(5L,TimeUnit.SECONDS);
            }
        }
        String message=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(process.exitValue()==exit&&message.contains(marker),
            "child mode="+mode+" exit="+process.exitValue()+
            " expected="+exit+" output="+message);
        return message;
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21118-durable-crossjvm-");
        boolean plainUnrecorded=false,foreignSuccessDurable=false;
        boolean accountBytesUnchanged=false,foreignCrashDurable=false;
        boolean thirdJvmRetryVeto=false,postcrashDefaultsUnfenced=false;
        boolean fullCapDenied=false,otherAccountIsolated=false;
        boolean stillNoLiveGrant=false,zeroJvmLeases=false;
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(r);
            Seed done=seed(world,DONE);
            Seed crash=seed(world,CRASH);
            Seed plain=seed(world,PLAIN);
            Seed full=seed(world,FULL);
            Seed other=seed(world,OTHER);
            for(Seed s:new Seed[]{done,crash,plain,full,other})
                strict.saveStrict(s.proposal.preparedPreimage);
            String doneAccount=hash(r.resolve(DONE));
            String crashedAccount=hash(r.resolve(CRASH));
            String originalOther=hash(r.resolve(OTHER));

            fork(root,PLAIN,"normal",0,
                "G21118_CHILD_DEFAULT_UNRECORDED");
            MailboxWriterAttemptEvidenceV1.Report old=
                MailboxWriterAttemptEvidenceV1.inspect(r,PLAIN);
            plainUnrecorded=old.recordCount==0&&
                kind(r,PLAIN).status==MailboxWriterKindEvidenceV2.Status
                    .NO_TYPED_EVIDENCE_UNKNOWN;

            fork(root,DONE,"success",0,
                "G21118_CHILD_FORCED_PAIR_NO_CONTENT_MUTATION");
            MailboxWriterAttemptEvidenceV1.Report v1=
                MailboxWriterAttemptEvidenceV1.inspect(r,DONE);
            MailboxWriterKindEvidenceV2.Report v2=kind(r,DONE);
            foreignSuccessDurable=v1.recordCount==2&&
                v1.started==1&&v1.completed==1&&
                v1.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP&&
                v2.status==MailboxWriterKindEvidenceV2.Status
                    .OBSERVED_TYPED_PAIRED_NO_AUTHORITY&&
                v2.typedBegins==1&&v2.typedCompletes==1&&
                v2.pairedByKind.size()==1&&
                v2.pairedByKind.getOrDefault(
                    MailboxWriterKindEvidenceV2.Kind
                        .COOPERATING_DIAGNOSTIC,0)==1&&
                leaves(root,DONE,".g21114-writer-kind-").length==2&&
                MailboxCrossJvmWriterLeaseProbeV1
                    .sample(r,DONE,140L).state==
                    MailboxCrossJvmWriterLeaseProbeV1.State
                        .QUIET_AT_SAMPLE_ONLY;
            accountBytesUnchanged=doneAccount.equals(
                hash(r.resolve(DONE)));

            // A fresh writer after success is unrecorded unless it
            // EXPLICITLY calls the G21.118 diagnostic opt-in.
            MailboxPublicationWriterLifecycle.withWriter(
                r.resolve(DONE),()->Boolean.TRUE);
            foreignSuccessDurable&=
                MailboxWriterAttemptEvidenceV1.inspect(r,DONE)
                    .recordCount==2&&
                kind(r,DONE).typedBegins==1;

            fork(root,CRASH,"halt",138,
                "G21118_CHILD_ENTERED_AFTER_FORCED_BEGIN");
            MailboxWriterAttemptEvidenceV1.Report crashed=
                MailboxWriterAttemptEvidenceV1.inspect(r,CRASH);
            MailboxWriterKindEvidenceV2.Report partial=kind(r,CRASH);
            foreignCrashDurable=crashed.started==1&&
                crashed.completed==0&&crashed.recordCount==1&&
                partial.status==MailboxWriterKindEvidenceV2.Status
                    .INCOMPLETE_TYPED_NO_AUTHORITY&&
                partial.typedBegins==1&&partial.typedCompletes==0&&
                crashedAccount.equals(hash(r.resolve(CRASH)))&&
                MailboxCrossJvmWriterLeaseProbeV1
                    .sample(r,CRASH,140L).state==
                    MailboxCrossJvmWriterLeaseProbeV1.State
                        .QUIET_AT_SAMPLE_ONLY;
            String[] beginV1=fingerprint(
                root,CRASH,".g21103-writer-attempt-");
            String[] beginV2=fingerprint(
                root,CRASH,".g21114-writer-kind-");
            fork(root,CRASH,"observe",0,
                "G21118_FRESH_JVM_INCOMPLETE_VETO_PASS");
            thirdJvmRetryVeto=
                Arrays.equals(beginV1,fingerprint(
                    root,CRASH,".g21103-writer-attempt-"))&&
                Arrays.equals(beginV2,fingerprint(
                    root,CRASH,".g21114-writer-kind-"));
            // Ordinary cooperating writers are still UNFENCED by
            // incomplete opt-in evidence. This is an explicit gap.
            MailboxPublicationWriterLifecycle.withWriter(
                r.resolve(CRASH),()->Boolean.TRUE);
            postcrashDefaultsUnfenced=
                MailboxWriterAttemptEvidenceV1.inspect(r,CRASH)
                    .recordCount==1&&
                kind(r,CRASH).typedBegins==1;

            // The shared v1 32-leaf limit is checked before a
            // diagnostic callback can execute or create v2 bytes.
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            String[] beforeFull=fingerprint(
                root,FULL,".g21103-writer-attempt-");
            String accountBeforeFull=hash(r.resolve(FULL));
            boolean[] called={false};
            fullCapDenied=rejected(
                ()->MailboxCrossJvmDurableWriterDiagnosticV1
                    .runRecorded(r,FULL,()->{
                        called[0]=true;
                        return Boolean.TRUE;
                    }),
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL")&&
                !called[0]&&
                Arrays.equals(beforeFull,fingerprint(
                    root,FULL,".g21103-writer-attempt-"))&&
                leaves(root,FULL,".g21114-writer-kind-").length==0&&
                accountBeforeFull.equals(hash(r.resolve(FULL)));
            otherAccountIsolated=originalOther.equals(hash(
                r.resolve(OTHER)))&&
                MailboxWriterAttemptEvidenceV1.inspect(r,OTHER)
                    .recordCount==0&&
                kind(r,OTHER).typedBegins==0;
            stillNoLiveGrant=noGrant(done)&&noGrant(crash)&&
                noGrant(plain)&&noGrant(full)&&noGrant(other);
            zeroJvmLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21118_DURABLE_CROSS_JVM_DIAGNOSTICS"+
                " legacyForeignNoopUnenrolled="+plainUnrecorded+
                " successfulForeignNoopPairedOnDisk="+
                    foreignSuccessDurable+
                " noCanonicalMutation="+accountBytesUnchanged+
                " crashedForeignBeginDurable="+foreignCrashDurable+
                " independentThirdJvmRetryVeto="+thirdJvmRetryVeto+
                " defaultAfterCrashStillUnfenced="+
                    postcrashDefaultsUnfenced+
                " shared32LeafCapVeto="+fullCapDenied+
                " unrelatedAccountUntouched="+otherAccountIsolated+
                " noLiveGrant="+stillNoLiveGrant+
                " zeroJvmLeases="+zeroJvmLeases);
            check(plainUnrecorded&&foreignSuccessDurable&&
                accountBytesUnchanged&&foreignCrashDurable&&
                thirdJvmRetryVeto&&postcrashDefaultsUnfenced&&
                fullCapDenied&&otherAccountIsolated&&
                stillNoLiveGrant&&zeroJvmLeases,
                "durable cross-JVM opt-in diagnostic acceptance");
            System.out.println("G21118_DURABLE_CROSS_JVM_OPTIN_PASS"+
                " realIndependentJvms=true beginForced=true"+
                " completeForced=true v1V2FormatUnchanged=true"+
                " diagnosticKindNotBusinessWriter=true"+
                " defaultsRemainUnenrolled=true"+
                " legacyHistoryNotFenced=true rawWritersNotFenced=true"+
                " noEpoch=true rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path f:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(f);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2],args[3]);
            return;
        }
        check(args.length==0,"unexpected args");
        run();
    }
}
