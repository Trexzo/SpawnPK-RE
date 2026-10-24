package spk.local;

import java.util.Arrays;

/**
 * R51: unit-level protocol transition matrix only. A matching
 * manufactured token NEVER equals disk ownership or authorizes login.
 */
public final class G21379AccountEpochTransitionMatrixRegression {
    private G21379AccountEpochTransitionMatrixRegression(){}
    private static final String A=
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B=
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String C=
        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static void check(boolean ok,String code){
        if(!ok)throw new AssertionError("R51_EPOCH_MATRIX "+code);
    }
    @FunctionalInterface interface Attempt{void run();}
    private static void rejects(String fragment,Attempt attempt){
        boolean rejected=false;
        try{attempt.run();}
        catch(IllegalStateException failure){
            rejected=failure.getMessage().contains("R51_"+fragment);
        }
        check(rejected,"did not reject "+fragment);
    }
    private static void nonadmitting(
        LocalAccountEpochTransitionModel.Head... heads
    ){
        for(LocalAccountEpochTransitionModel.Head h:heads)
            check(!h.admissionAuthorized&&!h.releaseAuthorized,
                "manufactured state granted admission/release");
    }
    public static void main(String[] args){
        check(args.length==0,"unexpected args");
        LocalAccountEpochTransitionModel.Head base=
            LocalAccountEpochTransitionModel.untrustedCommittedFixture(
                "r51-account",1L,0L,A);
        LocalAccountEpochTransitionModel.UntrustedOwner original=
            LocalAccountEpochTransitionModel.untrustedOwnerFixture(
                "r51-account",2L,101L,"world-a");
        LocalAccountEpochTransitionModel.UntrustedOwner foreign=
            LocalAccountEpochTransitionModel.untrustedOwnerFixture(
                "r51-account",2L,102L,"world-b");
        LocalAccountEpochTransitionModel.UntrustedOwner differentAccount=
            LocalAccountEpochTransitionModel.untrustedOwnerFixture(
                "r51-other",2L,101L,"world-a");
        LocalAccountEpochTransitionModel.Head claimed=
            LocalAccountEpochTransitionModel.proposeClaim(
                base,base,original);
        check(claimed.epoch==2L&&claimed.generation==101L&&
                claimed.sequence==1L&&A.equals(claimed.digest)&&
                claimed.phase==LocalAccountEpochTransitionModel.Phase.ACTIVE,
            "claim lost original snapshot or owner");
        rejects("UNRESOLVED_STATE_NO_CLAIM",()->
            LocalAccountEpochTransitionModel.proposeClaim(
                claimed,claimed,original));
        rejects("NEXT_EPOCH_OR_ACCOUNT_INVALID",()->
            LocalAccountEpochTransitionModel.proposeClaim(
                base,base,
                LocalAccountEpochTransitionModel.untrustedOwnerFixture(
                    "r51-account",3L,102L,"world-b")));
        rejects("NEXT_EPOCH_OR_ACCOUNT_INVALID",()->
            LocalAccountEpochTransitionModel.proposeClaim(
                base,base,differentAccount));
        rejects("FOREIGN_OR_STALE_OWNER",()->
            LocalAccountEpochTransitionModel.proposeCheckpoint(
                claimed,claimed,foreign,B));
        rejects("FOREIGN_OR_STALE_OWNER",()->
            LocalAccountEpochTransitionModel.proposeCheckpoint(
                claimed,claimed,differentAccount,B));
        LocalAccountEpochTransitionModel.Head checkpoint=
            LocalAccountEpochTransitionModel.proposeCheckpoint(
                claimed,claimed,original,B);
        check(checkpoint.sequence==2L&&checkpoint.epoch==2L&&
                B.equals(checkpoint.digest)&&
                checkpoint.phase==LocalAccountEpochTransitionModel.Phase.ACTIVE,
            "checkpoint changed owner or failed to advance");
        rejects("STALE_OR_REPLACED_HEAD",()->
            LocalAccountEpochTransitionModel.proposeCheckpoint(
                checkpoint,claimed,original,C));
        rejects("STALE_OR_REPLACED_HEAD",()->
            LocalAccountEpochTransitionModel.proposeFinalPending(
                checkpoint,claimed,original,C));
        LocalAccountEpochTransitionModel.Head finalPending=
            LocalAccountEpochTransitionModel.proposeFinalPending(
                checkpoint,checkpoint,original,C);
        check(finalPending.sequence==3L&&C.equals(finalPending.digest)&&
                finalPending.phase==
                    LocalAccountEpochTransitionModel.Phase.FINAL_PENDING,
            "final pending skipped immutable sequence");
        rejects("UNRESOLVED_STATE_NO_CLAIM",()->
            LocalAccountEpochTransitionModel.proposeClaim(
                finalPending,finalPending,
                LocalAccountEpochTransitionModel.untrustedOwnerFixture(
                    "r51-account",3L,103L,"world-c")));
        rejects("CHECKPOINT_NOT_ACTIVE",()->
            LocalAccountEpochTransitionModel.proposeCheckpoint(
                finalPending,finalPending,original,A));
        rejects("FINAL_NOT_ACTIVE",()->
            LocalAccountEpochTransitionModel.proposeFinalPending(
                finalPending,finalPending,original,A));

        LocalAccountEpochTransitionModel.Head review=
            LocalAccountEpochTransitionModel.proposeReview(
                finalPending,finalPending);
        rejects("REVIEW_IRREVERSIBLE",()->
            LocalAccountEpochTransitionModel.proposeReview(
                review,review));
        rejects("UNRESOLVED_STATE_NO_CLAIM",()->
            LocalAccountEpochTransitionModel.proposeClaim(
                review,review,
                LocalAccountEpochTransitionModel.untrustedOwnerFixture(
                    "r51-account",3L,103L,"world-c")));
        rejects("STALE_OR_REPLACED_HEAD",()->
            LocalAccountEpochTransitionModel.proposeReview(
                review,finalPending));
        rejects("INVALID_SNAPSHOT_DIGEST",()->
            LocalAccountEpochTransitionModel.proposeCheckpoint(
                checkpoint,checkpoint,original,"not-a-sha"));
        rejects("INVALID_OWNER_GENERATION",()->
            LocalAccountEpochTransitionModel.untrustedOwnerFixture(
                "r51-account",3L,0L,"bad"));
        rejects("INVALID_ACCOUNT",()->
            LocalAccountEpochTransitionModel.untrustedCommittedFixture(
                "../other",1L,0L,A));
        rejects("SEQUENCE_OVERFLOW",()->
            LocalAccountEpochTransitionModel.proposeClaim(
                LocalAccountEpochTransitionModel.untrustedCommittedFixture(
                    "r51-account",1L,Long.MAX_VALUE,A),
                LocalAccountEpochTransitionModel.untrustedCommittedFixture(
                    "r51-account",1L,Long.MAX_VALUE,A),original));
        nonadmitting(base,claimed,checkpoint,finalPending,review);
        System.out.println("R51_ACCOUNT_EPOCH_TRANSITION_MATRIX_PASS"+
            " immutableClaimAndCheckpoint=true"+
            " generationAndOwnerContinuity=true"+
            " staleExpectedHeadDenied=true"+
            " divergentSnapshotSequenceDenied=true"+
            " finalPendingNonReusable=true"+
            " reviewIrreversible=true"+
            " futureOwnerCannotClaimIncompleteFinal=true"+
            " malformedDigestAccountGenerationDenied=true"+
            " overflowDenied=true"+
            " protocolOnlyNoFilesystemWrites=true"+
            " fabricatedTokensNotAuthenticated=true"+
            " allStatesNonAdmitting=true"+
            " LocalSessionWired=false"+
            " crossJvmCASProven=false"+
            " durableFinalRelease=false");
    }
}
