package spk.local;

import java.io.IOException;

/**
 * G21.382 strict experimental pre-reservation check.
 * Read-only negative marker validation; never creates a grant, replay,
 * receipt or post-save recovery authority. Ordinary mode is unchanged.
 */
final class LocalStrictFinalSaveIntentGate {
    private LocalStrictFinalSaveIntentGate(){}

    static void requireBeforeReservation(
        boolean strictMode, LocalJvmAccountSessionLease.Token owner
    ){
        if(!strictMode)return;
        if(owner==null)
            throw new IllegalStateException(
                "G21.382 FINAL_SAVE_MISSING_LOCKED_SESSION_OWNER");
        try{
            LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
                owner.accountFileForReviewAdmission());
        }catch(IOException unsafeIntent){
            throw new IllegalStateException(
                "G21.382 FINAL_SAVE_ACTIVE_INTENT_NOT_VERIFIED",
                unsafeIntent);
        }
    }
}
