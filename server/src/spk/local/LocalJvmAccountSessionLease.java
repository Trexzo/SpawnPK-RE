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
        private final LocalCrossJvmAccountSessionLock.Handle processLock;
        private boolean released;

        private Token(
            Path path,String account,
            LocalCrossJvmAccountSessionLock.Handle processLock
        ){
            this.path=path;
            this.account=account;
            this.processLock=processLock;
        }

        /** Exact resolved file identity guarded by this token. */
        Path accountFileForReviewAdmission(){
            return path;
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
                if(processLock!=null){
                    try{
                        processLock.close();
                    }catch(java.io.IOException failure){
                        throw new IllegalStateException(
                            "G21.185 ACCOUNT_SESSION_LOCK_RELEASE_UNCERTAIN"+
                            " account="+account,
                            failure
                        );
                    }
                }
                active.remove(path);
                released=true;
            }
        }
    }

    static Token acquire(WorldPlayerPersistence persistence,String username){
        final boolean optIn=Boolean.getBoolean(PROPERTY);
        final boolean crossJvm=Boolean.getBoolean(
            LocalCrossJvmAccountSessionLock.PROPERTY);
        // Do not silently pretend a cross-process lock is in effect
        // when the prerequisite session-owner admission is disabled.
        if(crossJvm&&!optIn)
            throw new IllegalStateException(
                "G21.185 CROSS_JVM_LOCK_REQUIRES_JVM_LEASE");
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
            LocalCrossJvmAccountSessionLock.Handle processLock=null;
            if(crossJvm){
                try{
                    processLock=LocalCrossJvmAccountSessionLock.acquire(
                        key);
                }catch(java.io.IOException failure){
                    throw new IllegalStateException(
                        "G21.185 CROSS_JVM_ACCOUNT_SESSION_DENIED"+
                        " account="+username,
                        failure
                    );
                }
            }
            Token token=new Token(key,username,processLock);
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
