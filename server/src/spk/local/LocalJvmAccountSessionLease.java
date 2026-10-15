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
    static final String RAW_WRITE_GATE_PROPERTY=
        "spk.local.experimentalRawWriteJvmLeaseGate";

    // G21.208: cooperating, in-JVM RAW repository.save() reservations.
    // Only the opt-in FilePlayerRepository direct writer participates.
    // A reservation blocks a new opt-in lease until the RAW write
    // finishes, and an existing lease denies a new RAW reservation.
    // G21.216: optional SAME OS sibling lock as cooperating sessions
    // when cross-JVM opt-in enabled; still no durable epoch or raw
    // writer-completeness/OS-lock-path-substitution guarantee.
    private static final Map<Path,Integer> rawWriters=new HashMap<>();

    static final class RawWriteReservation implements AutoCloseable {
        private final Path path;
        private final LocalCrossJvmAccountSessionLock.Handle processLock;
        private boolean closed;
        private RawWriteReservation(
            Path path,LocalCrossJvmAccountSessionLock.Handle processLock
        ){
            this.path=path;
            this.processLock=processLock;
        }
        @Override public void close()throws java.io.IOException{
            synchronized(active){
                if(closed)return;
                Integer count=rawWriters.get(path);
                if(count==null||count!=1)
                    throw new IllegalStateException(
                        "G21.208 RAW_RESERVATION_ACCOUNTING_INVALID");
                // G21.216: keep the in-JVM reservation in place until
                // the cooperating cross-JVM OS lock is truly released.
                // If release is uncertain, retain negative JVM state.
                if(processLock!=null)processLock.close();
                rawWriters.remove(path);
                closed=true;
            }
        }
    }

    /**
     * Claim before any RAW temp creation; release only after its entire
     * saveInternal operation has returned or failed. Existing active
     * exact-path opt-in leases fail closed. Flag OFF preserves legacy RAW.
     * This is not a lock on arbitrary file writers or independent JVMs.
     */
    static RawWriteReservation reserveRawWrite(
        Path exactAccountPath,String account
    )throws java.io.IOException{
        Objects.requireNonNull(exactAccountPath,"exactAccountPath");
        if(!Boolean.getBoolean(RAW_WRITE_GATE_PROPERTY))return null;
        Path file=exactAccountPath.toAbsolutePath().normalize();
        synchronized(active){
            if(active.containsKey(file))
                throw new java.io.IOException(
                    "G21.208 RAW_WRITE_DENIED_ACTIVE_JVM_LEASE"+
                    " account="+account);
            if(rawWriters.containsKey(file))
                throw new java.io.IOException(
                    "G21.208 RAW_WRITE_DENIED_ANOTHER_RAW_WRITER"+
                    " account="+account);
            // G21.216: a cooperative RAW save must claim the SAME
            // stable sibling OS lock used by opt-in JVM sessions when
            // cross-JVM exclusion is requested. Do not pretend a RAW
            // writer holds that lock if it cannot acquire it.
            final LocalCrossJvmAccountSessionLock.Handle processLock;
            if(Boolean.getBoolean(
                    LocalCrossJvmAccountSessionLock.PROPERTY)){
                if(!Boolean.getBoolean(PROPERTY))
                    throw new java.io.IOException(
                        "G21.216 CROSS_JVM_RAW_REQUIRES_JVM_LEASE_OPT_IN");
                try{
                    processLock=LocalCrossJvmAccountSessionLock.acquire(
                        file);
                }catch(java.io.IOException conflict){
                    throw new java.io.IOException(
                        "G21.216 CROSS_JVM_RAW_ACCOUNT_WRITE_DENIED"+
                        " account="+account,conflict);
                }
            }else{
                processLock=null;
            }
            rawWriters.put(file,1);
            return new RawWriteReservation(file,processLock);
        }
    }

    static int rawWriteReservationsForTest(){
        synchronized(active){
            int sum=0;
            for(Integer count:rawWriters.values())sum+=count;
            return sum;
        }
    }


    private static final Map<Path,Token> active=new HashMap<>();
    // G21.201 CI correction: an old session admitted while the opt-in
    // lease was OFF is not retroactively made an owner of a later token.
    // Identity-keyed weak witnesses distinguish that earlier unleased
    // session from a newly forged foreign SaveTask. The legacy late-
    // enable concurrent-write hazard deliberately remains unresolved.
    private static final Map<WorldPlayer,UnleasedAdmission>
        unleasedAdmissions=new java.util.WeakHashMap<>();

    private static final class UnleasedAdmission {
        final Path accountPath;
        final String account;
        final long generation;
        UnleasedAdmission(Path path,String account,long generation){
            this.accountPath=path;
            this.account=account;
            this.generation=generation;
        }
    }
    // G21.201 diagnostic: count actual worker-side pre-publication checks.
    private static final java.util.concurrent.atomic.AtomicInteger
        workerOwnerChecks=new java.util.concurrent.atomic.AtomicInteger();
    // G21.202: separate counter for original-capture token identity
    // checks, distinct from the older current-path-only fallback.
    private static final java.util.concurrent.atomic.AtomicInteger
        captureBoundWorkerChecks=new java.util.concurrent.atomic.AtomicInteger();

    private LocalJvmAccountSessionLease(){}

    static final class Token {
        private final Path path;
        private final String account;
        private final LocalCrossJvmAccountSessionLock.Handle processLock;
        private boolean released;
        // G21.200: JVM-only identity binding, not a durable epoch.
        // Never serialized, exported as a disk receipt, or used to
        // authorize G21.188 ACTIVE retirement.
        private WorldPlayer admittedWorldOwner;
        private long admittedWorldGeneration;

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

        /**
         * G21.200: bind the actual returned World admission generation
         * to the already-acquired exact-path opt-in session token.
         * This is a JVM identity check only, NEVER a durable owner epoch.
         */
        synchronized void bindAdmittedWorldOwner(
            WorldPlayer owner,long generation
        ){
            Objects.requireNonNull(owner,"owner");
            if(generation<=0||!account.equals(owner.username())||
               !owner.accepts(generation))
                throw new IllegalStateException(
                    "G21.200 INVALID_REGISTERED_WORLD_OWNER");
            synchronized(active){
                if(released||active.get(path)!=this)
                    throw new IllegalStateException(
                        "G21.200 LEASE_NOT_CURRENT_OWNER");
                if(admittedWorldOwner!=null&&
                   (admittedWorldOwner!=owner||
                    admittedWorldGeneration!=generation))
                    throw new IllegalStateException(
                        "G21.200 WORLD_OWNER_REBIND_DENIED");
                admittedWorldOwner=owner;
                admittedWorldGeneration=generation;
            }
        }

        synchronized void requireAdmittedWorldOwner(
            WorldPlayer owner,long generation
        ){
            synchronized(active){
                if(released||active.get(path)!=this||
                   owner==null||admittedWorldOwner!=owner||
                   admittedWorldGeneration!=generation||
                   !account.equals(owner.username())||
                   !owner.accepts(generation))
                    throw new IllegalStateException(
                        "G21.200 WORLD_OWNER_GENERATION_NOT_CURRENT");
            }
        }

        /**
         * G21.201: immutable queued-save provenance against the admitted
         * in-JVM owner. A final save is allowed after World unregisters:
         * require object/generation equality, NOT owner.accepts().
         *
         * This does not prove that raw account bytes belong to the owner,
         * and does not create a durable receipt or retirement authority.
         */
        synchronized void requireCapturedSaveOwner(
            WorldPlayer owner,long capturedGeneration,String snapshotAccount
        ){
            synchronized(active){
                if(released||active.get(path)!=this||
                   admittedWorldOwner!=owner||
                   admittedWorldGeneration!=capturedGeneration||
                   !account.equals(snapshotAccount)||
                   owner==null||!account.equals(owner.username()))
                    throw new IllegalStateException(
                        "G21.201 CAPTURED_SAVE_OWNER_NOT_BOUND_TO_LEASE");
            }
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
                admittedWorldOwner=null;
                admittedWorldGeneration=0L;
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
            if(optIn&&rawWriters.containsKey(key))
                throw new IllegalStateException(
                    "G21.208 JVM_LEASE_DENIED_RAW_WRITE_IN_FLIGHT"+
                    " account="+username);
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

    /**
     * Test-only current-token witness for a live World owner.
     * No token/secret is returned and this is NOT a save or release API.
     */
    static void requireCurrentWorldOwnerForTest(
        Path accountFile,WorldPlayer owner,long generation
    ){
        Objects.requireNonNull(accountFile,"accountFile");
        Path path=accountFile.toAbsolutePath().normalize();
        // Read the current token under the map lock, then release
        // that lock BEFORE entering synchronized Token. All Token
        // operations use Token -> active, never active -> Token.
        final Token token;
        synchronized(active){
            token=active.get(path);
        }
        if(token==null)
            throw new IllegalStateException(
                "G21.200 NO_CURRENT_ACCOUNT_LEASE");
        token.requireAdmittedWorldOwner(owner,generation);
    }

    /**
     * Record admission provenance of a real file-backed World session
     * opened WITHOUT a JVM token, before any later dynamic flag enable.
     * This never creates a token, does not admit another player, and
     * does NOT protect the legacy unleased session from stale writes.
     */
    static void noteUnleasedWorldAdmission(
        Path exactAccountPath,String account,
        WorldPlayer owner,long generation
    ){
        Objects.requireNonNull(exactAccountPath,"exactAccountPath");
        Objects.requireNonNull(owner,"owner");
        if(generation<=0||!account.equals(owner.username())||
           !owner.accepts(generation))
            throw new IllegalStateException(
                "G21.201 INVALID_UNLEASED_ADMISSION_PROVENANCE");
        Path file=exactAccountPath.toAbsolutePath().normalize();
        synchronized(active){
            UnleasedAdmission current=unleasedAdmissions.get(owner);
            if(current!=null&&
               (!current.accountPath.equals(file)||
                !current.account.equals(account)||
                current.generation!=generation))
                throw new IllegalStateException(
                    "G21.201 UNLEASED_ADMISSION_OWNER_REBIND_DENIED");
            unleasedAdmissions.put(
                owner,new UnleasedAdmission(file,account,generation));
        }
    }

    /**
     * G21.202: IMMUTABLE, in-JVM capture-time identity witness.
     * This is never written to disk and cannot authorize review release.
     * There are intentionally 3 origins: the exact captured Token,
     * a previously admitted unleased World owner, and no token/witness.
     */
    static final class SaveCaptureWitness {
        final Path path;
        final String account;
        final WorldPlayer owner;
        final long generation;
        private final Token originalLease;
        private final boolean priorUnleased;

        private SaveCaptureWitness(
            Path path,String account,WorldPlayer owner,long generation,
            Token originalLease,boolean priorUnleased
        ){
            this.path=path;
            this.account=account;
            this.owner=owner;
            this.generation=generation;
            this.originalLease=originalLease;
            this.priorUnleased=priorUnleased;
        }
    }

    /**
     * Invoked only after a genuine World-owned capture was constructed.
     * Captures original Token IDENTITY rather than looking up only the
     * account path at eventual asynchronous worker execution.
     */
    static SaveCaptureWitness captureSaveWitness(
        Path exactAccountPath,String account,
        WorldPlayer owner,long generation
    ){
        Objects.requireNonNull(exactAccountPath,"exactAccountPath");
        Objects.requireNonNull(owner,"owner");
        Path path=exactAccountPath.toAbsolutePath().normalize();
        synchronized(active){
            UnleasedAdmission previous=unleasedAdmissions.get(owner);
            boolean priorUnleased=previous!=null&&
                previous.accountPath.equals(path)&&
                previous.account.equals(account)&&
                previous.generation==generation;
            // Do not let an unrelated newly admitted lease retroactively
            // own a genuinely prior-unleased World (G21.184).
            Token token=priorUnleased?null:active.get(path);
            return new SaveCaptureWitness(
                path,account,owner,generation,token,priorUnleased);
        }
    }

    /**
     * Compare the captured Token object with the CURRENT holder.
     * A released/replaced lease can never silently validate an old
     * capture. Historically unleased real sessions remain unguarded;
     * unknown/manual World saves preserve the G21.201 fallback check.
     */
    static boolean requireCapturedWitnessBeforeWrite(
        SaveCaptureWitness witness,Path exactAccountPath,
        String snapshotAccount,WorldPlayer owner,long generation
    ){
        Objects.requireNonNull(exactAccountPath,"exactAccountPath");
        if(witness==null)
            return requireCapturedSaveOwnerIfLeased(
                exactAccountPath,snapshotAccount,owner,generation);
        Path path=exactAccountPath.toAbsolutePath().normalize();
        if(!witness.path.equals(path)||
           !witness.account.equals(snapshotAccount)||
           witness.owner!=owner||
           witness.generation!=generation)
            throw new IllegalStateException(
                "G21.202 SAVE_CAPTURE_WITNESS_MISMATCH");
        if(witness.priorUnleased)
            return false; // Only exact previous real unleased admission.
        if(witness.originalLease==null)
            return requireCapturedSaveOwnerIfLeased(
                path,snapshotAccount,owner,generation);
        final Token current;
        synchronized(active){
            current=active.get(path);
        }
        if(current!=witness.originalLease)
            throw new IllegalStateException(
                "G21.202 SAVE_CAPTURE_ORIGINAL_LEASE_LOST_OR_REPLACED");
        current.requireCapturedSaveOwner(
            owner,generation,snapshotAccount);
        workerOwnerChecks.incrementAndGet();
        captureBoundWorkerChecks.incrementAndGet();
        return true;
    }

    /**
     * Only a currently held opt-in JVM lease is guarded. A null lease
     * preserves preexisting unleased/manual World adapter behavior.
     * Called by the existing async World SaveTask immediately before
     * FilePlayerRepository.saveForWorld; no new disk I/O or admission.
     */
    static boolean requireCapturedSaveOwnerIfLeased(
        Path exactAccountPath,String snapshotAccount,
        WorldPlayer owner,long capturedGeneration
    ){
        Objects.requireNonNull(exactAccountPath,"exactAccountPath");
        final Token token;
        Path file=exactAccountPath.toAbsolutePath().normalize();
        synchronized(active){
            token=active.get(file);
            // G21.184: a real already-admitted default-OFF session
            // must still be allowed its historical final save after
            // another World obtains a NEW opt-in lease. This is an
            // explicit characterization of the unsafe legacy toggle,
            // NOT permission for an arbitrary foreign SaveTask.
            UnleasedAdmission legacy=unleasedAdmissions.get(owner);
            if(token!=null&&legacy!=null&&
               legacy.accountPath.equals(file)&&
               legacy.account.equals(snapshotAccount)&&
               legacy.generation==capturedGeneration)
                return false;
        }
        if(token==null)return false;
        token.requireCapturedSaveOwner(
            owner,capturedGeneration,snapshotAccount);
        workerOwnerChecks.incrementAndGet();
        return true;
    }

    static int workerOwnerChecksForTest(){
        return workerOwnerChecks.get();
    }

    static int captureBoundWorkerChecksForTest(){
        return captureBoundWorkerChecks.get();
    }

    static int activeCountForTest(){
        synchronized(active){
            return active.size();
        }
    }
}
