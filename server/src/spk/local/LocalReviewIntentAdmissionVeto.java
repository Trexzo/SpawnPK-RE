package spk.local;

import java.io.IOException;
import java.nio.file.Path;

/**
 * G21.189: OPTIONAL, READ-ONLY, existing-review-marker admission veto.
 *
 * This does not create, retire or clear any account intent. It uses
 * the exact account path already held by the cooperating session token
 * and rejects a review marker before hydration and again before World
 * membership publication. Default OFF; no arbitrary runtime toggles.
 */
final class LocalReviewIntentAdmissionVeto {
    static final String PROPERTY=
        "spk.local.experimentalReviewIntentAdmissionVeto";

    private LocalReviewIntentAdmissionVeto(){}

    static void requireConfiguration(
        boolean enabled,boolean persistentAccount
    ){
        if(!enabled||!persistentAccount)return;
        // A read-only review-marker check is NOT equivalent to an
        // ownership fence. Refuse opt-in without both preceding
        // experimental session-owner layers rather than advertising
        // a false cross-process admission safety property.
        if(!Boolean.getBoolean(LocalJvmAccountSessionLease.PROPERTY)||
           !Boolean.getBoolean(
               LocalCrossJvmAccountSessionLock.PROPERTY))
            throw new IllegalStateException(
                "G21.189 REVIEW_VETO_REQUIRES_BOTH_SESSION_LOCKS");
    }

    static void requireAdmissible(
        boolean enabled,boolean persistentAccount,
        LocalJvmAccountSessionLease.Token sessionLease
    ){
        if(!enabled||!persistentAccount)return;
        if(sessionLease==null)
            throw new IllegalStateException(
                "G21.189 REVIEW_VETO_MISSING_SESSION_OWNER");
        final Path lockedPath=sessionLease
            .accountFileForReviewAdmission();
        try{
            LocalDurableAccountReviewIntentPrimitive
                .requireNoReviewMarker(lockedPath);
            // G21.302: even if ACTIVE was removed, an existing
            // one-shot final-review witness remains NEGATIVE.
            LocalFinalSaveNegativeWitness
                .requireNoNegativeWitness(lockedPath);
            // G21.378: reobserve the selected lock pathname AFTER
            // the negative-marker I/O, not only before it. A hostile
            // rename remains a TOCTOU risk, not a durable owner epoch.
            // This denies an observed changed lock before hydration.
            if(!lockedPath.equals(sessionLease.accountFileForReviewAdmission()))
                throw new IllegalStateException(
                    "G21.378 REVIEW_VETO_ACCOUNT_PATH_CHANGED");
        }catch(IOException negative){
            throw new IllegalStateException(
                "G21.189 REVIEW_INTENT_LOGIN_DENIED_NO_AUTO_RECOVERY",
                negative);
        }
    }
}
