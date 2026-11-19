package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R89 offline reference contract:
 * REAL R86 active handler -> exact source-proven kill/death facts ->
 * TEST-ONLY bound issuer -> independent single-file consumer value+receipts.
 *
 * A real SpawnPK combat ID / production projection transaction does
 * not exist. All fresh-JVM recovery inspections are read-only quarantine.
 */
public final class R89OfflineAtomicConsumerReceiptContractTest {
    private enum Scenario {
        COMPLETE,
        PARTIAL_AFTER_OBJECTIVE,
        MATCH_BEFORE_PUBLISH,
        MATCH_AFTER_PUBLISH_BEFORE_ACK,
        CAPACITY_DENIAL
    }

    private static final class Case implements AutoCloseable {
        final Path root;
        final R86OfflineActivePvpDomainBridgeTest.Fixture fixture;
        final R89OfflineVerifiedEmissionTokenIssuer issuer;
        final R89OfflineAtomicConsumerReceiptStore objective;
        final R89OfflineAtomicConsumerReceiptStore match;
        final R89OfflineAtomicConsumerReceiptStore collection;
        final Scenario scenario;
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        R89OfflineVerifiedEmissionTokenIssuer.Token killToken;
        R89OfflineVerifiedEmissionTokenIssuer.Token deathToken;
        R89OfflineAtomicConsumerReceiptStore.Result objectiveResult;
        R89OfflineAtomicConsumerReceiptStore.Result matchResult;
        R89OfflineAtomicConsumerReceiptStore.Result collectionResult;
        R89OfflineAtomicConsumerReceiptStore.Result duplicateResult;
        R89OfflineAtomicConsumerReceiptStore.Result deathCapacityResult;
        int callbackCount;

        Case(Scenario scenario)throws Exception{
            this.scenario=scenario;
            root=Files.createTempDirectory(
                "r89-atomic-consumers-"+
                scenario.name().toLowerCase()+"-"
            );
            fixture=new R86OfflineActivePvpDomainBridgeTest.Fixture(
                R86OfflineActivePvpDomainBridgeTest.Mode.NORMAL
            );
            issuer=new R89OfflineVerifiedEmissionTokenIssuer(
                fixture.bridge,8
            );
            int bound=scenario==Scenario.CAPACITY_DENIAL?1:8;
            objective=new R89OfflineAtomicConsumerReceiptStore(
                root,"objective",issuer,bound
            );
            match=new R89OfflineAtomicConsumerReceiptStore(
                root,"match",issuer,8
            );
            collection=new R89OfflineAtomicConsumerReceiptStore(
                root,"collection",issuer,8
            );

            fixture.world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.LOW,
                fact->{
                    callbackCount++;
                    try{
                        verifyAndApply(fact);
                    }catch(Throwable failure){
                        this.failure.set(failure);
                        throw new IllegalStateException(
                            "R89 source-exact fixture callback failed",
                            failure
                        );
                    }
                }
            );
        }

