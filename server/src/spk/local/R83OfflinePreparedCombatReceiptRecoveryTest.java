package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import spk.local.R82OfflineSourceScopedCombatReceiptTest.Fixture;
import spk.local.R82OfflineSourceScopedCombatReceiptTest.Mode;

/**
 * R83 OFFLINE ONLY. Actual R79/R82 PvP source -> synchronous verified
 * source ordinal -> forced PREPARED receipt witness BEFORE the first
 * Objective/Match/Collection Log write. Review in another JVM never
 * permits replay or claims transaction completion.
 *
 * Test operation UUIDs are purpose-built synthetic fixtures, NOT
 * identifiers recovered from real SpawnPK combat or a client packet.
 */
public final class R83OfflinePreparedCombatReceiptRecoveryTest {
    public static void main(String[] args)throws Exception{
        if(args.length>0){
            childReview(args);
            return;
        }

        normalPreparedThenCrossJvmQuarantine();
        afterPrepareBeforeMutationRemainsQuarantined();
        beforePrepareNeverAuthorizesReplay();
        partialFanoutDoesNotBecomeCompleted();
        collectionFailureDoesNotBecomeCompleted();
        corruptAndAmbiguousStageFailClosed();
        installationRefusesReplacementAndLateInstall();

        System.out.println(
            "R83_OFFLINE_PREPARED_COMBAT_RECEIPT_PASS "+
            "realPvpSourceAndProjectionStores=true "+
            "preparedForcedBeforeFirstProjection=true "+
            "twoDistinctSourceOrdinalsStored=true "+
            "readOnlySeparateJVMRecovery=true "+
            "restartPreparedNeverResumed=true "+
            "afterPreparedBeforeMutationQuarantined=true "+
            "missingWitnessDoesNotPermitReplay=true "+
            "partialObjectiveNotClaimedComplete=true "+
            "failedCollectionResolverNotRetried=true "+
            "tamperedPreparedEvidenceRejected=true "+
            "ambiguousStageNotPromotedOrDeleted=true "+
            "duplicatePreparedWriteRefused=true "+
            "duplicateAndLateGateInstallRejected=true "+
            "noCrossServiceAtomicityOrPowerLossClaim=true "+
            "noRealCombatOperationIdentifier=true "+
            "noLiveAccountNetworkOrRewardMutation=true"
        );
    }

    private static void normalPreparedThenCrossJvmQuarantine()
            throws Exception{
        Path root=Files.createTempDirectory("r83-prepared-");
        UUID op=UUID.randomUUID();

        try(Fixture f=new Fixture(Mode.COMPLETE,8)){
            R83OfflinePreparedCombatReceiptJournal journal=
                install(f,root,op,
                    R83OfflinePreparedCombatReceiptJournal.Fault.NONE);
            f.lethal();
            f.assertProjection(
                1L,2L,3L,Long.valueOf(7L),1,0
            );
            require(journal.preparedCountForTest()==2&&
                    journal.refusedCountForTest()==0&&
                    f.receipts.receiptCount()==2&&
                    f.receipts.preparedDenialCount()==0,
                "R83 first real verified two-fact source failed");

            assertReview(root,op,1L,
                R83OfflinePreparedCombatReceiptJournal.Status
                    .PREPARED_QUARANTINED);
            assertReview(root,op,2L,
                R83OfflinePreparedCombatReceiptJournal.Status
                    .PREPARED_QUARANTINED);
            assertChildReview(root,op,1L,
                "PREPARED_QUARANTINED");
        }

        // A newly constructed isolated World gets a fresh R82
        // non-durable scope. Reusing a *synthetic* test operation
        // UUID across restarts is intentionally denied; it cannot
        // prove it is the same gameplay event.
        try(Fixture restarted=new Fixture(Mode.COMPLETE,8)){
            R83OfflinePreparedCombatReceiptJournal duplicate=
                install(restarted,root,op,
                    R83OfflinePreparedCombatReceiptJournal.Fault.NONE);
            restarted.lethal();
            restarted.assertProjection(0L,0L,0L,null,0,0);
            require(duplicate.preparedCountForTest()==0&&
                    duplicate.refusedCountForTest()==4&&
                    restarted.receipts.receiptCount()==0&&
                    restarted.receipts.preparedDenialCount()==4,
                "R83 new World overwrote original prepared receipt");
            require(restarted.globalDelivery.get()==1,
                "R83 global notice blocked by pending combat evidence");
        }

        // A distinct TEST operation UUID is not conflated with the
        // old one. This is NOT proof that real distinct combat
        // events will have such a trustworthy operation ID.
        try(Fixture separate=new Fixture(Mode.COMPLETE,8)){
            UUID second=UUID.randomUUID();
            R83OfflinePreparedCombatReceiptJournal fresh=
                install(separate,root,second,
                    R83OfflinePreparedCombatReceiptJournal.Fault.NONE);
            separate.lethal();
            separate.assertProjection(
                1L,2L,3L,Long.valueOf(7L),1,0
            );
            require(fresh.preparedCountForTest()==2,
                "R83 unrelated fixture transaction was falsely denied");
        }
    }

