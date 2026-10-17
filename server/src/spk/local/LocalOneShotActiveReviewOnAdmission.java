package spk.local;

import java.io.IOException;
import java.nio.file.Path;

/**
 * G21.190 EXPERIMENTAL ONE-SHOT ACTIVE review intent before World
 * membership. This is intentionally unusable for ordinary repeated
 * account logins: once created, the marker is never automatically
 * retired, including after a healthy final save.
 *
 * No account write receipt, crash-safe intent retirement, replay,
 * recovery, grant or generic RAW writer exclusion is implemented.
 */
final class LocalOneShotActiveReviewOnAdmission {
    static final String PROPERTY=
        "spk.local.experimentalOneShotActiveReviewOnAdmission";

    private LocalOneShotActiveReviewOnAdmission(){}

    static void requireConfiguration(
        boolean enabled,boolean persistentAccount,boolean reviewVeto
    ){
        if(!enabled||!persistentAccount)return;
        if(!reviewVeto||
           !Boolean.getBoolean(LocalJvmAccountSessionLease.PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY))
            throw new IllegalStateException(
                "G21.190 ONE_SHOT_ACTIVE_INTENT_REQUIRES_ALL_LOCKS_AND_VETO");
    }

    static void forceBeforeWorldRegistration(
        boolean enabled,boolean persistentAccount,
        LocalJvmAccountSessionLease.Token token
    ){
        if(!enabled||!persistentAccount)return;
        if(token==null)
            throw new IllegalStateException(
                "G21.190 ONE_SHOT_INTENT_REQUIRES_LOCKED_ACCOUNT");
        final Path exactLockedAccount=token.accountFileForReviewAdmission();
        try{
            // G21.371: do not strand a new one-shot ACTIVE marker
            // on a filesystem that cannot even observe unix:nlink.
            // Only the strict negative-final-witness experiment adds
            // this admission preflight. A successful preflight is NOT
            // a durable owner receipt or hostile-writer atomic fence.
            if(LocalFinalSaveNegativeWitness.enabled())
                LocalFinalSaveNegativeWitness
                    .requirePreAdmissionLinkCountCapability(
                        exactLockedAccount);
            // Atomic exclusive creation; forced file+directory and
            // no automatic marker retirement even if registration
            // or final account publication later fails.
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(
                exactLockedAccount);
        }catch(IOException failure){
            throw new IllegalStateException(
                "G21.190 ONE_SHOT_INTENT_FORCE_FAILED_NO_ACCOUNT_ADMISSION",
                failure
            );
        }
    }
}
