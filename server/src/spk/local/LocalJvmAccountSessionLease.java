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
    static final String HARDLINK_COUNT_GATE_PROPERTY=
        "spk.local.experimentalAccountHardlinkCountGate";

    // R36: deterministic test-only cutpoint between observations of
    // the same opted-in account leaf. No production caller installs
    // this probe; it cannot authorize a write or remove a sidecar.
    @FunctionalInterface interface HardlinkObservationProbe {
        void afterFirstAttributes(Path file)throws java.io.IOException;
    }
    private static volatile HardlinkObservationProbe
        hardlinkObservationProbeForTest;
    static void hardlinkObservationProbeForTest(
        HardlinkObservationProbe probe
    ){ hardlinkObservationProbeForTest=probe; }

    /**
     * G21.230: cooperative opt-in admission policy. A named account
     * leaf with >1 hard links is never an exclusive account inode.
     * This is an admission-time NOFOLLOW observation, not a durable
     * file-object epoch or protection against post-check substitution.
     */
    static void requireNonHardlinkedAccount(
        Path account
    )throws java.io.IOException{
        if(!Boolean.getBoolean(HARDLINK_COUNT_GATE_PROPERTY))return;
        Path file=account.toAbsolutePath().normalize();
        // R35: this separately selectable NOFOLLOW hardlink-count
        // admission gate cannot trust just the final filename:
        // Files.exists(NOFOLLOW_LINKS) still traverses symlinked
        // parent and grandparent directories. Share the same opt-in
        // ancestry veto as the JVM inode-alias gate, including when
        // the named account leaf does not exist yet.
        LocalCrossJvmAccountSessionLock.requireNoSymlinkAncestors(
            file.getParent());
        // R36: Files.exists swallows I/O errors and returns false
        // both for true absence AND when existence cannot be proven.
        // Only NoSuchFileException permits a missing bootstrap leaf.
        final java.nio.file.attribute.BasicFileAttributes before;
        try{
            before=java.nio.file.Files.readAttributes(
                file,java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        }catch(java.nio.file.NoSuchFileException absent){
            return;
        }
        if(!before.isRegularFile()||before.isSymbolicLink()||
           before.fileKey()==null)
            throw new java.io.IOException(
                "G21.230 ACCOUNT_LEAF_NOT_REGULAR_OR_UNPROVEN");
        HardlinkObservationProbe probe=hardlinkObservationProbeForTest;
        if(probe!=null)probe.afterFirstAttributes(file);
        final Object count;
        try{
            count=java.nio.file.Files.getAttribute(
                file,"unix:nlink",
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        }catch(UnsupportedOperationException|
                IllegalArgumentException unavailable){
            throw new java.io.IOException(
                "G21.230 HARDLINK_COUNT_UNSUPPORTED_FAIL_CLOSED",
                unavailable);
        }
        // A pathname can now resolve to a different (even identical-
        // byte) inode than the one whose existence was verified.
        // Observe again: an uncooperative after-check race remains,
        // but an observed substitution never becomes admission.
        final java.nio.file.attribute.BasicFileAttributes after=
            java.nio.file.Files.readAttributes(
                file,java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||after.isSymbolicLink()||
           after.fileKey()==null||
           !before.fileKey().equals(after.fileKey())||
           !before.creationTime().equals(after.creationTime()))
            throw new java.io.IOException(
                "R36_HARDLINK_ACCOUNT_FILE_OBJECT_CHANGED_DURING_READ");
        if(!(count instanceof Number))
            throw new java.io.IOException(
                "G21.230 HARDLINK_COUNT_INVALID");
        if(((Number)count).longValue()!=1L)
            throw new java.io.IOException(
                "G21.230 MULTIPLE_ACCOUNT_HARDLINKS_DENIED"+
                " count="+count);
    }

    // G21.228: DEFAULT OFF, best-effort same-JVM file-object alias
    // collision detection for existing account inodes. NOT a durable
    // epoch, and not an across-process/hardlink-creation fence.
    static final String INODE_ALIAS_GATE_PROPERTY=
        "spk.local.experimentalRejectSameJvmAccountInodeAlias";


    // G21.208: cooperating, in-JVM RAW repository.save() reservations.
    // Only the opt-in FilePlayerRepository direct writer participates.
    // A reservation blocks a new opt-in lease until the RAW write
    // finishes, and an existing lease denies a new RAW reservation.
    // G21.216: optional SAME OS sibling lock as cooperating sessions
    // when cross-JVM opt-in enabled; still no durable epoch or raw
    // writer-completeness/OS-lock-path-substitution guarantee.
    private static final Map<Path,Integer> rawWriters=new HashMap<>();
    // R32 opt-in, observed existing-inode identity retained for the
    // reservation's full lifetime, including cross-JVM OS lock latency.
    // Absent bootstrap files have no fileKey and remain path-guarded.
    private static final Map<Path,Object>
        rawWriterAccountFileKeys=new HashMap<>();

    /** Caller holds active monitor. Raw versus pending/live World owners. */
    private static void requireNoRawAliasAgainstSessions(
        Path path,Object fileKey
    )throws java.io.IOException{
        if(fileKey==null)return;
        for(Token other:active.values()){
            if(!path.equals(other.path)&&
               fileKey.equals(other.initialAccountFileKey))
                throw new java.io.IOException(
                    "R32 SAME_FILE_OBJECT_ALIAS_RAW_WRITE_DENIED");
        }
        for(Map.Entry<Path,Object> pending:
                pendingAccountFileKeys.entrySet()){
            if(!path.equals(pending.getKey())&&
               fileKey.equals(pending.getValue()))
                throw new java.io.IOException(
                    "R32 SAME_FILE_OBJECT_ALIAS_RAW_WRITE_DENIED");
        }
    }

    /** Caller holds active monitor. Raw writer versus other raw writers. */
    private static void requireNoRawAliasAgainstRaw(
        Path path,Object fileKey
    )throws java.io.IOException{
        if(fileKey==null)return;
        for(Map.Entry<Path,Object> other:
                rawWriterAccountFileKeys.entrySet()){
            if(!path.equals(other.getKey())&&
               fileKey.equals(other.getValue()))
                throw new java.io.IOException(
                    "R32 SAME_FILE_OBJECT_ALIAS_RAW_WRITE_DENIED");
        }
    }

    /** Caller holds active monitor. World admission versus raw writers. */
    private static void requireNoSessionAliasAgainstRaw(
        Path path,Object fileKey
    ){
        if(fileKey==null)return;
        for(Map.Entry<Path,Object> other:
                rawWriterAccountFileKeys.entrySet()){
            if(!path.equals(other.getKey())&&
               fileKey.equals(other.getValue()))
                throw new IllegalStateException(
                    "R32 SAME_FILE_OBJECT_ALIAS_SESSION_DENIED_RAW_WRITER");
        }
    }

    // R39: deterministic package-only seam after a cooperating RAW
    // filesystem move. No production caller configures this hook.
    @FunctionalInterface interface AfterRawPublicationProbe {
        void afterMove(Path account)throws java.io.IOException;
    }
    private static volatile AfterRawPublicationProbe
        afterRawPublicationProbeForTest;
    static void setAfterRawPublicationProbeForTest(
        AfterRawPublicationProbe probe
    ){ afterRawPublicationProbeForTest=probe; }

    static final class RawWriteReservation implements AutoCloseable {
        private final Path path;
        private final LocalCrossJvmAccountSessionLock.Handle processLock;
        private final boolean inodeAliasGuardEnabled;
        private final Object originalAccountFileKey;
        private boolean closed;
        private RawWriteReservation(
            Path path,LocalCrossJvmAccountSessionLock.Handle processLock,
            boolean inodeAliasGuardEnabled,Object originalAccountFileKey
        ){
            this.path=path;
            this.processLock=processLock;
            this.inodeAliasGuardEnabled=inodeAliasGuardEnabled;
            this.originalAccountFileKey=originalAccountFileKey;
        }

        // R33: a RAW reservation is for the observed account object,
        // not permission to overwrite a successor inode at the same
        // pathname. Compare before the final move. No external CAS or
        // stable cross-JVM file object ownership is implied.
        void requireOriginalAccountObjectBeforePublication()
            throws java.io.IOException{
            if(!inodeAliasGuardEnabled)return;
            final Object current=existingAccountFileKey(path);
            if(!Objects.equals(originalAccountFileKey,current))
                throw new java.io.IOException(
                    "R33_RAW_ACCOUNT_OBJECT_CHANGED_BEFORE_PUBLICATION");
        }

        // R39: the FINAL negative World-save witness is a separate,
        // persistent veto from ACTIVE. A cooperating, opted-in RAW
        // writer must not publish over it, including when the current
        // witness-creation feature flag has been turned OFF.
        void requireNoFinalNegativeWitnessBeforePublication()
            throws java.io.IOException{
            LocalFinalSaveNegativeWitness.requireNoNegativeWitness(path);
        }

        void requireNoFinalNegativeWitnessAfterPublication()
            throws java.io.IOException{
            try{
                // The deterministic hook is useful for proving that
                // a newly arrived post-move witness produces UNKNOWN,
                // rather than a successful RAW writer receipt.
                AfterRawPublicationProbe probe=afterRawPublicationProbeForTest;
                if(probe!=null)probe.afterMove(path);
                requireNoFinalNegativeWitnessBeforePublication();
            }catch(java.io.IOException uncertain){
                // The filesystem move MAY already have committed new
                // bytes. Do not claim a rollback or normal RAW ACK.
                throw new java.io.IOException(
                    "R39_RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW",
                    uncertain);
            }
        }

        // G21.244: best-effort opt-in last-chance RAW lock object check.
        void requireLockObjectContinuity()throws java.io.IOException{
            if(processLock!=null)
                processLock.requireOriginalLockNameStillPresent();
        }

        @Override public synchronized void close()
            throws java.io.IOException{
            if(closed)return;
            synchronized(active){
                Integer count=rawWriters.get(path);
                if(count==null||count!=1)
                    throw new IllegalStateException(
                        "G21.208 RAW_RESERVATION_ACCOUNTING_INVALID");
            }
            // G21.216: perform OS close OUTSIDE the shared JVM
            // account monitor. Keep the in-JVM reservation in place
            // until OS release succeeds. Failed release leaves a
            // negative local reservation, never a silent handoff.
            if(processLock!=null)processLock.close();
            synchronized(active){
                Integer count=rawWriters.get(path);
                if(count==null||count!=1)
                    throw new IllegalStateException(
                        "G21.216 RAW_RESERVATION_CHANGED_DURING_RELEASE");
                rawWriters.remove(path);
                rawWriterAccountFileKeys.remove(path);
                closed=true;
            }
        }
    }

    /**
     * Reserve the same normalized account file before RAW temp I/O.
     * With the cross-JVM opt-in, also acquire the G21.185 OS session
     * lock. Do not hold the JVM-wide account monitor over filesystem
     * I/O: the in-memory reservation excludes local competitors while
     * the OS lock attempt is pending.
     */
    static RawWriteReservation reserveRawWrite(
        Path exactAccountPath,String account
    )throws java.io.IOException{
        Objects.requireNonNull(exactAccountPath,"exactAccountPath");
        // R40: disabling RAW participation while a same-JVM World
        // token, pending session or RAW writer is ALREADY reserved
        // cannot retroactively erase that reservation. Preserve the
        // original fully unleased/default-off path unchanged; reject
        // a late flag toggle for the exact held account BEFORE temp
        // or parent-directory mutation. A hostile external process
        // is not excluded by this JVM-only negative admission.
        if(!Boolean.getBoolean(RAW_WRITE_GATE_PROPERTY)){
            Path unchecked=exactAccountPath.toAbsolutePath().normalize();
            boolean checkPhysicalAlias;
            synchronized(active){
                if(active.containsKey(unchecked)||
                   pendingSessionAdmissions.contains(unchecked)||
                   rawWriters.containsKey(unchecked))
                    throw new java.io.IOException(
                        "R40_RAW_OPT_OUT_DURING_ACCOUNT_OWNERSHIP_DENIED");
                checkPhysicalAlias=Boolean.getBoolean(
                    INODE_ALIAS_GATE_PROPERTY)&&
                    (!active.isEmpty()||
                     !pendingSessionAdmissions.isEmpty()||
                     !rawWriters.isEmpty());
            }
            if(checkPhysicalAlias){
                // R42: an account already reserved through filename A
                // may also be named by hardlink B. The late flag-off
                // RAW path must not bypass the selected opt-in inode
                // alias guard just because B is a different path.
                // Observe the filesystem OUTSIDE the global monitor.
                final Object fileKey;
                try{
                    fileKey=existingAccountFileKey(unchecked);
                }catch(java.io.IOException unsafe){
                    throw new java.io.IOException(
                        "R42_RAW_OPT_OUT_ALIAS_PRECHECK_FAILED",unsafe);
                }
                synchronized(active){
                    if(active.containsKey(unchecked)||
                       pendingSessionAdmissions.contains(unchecked)||
                       rawWriters.containsKey(unchecked))
                        throw new java.io.IOException(
                            "R40_RAW_OPT_OUT_DURING_ACCOUNT_OWNERSHIP_DENIED");
                    requireNoRawAliasAgainstSessions(unchecked,fileKey);
                    requireNoRawAliasAgainstRaw(unchecked,fileKey);
                }
            }
            return null;
        }
        Path file=exactAccountPath.toAbsolutePath().normalize();
        requireNonHardlinkedAccount(file);
        // R39: do not allocate even a legacy fixed .tmp or register
        // an opt-in RAW reservation for a quarantined exact account.
        // Presence is negative regardless of producer opt-in status.
        LocalFinalSaveNegativeWitness.requireNoNegativeWitness(file);
        final boolean aliasGuard=Boolean.getBoolean(
            INODE_ALIAS_GATE_PROPERTY);
        final Object initialAccountFileKey;
        if(aliasGuard){
            try{
                initialAccountFileKey=existingAccountFileKey(file);
            }catch(java.io.IOException unsafe){
                throw new java.io.IOException(
                    "R32 RAW_ACCOUNT_INODE_PRECHECK_FAILED",unsafe);
            }
        }else initialAccountFileKey=null;
        final boolean crossJvm=Boolean.getBoolean(
            LocalCrossJvmAccountSessionLock.PROPERTY);
        if(crossJvm&&!Boolean.getBoolean(PROPERTY))
            throw new java.io.IOException(
                "G21.216 CROSS_JVM_RAW_REQUIRES_JVM_LEASE_OPT_IN");
        synchronized(active){
            if(active.containsKey(file)||
               pendingSessionAdmissions.contains(file))
                throw new java.io.IOException(
                    "G21.208 RAW_WRITE_DENIED_ACTIVE_JVM_LEASE"+
                    " account="+account);
            if(rawWriters.containsKey(file))
                throw new java.io.IOException(
                    "G21.208 RAW_WRITE_DENIED_ANOTHER_RAW_WRITER"+
                    " account="+account);
            if(aliasGuard){
                requireNoRawAliasAgainstSessions(
                    file,initialAccountFileKey);
                requireNoRawAliasAgainstRaw(
                    file,initialAccountFileKey);
            }
            // Local competing sessions/raw writers are now excluded
            // BEFORE any cross-process filesystem lock work begins.
            rawWriters.put(file,1);
            if(aliasGuard&&initialAccountFileKey!=null)
                rawWriterAccountFileKeys.put(
                    file,initialAccountFileKey);
        }
        LocalCrossJvmAccountSessionLock.Handle processLock=null;
        try{
            if(crossJvm){
                try{
                    processLock=LocalCrossJvmAccountSessionLock.acquire(
                        file);
                }catch(java.io.IOException conflict){
                    throw new java.io.IOException(
                        "G21.216 CROSS_JVM_RAW_ACCOUNT_WRITE_DENIED"+
                        " account="+account,conflict);
                }
            }
            if(aliasGuard){
                // Revalidate after potentially blocking cross-JVM I/O.
                // Keep the RAW reservation published until all checks
                // have passed or the OS lock has been safely closed.
                Object currentKey=existingAccountFileKey(file);
                if(!Objects.equals(initialAccountFileKey,currentKey))
                    throw new java.io.IOException(
                        "R32 RAW_ACCOUNT_INODE_CHANGED_DURING_ADMISSION");
                synchronized(active){
                    requireNoRawAliasAgainstSessions(
                        file,initialAccountFileKey);
                    requireNoRawAliasAgainstRaw(
                        file,initialAccountFileKey);
                }
            }
            // Recheck after potentially blocking for a cross-process
            // OS lock, while our same-JVM RAW reservation remains held.
            LocalFinalSaveNegativeWitness.requireNoNegativeWitness(file);
            return new RawWriteReservation(
                file,processLock,aliasGuard,initialAccountFileKey);
        }catch(java.io.IOException|RuntimeException failure){
            // A failed post-lock inode check must close the OS handle
            // BEFORE clearing the JVM reservation. Uncertain close
            // retains negative reservation, not a false new writer.
            if(processLock!=null){
                try{
                    processLock.close();
                }catch(java.io.IOException uncertain){
                    failure.addSuppressed(uncertain);
                    throw new IllegalStateException(
                        "R32 RAW_ALIAS_OS_LOCK_RELEASE_UNCERTAIN",
                        failure);
                }
            }
            synchronized(active){
                rawWriters.remove(file);
                rawWriterAccountFileKeys.remove(file);
            }
            throw failure;
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
    // G21.218: SAME-file JVM admissions remain reserved while G21.185
    // FileChannel.open/tryLock runs OUTSIDE the global account monitor.
    // Every other path can progress independently during OS I/O.
    private static final java.util.Set<Path> pendingSessionAdmissions=
        new java.util.HashSet<>();
    // Captured outside the global lock before pending claim, to deny
    // competing aliases to an existing physical account file object.
    private static final Map<Path,Object> pendingAccountFileKeys=
        new HashMap<>();

    private static Object existingAccountFileKey(Path path)
        throws java.io.IOException{
        // R34: NOFOLLOW on the leaf alone still follows a symlinked
        // parent or grandparent. Reuse G21.226 for opt-in RAW/World
        // inode identity, including an absent bootstrap account leaf.
        LocalCrossJvmAccountSessionLock.requireNoSymlinkAncestors(
            path.toAbsolutePath().normalize().getParent());
        final java.nio.file.attribute.BasicFileAttributes attrs;
        try{
            attrs=java.nio.file.Files.readAttributes(
                path,java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        }catch(java.nio.file.NoSuchFileException absent){
            return null; // bootstrap file has no inode yet
        }
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
           attrs.fileKey()==null)
            throw new java.io.IOException(
                "G21.228 ACCOUNT_FILE_OBJECT_UNSAFE");
        return attrs.fileKey();
    }

    // Always invoked with the global JVM account monitor held. No I/O.
    private static void requireNoActiveAccountInodeAlias(
        Path path,Object fileKey
    ){
        if(fileKey==null)return;
        for(Token other:active.values()){
            if(!path.equals(other.path)&&
               fileKey.equals(other.initialAccountFileKey))
                throw new IllegalStateException(
                    "G21.228 SAME_FILE_OBJECT_ALIAS_SESSION_DENIED");
        }
        for(Map.Entry<Path,Object> pending:
                pendingAccountFileKeys.entrySet()){
            if(!path.equals(pending.getKey())&&
               fileKey.equals(pending.getValue()))
                throw new IllegalStateException(
                    "G21.228 SAME_FILE_OBJECT_ALIAS_SESSION_DENIED");
        }
    }


    // Deterministic synchronous TEST-ONLY seam. A production process
    // leaves it null; it provides no admission or rollback authority.
    interface PendingSessionAdmissionProbe {
        void afterPending(Path exactFile,String username);
    }
    private static volatile PendingSessionAdmissionProbe pendingProbe;
    static void pendingSessionAdmissionProbeForTest(
        PendingSessionAdmissionProbe hook
    ){ pendingProbe=hook; }
    static int pendingSessionAdmissionsForTest(){
        synchronized(active){return pendingSessionAdmissions.size();}
    }

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
        private final Object initialAccountFileKey;
        private boolean released;
        // G21.200: JVM-only identity binding, not a durable epoch.
        // Never serialized, exported as a disk receipt, or used to
        // authorize G21.188 ACTIVE retirement.
        private WorldPlayer admittedWorldOwner;
        private long admittedWorldGeneration;

        private Token(
            Path path,String account,
            LocalCrossJvmAccountSessionLock.Handle processLock,
            Object initialAccountFileKey
        ){
            this.path=path;
            this.account=account;
            this.processLock=processLock;
            this.initialAccountFileKey=initialAccountFileKey;
        }

        /**
         * G21.221 optional observed lock-name continuity, never a
         * durable owner epoch. Filesystem I/O must NOT happen inside
         * synchronized(active), or one swapped account lock can stall
         * unrelated account admission. One observation is inherently
         * racy against a subsequent hostile rename/unlink.
         */
        private void requireObservedLockContinuity(){
            if(processLock==null)return;
            try{
                processLock.requireOriginalLockNameStillPresent();
            }catch(java.io.IOException changed){
                throw new IllegalStateException(
                    "G21.221 SESSION_LOCK_OBJECT_CHANGED_NO_PUBLICATION",
                    changed);
            }
        }

        /** Exact resolved file identity guarded by this token. */
        Path accountFileForReviewAdmission(){
            requireObservedLockContinuity();
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
            requireObservedLockContinuity();
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
            requireObservedLockContinuity();
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
            }
            // G21.218: Token's own synchronized monitor serializes
            // this release, while the SAME-file map entry remains
            // present across the OS close. Do not hold the global
            // account monitor over FileLock.release/channel.close.
            if(processLock!=null){
                try{
                    processLock.close();
                }catch(java.io.IOException failure){
                    // Fail closed: still present in active map.
                    throw new IllegalStateException(
                        "G21.185 ACCOUNT_SESSION_LOCK_RELEASE_UNCERTAIN"+
                        " account="+account,failure);
                }
            }
            synchronized(active){
                if(active.get(path)!=this)
                    throw new IllegalStateException(
                        "G21.218 SESSION_CHANGED_DURING_LOCK_RELEASE");
                active.remove(path);
                released=true;
                admittedWorldOwner=null;
                admittedWorldGeneration=0L;
            }
        }
    }

    static Token acquire(
        WorldPlayerPersistence persistence,String username
    ){
        final boolean optIn=Boolean.getBoolean(PROPERTY);
        final boolean crossJvm=Boolean.getBoolean(
            LocalCrossJvmAccountSessionLock.PROPERTY);
        if(crossJvm&&!optIn)
            throw new IllegalStateException(
                "G21.185 CROSS_JVM_LOCK_REQUIRES_JVM_LEASE");

        // Preserve default-off fast path ONLY if no opt-in admission
        // is committed OR pending. Live flag toggles are unsupported.
        if(!optIn){
            synchronized(active){
                // R41: a previously admitted RAW reservation does not
                // disappear merely because session-lease participation
                // is toggled off after RAW started. Still run the exact
                // account-path collision check in that unusual state.
                // If no owner exists, retain historical unleased mode.
                if(active.isEmpty()&&pendingSessionAdmissions.isEmpty()&&
                   rawWriters.isEmpty())
                    return null;
            }
        }
        Objects.requireNonNull(persistence,"persistence");
        Objects.requireNonNull(username,"username");
        Path file=persistence.fileBackedAccountPathForJvmSessionLease(
            username);
        if(file==null)return null;
        Path key=file.toAbsolutePath().normalize();
        if(optIn){
            try{
                requireNonHardlinkedAccount(key);
            }catch(java.io.IOException invalid){
                throw new IllegalStateException(
                    "G21.230 HARDLINK_ACCOUNT_ADMISSION_DENIED",
                    invalid);
            }
        }
        // R42: when opt-in is toggled OFF after another RAW/World
        // claimant reserved an inode, the disabled-path check must
        // still consult the separately enabled inode-alias guard.
        // The wholly idle default-off fast path returned above.
        final boolean aliasGuard=
            Boolean.getBoolean(INODE_ALIAS_GATE_PROPERTY);
        final Object initialAccountFileKey;
        if(aliasGuard){
            try{
                initialAccountFileKey=existingAccountFileKey(key);
            }catch(java.io.IOException invalid){
                throw new IllegalStateException(
                    "G21.228 ACCOUNT_FILE_OBJECT_PRECHECK_FAILED",
                    invalid);
            }
        }else{
            initialAccountFileKey=null;
        }
        synchronized(active){
            if(rawWriters.containsKey(key))
                throw new IllegalStateException(
                    (optIn
                        ?"G21.208 JVM_LEASE_DENIED_RAW_WRITE_IN_FLIGHT"
                        :"R41_UNLEASED_SESSION_DENIED_HELD_RAW_WRITER")+
                    " account="+username);
            if(active.containsKey(key)||
               pendingSessionAdmissions.contains(key))
                throw new IllegalStateException(
                    "G21.181 JVM_ACCOUNT_SESSION_OWNED_NO_RECOVERY"+
                    " account="+username+" path="+key);
            if(aliasGuard){
                requireNoActiveAccountInodeAlias(
                    key,initialAccountFileKey);
                requireNoSessionAliasAgainstRaw(
                    key,initialAccountFileKey);
            }
            if(!optIn)return null;
            // Reserve same-file alias identity before OS I/O.
            pendingSessionAdmissions.add(key);
            if(aliasGuard&&initialAccountFileKey!=null)
                pendingAccountFileKeys.put(key,initialAccountFileKey);
        }

        LocalCrossJvmAccountSessionLock.Handle processLock=null;
        boolean installed=false;
        try{
            PendingSessionAdmissionProbe probe=pendingProbe;
            if(probe!=null)probe.afterPending(key,username);
            if(crossJvm){
                try{
                    processLock=LocalCrossJvmAccountSessionLock.acquire(
                        key);
                }catch(java.io.IOException failure){
                    throw new IllegalStateException(
                        "G21.185 CROSS_JVM_ACCOUNT_SESSION_DENIED"+
                        " account="+username,failure);
                }
            }
            // Metadata may have changed during the OS lock attempt.
            // Read OUTSIDE the JVM monitor and reject inconclusive
            // identity, never silently grant the alias permission.
            if(aliasGuard){
                try{
                    Object currentKey=existingAccountFileKey(key);
                    if(!Objects.equals(
                            initialAccountFileKey,currentKey))
                        throw new IllegalStateException(
                            "G21.228 ACCOUNT_FILE_OBJECT_CHANGED_DURING_ADMISSION");
                }catch(java.io.IOException changed){
                    throw new IllegalStateException(
                        "G21.228 ACCOUNT_FILE_OBJECT_RECHECK_FAILED",
                        changed);
                }
            }
            synchronized(active){
                if(aliasGuard){
                    requireNoActiveAccountInodeAlias(
                        key,initialAccountFileKey);
                    requireNoSessionAliasAgainstRaw(
                        key,initialAccountFileKey);
                }
                if(!pendingSessionAdmissions.contains(key)||
                   active.containsKey(key)||rawWriters.containsKey(key))
                    throw new IllegalStateException(
                        "G21.218 PENDING_SESSION_ADMISSION_REPLACED");
                Token token=new Token(
                    key,username,processLock,initialAccountFileKey);
                active.put(key,token);
                pendingSessionAdmissions.remove(key);
                pendingAccountFileKeys.remove(key);
                installed=true;
                return token;
            }
        }finally{
            if(!installed){
                // No new admission can win while a potentially acquired
                // OS lock is still held. Failed close must retain the
                // negative pending reservation, never falsely grant.
                boolean releasedOs=true;
                if(processLock!=null){
                    try{
                        processLock.close();
                    }catch(java.io.IOException failure){
                        releasedOs=false;
                        throw new IllegalStateException(
                            "G21.218 PENDING_OS_SESSION_RELEASE_UNCERTAIN"+
                            " account="+username,failure);
                    }finally{
                        if(releasedOs){
                            synchronized(active){
                                pendingSessionAdmissions.remove(key);
                                pendingAccountFileKeys.remove(key);
                            }
                        }
                    }
                }else{
                    synchronized(active){
                        pendingSessionAdmissions.remove(key);
                        pendingAccountFileKeys.remove(key);
                    }
                }
            }
        }
    }

    /**
     * Test-only current-token witness for a live World owner.
     * No token/secret is returned and this is NOT a save or release API.
     */
    /**
     * G21.239: last-chance opt-in OS lock OBJECT observation under the
     * guarded World file-publication critical section. A captured
     * owner/generation witness has already been checked by the
     * WorldPlayerPersistence worker; this additional check does NOT
     * manufacture ownership or a durable epoch from the current map.
     * Read filesystem attributes with NO global active-map monitor.
     */
    static void requireCurrentLeaseLockAtWorldPublication(
        Path accountFile
    )throws java.io.IOException{
        if(!Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            return;
        Path key=Objects.requireNonNull(
            accountFile,"accountFile").toAbsolutePath().normalize();
        final Token token;
        synchronized(active){
            token=active.get(key);
        }
        if(token==null)return; // preserve unleased/default-off path
        try{
            synchronized(token){
                token.requireObservedLockContinuity();
            }
        }catch(IllegalStateException invalid){
            throw new java.io.IOException(
                "G21.239 WORLD_COMMIT_SESSION_LOCK_OBJECT_CHANGED"+
                " action=REJECT_WORLD_PUBLICATION",invalid);
        }
    }

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