        private void verifyAndApply(CombatOutcome fact){
            // Only the actual in-scope R86 callback may mint a
            // synthetic token; the same real object never gets a
            // second token even if a listener asks twice.
            R89OfflineVerifiedEmissionTokenIssuer.Token token=
                issuer.issue(fact);
            require(issuer.issue(fact)==token,
                "R89 minted two IDs for one exact real fact");
            if(fact.type()==CombatOutcomeType.PLAYER_DEATH){
                deathToken=token;
                if(scenario==Scenario.CAPACITY_DENIAL)
                    deathCapacityResult=objective.apply(
                        fact,token,2L,
                        R89OfflineAtomicConsumerReceiptStore.Fault.NONE
                    );
                return;
            }
            require(fact.type()==CombatOutcomeType.PLAYER_KILL,
                "R89 non-PvP kill fact entered test ledger");
            killToken=token;
            objectiveResult=objective.apply(
                fact,token,1L,
                R89OfflineAtomicConsumerReceiptStore.Fault.NONE
            );
            duplicateResult=objective.apply(
                fact,token,1L,
                R89OfflineAtomicConsumerReceiptStore.Fault.NONE
            );
            if(scenario==Scenario.PARTIAL_AFTER_OBJECTIVE)return;

            R89OfflineAtomicConsumerReceiptStore.Fault matchFault=
                scenario==Scenario.MATCH_BEFORE_PUBLISH
                    ?R89OfflineAtomicConsumerReceiptStore.Fault
                        .BEFORE_ATOMIC_PUBLISH
                    :scenario==Scenario.MATCH_AFTER_PUBLISH_BEFORE_ACK
                    ?R89OfflineAtomicConsumerReceiptStore.Fault
                        .AFTER_ATOMIC_PUBLISH_BEFORE_ACK
                    :R89OfflineAtomicConsumerReceiptStore.Fault.NONE;
            matchResult=match.apply(fact,token,2L,matchFault);
            if(scenario==Scenario.MATCH_BEFORE_PUBLISH)return;
            if(scenario==Scenario.MATCH_AFTER_PUBLISH_BEFORE_ACK){
                // A repeat during the SAME verified callback sees
                // the atomically recorded receipt and does not
                // apply another +2, despite the lost acknowledgment.
                require(match.apply(
                    fact,token,2L,
                    R89OfflineAtomicConsumerReceiptStore.Fault.NONE
                )==R89OfflineAtomicConsumerReceiptStore.Result.DUPLICATE,
                    "R89 post-publish ambiguous result double-counted");
                return;
            }
            collectionResult=collection.apply(
                fact,token,7L,
                R89OfflineAtomicConsumerReceiptStore.Fault.NONE
            );
        }

        void lethal()throws Exception{
            fixture.lethal();
            if(failure.get()!=null)
                throw new AssertionError(
                    "R89 callback failure",failure.get()
                );
            require(callbackCount==2&&killToken!=null&&
                    deathToken!=null&&killToken!=deathToken&&
                    !killToken.syntheticIdForAudit().equals(
                        deathToken.syntheticIdForAudit())&&
                    issuer.issuedCountForTest()==2,
                "R89 real kill/death distinct source identity absent");
            require(fixture.progress()==1L&&
                    fixture.globalEvents.get()==1&&
                    fixture.bridge.admittedCountForTest()==2,
                "R89 real active PvP source diverged from R86");
            require(objectiveResult==
                    R89OfflineAtomicConsumerReceiptStore.Result.APPLIED&&
                    duplicateResult==
                    R89OfflineAtomicConsumerReceiptStore.Result.DUPLICATE,
                "R89 did not atomically dedupe genuine source");
        }

