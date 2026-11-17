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
 * R84 OFFLINE ONLY: after-effect read-only snapshots of actual objective,
 * match and Collection Log services, with real R79 verified PvP source,
 * R82 shared receipts and R83 PREPARED witness. No production persistence,
 * game login, rewards or automatic recovery.
 */
public final class R84OfflineProjectionObservationRecoveryTest {
    public static void main(String[] args)throws Exception{
        if(args.length>0){
            childReview(args);
            return;
        }
        normalObservationsCannotCertifyCommit();
        partialObserverFanoutHasOnlyPartialEvidence();
        failingCollectionObserverIsRecordedButNotCompleted();
        missingEvidenceAfterRealMutationCannotBeReplayed();
        missingPreparedWitnessBlocksObservation();
        alteredAndStagedEvidenceIsIndeterminate();
        staleSourceAndDuplicatePolicyAreDenied();

        System.out.println(
            "R84_OFFLINE_PROJECTION_OBSERVATION_PASS "+
            "realPvpThreeProjectionServiceSnapshots=true "+
            "sixSourceScopedPostObserverEvidenceRecords=true "+
            "differentSnapshotsBetweenObserverIndexes=true "+
            "independentJvmReadOnlyReview=true "+
            "allEvidenceStillNotCommitCertificate=true "+
            "partialFanoutEvidenceRemainsIncomplete=true "+
            "collectionResolverFailureCapturedAsFailure=true "+
            "missingPostMutationEvidenceCannotAuthorizeReplay=true "+
            "missingPreparedEvidenceBlocksObservation=true "+
            "tamperedAndAmbiguousEvidenceRefused=true "+
            "sourceProofRequiredForObservation=true "+
            "duplicateOfflineObserverPolicyDenied=true "+
            "noCrossServiceDurabilityOrAtomicityClaim=true "+
            "noRealCombatIdLoginRewardOrLivePath=true"
        );
    }

    private static final class Case implements AutoCloseable{
        final Path prepared;
        final Path observed;
        final UUID operation=UUID.randomUUID();
        final Fixture f;
        final R83OfflinePreparedCombatReceiptJournal preparation;
        final R84OfflineProjectionObservationJournal audit;

        Case(Mode mode,
            R84OfflineProjectionObservationJournal.Fault fault,
            boolean correctPreparedRoot)throws Exception{
            prepared=Files.createTempDirectory("r84-prepared-");
            observed=Files.createTempDirectory("r84-observed-");
            f=new Fixture(mode,8);
            preparation=new R83OfflinePreparedCombatReceiptJournal(
                prepared,operation,
                R83OfflinePreparedCombatReceiptJournal.Fault.NONE
            );
            f.receipts.installOfflinePreparedAdmissionForTest(preparation);
            audit=new R84OfflineProjectionObservationJournal(
                observed,
                correctPreparedRoot?prepared:
                    Files.createTempDirectory("r84-missing-prepared-"),
                operation,
                f.receipts,f.objectives,"combat:r82-kills",
                f.matches,f.matchId,f.attacker.id().toString(),
                f.attackerTeam,f.collection,f.collectionId,fault
            );
            f.group1.installOfflinePostObserverObservationForTest(audit);
        }

        void lethal()throws Exception{
            f.lethal();
        }

        R84OfflineProjectionObservationJournal.Review review(
            long ordinal,int observerIndex
        ){
            return R84OfflineProjectionObservationJournal.review(
                observed,prepared,operation,ordinal,observerIndex
            );
        }

        void checkReview(
            long ordinal,int observerIndex,
            R84OfflineProjectionObservationJournal.Status expected,
            Long progress,Long participant,Long team,Long collection,
            Boolean returned
        ){
            R84OfflineProjectionObservationJournal.Review review=
                review(ordinal,observerIndex);
            require(review.status==expected,
                "R84 expected "+expected+" got "+review.status+
                " reason="+review.reason);
            require(!review.replayAllowed()&&
                    !review.projectionCommitted()&&
                    !review.recoveryAuthorized()&&
                    !review.loginAllowed()&&
                    !review.rewardAllowed(),
                "R84 observational witness illegally authorized recovery");
            if(expected==R84OfflineProjectionObservationJournal
                    .Status.OBSERVED_QUARANTINED){
                require(java.util.Objects.equals(
                        review.objectiveProgress,progress)&&
                        java.util.Objects.equals(
                            review.participantScore,participant)&&
                        java.util.Objects.equals(
                            review.teamScore,team)&&
                        java.util.Objects.equals(
                            review.collectionKillCount,collection)&&
                        review.observerReturned==returned.booleanValue(),
                    "R84 expected exact post-observer projection state");
            }
        }

