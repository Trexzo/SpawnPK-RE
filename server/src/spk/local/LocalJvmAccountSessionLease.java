package spk.local;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * G21.181 EXPERIMENTAL opt-in same-JVM session ownership.
 *
 * One lease is keyed by the actual resolved named-account file PATH
 * before account hydration; it is held across final write completion.
 * A failed or unconfirmed final save deliberately retains a JVM-only
 * negative lease. Nothing here is crash-durable or cross-process.
 *
 * Existing LocalLab behavior remains the default until the broader
 * writer/intent protocol is specified and independently certified.
 */
final class LocalJvmAccountSessionLease {
    static final String PROPERTY=
        "spk.local.experimentalJvmAccountSessionLease";

    private static final Map<Path,Token> active=new HashMap<>();

    private LocalJvmAccountSessionLease(){}

    static final class Token {
        private final Path path;
        private final String account;
        private boolean released;

        private Token(Path path,String account){
            this.path=path;
            this.account=account;
        }

        /** Only the exact admitted owner can release on proven completion. */
        synchronized void releaseAfterConfirmedFinalSave(){
            release();
        }

        /** Registration never completed: no session state was admitted. */
        synchronized void releaseAfterInitializationFailure(){
            release();
        }

        private void release(){
            if(released)return;
            synchronized(active){
                if(active.get(path)!=this)
                    throw new IllegalStateException(
                        "G21.181 JVM_ACCOUNT_LEASE_OWNER_CHANGED account="+
                        account);
                active.remove(path);
                released=true;
            }
        }
    }

    static Token acquire(WorldPlayerPersistence persistence,String username){
        final boolean optIn=Boolean.getBoolean(PROPERTY);
        /*
         * G21.183: turning the system property OFF while an already
         * admitted opt-in owner is active must not erase that owner's
         * protection. Fast path remains unchanged for the ordinary
         * default-off state when no JVM account token exists.
         *
         * This is NOT a promise that an unleased session admitted while
         * disabled becomes protected if the flag later changes.
         */
        if(!optIn){
            synchronized(active){
                if(active.isEmpty())
                    return null;
            }
        }
        Objects.requireNonNull(persistence,"persistence");
        Objects.requireNonNull(username,"username");

        Path file=persistence.fileBackedAccountPathForJvmSessionLease(
            username);
        if(file==null)return null;
        Path key=file.toAbsolutePath().normalize();
        synchronized(active){
            if(active.containsKey(key))
                throw new IllegalStateException(
                    "G21.181 JVM_ACCOUNT_SESSION_OWNED_NO_RECOVERY"+
                    " account="+username+
                    " path="+key);
            // A distinct account may still use the default-off path;
            // existing SAME-path leases are refused regardless of flag.
            if(!optIn)
                return null;
            Token token=new Token(key,username);
            active.put(key,token);
            return token;
        }
    }

    static int activeCountForTest(){
        synchronized(active){
            return active.size();
        }
    }
}