        void staleAndCopiedSourceDenied(){
            CombatOutcome fact=fixture.killFact.get();
            require(fact!=null,"R89 no original source fact");
            CombatOutcome copied=new CombatOutcome(
                fact.attacker(),fact.victim(),fact.type(),
                fact.context(),fact.worldTick(),
                fact.sourceAuthority()
            );
            try{
                issuer.issue(copied);
                throw new AssertionError(
                    "R89 forged same-fields source minted a token"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "missing exact active PvP proof"),
                    "R89 incorrect copy denial");
            }
            try{
                issuer.issue(fact);
                throw new AssertionError(
                    "R89 closed source scope minted another token"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "missing exact active PvP proof"),
                    "R89 source proof escaped its live callback");
            }
            require(objective.apply(
                    fact,killToken,1L,
                    R89OfflineAtomicConsumerReceiptStore.Fault.NONE
                )==R89OfflineAtomicConsumerReceiptStore.Result
                    .REFUSED_UNVERIFIED,
                "R89 old event replay accepted outside source scope");
            require(objective.apply(
                    copied,killToken,1L,
                    R89OfflineAtomicConsumerReceiptStore.Fault.NONE
                )==R89OfflineAtomicConsumerReceiptStore.Result
                    .REFUSED_UNVERIFIED,
                "R89 forged copy reused protected token");
        }

        @Override public void close(){fixture.close();}
    }

    public static void main(String[] args)throws Exception{
        if(args.length>0){
            childReview(args);
            return;
        }
        completeThreeIndependentConsumerFiles();
        interruptedFanoutIsNotGlobalTransaction();
        beforeAtomicPublishLeavesNoReceipt();
        afterPublishBeforeAckHasBothValueAndReceipt();
        duplicateTokenMintingAndCapacityFailClosed();
        damagedAndAmbiguousFilesRemainQuarantined();
        System.out.println(
            "R89_OFFLINE_ATOMIC_CONSUMER_RECEIPT_CONTRACT_PASS "+
            "realActiveR86PvPProducerUsed=true "+
            "killDeathExactFactsHaveDistinctSyntheticTokens=true "+
            "sameRealFactCannotMintSecondToken=true "+
            "sameFieldCloneCannotMintTrustedToken=true "+
            "oneFilePerConsumerContainsValueAndReceipt=true "+
            "duplicateSourceCannotDoubleApply=true "+
            "postPublishLostAckDuplicatesRefused=true "+
            "prePublishFaultLeavesNoReceipt=true "+
            "partialThreeConsumerFanoutObserved=true "+
            "capacityFailsClosedWithoutEviction=true "+
            "corruptOrAmbiguousStageQuarantined=true "+
            "separateJVMReadOnlyRecoveryReview=true "+
            "noReplayLoginRewardOrRollbackAuthorization=true "+
            "sourceTokensAreNotRestartStable=true "+
            "noCrossConsumerAtomicityOrPowerLossClaim=true "+
            "noLiveProductionIntegration=true"
        );
    }

    private static void completeThreeIndependentConsumerFiles()
            throws Exception{
        try(Case c=new Case(Scenario.COMPLETE)){
            c.lethal();
            require(c.matchResult==
                    R89OfflineAtomicConsumerReceiptStore.Result.APPLIED&&
                    c.collectionResult==
                    R89OfflineAtomicConsumerReceiptStore.Result.APPLIED,
                "R89 normal source did not write three receipts");
            check(c.root,"objective",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .PRESENT_QUARANTINED,1L,1);
            check(c.root,"match",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .PRESENT_QUARANTINED,2L,1);
            check(c.root,"collection",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .PRESENT_QUARANTINED,7L,1);
            c.staleAndCopiedSourceDenied();
            childCheck(c.root,"1,2,7");
        }
    }

    private static void interruptedFanoutIsNotGlobalTransaction()
            throws Exception{
        try(Case c=new Case(Scenario.PARTIAL_AFTER_OBJECTIVE)){
            c.lethal();
            check(c.root,"objective",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .PRESENT_QUARANTINED,1L,1);
            check(c.root,"match",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .MISSING_BLOCKED,0L,0);
            check(c.root,"collection",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .MISSING_BLOCKED,0L,0);
            c.staleAndCopiedSourceDenied();
            childCheck(c.root,"1,missing,missing");
        }
    }

    private static void beforeAtomicPublishLeavesNoReceipt()
            throws Exception{
        try(Case c=new Case(Scenario.MATCH_BEFORE_PUBLISH)){
            c.lethal();
            require(c.matchResult==
                    R89OfflineAtomicConsumerReceiptStore.Result
                        .INDETERMINATE,
                "R89 injected pre-publish fault did not fail closed");
            check(c.root,"match",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .MISSING_BLOCKED,0L,0);
            childCheck(c.root,"1,missing,missing");
        }
    }

    private static void afterPublishBeforeAckHasBothValueAndReceipt()
            throws Exception{
        try(Case c=new Case(
                Scenario.MATCH_AFTER_PUBLISH_BEFORE_ACK)){
            c.lethal();
            require(c.matchResult==
                    R89OfflineAtomicConsumerReceiptStore.Result
                        .INDETERMINATE,
                "R89 injected post-publish ack loss not recorded");
            check(c.root,"match",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .PRESENT_QUARANTINED,2L,1);
            childCheck(c.root,"1,2,missing");
        }
    }

    private static void duplicateTokenMintingAndCapacityFailClosed()
            throws Exception{
        try(Case c=new Case(Scenario.CAPACITY_DENIAL)){
            c.lethal();
            require(c.deathCapacityResult==
                    R89OfflineAtomicConsumerReceiptStore.Result
                        .REFUSED_CAPACITY,
                "R89 full ledger evicted earlier op receipt");
            check(c.root,"objective",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .PRESENT_QUARANTINED,1L,1);
            childCheck(c.root,"1,2,7");
        }
    }

    private static void damagedAndAmbiguousFilesRemainQuarantined()
            throws Exception{
        try(Case c=new Case(Scenario.COMPLETE)){
            c.lethal();
            Files.write(c.objective.canonicalForTest(),
                "tampered".getBytes(StandardCharsets.UTF_8));
            check(c.root,"objective",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .INDETERMINATE_BLOCKED,0L,0);
            Path target=c.collection.canonicalForTest();
            Path stage=c.root.resolve(
                "."+target.getFileName()+".r16-interrupted.stage"
            );
            Files.write(stage,
                "abandoned-test-stage".getBytes(StandardCharsets.UTF_8));
            check(c.root,"collection",
                R89OfflineAtomicConsumerReceiptStore.Status
                    .INDETERMINATE_BLOCKED,0L,0);
            require(Files.isRegularFile(stage),
                "R89 reviewer destroyed ambiguous writer evidence");
            childCheck(c.root,"bad,2,bad");
        }
    }

    private static void check(
        Path root,String name,
        R89OfflineAtomicConsumerReceiptStore.Status status,
        long value,int count
    ){
        R89OfflineAtomicConsumerReceiptStore.Review review=
            R89OfflineAtomicConsumerReceiptStore.review(root,name);
        require(review.status==status&&
                review.observedValue==value&&
                review.observedReceipts==count&&
                !review.replayAuthorized()&&
                !review.crossConsumerCommitted()&&
                !review.rewardAuthorized()&&
                !review.loginAuthorized(),
            "R89 invalid review consumer="+name+" status="+
                review.status+" value="+review.observedValue+
                " receiptCount="+review.observedReceipts);
    }

    private static void childCheck(Path root,String expected)
            throws Exception{
        Path java=Paths.get(
            System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase()
                .contains("win")?"java.exe":"java"
        );
        Process child=new ProcessBuilder(
            java.toString(),"-cp",
            System.getProperty("java.class.path"),
            R89OfflineAtomicConsumerReceiptContractTest.class.getName(),
            "--child-review",root.toString(),expected
        ).redirectErrorStream(true).start();
        if(!child.waitFor(30L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            throw new AssertionError(
                "R89 independent JVM review timeout"
            );
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&
                output.contains(
                    "R89_SEPARATE_JVM_QUARANTINED_REVIEW_PASS"
                ),
            "R89 child review failed "+output);
    }

    private static void childReview(String[] args){
        require(args.length==3&&
                "--child-review".equals(args[0]),
            "R89 invalid child review arguments");
        Path root=Paths.get(args[1]);
        String[] expected=args[2].split(",",-1);
        require(expected.length==3,"R89 invalid expected state count");
        String[] consumers={"objective","match","collection"};
        for(int i=0;i<3;i++){
            R89OfflineAtomicConsumerReceiptStore.Review result=
                R89OfflineAtomicConsumerReceiptStore.review(
                    root,consumers[i]
                );
            String want=expected[i];
            if("bad".equals(want)){
                require(result.status==
                    R89OfflineAtomicConsumerReceiptStore.Status
                        .INDETERMINATE_BLOCKED,
                    "R89 child trusted a corrupted/ambiguous receipt");
            }else if("missing".equals(want)){
                require(result.status==
                    R89OfflineAtomicConsumerReceiptStore.Status
                        .MISSING_BLOCKED,
                    "R89 child fabricated missing consumer receipt");
            }else{
                require(result.status==
                    R89OfflineAtomicConsumerReceiptStore.Status
                        .PRESENT_QUARANTINED&&
                    result.observedValue==Long.parseLong(want)&&
                    result.observedReceipts==1,
                    "R89 child consumer value/receipt not atomic");
            }
            require(!result.replayAuthorized()&&
                    !result.crossConsumerCommitted()&&
                    !result.rewardAuthorized()&&
                    !result.loginAuthorized(),
                "R89 child authorized untrusted replay/reward");
        }
        System.out.println(
            "R89_SEPARATE_JVM_QUARANTINED_REVIEW_PASS "+
            "threeIndependentConsumerFiles=true "+
            "automaticReplay=false crossConsumerCommit=false"
        );
    }

    private static void require(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }

    private R89OfflineAtomicConsumerReceiptContractTest(){}
}