        void childCheck(long ordinal,int index,String expected)
                throws Exception{
            Path java=Paths.get(
                System.getProperty("java.home"),"bin",
                System.getProperty("os.name").toLowerCase()
                    .contains("win")?"java.exe":"java"
            );
            Process child=new ProcessBuilder(
                java.toString(),"-cp",
                System.getProperty("java.class.path"),
                R84OfflineProjectionObservationRecoveryTest
                    .class.getName(),
                "--child-review",observed.toString(),
                prepared.toString(),operation.toString(),
                Long.toString(ordinal),Integer.toString(index),expected
            ).redirectErrorStream(true).start();
            if(!child.waitFor(30L,TimeUnit.SECONDS)){
                child.destroyForcibly();
                throw new AssertionError(
                    "R84 separate-JVM review timed out"
                );
            }
            String output=new String(
                child.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
            );
            require(child.exitValue()==0&&
                    output.contains(
                        "R84_SEPARATE_JVM_READ_ONLY_REVIEW_PASS"
                    ),"R84 separate JVM review failed "+output);
        }

        @Override public void close(){
            f.close();
        }
    }

    private static void normalObservationsCannotCertifyCommit()
            throws Exception{
        try(Case c=new Case(Mode.COMPLETE,
                R84OfflineProjectionObservationJournal.Fault.NONE,true)){
            c.lethal();
            c.f.assertProjection(1L,2L,3L,Long.valueOf(7L),1,0);
            require(c.preparation.preparedCountForTest()==2&&
                    c.audit.observedCountForTest()==6&&
                    c.audit.refusedCountForTest()==0,
                "R84 baseline observation count mismatch");
            // These are distinct real projections captured AFTER each
            // separate observer. The intermediate states are visible,
            // but they are NOT a durable three-service transaction.
            c.checkReview(1L,0,
                R84OfflineProjectionObservationJournal.Status
                    .OBSERVED_QUARANTINED,
                1L,0L,0L,null,true);
            c.checkReview(1L,1,
                R84OfflineProjectionObservationJournal.Status
                    .OBSERVED_QUARANTINED,
                1L,2L,3L,null,true);
            c.checkReview(1L,2,
                R84OfflineProjectionObservationJournal.Status
                    .OBSERVED_QUARANTINED,
                1L,2L,3L,7L,true);
            c.checkReview(2L,2,
                R84OfflineProjectionObservationJournal.Status
                    .OBSERVED_QUARANTINED,
                1L,2L,3L,7L,true);
            c.childCheck(1L,2,"OBSERVED_QUARANTINED");
        }
    }

    private static void partialObserverFanoutHasOnlyPartialEvidence()
            throws Exception{
        try(Case c=new Case(Mode.PARTIAL_AFTER_OBJECTIVE,
                R84OfflineProjectionObservationJournal.Fault.NONE,true)){
            c.lethal();
            c.f.assertProjection(1L,0L,0L,null,0,0);
            require(c.audit.observedCountForTest()==4,
                "R84 partial first-group fanout was replayed");
            c.checkReview(1L,0,
                R84OfflineProjectionObservationJournal.Status
                    .OBSERVED_QUARANTINED,
                1L,0L,0L,null,true);
            c.checkReview(1L,1,
                R84OfflineProjectionObservationJournal.Status
                    .MISSING_BLOCKED,
                null,null,null,null,null);
            c.childCheck(1L,1,"MISSING_BLOCKED");
        }
    }

    private static void failingCollectionObserverIsRecordedButNotCompleted()
            throws Exception{
        try(Case c=new Case(Mode.FAILED_COLLECTION_RESOLVER,
                R84OfflineProjectionObservationJournal.Fault.NONE,true)){
            c.lethal();
            c.f.assertProjection(1L,2L,3L,null,1,1);
            require(c.audit.observedCountForTest()==6,
                "R84 failing observer was incorrectly omitted");
            c.checkReview(1L,2,
                R84OfflineProjectionObservationJournal.Status
                    .OBSERVED_QUARANTINED,
                1L,2L,3L,null,false);
            c.childCheck(1L,2,"OBSERVED_QUARANTINED");
        }
    }

