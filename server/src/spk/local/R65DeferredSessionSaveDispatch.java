package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * R65 OFFLINE-ONLY deferred persistence callback boundary.
 *
 * Capture is an in-memory operation: it freezes a previously issued R64
 * session-owner token and an immutable PlayerSnapshot, not a live session
 * pointer. Dispatch ALWAYS reacquires R60's cross-process OS FileLock, then
 * checks the on-disk owner UUID, epoch, phase, account generation, digest
 * and R59 journal revision BEFORE writing canonical account bytes.
 *
 * The LOGOUT_FINAL_SAVE case advances account+R63 ledger+R64 owner record,
 * then publishes RELEASED while holding the SAME OS lock. Interrupted
 * multi-file writes are NOT an atomic transaction and remain fail-closed
 * according to R63/R64; the callback is not silently retried or promoted.
 *
 * No production scheduler, player login, game socket or account callback
 * is connected to these test-only entrypoints. The token is not a user
 * credential, and a clear result never authorizes actual player login.
 */
final class R65DeferredSessionSaveDispatch {
    enum Reason {
        AUTOSAVE,
        LOGOUT_FINAL_SAVE
    }

    static final class CapturedCallback {
        final UUID callbackId;
        final Reason reason;
        final R64OfflineSessionOwnership.Token capturedOwner;
        final PlayerSnapshot capturedSnapshot;
        final String capturedPostDigest;

        private CapturedCallback(UUID callbackId,Reason reason,
                R64OfflineSessionOwnership.Token owner,
                PlayerSnapshot snapshot,String digest){
            this.callbackId=callbackId;
            this.reason=reason;
            capturedOwner=owner;
            capturedSnapshot=snapshot;
            capturedPostDigest=digest;
        }
    }

    static final class Outcome {
        final UUID callbackId;
        final R64OfflineSessionOwnership.Token latestOwner;
        final boolean ownerReleased;
        Outcome(UUID callbackId,R64OfflineSessionOwnership.Token owner){
            this.callbackId=callbackId;
            latestOwner=owner;
            ownerReleased=owner.phase==
                R64OfflineSessionOwnership.Phase.RELEASED;
        }
        boolean loginAuthorized(){return false;}
        boolean automaticRecoveryAttempted(){return false;}
    }

    /**
     * Represents capture by an offline session's queued save callback.
     * Capture does not access or mutate any filesystem: the future
     * DISPATCH, not capture time, decides whether ownership is still valid.
     */
    static CapturedCallback capture(
        Reason reason,R64OfflineSessionOwnership.Token owner,
        PlayerSnapshot proposed
    )throws IOException{
        Objects.requireNonNull(reason,"reason");
        Objects.requireNonNull(owner,"owner");
        Objects.requireNonNull(proposed,"proposed");
        if(owner.phase!=R64OfflineSessionOwnership.Phase.ACTIVE||
           !owner.username.equals(proposed.username())||
           proposed.version()!=PlayerSnapshot.CURRENT_VERSION)
            throw new IOException(
                "R65_CAPTURE_DENIED inactive/foreign/invalid snapshot"
            );
        String digest=R57TradePairJournal.digest(proposed);
        if(digest.equals(owner.account.snapshotDigest))
            throw new IOException(
                "R65_CAPTURE_DENIED unchanged callback snapshot"
            );
        return new CapturedCallback(
            UUID.randomUUID(),reason,owner,proposed,digest
        );
    }

    /**
     * Atomicity boundary for COOPERATIVE callbacks: all authority checks
     * and file publications are serialized under one R60 OS FileLock.
     * A callback from a superseded owner fails at R64 requireMatches()
     * before changing account, generation or session owner evidence.
     */
    static Outcome dispatch(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,CapturedCallback callback
    )throws IOException{
        Objects.requireNonNull(callback,"callback");
        return R60TradePairCoordinator.withExclusive(
            journalRoot,false,()->{
                if(!callback.capturedPostDigest.equals(
                        R57TradePairJournal.digest(
                            callback.capturedSnapshot
                        )))
                    throw new IOException(
                        "R65_DISPATCH_DENIED captured snapshot drift"
                    );
                R64OfflineSessionOwnership.Token afterSave=
                    R64OfflineSessionOwnership.saveOwnedUnderLock(
                        journalRoot,accountRoot,generationRoot,sessionRoot,
                        callback.capturedOwner,
                        callback.capturedSnapshot,false
                    );
                if(callback.reason==Reason.LOGOUT_FINAL_SAVE){
                    // Crucial: no release/reacquire between final save
                    // and logout fencing. A cooperating successor cannot
                    // take over between the two steps.
                    afterSave=
                        R64OfflineSessionOwnership.releaseOwnerUnderLock(
                            journalRoot,accountRoot,generationRoot,
                            sessionRoot,afterSave
                        );
                }
                return new Outcome(callback.callbackId,afterSave);
            }
        );
    }

    private R65DeferredSessionSaveDispatch(){}
}
