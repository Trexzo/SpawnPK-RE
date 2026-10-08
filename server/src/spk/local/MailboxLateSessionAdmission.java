package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * G21.35: one last file-backed account admission check at the actual
 * LocalSessionPlayerInitializer registration boundary.
 *
 * The normal WorldPlayerPersistence.load() queues on the SAME existing
 * single FIFO worker and re-applies G21.31 journal + G21.32 sidecar
 * review-fence policy. This closes the measured initialization gap if
 * a marker appears before this check; it cannot claim an atomic lock
 * against external marker writers immediately AFTER this check.
 *
 * No postimage may grant, replay, roll back, clear a fence or send a packet.
 */
final class MailboxLateSessionAdmission {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2135_PRE_REGISTRATION_RECHECK_NO_GRANT";

    static void requireStillAdmissible(
        LocalAccountLifecycle.Selection selected,
        LocalAccountLifecycle.LoadResult initial,
        WorldPlayerPersistence persistence
    ){
        Objects.requireNonNull(selected,"selected");
        Objects.requireNonNull(initial,"initial");
        Objects.requireNonNull(persistence,"persistence");

        if(!selected.persistent)
            return;

        if(initial.failed||(!initial.loaded&&!initial.missing))
            throw veto("INITIAL_LOAD_NOT_ADMISSIBLE",null);

        final Optional<PlayerSnapshot> current;
        try{
            // Reuse the same FIFO, same G21.31 journal admission and same
            // G21.32 FilePlayerRepository sidecar checks as the initial
            // account load. This is NOT the forensic untrusted read.
            current=persistence.load(selected.username);
        }catch(IOException|RuntimeException refused){
            throw veto("LATE_ACCOUNT_RECHECK_FAILED",refused);
        }

        if(initial.missing){
            if(current.isPresent())
                throw veto("ACCOUNT_CREATED_DURING_LOGIN",null);
            return;
        }

        if(!current.isPresent())
            throw veto("ACCOUNT_REMOVED_DURING_LOGIN",null);

        PlayerSnapshot seen=current.get();
        if(!selected.username.equals(seen.username())||
           initial.loadedSnapshotSha256==null||
           seen.version()!=PlayerSnapshot.CURRENT_VERSION)
            throw veto("ACCOUNT_IDENTITY_OR_VERSION_CHANGED",null);

        // Prevent a valid but DIFFERENT snapshot (including a stripped
        // journal, mailbox row, equipment or inventory change) from being
        // published through an already hydrated session player.
        final String digest;
        try{
            digest=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(
                    PlayerSnapshotCodec.validateAndNormalize(seen)
                );
        }catch(RuntimeException invalid){
            throw veto("LATEST_ACCOUNT_NOT_CANONICAL",invalid);
        }

        if(!initial.loadedSnapshotSha256.equals(digest))
            throw veto("ACCOUNT_CHANGED_DURING_LOGIN",null);
    }

    private static IllegalStateException veto(
        String reason,Throwable cause
    ){
        return new IllegalStateException(
            "G21.35 MAILBOX_LATE_SESSION_ADMISSION_REJECTED"+
            " reason="+reason+" action=REJECT_SESSION",cause
        );
    }

    private MailboxLateSessionAdmission(){}
}