    private static void missingEvidenceAfterRealMutationCannotBeReplayed()
            throws Exception{
        try(Case c=new Case(Mode.COMPLETE,
                R84OfflineProjectionObservationJournal.Fault
                    .DENY_FIRST_EVIDENCE_BEFORE_WRITE,true)){
            c.lethal();
            c.f.assertProjection(1L,2L,3L,Long.valueOf(7L),1,0);
            require(c.audit.observedCountForTest()==5&&
                    c.audit.refusedCountForTest()==1,
                "R84 observation write fault changed fanout behavior");
            c.checkReview(1L,0,
                R84OfflineProjectionObservationJournal.Status
                    .MISSING_BLOCKED,
                null,null,null,null,null);
            c.checkReview(1L,1,
                R84OfflineProjectionObservationJournal.Status
                    .OBSERVED_QUARANTINED,
                1L,2L,3L,null,true);
            c.childCheck(1L,0,"MISSING_BLOCKED");
            // First objective already advanced despite missing
            // evidence; replaying it would double count.
            require(c.f.objectives.get(
                    "combat:r82-kills").progress==1L,
                "R84 missing evidence rolled back objective");
        }
    }

    private static void missingPreparedWitnessBlocksObservation()
            throws Exception{
        try(Case c=new Case(Mode.COMPLETE,
                R84OfflineProjectionObservationJournal.Fault.NONE,false)){
            c.lethal();
            c.f.assertProjection(1L,2L,3L,Long.valueOf(7L),1,0);
            require(c.audit.observedCountForTest()==0&&
                    c.audit.refusedCountForTest()==6,
                "R84 untrusted PREPARED root permitted observations");
        }
    }

    private static void alteredAndStagedEvidenceIsIndeterminate()
            throws Exception{
        try(Case c=new Case(Mode.COMPLETE,
                R84OfflineProjectionObservationJournal.Fault.NONE,true)){
            c.lethal();
            Path first=
                R84OfflineProjectionObservationJournal.recordPathForTest(
                    c.observed,c.operation,1L,0
                );
            Files.write(first,"tampered observation".getBytes(
                StandardCharsets.UTF_8
            ));
            c.checkReview(1L,0,
                R84OfflineProjectionObservationJournal.Status
                    .INDETERMINATE_BLOCKED,
                null,null,null,null,null);
            c.childCheck(1L,0,"INDETERMINATE_BLOCKED");

            Path second=
                R84OfflineProjectionObservationJournal.recordPathForTest(
                    c.observed,c.operation,1L,1
                );
            Path stage=c.observed.resolve(
                "."+second.getFileName()+".r16-interrupted.stage"
            );
            Files.write(stage,"incomplete staged write".getBytes(
                StandardCharsets.UTF_8
            ));
            c.checkReview(1L,1,
                R84OfflineProjectionObservationJournal.Status
                    .INDETERMINATE_BLOCKED,
                null,null,null,null,null);
            require(Files.exists(stage),
                "R84 forensic reviewer deleted ambiguous stage");
            c.childCheck(1L,1,"INDETERMINATE_BLOCKED");
        }
    }

    private static void staleSourceAndDuplicatePolicyAreDenied()
            throws Exception{
        try(Case c=new Case(Mode.COMPLETE,
                R84OfflineProjectionObservationJournal.Fault.NONE,true)){
            try{
                c.f.group1.installOfflinePostObserverObservationForTest(
                    (fact,index,returned)->{}
                );
                throw new AssertionError(
                    "R84 replaced an already installed observer gate"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "R84_OBSERVATION_GATE_DENIED"
                ),"R84 unexpected duplicate gate refusal");
            }
            c.lethal();
            int before=c.audit.observedCountForTest();
            c.f.attemptUnprovenClones();
            require(c.audit.observedCountForTest()==before&&
                    c.audit.refusedCountForTest()==0,
                "R84 forged/stale callback generated observation files");
        }
    }

    private static void childReview(String[] args){
        require(args.length==7&&
                "--child-review".equals(args[0]),
            "R84 invalid separate-JVM invocation");
        Path observed=Paths.get(args[1]);
        Path prepared=Paths.get(args[2]);
        UUID operation=UUID.fromString(args[3]);
        long ordinal=Long.parseLong(args[4]);
        int index=Integer.parseInt(args[5]);
        R84OfflineProjectionObservationJournal.Status expected=
            R84OfflineProjectionObservationJournal.Status.valueOf(
                args[6]
            );
        R84OfflineProjectionObservationJournal.Review review=
            R84OfflineProjectionObservationJournal.review(
                observed,prepared,operation,ordinal,index
            );
        require(review.status==expected&&
                !review.replayAllowed()&&
                !review.projectionCommitted()&&
                !review.recoveryAuthorized()&&
                !review.loginAllowed()&&
                !review.rewardAllowed(),
            "R84 child authorized recovery/commit "+review.status);
        System.out.println(
            "R84_SEPARATE_JVM_READ_ONLY_REVIEW_PASS "+
            "status="+review.status+
            " replay=false commit=false login=false rewards=false"
        );
    }

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }

    private R84OfflineProjectionObservationRecoveryTest(){}
}