    private static void afterPrepareBeforeMutationRemainsQuarantined()
            throws Exception{
        Path root=Files.createTempDirectory("r83-inject-after-");
        UUID op=UUID.randomUUID();
        try(Fixture f=new Fixture(Mode.COMPLETE,8)){
            R83OfflinePreparedCombatReceiptJournal journal=
                install(f,root,op,
                    R83OfflinePreparedCombatReceiptJournal.Fault
                        .DENY_AFTER_PREPARE_BEFORE_PROJECTION);
            f.lethal();
            f.assertProjection(0L,0L,0L,null,0,0);
            require(journal.preparedCountForTest()==2&&
                    journal.refusedCountForTest()==4&&
                    f.receipts.receiptCount()==0,
                "R83 injected after-prepare failure mutated projection");
            assertReview(root,op,1L,
                R83OfflinePreparedCombatReceiptJournal.Status
                    .PREPARED_QUARANTINED);
            assertChildReview(root,op,1L,"PREPARED_QUARANTINED");
        }
    }

    private static void beforePrepareNeverAuthorizesReplay()
            throws Exception{
        Path root=Files.createTempDirectory("r83-inject-before-");
        UUID op=UUID.randomUUID();
        try(Fixture f=new Fixture(Mode.COMPLETE,8)){
            R83OfflinePreparedCombatReceiptJournal journal=
                install(f,root,op,
                    R83OfflinePreparedCombatReceiptJournal.Fault
                        .DENY_BEFORE_PREPARE);
            f.lethal();
            f.assertProjection(0L,0L,0L,null,0,0);
            require(journal.preparedCountForTest()==0&&
                    f.receipts.receiptCount()==0,
                "R83 prewrite fault let an observer execute");
            assertReview(root,op,1L,
                R83OfflinePreparedCombatReceiptJournal.Status
                    .MISSING_BLOCKED);
            assertChildReview(root,op,1L,"MISSING_BLOCKED");
        }
    }

    private static void partialFanoutDoesNotBecomeCompleted()
            throws Exception{
        Path root=Files.createTempDirectory("r83-partial-");
        UUID op=UUID.randomUUID();
        try(Fixture f=new Fixture(
                Mode.PARTIAL_AFTER_OBJECTIVE,8)){
            install(f,root,op,
                R83OfflinePreparedCombatReceiptJournal.Fault.NONE);
            f.lethal();
            f.assertProjection(1L,0L,0L,null,0,0);
            assertChildReview(root,op,1L,"PREPARED_QUARANTINED");
        }
    }

    private static void collectionFailureDoesNotBecomeCompleted()
            throws Exception{
        Path root=Files.createTempDirectory("r83-collection-");
        UUID op=UUID.randomUUID();
        try(Fixture f=new Fixture(
                Mode.FAILED_COLLECTION_RESOLVER,8)){
            install(f,root,op,
                R83OfflinePreparedCombatReceiptJournal.Fault.NONE);
            f.lethal();
            f.assertProjection(1L,2L,3L,null,1,1);
            assertChildReview(root,op,1L,"PREPARED_QUARANTINED");
        }
    }

