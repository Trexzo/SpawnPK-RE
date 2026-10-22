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

        // R48: preserve both R47 file identity and a bounded, NOFOLLOW
        // SHA-256 of the serialized temp, so in-place postmove changes
        // on an unchanged inode cannot earn a normal RAW save result.
        // The same opt-in inode gate controls both checks.
        static final class StagedRawAccountObject {
            final java.nio.file.attribute.BasicFileAttributes attributes;
            final byte[] sha256;
            StagedRawAccountObject(
                java.nio.file.attribute.BasicFileAttributes attributes,
                byte[] sha256
            ){
                this.attributes=attributes;
                this.sha256=sha256.clone();
            }
        }

        StagedRawAccountObject captureStagedRawAccountObject(Path temp)
            throws java.io.IOException{
            if(!inodeAliasGuardEnabled)return null;
            final java.nio.file.attribute.BasicFileAttributes staged=
                java.nio.file.Files.readAttributes(
                    temp,java.nio.file.attribute.BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if(!staged.isRegularFile()||staged.isSymbolicLink()||
                    staged.fileKey()==null)
                throw new java.io.IOException(
                    "R47_RAW_STAGED_ACCOUNT_OBJECT_UNPROVEN_NO_MOVE");
            return new StagedRawAccountObject(
                staged,LocalOptInRawTemp.seal(temp,staged));
        }

        void requirePublishedRawAccountObjectStillCurrent(
            StagedRawAccountObject evidence
        )throws java.io.IOException{
            if(evidence==null)return;
            final java.nio.file.attribute.BasicFileAttributes staged=
                evidence.attributes;
            if(staged==null)return;
            try{
                LocalCrossJvmAccountSessionLock.requireNoSymlinkAncestors(
                    path.getParent());
                final java.nio.file.attribute.BasicFileAttributes current;
                try{
                    current=java.nio.file.Files.readAttributes(
                        path,java.nio.file.attribute.BasicFileAttributes.class,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS);
                }catch(java.nio.file.NoSuchFileException removed){
                    // R47.1: an ABSENT postmove leaf is also a changed
                    // published account object, not a different class
                    // of save outcome. Retain the underlying IO cause.
                    throw new java.io.IOException(
                        "R47_RAW_POST_MOVE_ACCOUNT_OBJECT_CHANGED",removed);
                }
                if(!current.isRegularFile()||current.isSymbolicLink()||
                        current.fileKey()==null||
                        !Objects.equals(staged.fileKey(),current.fileKey())||
                        !Objects.equals(staged.creationTime(),
                            current.creationTime()))
                    throw new java.io.IOException(
                        "R47_RAW_POST_MOVE_ACCOUNT_OBJECT_CHANGED");
            }catch(java.io.IOException uncertain){
                // Move might already be committed. Never claim
                // rollback, successful save, or positive final ACK.
                throw new java.io.IOException(
                    "R47_RAW_POST_MOVE_ACCOUNT_OBJECT_UNCONFIRMED_MANUAL_REVIEW",
                    uncertain);
            }
        }

        // R48: same inode, creation-time, link-count and even original
        // mtime are insufficient. Verify exact bounded staged bytes
        // only AFTER R47's object continuity and the R39 witness check.
        void requirePublishedRawAccountBytesStillCurrent(
            StagedRawAccountObject evidence
        )throws java.io.IOException{
            if(evidence==null)return;
            try{
                LocalOptInRawTemp.requireSealed(
                    path,evidence.attributes,evidence.sha256);
            }catch(java.io.IOException drift){
                // Already-moved account bytes may be compromised:
                // UNKNOWN/manual review, never a claimed rollback.
                throw new java.io.IOException(
                    "R48_RAW_POST_MOVE_BYTES_UNCONFIRMED_MANUAL_REVIEW",
                    drift);
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
        // R53: default-off legacy RAW also refuses a retained negative
        // HEAD before creating temp, lock or opt-in reservation.
        LocalNegativeAccountHeadPrototype.requireNoPublishedHead(
            exactAccountPath);
        LocalReusableLabEvidenceNegativeGate
            .requireNoLegacyCollision(exactAccountPath);
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
        // R79: strictly local provenance of the successful R78
        // capture-bound LAB transition. Not a durable/replayable
        // owner token and never permission to release this lease.
        private LocalReusableAccountEpochLab.Observation
            capturedOwnedLabEpoch;
        // R82: original-token-only LAB pending provenance; not an
        // acknowledgment or authority to release/account handoff.
        private LocalReusableAccountEpochLab.Observation
            capturedPendingLabEpoch;
        // R86: count *starts* of authenticated World snapshot
        // captures. This in-JVM ordinal is not serialized and cannot
        // authorize a save, lease release or admission.
        private final java.util.concurrent.atomic.AtomicLong
            worldCaptureStartOrdinal=
                new java.util.concurrent.atomic.AtomicLong();
        private long pendingLabCaptureStartFence;

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
                capturedOwnedLabEpoch=null;
                capturedPendingLabEpoch=null;
                pendingLabCaptureStartFence=0L;
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

    /**
     * R65: capture a read-only observation of the ACTUAL admitted
     * WorldPlayer identity/generation while the original G21.185
     * FileLock is still held by its owning JVM session Token.
     *
     * This does NOT export the OS lock or token, write LAB records,
     * attest cross-process World identity, or give login/release/ACK
     * authority. Callers must never treat an earlier observation as
     * current once this call has returned.
     */
    static final class WorldOwnerLabObservation {
        final Path accountFile;
        final String username;
        final long observedWorldGeneration;
        final boolean admissionAuthorized=false;
        final boolean releaseAuthorized=false;
        private WorldOwnerLabObservation(
            Path selected,String account,long generation
        ){
            this.accountFile=selected;
            this.username=account;
            this.observedWorldGeneration=generation;
        }
    }

    @FunctionalInterface
    private interface LockedRealWorldObservation<T>{
        T collect(Path account,String username,long generation)
            throws java.io.IOException;
    }

    private static <T> T withRealWorldOwnerOriginalLockForLab(
        Path exactAccountFile,WorldPlayer owner,long generation,
        LockedRealWorldObservation<T> operation
    )throws java.io.IOException{
        Objects.requireNonNull(operation,"operation");
        Objects.requireNonNull(exactAccountFile,"exactAccountFile");
        final Path selected=exactAccountFile.toAbsolutePath().normalize();
        if(!Boolean.getBoolean(PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
               LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new java.io.IOException(
                "R65_REAL_WORLD_LOCK_OBSERVATION_REQUIRES_EXPLICIT_FLAGS");
        final Token token;
        synchronized(active){
            token=active.get(selected);
        }
        if(token==null)
            throw new java.io.IOException(
                "R65_NO_CURRENT_REAL_WORLD_SESSION_TOKEN");
        // Follow the existing Token -> active monitor order. Never
        // hold the global map while checking OS lock continuity.
        synchronized(token){
            try{
                token.requireAdmittedWorldOwner(owner,generation);
            }catch(IllegalStateException stale){
                throw new java.io.IOException(
                    "R65_REAL_WORLD_IDENTITY_OR_GENERATION_REJECTED",stale);
            }
            if(token.processLock==null)
                throw new java.io.IOException(
                    "R65_REAL_WORLD_SESSION_HAS_NO_OS_LOCK");
            return token.processLock.withHeldOriginalAccount(selected,()->{
                try{
                    token.requireAdmittedWorldOwner(owner,generation);
                }catch(IllegalStateException stale){
                    throw new java.io.IOException(
                        "R65_REAL_WORLD_OWNER_CHANGED_DURING_OBSERVATION",
                        stale);
                }
                // Negative admission evidence is checked under the
                // same original OS session lock. A real token cannot
                // turn a concurrent LAB orphan or failed-final witness
                // into a positive account observation.
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(selected);
                T result=operation.collect(
                    selected,token.account,generation);
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(selected);
                try{
                    token.requireAdmittedWorldOwner(owner,generation);
                }catch(IllegalStateException stale){
                    throw new java.io.IOException(
                        "R66_REAL_WORLD_OWNER_CHANGED_AFTER_OBSERVATION",
                        stale);
                }
                return result;
            });
        }
    }

    static WorldOwnerLabObservation observeRealWorldOwnerUnderOsLockForLab(
        Path exactAccountFile,WorldPlayer owner,long generation
    )throws java.io.IOException{
        return withRealWorldOwnerOriginalLockForLab(
            exactAccountFile,owner,generation,
            (path,name,actualGen)->new WorldOwnerLabObservation(
                path,name,actualGen));
    }

    /**
     * R66: one strictly read-only, bounded DOUBLE fingerprint of the
     * selected account object, while the genuine G21.200 World Token
     * owns the original G21.185 FileLock.
     *
     * Never authorize gameplay, publish/replay files, or interpret
     * observed bytes as an immutable snapshot after returning.
     * Cooperative exclusion is NOT hostile/uncooperative writer CAS.
     */
    static final class WorldAccountImageLabObservation {
        final Path accountFile;
        final String username;
        final long observedWorldGeneration;
        final int imageLength;
        final String imageSha256;
        final boolean admissionAuthorized=false;
        final boolean releaseAuthorized=false;
        private WorldAccountImageLabObservation(
            Path account,String user,long gen,int bytes,String digest
        ){
            accountFile=account;
            username=user;
            observedWorldGeneration=gen;
            imageLength=bytes;
            imageSha256=digest;
        }
    }

    @FunctionalInterface interface AccountImageObservationCutpoint {
        void afterFirstImageRead(Path account)throws java.io.IOException;
    }
    private static final class AccountImageFingerprint {
        final int length;
        final String digest;
        final Object fileKey;
        private AccountImageFingerprint(int n,String d,Object k){
            length=n;digest=d;fileKey=k;
        }
    }
    private static final int R66_MAX_OBSERVED_ACCOUNT_BYTES=8*1024*1024;

    private static AccountImageFingerprint readAccountImageForLab(Path path)
        throws java.io.IOException{
        LocalCrossJvmAccountSessionLock
            .requireNoSymlinkAncestors(path.getParent());
        final java.nio.file.attribute.BasicFileAttributes first;
        try{
            first=java.nio.file.Files.readAttributes(path,
                java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        }catch(java.nio.file.NoSuchFileException absent){
            throw new java.io.IOException(
                "R66_ACCOUNT_IMAGE_MISSING_NO_POSITIVE_PROOF",absent);
        }
        if(!first.isRegularFile()||first.isSymbolicLink()||
           first.fileKey()==null||first.size()<1||
           first.size()>R66_MAX_OBSERVED_ACCOUNT_BYTES)
            throw new java.io.IOException(
                "R66_UNSAFE_OR_OVERSIZED_ACCOUNT_IMAGE");
        final java.security.MessageDigest digest;
        try{
            digest=java.security.MessageDigest.getInstance("SHA-256");
        }catch(java.security.NoSuchAlgorithmException unavailable){
            throw new java.io.IOException(
                "R66_SHA256_UNAVAILABLE",unavailable);
        }
        java.nio.ByteBuffer buffer=java.nio.ByteBuffer.allocate(8192);
        int count=0;
        try(java.nio.channels.FileChannel channel=
                java.nio.channels.FileChannel.open(path,
                    java.nio.file.StandardOpenOption.READ,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)){
            while(true){
                buffer.clear();
                int n=channel.read(buffer);
                if(n<0)break;
                if(n==0)throw new java.io.IOException(
                    "R66_ACCOUNT_IMAGE_READ_STALLED");
                count+=n;
                if(count>R66_MAX_OBSERVED_ACCOUNT_BYTES)
                    throw new java.io.IOException(
                        "R66_ACCOUNT_IMAGE_GREW_DURING_READ");
                digest.update(buffer.array(),0,n);
            }
        }
        java.nio.file.attribute.BasicFileAttributes second=
            java.nio.file.Files.readAttributes(path,
                java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if(!second.isRegularFile()||second.isSymbolicLink()||
           second.fileKey()==null||
           !first.fileKey().equals(second.fileKey())||
           !first.creationTime().equals(second.creationTime())||
           !first.lastModifiedTime().equals(second.lastModifiedTime())||
           first.size()!=second.size()||count!=first.size())
            throw new java.io.IOException(
                "R66_ACCOUNT_IMAGE_FILE_OBJECT_CHANGED_DURING_READ");
        StringBuilder hash=new StringBuilder(64);
        for(byte value:digest.digest()){
            String digit=Integer.toHexString(value&255);
            if(digit.length()==1)hash.append('0');
            hash.append(digit);
        }
        return new AccountImageFingerprint(count,hash.toString(),
            second.fileKey());
    }

    static WorldAccountImageLabObservation
    observeRealWorldAccountImageUnderOsLockForLab(
        Path exactAccountFile,WorldPlayer owner,long generation
    )throws java.io.IOException{
        return observeRealWorldAccountImageUnderOsLockForLab(
            exactAccountFile,owner,generation,account->{});
    }

    /** Test-only deterministic mid-observation adversarial mutation. */
    static WorldAccountImageLabObservation
    observeRealWorldAccountImageUnderOsLockForLab(
        Path exactAccountFile,WorldPlayer owner,long generation,
        AccountImageObservationCutpoint cutpoint
    )throws java.io.IOException{
        Objects.requireNonNull(cutpoint,"cutpoint");
        return withRealWorldOwnerOriginalLockForLab(
            exactAccountFile,owner,generation,(path,name,gen)->{
                AccountImageFingerprint first=readAccountImageForLab(path);
                cutpoint.afterFirstImageRead(path);
                AccountImageFingerprint second=readAccountImageForLab(path);
                if(first.length!=second.length||
                   !first.digest.equals(second.digest)||
                   !first.fileKey.equals(second.fileKey))
                    throw new java.io.IOException(
                        "R66_ACCOUNT_IMAGE_CHANGED_DURING_REAL_OWNER_OBSERVATION");
                return new WorldAccountImageLabObservation(
                    path,name,gen,second.length,second.digest);
            });
    }

    /**
     * R67 LAB ONLY: prove that a particular immutable, already-captured
     * World-owned PlayerSnapshot is bound to the ORIGINAL G21.202
     * capture-time session Token, same WorldPlayer identity/generation,
     * selected account path and original held G21.185 OS lock.
     *
     * Unlike the registered-owner observation, this accepts the same
     * exact captured owner AFTER World unregisters while Token still
     * owns the lock, but it never authorizes a save/ACK/release.
     * No synthetic caller-created nonce can replace the actual witness.
     */
    static LocalCapturedWorldSnapshotFingerprintLab.Observation
    observeCapturedWorldSnapshotUnderOsLockForLab(
        Path exactAccountFile,WorldPlayer owner,long generation,
        PlayerSnapshot immutableSnapshot,SaveCaptureWitness witness
    )throws java.io.IOException{
        Objects.requireNonNull(exactAccountFile,"exactAccountFile");
        Objects.requireNonNull(immutableSnapshot,"immutableSnapshot");
        final Path selected=exactAccountFile.toAbsolutePath().normalize();
        if(!Boolean.getBoolean(PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
               LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new java.io.IOException(
                "R67_CAPTURED_PROOF_REQUIRES_ORIGINAL_SESSION_LOCK_FLAGS");
        if(witness==null||witness.priorUnleased||
           witness.originalLease==null||owner==null||
           witness.owner!=owner||witness.generation!=generation||
           !selected.equals(witness.path)||
           !witness.account.equals(immutableSnapshot.username()))
            throw new java.io.IOException(
                "R67_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
        final Token token;
        synchronized(active){
            token=active.get(selected);
        }
        if(token==null||token!=witness.originalLease)
            throw new java.io.IOException(
                "R67_CAPTURE_ORIGINAL_LEASE_RELEASED_OR_REPLACED");
        synchronized(token){
            try{
                token.requireCapturedSaveOwner(
                    owner,generation,immutableSnapshot.username());
            }catch(IllegalStateException wrong){
                throw new java.io.IOException(
                    "R67_CAPTURE_OWNER_GENERATION_STALE",wrong);
            }
            if(token.processLock==null)
                throw new java.io.IOException(
                    "R67_CAPTURE_MISSING_ORIGINAL_OS_LOCK");
            return token.processLock.withHeldOriginalAccount(selected,()->{
                // This tests provenance, NOT account-file bytes. A
                // pending World capture need not match the old disk
                // image until a genuinely successful final write.
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(selected);
                LocalCapturedWorldSnapshotFingerprintLab.Observation result=
                    LocalCapturedWorldSnapshotFingerprintLab.fingerprint(
                        selected,immutableSnapshot,generation);
                try{
                    token.requireCapturedSaveOwner(
                        owner,generation,immutableSnapshot.username());
                }catch(IllegalStateException stale){
                    throw new java.io.IOException(
                        "R67_CAPTURE_OWNER_CHANGED_DURING_FINGERPRINT",
                        stale);
                }
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(selected);
                return result;
            });
        }
    }

    /**
     * R68 STANDALONE, OPT-IN, PERMANENT NEGATIVE ONLY:
     *
     * Publish an immutable canonical captured World snapshot to the
     * already existing R50 negative HEAD protocol while the genuine
     * G21.202 capture-time Token still owns its ORIGINAL G21.185
     * cross-JVM FileLock. This deliberately does NOT continue/ACK
     * the real final save, release the session or authorize recovery.
     *
     * Once published, R53/R63 default-off legacy paths refuse the
     * account. Even an orphan forced object without HEAD is negative.
     * A subsequent real final-save attempt is expected to FAIL.
     * No automatic retirement, replay or promotion is available.
     */
    static final String CAPTURED_NEGATIVE_INTENT_LAB_PROPERTY=
        "spk.local.experimentalCapturedNegativeFinalIntentLab";

    static LocalNegativeAccountHeadPrototype.NegativeObservation
    publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot captured,SaveCaptureWitness witness
    )throws java.io.IOException{
        return publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
            accountFile,owner,generation,captured,witness,stage->{});
    }

    /** Package-only deterministic force/cutpoint forwarding to R50. */
    static LocalNegativeAccountHeadPrototype.NegativeObservation
    publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot captured,SaveCaptureWitness witness,
        LocalNegativeAccountHeadPrototype.Cutpoint cutpoint
    )throws java.io.IOException{
        if(!Boolean.getBoolean(CAPTURED_NEGATIVE_INTENT_LAB_PROPERTY))
            throw new java.io.IOException(
                "R68_CAPTURED_NEGATIVE_INTENT_LAB_DEFAULT_OFF");
        if(cutpoint==null)
            throw new java.io.IOException("R68_NULL_PUBLICATION_CUTPOINT");
        Objects.requireNonNull(accountFile,"accountFile");
        Objects.requireNonNull(captured,"captured");
        Path selected=accountFile.toAbsolutePath().normalize();
        if(!Boolean.getBoolean(PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new java.io.IOException(
                "R68_REQUIRES_ACTUAL_HELD_SESSION_OS_LOCK");
        if(witness==null||witness.priorUnleased||
           witness.originalLease==null||owner==null||
           witness.owner!=owner||witness.generation!=generation||
           !selected.equals(witness.path)||
           !witness.account.equals(captured.username()))
            throw new java.io.IOException(
                "R68_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
        final Token token;
        synchronized(active){token=active.get(selected);}
        if(token==null||token!=witness.originalLease)
            throw new java.io.IOException(
                "R68_ORIGINAL_SESSION_TOKEN_RELEASED_OR_REPLACED");
        synchronized(token){
            try{
                token.requireCapturedSaveOwner(
                    owner,generation,captured.username());
            }catch(IllegalStateException incorrect){
                throw new java.io.IOException(
                    "R68_CAPTURED_WORLD_OWNER_STALE",incorrect);
            }
            if(token.processLock==null)
                throw new java.io.IOException(
                    "R68_CAPTURED_OWNER_MISSING_ORIGINAL_OS_LOCK");
            return token.processLock.withHeldOriginalAccount(selected,()->{
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(selected);
                byte[] canonical=
                    LocalCapturedWorldSnapshotFingerprintLab
                        .canonicalBytes(selected,captured,generation);
                LocalCapturedWorldSnapshotFingerprintLab.Observation
                    fingerprint=LocalCapturedWorldSnapshotFingerprintLab
                        .fingerprint(selected,captured,generation);
                final String canonicalDigest;
                try{
                    byte[] sha=java.security.MessageDigest
                        .getInstance("SHA-256").digest(canonical);
                    StringBuilder hex=new StringBuilder(64);
                    for(byte value:sha){
                        String nibble=Integer.toHexString(value&255);
                        if(nibble.length()==1)hex.append('0');
                        hex.append(nibble);
                    }
                    canonicalDigest=hex.toString();
                }catch(java.security.NoSuchAlgorithmException missing){
                    throw new java.io.IOException(
                        "R68_SHA256_UNAVAILABLE",missing);
                }
                if(canonical.length!=fingerprint.canonicalBytes||
                   !canonicalDigest.equals(fingerprint.canonicalSha256))
                    throw new java.io.IOException(
                        "R68_CAPTURE_CANONICAL_DIGEST_MISMATCH");
                // The R50 publisher has NO nested OS lock acquisition.
                // The actual session's Token retains the sole FileLock.
                LocalNegativeAccountHeadPrototype.NegativeObservation
                    head=LocalNegativeAccountHeadPrototype.publishNegative(
                        selected,captured.username(),generation,
                        canonical,cutpoint);
                if(!head.snapshotSha256.equals(canonicalDigest))
                    throw new java.io.IOException(
                        "R68_FORCED_NEGATIVE_HEAD_DIGEST_UNCERTAIN");
                try{
                    token.requireCapturedSaveOwner(
                        owner,generation,captured.username());
                }catch(IllegalStateException stale){
                    throw new java.io.IOException(
                        "R68_CAPTURE_OWNER_CHANGED_AFTER_NEGATIVE_INTENT",
                        stale);
                }
                return head;
            });
        }
    }

    /**
     * R76 STANDALONE, EXPLICIT OPT-IN, PERMANENT NEGATIVE LAB ONLY:
     * initialize the R57 immutable epoch prototype with the canonical
     * original G21.202 World capture while borrowing that very Token's
     * existing G21.185 OS FileLock from genesis onward.
     *
     * The R57 ledger is NOT a live final-save protocol: CLOSED cannot
     * authorize a login, a World handoff, release or replay. Publishing
     * this LAB genesis deliberately strands a NEGATIVE admission signal
     * and may cause the original final save to fail. Never activate
     * this method during ordinary session teardown.
     */
    static final String CAPTURED_EPOCH_GENESIS_LAB_PROPERTY=
        "spk.local.experimentalCapturedEpochGenesisLab";

    static LocalReusableAccountEpochLab.Observation
    initializeCapturedEpochGenesisUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot captured,SaveCaptureWitness witness
    )throws java.io.IOException{
        return initializeCapturedEpochGenesisUnderOriginalLockForLab(
            accountFile,owner,generation,captured,witness,stage->{});
    }

    /** R77 package-only force/cutpoint forwarding, no admission. */
    static LocalReusableAccountEpochLab.Observation
    initializeCapturedEpochGenesisUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot captured,SaveCaptureWitness witness,
        LocalReusableAccountEpochLab.Cutpoint cutpoint
    )throws java.io.IOException{
        if(cutpoint==null)
            throw new java.io.IOException("R77_NULL_CAPTURE_GENESIS_CUTPOINT");
        if(!Boolean.getBoolean(CAPTURED_EPOCH_GENESIS_LAB_PROPERTY))
            throw new java.io.IOException(
                "R76_CAPTURED_EPOCH_GENESIS_LAB_DEFAULT_OFF");
        Objects.requireNonNull(accountFile,"accountFile");
        Objects.requireNonNull(captured,"captured");
        Path selected=accountFile.toAbsolutePath().normalize();
        if(!Boolean.getBoolean(PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
               LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new java.io.IOException(
                "R76_REQUIRES_ACTUAL_SESSION_ORIGINAL_OS_LOCK");
        // R85: owner/generation provenance alone cannot attest a
        // caller-selected snapshot. Preserve the World-captured
        // object's identity end to end, even when account matches.
        if(witness!=null&&witness.capturedSnapshot!=captured)
            throw new java.io.IOException(
                "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
        if(witness==null||witness.priorUnleased||
           witness.originalLease==null||owner==null||
           witness.owner!=owner||witness.generation!=generation||
           !selected.equals(witness.path)||
           !witness.account.equals(captured.username()))
            throw new java.io.IOException(
                "R76_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
        final Token token;
        synchronized(active){token=active.get(selected);}
        if(token==null||token!=witness.originalLease)
            throw new java.io.IOException(
                "R76_ORIGINAL_SESSION_TOKEN_RELEASED_OR_REPLACED");
        synchronized(token){
            try{
                token.requireCapturedSaveOwner(
                    owner,generation,captured.username());
            }catch(IllegalStateException invalid){
                throw new java.io.IOException(
                    "R76_CAPTURE_OWNER_GENERATION_STALE",invalid);
            }
            if(token.processLock==null)
                throw new java.io.IOException(
                    "R76_CAPTURED_OWNER_MISSING_ORIGINAL_OS_LOCK");
            return token.processLock.withHeldOriginalAccount(selected,()->{
                // Inspect before any persistent LAB write, while still
                // under the sole original cooperating OS lock.
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(selected);
                byte[] canonical=
                    LocalCapturedWorldSnapshotFingerprintLab
                        .canonicalBytes(selected,captured,generation);
                LocalCapturedWorldSnapshotFingerprintLab.Observation proof=
                    LocalCapturedWorldSnapshotFingerprintLab
                        .fingerprint(selected,captured,generation);
                LocalReusableAccountEpochLab.Observation ledger=
                    LocalReusableAccountEpochLab
                        .initializeUnderHeldAccountLock(
                            selected,token.processLock,canonical,cutpoint);
                if(ledger.sequence!=0||
                   ledger.phase!=LocalReusableAccountEpochLab.Phase.CLOSED||
                   ledger.imageLength!=proof.canonicalBytes||
                   !ledger.imageSha.equals(proof.canonicalSha256)||
                   ledger.admissionAuthorized||ledger.releaseAuthorized)
                    throw new java.io.IOException(
                        "R76_CAPTURED_LEDGER_GENESIS_UNCERTAIN");
                try{
                    token.requireCapturedSaveOwner(
                        owner,generation,captured.username());
                }catch(IllegalStateException stale){
                    throw new java.io.IOException(
                        "R76_WORLD_OWNER_CHANGED_AFTER_GENESIS",stale);
                }
                // The newborn LAB evidence is intentionally a
                // permanent NEGATIVE for default-off legacy admission.
                return ledger;
            });
        }
    }

    /**
     * R78 STANDALONE NEGATIVE-ONLY: associate an existing, exactly
     * captured R76 ledger genesis with its genuine World owner under
     * the same original G21.185 lock. The fresh owner nonce is
     * generated internally; neither a caller-provided nonce nor a
     * guessed World generation can be used to claim the ledger here.
     *
     * OWNED is merely an immutable experimental audit phase. It
     * never gives permission to admit, publish a real final save,
     * acknowledge final completion, release, replay or recover.
     * Both the R76 genesis and this transition are explicit opt-ins.
     */
    static final String CAPTURED_EPOCH_CLAIM_LAB_PROPERTY=
        "spk.local.experimentalCapturedEpochClaimLab";

    static LocalReusableAccountEpochLab.Observation
    claimCapturedEpochUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot captured,SaveCaptureWitness witness
    )throws java.io.IOException{
        if(!Boolean.getBoolean(CAPTURED_EPOCH_CLAIM_LAB_PROPERTY)||
           !Boolean.getBoolean(CAPTURED_EPOCH_GENESIS_LAB_PROPERTY))
            throw new java.io.IOException(
                "R78_CAPTURED_EPOCH_CLAIM_LAB_DEFAULT_OFF");
        Objects.requireNonNull(accountFile,"accountFile");
        Objects.requireNonNull(captured,"captured");
        Path selected=accountFile.toAbsolutePath().normalize();
        if(!Boolean.getBoolean(PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
               LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new java.io.IOException(
                "R78_REQUIRES_ACTUAL_SESSION_ORIGINAL_OS_LOCK");
        // R85: owner/generation provenance alone cannot attest a
        // caller-selected snapshot. Preserve the World-captured
        // object's identity end to end, even when account matches.
        if(witness!=null&&witness.capturedSnapshot!=captured)
            throw new java.io.IOException(
                "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
        if(witness==null||witness.priorUnleased||
           witness.originalLease==null||owner==null||
           witness.owner!=owner||witness.generation!=generation||
           !selected.equals(witness.path)||
           !witness.account.equals(captured.username()))
            throw new java.io.IOException(
                "R78_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
        final Token token;
        synchronized(active){token=active.get(selected);}
        if(token==null||token!=witness.originalLease)
            throw new java.io.IOException(
                "R78_ORIGINAL_SESSION_TOKEN_RELEASED_OR_REPLACED");
        synchronized(token){
            try{
                token.requireCapturedSaveOwner(
                    owner,generation,captured.username());
            }catch(IllegalStateException invalid){
                throw new java.io.IOException(
                    "R78_CAPTURE_OWNER_GENERATION_STALE",invalid);
            }
            if(token.processLock==null)
                throw new java.io.IOException(
                    "R78_CAPTURED_OWNER_MISSING_ORIGINAL_OS_LOCK");
            return token.processLock.withHeldOriginalAccount(selected,()->{
                // The R76 ledger is intentionally NEGATIVE. Do not
                // invoke the default-off legacy admission gate here.
                LocalReusableAccountEpochLab.Observation genesis=
                    LocalReusableAccountEpochLab.inspect(selected);
                if(genesis.sequence!=0||genesis.epoch!=1||
                   genesis.phase!=LocalReusableAccountEpochLab.Phase.CLOSED)
                    throw new java.io.IOException(
                        "R78_GENESIS_NOT_CLOSED_OR_SEQUENCE_CHANGED");
                LocalCapturedWorldSnapshotFingerprintLab.Observation proof=
                    LocalCapturedWorldSnapshotFingerprintLab.fingerprint(
                        selected,captured,generation);
                if(genesis.imageLength!=proof.canonicalBytes||
                   !genesis.imageSha.equals(proof.canonicalSha256))
                    throw new java.io.IOException(
                        "R78_CAPTURE_NOT_LEDGER_GENESIS");
                // Distinct from the JVM generation: a random internal
                // negative-only nonce, not trusted external authority.
                String nonce="r78_"+
                    java.util.UUID.randomUUID().toString().replace("-","");
                LocalReusableAccountEpochLab.Observation owned=
                    LocalReusableAccountEpochLab.claimUnderHeldAccountLock(
                        selected,token.processLock,0,generation,nonce);
                if(owned.sequence!=1||owned.epoch!=2||
                   owned.phase!=LocalReusableAccountEpochLab.Phase.OWNED||
                   owned.worldGeneration!=generation||
                   !nonce.equals(owned.ownerNonce)||
                   owned.imageLength!=proof.canonicalBytes||
                   !owned.imageSha.equals(proof.canonicalSha256)||
                   owned.admissionAuthorized||owned.releaseAuthorized)
                    throw new java.io.IOException(
                        "R78_CAPTURED_OWNED_PHASE_UNCERTAIN");
                try{
                    token.requireCapturedSaveOwner(
                        owner,generation,captured.username());
                }catch(IllegalStateException stale){
                    throw new java.io.IOException(
                        "R78_WORLD_OWNER_CHANGED_AFTER_CLAIM",stale);
                }
                // Record only after exact forced/readback R78 claim.
                // A failure between publication and this assignment
                // leaves permanent negative evidence, not authority.
                if(token.capturedOwnedLabEpoch!=null)
                    throw new java.io.IOException(
                        "R79_CAPTURED_LAB_EPOCH_ALREADY_BOUND");
                token.capturedOwnedLabEpoch=owned;
                return owned;
            });
        }
    }

    /**
     * R79: one original authenticated R78 LAB OWNED observation may
     * advance to FINAL_PENDING while the SAME JVM Token and the
     * ORIGINAL cooperating G21.185 lock remain held.
     *
     * This is NOT the genuine World final-save path. It publishes no
     * account postimage, no successful final receipt, and never
     * permits owner release, successor admission, replay or recovery.
     * The R78 nonce cannot be re-supplied by an external caller.
     */
    static final String CAPTURED_EPOCH_PREPARE_LAB_PROPERTY=
        "spk.local.experimentalCapturedEpochPrepareLab";

    static LocalReusableAccountEpochLab.Observation
    prepareCapturedEpochUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot captured,SaveCaptureWitness witness
    )throws java.io.IOException{
        return prepareCapturedEpochUnderOriginalLockForLab(
            accountFile,owner,generation,captured,witness,stage->{});
    }

    /** R80 package-only process-kill cutpoint, NOT a final ACK. */
    static LocalReusableAccountEpochLab.Observation
    prepareCapturedEpochUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot captured,SaveCaptureWitness witness,
        LocalReusableAccountEpochLab.Cutpoint cutpoint
    )throws java.io.IOException{
        if(cutpoint==null)
            throw new java.io.IOException("R80_NULL_FINAL_PREPARE_CUTPOINT");
        if(!Boolean.getBoolean(CAPTURED_EPOCH_PREPARE_LAB_PROPERTY)||
           !Boolean.getBoolean(CAPTURED_EPOCH_CLAIM_LAB_PROPERTY)||
           !Boolean.getBoolean(CAPTURED_EPOCH_GENESIS_LAB_PROPERTY))
            throw new java.io.IOException(
                "R79_CAPTURED_EPOCH_PREPARE_LAB_DEFAULT_OFF");
        Objects.requireNonNull(accountFile,"accountFile");
        Objects.requireNonNull(captured,"captured");
        Path selected=accountFile.toAbsolutePath().normalize();
        if(!Boolean.getBoolean(PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
               LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new java.io.IOException(
                "R79_REQUIRES_ACTUAL_SESSION_ORIGINAL_OS_LOCK");
        // R85: owner/generation provenance alone cannot attest a
        // caller-selected snapshot. Preserve the World-captured
        // object's identity end to end, even when account matches.
        if(witness!=null&&witness.capturedSnapshot!=captured)
            throw new java.io.IOException(
                "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
        if(witness==null||witness.priorUnleased||
           witness.originalLease==null||owner==null||
           witness.owner!=owner||witness.generation!=generation||
           !selected.equals(witness.path)||
           !witness.account.equals(captured.username()))
            throw new java.io.IOException(
                "R79_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
        final Token token;
        synchronized(active){token=active.get(selected);}
        if(token==null||token!=witness.originalLease)
            throw new java.io.IOException(
                "R79_ORIGINAL_SESSION_TOKEN_RELEASED_OR_REPLACED");
        synchronized(token){
            try{
                token.requireCapturedSaveOwner(
                    owner,generation,captured.username());
            }catch(IllegalStateException invalid){
                throw new java.io.IOException(
                    "R79_CAPTURE_OWNER_GENERATION_STALE",invalid);
            }
            if(token.processLock==null)
                throw new java.io.IOException(
                    "R79_CAPTURED_OWNER_MISSING_ORIGINAL_OS_LOCK");
            LocalReusableAccountEpochLab.Observation remembered=
                token.capturedOwnedLabEpoch;
            if(remembered==null)
                throw new java.io.IOException(
                    "R79_NO_AUTHENTICATED_R78_OWNED_TOKEN");
            return token.processLock.withHeldOriginalAccount(selected,()->{
                LocalReusableAccountEpochLab.Observation disk=
                    LocalReusableAccountEpochLab.inspect(selected);
                if(disk.sequence!=remembered.sequence||
                   disk.epoch!=remembered.epoch||
                   disk.phase!=LocalReusableAccountEpochLab.Phase.OWNED||
                   disk.worldGeneration!=generation||
                   !disk.ownerNonce.equals(remembered.ownerNonce)||
                   !disk.imageSha.equals(remembered.imageSha)||
                   disk.imageLength!=remembered.imageLength)
                    throw new java.io.IOException(
                        "R79_OWNED_DISK_WITNESS_NOT_ORIGINAL_TOKEN");
                LocalCapturedWorldSnapshotFingerprintLab.Observation proof=
                    LocalCapturedWorldSnapshotFingerprintLab.fingerprint(
                        selected,captured,generation);
                if(proof.canonicalBytes!=remembered.imageLength||
                   !proof.canonicalSha256.equals(remembered.imageSha))
                    throw new java.io.IOException(
                        "R79_CAPTURE_NOT_ORIGINAL_OWNED_SNAPSHOT");
                LocalReusableAccountEpochLab.Observation pending=
                    LocalReusableAccountEpochLab
                        .prepareFinalUnderHeldAccountLock(
                            selected,token.processLock,
                            remembered.sequence,generation,
                            remembered.ownerNonce,cutpoint);
                if(pending.sequence!=2||pending.epoch!=remembered.epoch||
                   pending.phase!=LocalReusableAccountEpochLab
                       .Phase.FINAL_PENDING||
                   pending.worldGeneration!=generation||
                   !pending.ownerNonce.equals(remembered.ownerNonce)||
                   !pending.imageSha.equals(remembered.imageSha)||
                   pending.admissionAuthorized||pending.releaseAuthorized)
                    throw new java.io.IOException(
                        "R79_FINAL_PENDING_LAB_UNCERTAIN");
                try{
                    token.requireCapturedSaveOwner(
                        owner,generation,captured.username());
                }catch(IllegalStateException stale){
                    throw new java.io.IOException(
                        "R79_WORLD_OWNER_CHANGED_AFTER_PREPARE",stale);
                }
                // Token assignment happens only after the forced,
                // read-back lab transition. If interrupted before
                // assignment, the persistent LAB remains negative.
                if(token.capturedPendingLabEpoch!=null)
                    throw new java.io.IOException(
                        "R82_LAB_PENDING_ALREADY_BOUND");
                token.capturedPendingLabEpoch=pending;
                // A World snapshot whose capture started BEFORE
                // FINAL_PENDING became authoritative as LAB-only
                // evidence cannot be selected as its later postimage.
                token.pendingLabCaptureStartFence=
                    token.worldCaptureStartOrdinal.get();
                // Keep the original lock. No real final-save ACK.
                return pending;
            });
        }
    }

    /**
     * R82: complete only the DETACHED nonadmitting R57 ledger with
     * a later immutable World-captured domain snapshot (not legacy
     * account-file bytes). The original live World final save is
     * deliberately NOT performed by this method.
     *
     * This requires the real G21.202 capture Token, original held
     * G21.185 FileLock, exact R79 pending sequence/nonce and a
     * distinct subsequently captured image. A consistent CLOSED
     * ledger is still permanent R63 NEGATIVE evidence for all real
     * World admissions and saves: do not release, replay or recover.
     */
    static final String CAPTURED_EPOCH_SETTLE_LAB_PROPERTY=
        "spk.local.experimentalCapturedEpochSettlementLab";

    static LocalReusableAccountEpochLab.Observation
    settleCapturedEpochUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot finalCapture,SaveCaptureWitness finalWitness
    )throws java.io.IOException{
        return settleCapturedEpochUnderOriginalLockForLab(
            accountFile,owner,generation,finalCapture,finalWitness,stage->{});
    }

    /**
     * R83 package-only exact filesystem force cutpoint. It is
     * invoked under the original real World Token/OS lock and
     * cannot grant admission, final ACK or release authority.
     */
    static LocalReusableAccountEpochLab.Observation
    settleCapturedEpochUnderOriginalLockForLab(
        Path accountFile,WorldPlayer owner,long generation,
        PlayerSnapshot finalCapture,SaveCaptureWitness finalWitness,
        LocalReusableAccountEpochLab.Cutpoint cutpoint
    )throws java.io.IOException{
        if(cutpoint==null)
            throw new java.io.IOException("R83_NULL_SETTLEMENT_CUTPOINT");
        if(!Boolean.getBoolean(CAPTURED_EPOCH_SETTLE_LAB_PROPERTY)||
           !Boolean.getBoolean(CAPTURED_EPOCH_PREPARE_LAB_PROPERTY)||
           !Boolean.getBoolean(CAPTURED_EPOCH_CLAIM_LAB_PROPERTY)||
           !Boolean.getBoolean(CAPTURED_EPOCH_GENESIS_LAB_PROPERTY))
            throw new java.io.IOException(
                "R82_CAPTURED_EPOCH_SETTLEMENT_LAB_DEFAULT_OFF");
        Objects.requireNonNull(accountFile,"accountFile");
        Objects.requireNonNull(finalCapture,"finalCapture");
        final Path selected=accountFile.toAbsolutePath().normalize();
        if(!Boolean.getBoolean(PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
               LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new java.io.IOException(
                "R82_REQUIRES_ACTUAL_SESSION_ORIGINAL_OS_LOCK");
        // R85: owner/generation provenance alone cannot attest a
        // caller-selected snapshot. Preserve the World-captured
        // object's identity end to end, even when account matches.
        if(finalWitness!=null&&finalWitness.capturedSnapshot!=finalCapture)
            throw new java.io.IOException(
                "R85_CAPTURE_SNAPSHOT_IDENTITY_MISMATCH");
        if(finalWitness==null||finalWitness.priorUnleased||
           finalWitness.originalLease==null||owner==null||
           finalWitness.owner!=owner||
           finalWitness.generation!=generation||
           !selected.equals(finalWitness.path)||
           !finalWitness.account.equals(finalCapture.username()))
            throw new java.io.IOException(
                "R82_CAPTURE_WITNESS_NOT_ORIGINAL_OWNER");
        final Token token;
        synchronized(active){token=active.get(selected);}
        if(token==null||token!=finalWitness.originalLease)
            throw new java.io.IOException(
                "R82_ORIGINAL_SESSION_TOKEN_RELEASED_OR_REPLACED");
        synchronized(token){
            try{
                token.requireCapturedSaveOwner(
                    owner,generation,finalCapture.username());
            }catch(IllegalStateException invalid){
                throw new java.io.IOException(
                    "R82_CAPTURE_OWNER_GENERATION_STALE",invalid);
            }
            if(token.processLock==null)
                throw new java.io.IOException(
                    "R82_CAPTURED_OWNER_MISSING_ORIGINAL_OS_LOCK");
            LocalReusableAccountEpochLab.Observation remembered=
                token.capturedPendingLabEpoch;
            if(remembered==null)
                throw new java.io.IOException(
                    "R82_NO_ORIGINAL_AUTHENTICATED_PENDING_TOKEN");
            if(finalWitness.worldCaptureStartOrdinal<=0L||
               finalWitness.worldCaptureStartOrdinal<=
                   token.pendingLabCaptureStartFence)
                throw new java.io.IOException(
                    "R86_FINAL_CAPTURE_NOT_STARTED_AFTER_PREPARE");
            // R89: a normal deferred/checkpoint World capture may
            // be new and correctly attested, but it is not a genuine
            // deferred FINAL-disconnect capture. Neither grants
            // any real final-save ACK or lease release.
            if(!finalWitness.finalDisconnectCapture)
                throw new java.io.IOException(
                    "R89_SETTLEMENT_REQUIRES_FINAL_DISCONNECT_CAPTURE");
            // R90: final-disconnect capture alone is not proof
            // that a final-save FIFO reservation was admitted.
            // Require evidence observed from the existing real
            // persistence queue at capture time; this is LAB-only
            // provenance, NOT permission to publish or ACK.
            if(!finalWitness.finalReservationAtCapture)
                throw new java.io.IOException(
                    "R90_FINAL_CAPTURE_WITHOUT_ACTIVE_RESERVATION");
            return token.processLock.withHeldOriginalAccount(selected,()->{
                LocalReusableAccountEpochLab.Observation disk=
                    LocalReusableAccountEpochLab.inspect(selected);
                if(disk.sequence!=2||disk.sequence!=remembered.sequence||
                   disk.epoch!=remembered.epoch||
                   disk.phase!=LocalReusableAccountEpochLab
                       .Phase.FINAL_PENDING||
                   disk.worldGeneration!=generation||
                   !disk.ownerNonce.equals(remembered.ownerNonce)||
                   !disk.imageSha.equals(remembered.imageSha)||
                   disk.imageLength!=remembered.imageLength)
                    throw new java.io.IOException(
                        "R82_PENDING_DISK_NOT_ORIGINAL_TOKEN");
                LocalCapturedWorldSnapshotFingerprintLab.Observation proof=
                    LocalCapturedWorldSnapshotFingerprintLab.fingerprint(
                        selected,finalCapture,generation);
                if(proof.canonicalSha256.equals(remembered.imageSha))
                    throw new java.io.IOException(
                        "R82_FINAL_SNAPSHOT_NOT_DISTINCT_FROM_PENDING");
                byte[] image=LocalCapturedWorldSnapshotFingerprintLab
                    .canonicalBytes(selected,finalCapture,generation);
                LocalReusableAccountEpochLab.Observation closed=
                    LocalReusableAccountEpochLab
                        .settleLabFinalUnderHeldAccountLock(
                            selected,token.processLock,
                            remembered.sequence,generation,
                            remembered.ownerNonce,image,cutpoint);
                if(closed.sequence!=3||closed.epoch!=remembered.epoch||
                   closed.phase!=LocalReusableAccountEpochLab.Phase.CLOSED||
                   closed.worldGeneration!=0||
                   !"none".equals(closed.ownerNonce)||
                   !closed.imageSha.equals(proof.canonicalSha256)||
                   closed.imageLength!=proof.canonicalBytes||
                   closed.admissionAuthorized||closed.releaseAuthorized)
                    throw new java.io.IOException(
                        "R82_LAB_CLOSED_NOT_A_REAL_FINAL_RECEIPT");
                try{
                    token.requireCapturedSaveOwner(
                        owner,generation,finalCapture.username());
                }catch(IllegalStateException stale){
                    throw new java.io.IOException(
                        "R82_WORLD_OWNER_CHANGED_AFTER_SETTLEMENT",stale);
                }
                // Keep the JVM token and original OS lock held.
                // This is LAB history, NEVER real final-save ACK.
                return closed;
            });
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
     * R87: an opaque, single-use in-JVM capture-start ticket. A raw
     * ordinal supplied by another package caller is not provenance.
     * Only the original Token may mint this ticket, before snapshot
     * construction, and the exact capture witness consumes it once.
     *
     * This is NOT admission, a durable epoch or a writer capability.
     */
    static final class WorldCaptureStartForLab {
        private final Token originalLease;
        private final World captureWorld;
        private final Path path;
        private final String account;
        private final WorldPlayer owner;
        private final long generation;
        private final long ordinal;
        private boolean consumed;

        private WorldCaptureStartForLab(
            Token token,World world,Path path,String account,
            WorldPlayer owner,long generation,long ordinal
        ){
            this.originalLease=token;
            this.captureWorld=world;
            this.path=path;
            this.account=account;
            this.owner=owner;
            this.generation=generation;
            this.ordinal=ordinal;
        }
    }

    /**
     * R88: mint ONLY in the actual World execution context for the
     * registered owner/generation, before codec encoding. A caller
     * holding the correct Token identity on an unrelated thread
     * cannot manufacture a positive capture start. OFF for normal
     * gameplay; no Token monitor or filesystem I/O on the hotpath.
     */
    static WorldCaptureStartForLab markWorldSnapshotCaptureStartForLab(
        World world,Path accountFile,String account,
        WorldPlayer owner,long generation
    ){
        if(!Boolean.getBoolean(CAPTURED_EPOCH_PREPARE_LAB_PROPERTY))
            return null;
        if(world==null||owner==null||accountFile==null||
           generation<=0||!world.pulse().inExecutionContext()||
           !world.players().owns(owner,generation))
            throw new IllegalStateException(
                "R88_CAPTURE_START_NOT_ACTUAL_WORLD_EXECUTION_CONTEXT");
        Path path=accountFile.toAbsolutePath().normalize();
        synchronized(active){
            Token token=active.get(path);
            if(token==null||token.released||
               token.admittedWorldOwner!=owner||
               token.admittedWorldGeneration!=generation||
               !token.account.equals(account))
                return null;
            long ordinal=token.worldCaptureStartOrdinal.incrementAndGet();
            if(ordinal<=0)
                throw new IllegalStateException(
                    "R87_CAPTURE_START_ORDINAL_EXHAUSTED");
            return new WorldCaptureStartForLab(
                token,world,path,account,owner,generation,ordinal);
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
        // R85: the exact immutable object constructed by the
        // World-thread capture. Not an untrusted matching username
        // or a caller-swappable postimage and never disk authority.
        private final PlayerSnapshot capturedSnapshot;
        private final long worldCaptureStartOrdinal;
        // R89: this is classification of the original immutable
        // World capture, NOT proof of durable final publication.
        private final boolean finalDisconnectCapture;
        private final boolean finalReservationAtCapture;

        private SaveCaptureWitness(
            Path path,String account,WorldPlayer owner,long generation,
            Token originalLease,boolean priorUnleased,
            PlayerSnapshot capturedSnapshot,long worldCaptureStartOrdinal,
            boolean finalDisconnectCapture,
            boolean finalReservationAtCapture
        ){
            this.path=path;
            this.account=account;
            this.owner=owner;
            this.generation=generation;
            this.originalLease=originalLease;
            this.priorUnleased=priorUnleased;
            this.capturedSnapshot=capturedSnapshot;
            this.worldCaptureStartOrdinal=worldCaptureStartOrdinal;
            this.finalDisconnectCapture=finalDisconnectCapture;
            this.finalReservationAtCapture=finalReservationAtCapture;
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
        // Compatibility for older explicit package callers. Missing
        // exact capture provenance cannot enter any R76-R85 LAB API.
        return captureSaveWitness(
            exactAccountPath,account,owner,generation,null);
    }

    static SaveCaptureWitness captureSaveWitness(
        Path exactAccountPath,String account,
        WorldPlayer owner,long generation,
        PlayerSnapshot capturedSnapshot
    ){
        // Package-only compatibility caller has no minted start:
        // no positive R86/R87 final settlement ordinal is granted.
        return captureSaveWitness(
            exactAccountPath,account,owner,generation,
            capturedSnapshot,(WorldCaptureStartForLab)null);
    }

    static SaveCaptureWitness captureSaveWitness(
        Path exactAccountPath,String account,
        WorldPlayer owner,long generation,
        PlayerSnapshot capturedSnapshot,
        WorldCaptureStartForLab captureStart
    ){
        // Compatibility/capture-test callers are always ordinary
        // captures and may not opt into final classification.
        return captureSaveWitness(
            exactAccountPath,account,owner,generation,
            capturedSnapshot,captureStart,false,false);
    }

    /**
     * R89-R90 source-only experimental classification. Actual World
     * persistence supplies whether this was a deferred-final capture
     * and whether a matching final FIFO reservation was really active.
     * Neither fact is a durable publication/ACK capability.
     */
    static SaveCaptureWitness captureSaveWitness(
        Path exactAccountPath,String account,
        WorldPlayer owner,long generation,
        PlayerSnapshot capturedSnapshot,
        WorldCaptureStartForLab captureStart,
        boolean finalDisconnectCapture,
        boolean finalReservationAtCapture
    ){
        Objects.requireNonNull(exactAccountPath,"exactAccountPath");
        Objects.requireNonNull(owner,"owner");
        Path path=exactAccountPath.toAbsolutePath().normalize();
        // R88.1: perform World-registry/context reads BEFORE the
        // session map monitor. Taking World players' ownership lock
        // under active would introduce a potential lock-order cycle
        // with account admission. The captured World command remains
        // serialized for this specific owner/generation.
        if(captureStart!=null&&
           (captureStart.captureWorld==null||
            !captureStart.captureWorld.pulse().inExecutionContext()||
            !captureStart.captureWorld.players().owns(owner,generation)))
            throw new IllegalStateException(
                "R88_CAPTURE_WITNESS_NOT_ACTUAL_WORLD_CONTEXT");
        synchronized(active){
            UnleasedAdmission previous=unleasedAdmissions.get(owner);
            boolean priorUnleased=previous!=null&&
                previous.accountPath.equals(path)&&
                previous.account.equals(account)&&
                previous.generation==generation;
            // Do not let an unrelated newly admitted lease retroactively
            // own a genuinely prior-unleased World (G21.184).
            Token token=priorUnleased?null:active.get(path);
            long ordinal=0L;
            if(captureStart!=null){
                if(captureStart.consumed)
                    throw new IllegalStateException(
                        "R87_CAPTURE_START_ALREADY_CONSUMED");
                if(token==null||token.released||
                   captureStart.originalLease!=token||
                   captureStart.path==null||
                   !captureStart.path.equals(path)||
                   !captureStart.account.equals(account)||
                   captureStart.owner!=owner||
                   captureStart.generation!=generation||
                   captureStart.ordinal<=0||capturedSnapshot==null)
                    throw new IllegalStateException(
                        "R87_CAPTURE_START_NOT_ORIGINAL_WORLD_TOKEN");
                // Exactly one immutable capture may claim this
                // original-token-issued start; duplicate claims fail.
                captureStart.consumed=true;
                ordinal=captureStart.ordinal;
            }
            return new SaveCaptureWitness(
                path,account,owner,generation,token,priorUnleased,
                capturedSnapshot,ordinal,finalDisconnectCapture,
                finalDisconnectCapture&&finalReservationAtCapture);
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