    private static void corruptAndAmbiguousStageFailClosed()
            throws Exception{
        Path root=Files.createTempDirectory("r83-corrupt-");
        UUID op=UUID.randomUUID();
        try(Fixture f=new Fixture(Mode.COMPLETE,8)){
            install(f,root,op,
                R83OfflinePreparedCombatReceiptJournal.Fault.NONE);
            f.lethal();
        }
        Path first=
            R83OfflinePreparedCombatReceiptJournal.receiptPathForTest(
                root,op,1L
            );
        Files.write(first,"tampered witness".getBytes(
            StandardCharsets.UTF_8
        ));
        assertReview(root,op,1L,
            R83OfflinePreparedCombatReceiptJournal.Status
                .INDETERMINATE_BLOCKED);
        assertChildReview(root,op,1L,"INDETERMINATE_BLOCKED");

        Path second=
            R83OfflinePreparedCombatReceiptJournal.receiptPathForTest(
                root,op,2L
            );
        Path stage=root.resolve(
            "."+second.getFileName()+".r16-injected.stage"
        );
        Files.write(stage,"incomplete writer".getBytes(
            StandardCharsets.UTF_8
        ));
        assertReview(root,op,2L,
            R83OfflinePreparedCombatReceiptJournal.Status
                .INDETERMINATE_BLOCKED);
        require(Files.exists(stage),
            "R83 reviewer deleted an ambiguous staging file");
        assertChildReview(root,op,2L,"INDETERMINATE_BLOCKED");
    }

    private static void installationRefusesReplacementAndLateInstall()
            throws Exception{
        Path root=Files.createTempDirectory("r83-gate-");
        UUID op=UUID.randomUUID();
        try(Fixture f=new Fixture(Mode.COMPLETE,8)){
            R83OfflinePreparedCombatReceiptJournal journal=
                install(f,root,op,
                    R83OfflinePreparedCombatReceiptJournal.Fault.NONE);
            try{
                f.receipts.installOfflinePreparedAdmissionForTest(
                    journal
                );
                throw new AssertionError(
                    "R83 replaced an existing writer policy"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "R83_PREPARE_GATE_DENIED"
                ),"R83 incorrect duplicate-policy denial");
            }
            f.lethal();
            try{
                f.receipts.installOfflinePreparedAdmissionForTest(
                    journal
                );
                throw new AssertionError(
                    "R83 allowed a late receipt gate"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "R83_PREPARE_GATE_DENIED"
                ),"R83 incorrect late-policy denial");
            }
        }
    }

    private static R83OfflinePreparedCombatReceiptJournal install(
        Fixture f,Path root,UUID operation,
        R83OfflinePreparedCombatReceiptJournal.Fault fault
    ){
        R83OfflinePreparedCombatReceiptJournal journal=
            new R83OfflinePreparedCombatReceiptJournal(
                root,operation,fault
            );
        f.receipts.installOfflinePreparedAdmissionForTest(
            journal
        );
        return journal;
    }

    private static void assertReview(
        Path root,UUID operation,long ordinal,
        R83OfflinePreparedCombatReceiptJournal.Status expected
    ){
        R83OfflinePreparedCombatReceiptJournal.Review result=
            R83OfflinePreparedCombatReceiptJournal.review(
                root,operation,ordinal
            );
        require(result.status==expected&&
                !result.replayAllowed()&&
                !result.projectionComplete()&&
                !result.loginAllowed()&&
                !result.rewardAllowed(),
            "R83 reviewer incorrectly authorized an event "+result);
    }

    private static void assertChildReview(
        Path root,UUID operation,long ordinal,String expected
    )throws Exception{
        Path java=Paths.get(
            System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase()
                .contains("win")?"java.exe":"java"
        );
        Process child=new ProcessBuilder(
            java.toString(),"-cp",
            System.getProperty("java.class.path"),
            R83OfflinePreparedCombatReceiptRecoveryTest.class.getName(),
            "--child-review",root.toString(),
            operation.toString(),Long.toString(ordinal),expected
        ).redirectErrorStream(true).start();
        if(!child.waitFor(30L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            throw new AssertionError(
                "R83 independent JVM review timed out"
            );
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&
                output.contains(
                    "R83_SEPARATE_JVM_READ_ONLY_REVIEW_PASS"
                ),
            "R83 cross-JVM review failed exit="+child.exitValue()+
                " output="+output
        );
    }

    private static void childReview(String[] args){
        require(args.length==5&&
                "--child-review".equals(args[0]),
            "R83 invalid read-only child invocation");
        Path root=Paths.get(args[1]);
        UUID operation=UUID.fromString(args[2]);
        long ordinal=Long.parseLong(args[3]);
        R83OfflinePreparedCombatReceiptJournal.Status expected=
            R83OfflinePreparedCombatReceiptJournal.Status.valueOf(
                args[4]
            );
        assertReview(root,operation,ordinal,expected);
        System.out.println(
            "R83_SEPARATE_JVM_READ_ONLY_REVIEW_PASS "+
            "status="+expected+" replayAuthorized=false "+
            "projectionComplete=false rewardAuthorized=false"
        );
    }

    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }

    private R83OfflinePreparedCombatReceiptRecoveryTest(){}
}
