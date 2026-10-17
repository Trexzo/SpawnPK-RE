package spk.local;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Schema-v1 file repository compatible with existing opensrc/src properties.
 *
 * All filesystem I/O occurs after the immutable PlayerSnapshot has been
 * captured. Saving retains the existing temp-file + replace discipline.
 */
final class FilePlayerRepository
    implements PlayerRepository {

    interface PathResolver {
        Path resolve(String username);
    }

    private final PathResolver paths;
    // G21.192 opt-in: test-observable forced, verified real World save
    // count. This is NOT a durable account epoch, receipt or grant.
    static final String EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY=
        "spk.local.experimentalForceWorldAccountPostimage";
    private final java.util.concurrent.atomic.AtomicInteger
        forcedWorldPostimageCount=new java.util.concurrent.atomic.AtomicInteger();

    int forcedWorldPostimageCountForTest(){
        return forcedWorldPostimageCount.get();
    }
    /**
     * G21.36 package-scoped deterministic test seam; it never relaxes the
     * normal before-replacement account review-fence check.
     */
    interface BeforeWorldReplace {
        void run(String account)throws IOException;
    }
    private final BeforeWorldReplace beforeWorldReplace;
    /** Test-only hook inside the exclusive last-check/replace section. */
    private final BeforeWorldReplace insideWorldPublication;
    /** G21.58 test seam after bytes are read, before session marker recheck. */
    private final BeforeWorldReplace afterWorldSessionRead;
    /** G21.74 deterministic test-only replacement between witness passes. */
    private final BeforeWorldReplace betweenRestartWitnessPasses;
    // G21.234 TEST-ONLY RAW hook: after serialization/reservation but
    // immediately before unguarded legacy fixed-temp replacement.
    // Ordinary repositories install an inert callback.
    private final BeforeWorldReplace beforeRawReplace;
    // G21.271 package-only deterministic seam AFTER guarded forced
    // World account replacement, BEFORE final postimage and negative
    // sidecar observations. Never used for ordinary production saves.
    private final BeforeWorldReplace afterForcedWorldMoveForTest;

    FilePlayerRepository(){
        this(
            username->
                LocalAccountProfiles.accountFile(
                    username
                )
        );
    }

    FilePlayerRepository(PathResolver paths){
        this(paths,account->{});
    }

    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace
    ){
        this(paths,beforeWorldReplace,account->{});
    }

    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace,
        BeforeWorldReplace insideWorldPublication
    ){
        this(paths,beforeWorldReplace,insideWorldPublication,account->{});
    }

    /** Package-scoped deterministic guarded-read injection only. */
    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace,
        BeforeWorldReplace insideWorldPublication,
        BeforeWorldReplace afterWorldSessionRead
    ){
        this(paths,beforeWorldReplace,insideWorldPublication,
            afterWorldSessionRead,account->{});
    }

    /** G21.74 package-scoped deterministic test injection only. */
    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace,
        BeforeWorldReplace insideWorldPublication,
        BeforeWorldReplace afterWorldSessionRead,
        BeforeWorldReplace betweenRestartWitnessPasses
    ){
        this(paths,beforeWorldReplace,insideWorldPublication,
            afterWorldSessionRead,betweenRestartWitnessPasses,
            account->{});
    }

    /** G21.234 RAW writer post-reservation TEST-ONLY race injection. */
    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace,
        BeforeWorldReplace insideWorldPublication,
        BeforeWorldReplace afterWorldSessionRead,
        BeforeWorldReplace betweenRestartWitnessPasses,
        BeforeWorldReplace beforeRawReplace
    ){
        this(paths,beforeWorldReplace,insideWorldPublication,
            afterWorldSessionRead,betweenRestartWitnessPasses,
            beforeRawReplace,account->{});
    }

    /** G21.271 test-only post-ATOMIC_MOVE forced World race seam. */
    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace,
        BeforeWorldReplace insideWorldPublication,
        BeforeWorldReplace afterWorldSessionRead,
        BeforeWorldReplace betweenRestartWitnessPasses,
        BeforeWorldReplace beforeRawReplace,
        BeforeWorldReplace afterForcedWorldMoveForTest
    ){
        this.paths=Objects.requireNonNull(paths,"paths");
        this.beforeWorldReplace=Objects.requireNonNull(
            beforeWorldReplace,"beforeWorldReplace");
        this.insideWorldPublication=Objects.requireNonNull(
            insideWorldPublication,"insideWorldPublication");
        this.afterWorldSessionRead=Objects.requireNonNull(
            afterWorldSessionRead,"afterWorldSessionRead");
        this.betweenRestartWitnessPasses=Objects.requireNonNull(
            betweenRestartWitnessPasses,"betweenRestartWitnessPasses");
        this.beforeRawReplace=Objects.requireNonNull(
            beforeRawReplace,"beforeRawReplace");
        this.afterForcedWorldMoveForTest=Objects.requireNonNull(
            afterForcedWorldMoveForTest,"afterForcedWorldMoveForTest");
    }

    @Override public Optional<PlayerSnapshot> load(
        String username
    )throws IOException{
        return loadExactFile(username,normalizedPath(username));
    }

    /**
     * G21.58 session-only negative admission: read the very same account
     * file that supplies all four negative marker names. A resolver drift
     * before return is a refusal, not a fresh file-selection instruction.
     * Raw repository.load remains a non-admitting forensic primitive.
     */
    Optional<PlayerSnapshot> loadForWorldSession(
        String username
    )throws IOException{
        final String account=clean(username);
        final Path file=normalizedPath(account);
        final PathResolver markerPaths=requested->{
            if(!account.equals(clean(requested)))
                throw new IllegalArgumentException(
                    "G21.58 session marker account identity changed"
                );
            return file;
        };
        // G21.84: use exactly the selected account file and existing
        // cooperating publication lock for optional journal admission.
        final MailboxDurableIdempotencyIntentJournal sessionJournal=
            new MailboxDurableIdempotencyIntentJournal(markerPaths);
        // G21.59: make the entire admitted account observation one
        // cooperating account-local publication critical section. A
        // marker or account writer holding this lock must run wholly
        // BEFORE or wholly AFTER the negative checks and snapshot read.
        // This is not an atomic disk snapshot or a lock on raw save().
        return MailboxAccountPublicationCoordinator
            .withExclusivePublication(file,()->{
                // G21.302: always refuse retained negative final
                // review witness, even after feature was disabled at
                // process restart or a foreign actor removed ACTIVE.
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(file);
                requireUnfencedSessionLoad(account,markerPaths);
                requireNoDiskCommitForSession(account,file);
                final byte[] journalBefore=
                    sessionJournal.sessionJournalWitnessInsidePublicationLock(
                        account,file);
                // G21.60: metadata describes the selected filesystem
                // object, not merely the path text. NOFOLLOW refuses
                // symlink roots even when they resolve to valid players.
                BasicFileAttributes before=
                    admittedAccountFileEvidence(account,file);
                // G21.61: bind the decoded byte stream to its own
                // SHA-256 fingerprint; G21.60 metadata alone cannot
                // detect a same-size rewrite with restored mtime.
                MessageDigest decodedHash=newAccountDigest();
                Optional<PlayerSnapshot> observed=
                    loadExactFile(account,file,true,decodedHash);
                afterWorldSessionRead.run(account);
                // G21.303: an external actor can create this negative
                // file while the admitted account bytes are being read.
                // Recheck exact selected pathname before returning.
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(file);
                requireUnfencedSessionLoad(account,markerPaths);
                requireNoDiskCommitForSession(account,file);
                if(!file.equals(normalizedPath(account)))
                    throw new IOException(
                        "G21.58 MAILBOX_SESSION_ACCOUNT_PATH_CHANGED"+
                        " account="+account+" action=REJECT_SESSION"
                    );
                BasicFileAttributes after=
                    admittedAccountFileEvidence(account,file);
                if(!sameAdmittedAccountObject(before,after))
                    throw new IOException(
                        "G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED"+
                        " account="+account+" action=REJECT_SESSION"
                    );
                if(before!=null){
                    // Re-read only the pinned admitted account path.
                    // The independent digest catches a content rewrite
                    // that preserves size, file key and timestamp.
                    if(!MessageDigest.isEqual(
                            decodedHash.digest(),
                            digestAdmittedAccountFile(file)))
                        throw new IOException(
                            "G21.61 MAILBOX_SESSION_ACCOUNT_BYTES_CHANGED"+
                            " account="+account+" action=REJECT_SESSION"
                        );
                    // Check identity again after the second read.
                    BasicFileAttributes afterDigest=
                        admittedAccountFileEvidence(account,file);
                    if(!sameAdmittedAccountObject(before,afterDigest))
                        throw new IOException(
                            "G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED"+
                            " account="+account+" action=REJECT_SESSION"
                        );
                }
                // Same critical section, after existing independent
                // account-byte digest. An absent journal stays optional.
                sessionJournal.requireWorldSessionPreparedInsidePublicationLock(
                    account,file,journalBefore,observed);
                // G21.96: even when BOTH external COMMIT and PREPARED
                // sidecars have vanished, the embedded terminal account
                // is still a durable NEGATIVE admission witness. Reject
                // here, while the G21.59 publication lock is held, not
                // just in the outer WorldPlayerPersistence caller.
                if(observed.isPresent()&&
                   MailboxAtomicTerminalSnapshot.inspect(observed.get())
                       .state!=MailboxAtomicTerminalSnapshot.State.ABSENT){
                    // Preserve G21.31's established terminal/invalid
                    // classification for callers and legacy regressions.
                    // This only moves the same denial INSIDE the existing
                    // account publication lock; no positive admission.
                    MailboxPreparedRestartAdmission.Decision refusal=
                        MailboxPreparedRestartAdmission.inspect(
                            observed.get());
                    throw new IOException(
                        "G21.31 MAILBOX_PREPARED_LOAD_QUARANTINE"+
                        " account="+account+
                        " reason="+refusal.state+
                        " action=REJECT_SESSION"+
                        " boundary=G21.96_ORPHANED_TERMINAL");
                }
                return observed;
            });
    }


    /**
     * G21.71: non-admitting restart recovery FORENSICS for an expired,
     * interrupted or uncertain Mailbox terminal attempt.
     *
     * This observation intentionally does not return a PlayerSnapshot,
     * clear markers, authorize replay, or accept an existing account
     * into World. Only the ordinary World session admission can do so.
     */
    static final class RestartRecoveryEvidence {
        enum State {
            DURABLE_REVIEW_MARKER,
            UNCERTAIN_COMMIT_MARKER,
            STRANDED_WRITE_INTENT_MARKER,
            MISSING_ACCOUNT_NO_REPLAY,
            COHERENT_TERMINAL_QUARANTINE,
            INVALID_TERMINAL_QUARANTINE,
            PREPARED_UNCLAIMED_NO_REPLAY,
            INVALID_PREPARED_QUARANTINE,
            LEGACY_NO_JOURNAL_NON_ADMITTING
        }
        final State state;
        final String account;
        final boolean pinnedPublicationLockObserved=true;
        final boolean exactObjectAndBytesObserved;
        final boolean restartAdmissionAuthorized=false;
        final boolean durabilityConfirmed=false;
        final boolean transactionCommitted=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private RestartRecoveryEvidence(
            String account,State state,boolean exact
        ){
            this.account=account;
            this.state=Objects.requireNonNull(state,"state");
            this.exactObjectAndBytesObserved=exact;
        }
    }

    /**
     * Bit-presence, not marker-body acceptance. Any marker (including
     * malformed/nonregular/symlink) denies automatic recovery; marker
     * metadata failures throw IOException and cannot grant access.
     */
    private static int restartReviewMask(
        String account,PathResolver pinned
    )throws IOException{
        int mask=0;
        if(new MailboxDurableReviewFence(pinned).present(account))
            mask|=1;
        if(new MailboxStrictUncertainFence(pinned).present(account))
            mask|=2;
        if(new MailboxStrictWriteIntentFence(pinned).present(account))
            mask|=4;
        return mask;
    }

    private static RestartRecoveryEvidence.State restartMarkerReason(
        int mask
    ){
        if((mask&1)!=0)
            return RestartRecoveryEvidence.State.DURABLE_REVIEW_MARKER;
        if((mask&2)!=0)
            return RestartRecoveryEvidence.State.UNCERTAIN_COMMIT_MARKER;
        return RestartRecoveryEvidence.State.STRANDED_WRITE_INTENT_MARKER;
    }

    RestartRecoveryEvidence inspectRestartRecoveryReadOnly(
        String username
    )throws IOException{
        final String account=clean(username);
        final Path pinned=normalizedPath(account);
        final PathResolver sameFile=requested->{
            if(!account.equals(clean(requested)))
                throw new IllegalArgumentException(
                    "G21.71 recovery account identity changed"
                );
            return pinned;
        };
        return MailboxAccountPublicationCoordinator
            .withExclusivePublication(pinned,()->
                inspectRestartRecoveryLocked(account,pinned,sameFile)
            );
    }

    /**
     * G21.166: forensic check of the account-local advisory lock PATH,
     * not a proof that the open OS FileLock is attached to this inode.
     * A raw actor can unlink/recreate a lock path behind a held channel.
     * Never accept this evidence as permission for writer fencing/grants.
     */
    private static BasicFileAttributes cutoverLockLeafEvidence(
        Path accountFile
    )throws IOException{
        Path lock=MailboxAccountPublicationCoordinator.lockPath(
            accountFile);
        final BasicFileAttributes attrs;
        try{
            attrs=Files.readAttributes(
                lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(IOException failure){
            throw new IOException(
                "G21.166 CUTOVER_LOCK_LEAF_UNAVAILABLE_UNSAFE_NO_AUTHORITY",
                failure);
        }
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
           attrs.fileKey()==null)
            throw new IOException(
                "G21.166 CUTOVER_LOCK_LEAF_IDENTITY_UNSAFE_NO_AUTHORITY");
        return attrs;
    }

    /**
     * G21.167: capture POSIX ctime alongside G21.166's stable lock
     * pathname metadata. A preserved mtime/size cannot erase a usual
     * in-place content rewrite from POSIX ctime. Some providers expose
     * no unix:ctime, in which case this is explicitly UNPROVED.
     */
    private static final class CutoverLockLeafCensus {
        final BasicFileAttributes object;
        final java.nio.file.attribute.FileTime changeTime;
        final String contentWitness;
        CutoverLockLeafCensus(
            BasicFileAttributes object,
            java.nio.file.attribute.FileTime changeTime,
            String contentWitness
        ){
            this.object=object;
            this.changeTime=changeTime;
            this.contentWitness=contentWitness;
        }
    }

    /**
     * G21.168: bounded NOFOLLOW physical SHA-256 of the G21.39 lock
     * path. Also package-visible to exercise this SAME primitive under
     * a real cooperating lock in deterministic integration tests.
     * It is neither a lock-descriptor identity proof nor a fencing token.
     */
    static String cutoverLockLeafContentWitness(
        Path accountFile
    )throws IOException{
        Path lock=MailboxAccountPublicationCoordinator.lockPath(
            accountFile);
        try{
            return restartFingerprintPart(lock,4096L);
        }catch(IOException unreadable){
            throw new IOException(
                "G21.168 CUTOVER_LOCK_CONTENT_UNSAFE_NO_AUTHORITY",
                unreadable);
        }
    }

    private static CutoverLockLeafCensus captureCutoverLockLeaf(
        Path accountFile
    )throws IOException{
        BasicFileAttributes first=cutoverLockLeafEvidence(accountFile);
        String digest=cutoverLockLeafContentWitness(accountFile);
        java.nio.file.attribute.FileTime changed=
            optionalRestartChangeTime(
                MailboxAccountPublicationCoordinator.lockPath(accountFile));
        BasicFileAttributes second=cutoverLockLeafEvidence(accountFile);
        if(!Objects.equals(first.fileKey(),second.fileKey())||
           !Objects.equals(first.creationTime(),second.creationTime())||
           first.size()!=second.size()||
           !Objects.equals(
               first.lastModifiedTime(),second.lastModifiedTime()))
            throw new IOException(
                "G21.167 CUTOVER_LOCK_CTIME_SAMPLE_UNSAFE_NO_AUTHORITY");
        return new CutoverLockLeafCensus(first,changed,digest);
    }

    private static void requireStableCutoverLockLeaf(
        Path accountFile,CutoverLockLeafCensus original
    )throws IOException{
        BasicFileAttributes now=cutoverLockLeafEvidence(accountFile);
        BasicFileAttributes first=original.object;
        if(!Objects.equals(first.fileKey(),now.fileKey())||
           !Objects.equals(first.creationTime(),now.creationTime())||
           first.size()!=now.size()||
           !Objects.equals(
               first.lastModifiedTime(),now.lastModifiedTime()))
            throw new IOException(
                "G21.166 CUTOVER_LOCK_LEAF_CHANGED_UNSAFE_NO_AUTHORITY");
        java.nio.file.attribute.FileTime changed=
            optionalRestartChangeTime(
                MailboxAccountPublicationCoordinator.lockPath(accountFile));
        // G21.75 optional ctime is an observation, not a writer fence.
        // Null/null explicitly means this provider offers no ctime proof.
        if(!Objects.equals(original.changeTime,changed))
            throw new IOException(
                "G21.167 CUTOVER_LOCK_CTIME_CHANGED_UNSAFE_NO_AUTHORITY");
        // G21.168: independent byte comparison after G21.167's
        // change-time check. Unlike unix:ctime, persistent byte
        // differences are observable where the ctime view is absent.
        // This does NOT catch returned-to-identical-bytes ABA.
        String latest=cutoverLockLeafContentWitness(accountFile);
        if(!original.contentWitness.equals(latest))
            throw new IOException(
                "G21.168 CUTOVER_LOCK_BYTES_CHANGED_UNSAFE_NO_AUTHORITY");
        // Bind both ctime and digest reads back to the same pathname.
        BasicFileAttributes second=cutoverLockLeafEvidence(accountFile);
        if(!Objects.equals(now.fileKey(),second.fileKey())||
           !Objects.equals(now.creationTime(),second.creationTime())||
           now.size()!=second.size()||
           !Objects.equals(
               now.lastModifiedTime(),second.lastModifiedTime()))
            throw new IOException(
                "G21.167 CUTOVER_LOCK_CTIME_SAMPLE_UNSAFE_NO_AUTHORITY");
    }

    /**
     * G21.163: narrowly compose G21.71's ORIGINAL physical-account
     * inspection and G21.162's denial under ONE existing cooperating
     * publication lock. There is NO approval or snapshot return path.
     * This does not lock uncooperative/RAW writers or ensure a durable
     * crash-recovery epoch.
     */
    void requireFreshCutoverUnderPublicationLock(
        String username,byte[] candidatePublicKeyDer
    )throws IOException{
        requireFreshCutoverUnderPublicationLock(
            username,candidatePublicKeyDer,account->{});
    }

    /**
     * Package-only DETERMINISTIC TEST SEAM, invoked after locked
     * inspection but before denial to prove cooperative writer
     * exclusion. Never called from a production entry point.
     */
    void requireFreshCutoverUnderPublicationLock(
        String username,byte[] candidatePublicKeyDer,
        BeforeWorldReplace afterLockedInspectForTest
    )throws IOException{
        final String account=clean(username);
        final Path pinned=normalizedPath(account);
        final PathResolver sameFile=requested->{
            if(!account.equals(clean(requested)))
                throw new IllegalArgumentException(
                    "G21.163 CUTOVER_ACCOUNT_CHANGED_UNSAFE_NO_AUTHORITY");
            return pinned;
        };
        Objects.requireNonNull(afterLockedInspectForTest,
            "afterLockedInspectForTest");
        // G21.164: keep the SAME cooperating lock through inspection and
        // denial, but cap acquisition wait; a busy or unsafe lock must
        // veto instead of wedging the optional cutover facade indefinitely.
        // The G21.73 deadline bounds acquisition, not disk I/O latency.
        MailboxAccountPublicationCoordinator.withExclusivePublicationBounded(
            pinned,3000L,()->{
                // G21.166: snapshot the actual lock PATH once the
                // G21.73 cooperating lease is held. Recheck even when
                // G21.162 correctly denies, using finally below.
                CutoverLockLeafCensus lockLeafBefore=
                    captureCutoverLockLeaf(pinned);
                try{
                // G21.165: physical seven-leaf evidence, not merely
                // a retained cutover enum. No nested lock: both complete
                // G21.87 witnesses run INSIDE this already-held G21.39
                // lock. A raw writer ignoring the lock can still act.
                RestartDirectoryAncestry ancestry=
                    restartAncestryCensus(pinned);
                Path[] seven=restartRecoveryCensusPaths(pinned);
                RestartObjectCensus before=restartRecoveryCensus(seven);
                requireBoundedRestartFiles(before);
                String first=restartWitnessInsidePublication(
                    account,pinned,sameFile);
                RestartRecoveryEvidence observed=
                    inspectRestartRecoveryLocked(account,pinned,sameFile);
                afterLockedInspectForTest.run(account);
                if(!pinned.equals(normalizedPath(account)))
                    throw new IOException(
                        "G21.163 CUTOVER_PATH_DRIFT_UNSAFE_NO_AUTHORITY");
                try{
                    String last=restartWitnessInsidePublication(
                        account,pinned,sameFile);
                    requireUnchangedRecoveryCensus(seven,before);
                    requireUnchangedRestartAncestry(ancestry);
                    if(!MessageDigest.isEqual(
                            first.getBytes(
                                java.nio.charset.StandardCharsets.US_ASCII),
                            last.getBytes(
                                java.nio.charset.StandardCharsets.US_ASCII)))
                        throw new IOException(
                            "G21.165 CUTOVER_SEVEN_LEAF_WITNESS_CHANGED");
                }catch(IOException drift){
                    throw new IOException(
                        "G21.165 CUTOVER_WINDOW_UNSTABLE_UNSAFE_NO_AUTHORITY",
                        drift);
                }
                MailboxFreshFileCutoverAdmissionV1
                    .requireObservedCutoverInsidePublicationLock(
                        account,observed,candidatePublicKeyDer);
                // Contract is always negative. Even an accidental
                // future return from policy cannot be an admission.
                throw new IOException(
                    "G21.163 CUTOVER_MUST_DENY_UNSAFE_NO_AUTHORITY");
                }finally{
                    // A raw file replacement can split the advisory
                    // lock domain even with a still-valid open handle.
                    // This is detection, NEVER a writer-exclusion proof.
                    requireStableCutoverLockLeaf(pinned,lockLeafBefore);
                }
            });
    }

    /** Called with G21.39's cooperating account publication lock HELD. */
    private RestartRecoveryEvidence inspectRestartRecoveryLocked(
        String account,Path pinned,PathResolver sameFile
    )throws IOException{
        return inspectRestartRecoveryLocked(
            account,pinned,sameFile,Long.MAX_VALUE);
    }

    /** Bounded only for G21.77 portable witness, not normal session. */
    private RestartRecoveryEvidence inspectRestartRecoveryLocked(
        String account,Path pinned,PathResolver sameFile,long maximum
    )throws IOException{
                final int markersBefore=restartReviewMask(
                    account,sameFile
                );
                // A negative sidecar is sufficient to quarantine
                // regardless of the current account bytes. Recheck the
                // exact marker set before returning, under the lock.
                if(markersBefore!=0){
                    afterWorldSessionRead.run(account);
                    if(markersBefore!=restartReviewMask(
                            account,sameFile)||
                       !pinned.equals(normalizedPath(account)))
                        throw new IOException(
                            "G21.71 RECOVERY_MARKER_OR_PATH_CHANGED_NO_GRANT"
                        );
                    return new RestartRecoveryEvidence(
                        account,restartMarkerReason(markersBefore),false
                    );
                }

                // Reuse G21.60/61 session-quality NOFOLLOW metadata,
                // digest during decoded read, and independent digest.
                BasicFileAttributes before=
                    admittedAccountFileEvidence(account,pinned);
                if(before!=null&&before.size()>maximum)
                    throw new IOException(
                        "G21.77 RECOVERY_ACCOUNT_OVERSIZE_NO_GRANT"
                    );
                MessageDigest firstDigest=newAccountDigest();
                Optional<PlayerSnapshot> read=loadExactFile(
                    account,pinned,true,firstDigest,maximum
                );
                afterWorldSessionRead.run(account);
                int markersAfter=restartReviewMask(account,sameFile);
                if(markersAfter!=markersBefore||
                   !pinned.equals(normalizedPath(account)))
                    throw new IOException(
                        "G21.71 RECOVERY_MARKER_OR_PATH_CHANGED_NO_GRANT"
                    );
                BasicFileAttributes after=
                    admittedAccountFileEvidence(account,pinned);
                if(!sameAdmittedAccountObject(before,after))
                    throw new IOException(
                        "G21.71 RECOVERY_FILE_OBJECT_CHANGED_NO_GRANT"
                    );
                if(before==null){
                    if(read.isPresent())
                        throw new IOException(
                            "G21.71 MISSING_ACCOUNT_OBJECT_INCONSISTENT"
                        );
                    return new RestartRecoveryEvidence(
                        account,
                        RestartRecoveryEvidence.State
                            .MISSING_ACCOUNT_NO_REPLAY,
                        false
                    );
                }
                if(!read.isPresent()||
                   !MessageDigest.isEqual(firstDigest.digest(),
                       digestAdmittedAccountFile(pinned,maximum))||
                   !sameAdmittedAccountObject(before,
                       admittedAccountFileEvidence(account,pinned))||
                   restartReviewMask(account,sameFile)!=markersBefore||
                   !pinned.equals(normalizedPath(account)))
                    throw new IOException(
                        "G21.71 RECOVERY_ACCOUNT_BYTES_CHANGED_NO_GRANT"
                    );
                PlayerSnapshot disk=read.get();
                MailboxAtomicTerminalSnapshot.Observation terminal=
                    MailboxAtomicTerminalSnapshot.inspect(disk);
                final RestartRecoveryEvidence.State state;
                if(terminal.state==
                        MailboxAtomicTerminalSnapshot.State
                            .COHERENT_TERMINAL_NO_GRANT)
                    state=RestartRecoveryEvidence.State
                        .COHERENT_TERMINAL_QUARANTINE;
                else if(terminal.state==
                        MailboxAtomicTerminalSnapshot.State.INVALID_TERMINAL)
                    state=RestartRecoveryEvidence.State
                        .INVALID_TERMINAL_QUARANTINE;
                else{
                    MailboxPreparedRestartAdmission.Decision restart=
                        MailboxPreparedRestartAdmission.inspect(disk);
                    if(restart.state==
                            MailboxPreparedRestartAdmission.State
                                .VALID_PREPARED_UNCLAIMED)
                        state=RestartRecoveryEvidence.State
                            .PREPARED_UNCLAIMED_NO_REPLAY;
                    else if(restart.state==
                            MailboxPreparedRestartAdmission.State.NO_JOURNAL)
                        state=RestartRecoveryEvidence.State
                            .LEGACY_NO_JOURNAL_NON_ADMITTING;
                    else state=RestartRecoveryEvidence.State
                        .INVALID_PREPARED_QUARANTINE;
                }
                return new RestartRecoveryEvidence(account,state,true);

    }


    /**
     * G21.72 portable read-only forensic continuity. This is an
     * UNAUTHENTICATED comparison string, NEVER a grant/replay/COMMIT or
     * restart admission credential. Every use must freshly inspect disk.
     */
    static final class RestartContinuityComparison {
        enum State {
            UNCHANGED_FORENSICS_NO_GRANT,
            CHANGED_FORENSICS_QUARANTINE
        }
        final State state;
        final boolean restartAdmissionAuthorized=false;
        final boolean transactionCommitted=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        RestartContinuityComparison(State state){this.state=state;}
    }

    private static final String RESTART_WITNESS_VERSION="G2187";
    private static final long RESTART_WITNESS_MAX_ACCOUNT_BYTES=64L*1024*1024;
    private static final long RESTART_WITNESS_MAX_MARKER_BYTES=4096L;
    // G21.85: bind the four pre-existing NEGATIVE markers plus G21.83's
    // optional PREPARED intent journal to one physical-file census.
    // The last entry is a NON-GRANTING journal with its own 1 KiB cap.
    // The .lock leaf is NOT part of the portable transaction identity.
    private static final String[] RESTART_MARKER_SUFFIXES={
        ".g2132-mailbox-review",
        ".g2147-strict-postpublication-review",
        MailboxStrictUncertainFence.SUFFIX,
        MailboxStrictWriteIntentFence.SUFFIX,
        MailboxDurableIdempotencyIntentJournal.SUFFIX,
        MailboxGuardedDiskCommitRecord.SUFFIX
    };
    private static final long RESTART_WITNESS_MAX_JOURNAL_BYTES=1024L;

    private static String restartDigestHex(byte[] input){
        char[] encoded=new char[input.length*2];
        char[] digits="0123456789abcdef".toCharArray();
        for(int i=0;i<input.length;i++){
            encoded[2*i]=digits[(input[i]&255)>>>4];
            encoded[2*i+1]=digits[input[i]&15];
        }
        return new String(encoded);
    }

    /**
     * A fresh, bounded, NOFOLLOW file digest. Comparing two complete
     * captures catches ordinary raw overwrites which restore mtime.
     * Cooperating publishers are excluded by G21.39 lock; uncooperative
     * adversarial ABA and physical-media crash safety are NOT solved.
     */
    private static String restartFingerprintPart(
        Path file,long maximum
    )throws IOException{
        BasicFileAttributes before=admittedAccountFileEvidence(
            file.getFileName().toString(),file
        );
        if(before==null)return "absent";
        if(before.size()>maximum)
            throw new IOException(
                "G21.72 RECOVERY_WITNESS_FILE_TOO_LARGE_NO_GRANT"
            );
        MessageDigest digest=newAccountDigest();
        long read=0L;
        try(InputStream input=new DigestInputStream(
            java.nio.channels.Channels.newInputStream(
                java.nio.channels.FileChannel.open(
                    file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS
                )),digest)){
            byte[] buffer=new byte[8192];
            int count;
            while((count=input.read(buffer))!=-1){
                read+=count;
                if(read>maximum)
                    throw new IOException(
                        "G21.72 RECOVERY_WITNESS_CHANGED_SIZE_NO_GRANT"
                    );
            }
        }
        BasicFileAttributes after=admittedAccountFileEvidence(
            file.getFileName().toString(),file
        );
        if(!sameAdmittedAccountObject(before,after)||read!=before.size())
            throw new IOException(
                "G21.72 RECOVERY_WITNESS_OBJECT_CHANGED_NO_GRANT"
            );
        return "sha256:"+restartDigestHex(digest.digest());
    }

    /** Called only while holding the same account's G21.39 lock. */
    private String restartWitnessInsidePublication(
        String account,Path pinned,PathResolver sameFile
    )throws IOException{
        RestartRecoveryEvidence state=inspectRestartRecoveryLocked(
            account,pinned,sameFile,RESTART_WITNESS_MAX_ACCOUNT_BYTES
        );
        StringBuilder bits=new StringBuilder();
        bits.append(RESTART_WITNESS_VERSION).append('|')
            .append(account).append('|')
            .append(pinned).append('|')
            .append(state.state).append('|')
            .append(restartFingerprintPart(
                pinned,RESTART_WITNESS_MAX_ACCOUNT_BYTES));
        for(String suffix:RESTART_MARKER_SUFFIXES){
            Path marker=pinned.resolveSibling(
                pinned.getFileName().toString()+suffix
            );
            bits.append('|').append(suffix).append('=')
                .append(restartFingerprintPart(
                    marker,(suffix.equals(
                        MailboxDurableIdempotencyIntentJournal.SUFFIX)||
                        suffix.equals(MailboxGuardedDiskCommitRecord.SUFFIX))
                            ?RESTART_WITNESS_MAX_JOURNAL_BYTES
                            :RESTART_WITNESS_MAX_MARKER_BYTES));
        }
        // G21.85: journal semantics are observed under the SAME
        // already-held account lock. Never call inspect() here: it
        // would attempt a nested lock and risk deadlocking this thread.
        MailboxDurableIdempotencyIntentJournal.Observation intent=
            new MailboxDurableIdempotencyIntentJournal(sameFile)
                .inspectInsidePublicationLock(account,pinned);
        bits.append("|g2183-status=").append(intent.status);
        MailboxGuardedDiskCommitRecord.Observation commit=
            new MailboxGuardedDiskCommitRecord(sameFile)
                .inspectInsidePublicationLock(account,pinned);
        bits.append("|g2186-status=").append(commit.status);
        // Re-check all marker names, account path and G21.71's
        // authoritative classification before returning this witness.
        RestartRecoveryEvidence second=inspectRestartRecoveryLocked(
            account,pinned,sameFile,RESTART_WITNESS_MAX_ACCOUNT_BYTES
        );
        if(second.state!=state.state||!pinned.equals(
                normalizedPath(account)))
            throw new IOException(
                "G21.72 RECOVERY_WITNESS_STATE_CHANGED_NO_GRANT"
            );
        return restartDigestHex(newAccountDigest().digest(
            bits.toString().getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            )));
    }

    /**
     * G21.87: retain NOFOLLOW identity of all seven forensic objects
     * (account + four negative sidecars + PREPARED + disk COMMIT)
     * over BOTH
     * complete G21.72 witness passes. The portable witness
     * deliberately omits fileKey: it compares bytes across restarts,
     * while this census only rejects replacement DURING a capture.
     */
    private static Path[] restartRecoveryCensusPaths(Path pinned){
        Path[] paths=new Path[1+RESTART_MARKER_SUFFIXES.length];
        paths[0]=pinned;
        for(int i=0;i<RESTART_MARKER_SUFFIXES.length;i++)
            paths[i+1]=pinned.resolveSibling(
                pinned.getFileName().toString()+
                RESTART_MARKER_SUFFIXES[i]
            );
        return paths;
    }

    /**
     * G21.75: POSIX ctime is an inode CHANGE timestamp, distinct from
     * user-resettable mtime. Some Java/Windows providers do not expose
     * unix:ctime; on those providers retain G21.74 identity checks and
     * explicitly DO NOT claim to detect restored-byte same-inode ABA.
     *
     * Any genuine filesystem read failure, rather than an unsupported
     * attribute view, fails the forensic read closed.
     */
    private static java.nio.file.attribute.FileTime
        optionalRestartChangeTime(Path file)throws IOException{
        try{
            return (java.nio.file.attribute.FileTime)Files.getAttribute(
                file,"unix:ctime",LinkOption.NOFOLLOW_LINKS
            );
        }catch(UnsupportedOperationException|
                IllegalArgumentException viewUnavailable){
            return null;
        }
    }

    private static final class RestartObjectCensus {
        final BasicFileAttributes[] objects;
        final java.nio.file.attribute.FileTime[] changeTimes;

        RestartObjectCensus(
            BasicFileAttributes[] objects,
            java.nio.file.attribute.FileTime[] changeTimes
        ){
            this.objects=objects;
            this.changeTimes=changeTimes;
        }
    }

    private static RestartObjectCensus restartRecoveryCensus(
        Path[] files
    )throws IOException{
        BasicFileAttributes[] census=new BasicFileAttributes[files.length];
        java.nio.file.attribute.FileTime[] changeTimes=
            new java.nio.file.attribute.FileTime[files.length];
        for(int i=0;i<files.length;i++){
            BasicFileAttributes item=admittedAccountFileEvidence(
                files[i].getFileName().toString(),files[i]
            );
            // Without a stable filesystem object key, same-size and
            // same-mtime atomic replacement cannot be distinguished.
            // Fail closed rather than report a fictitious identity.
            if(item!=null&&item.fileKey()==null)
                throw new IOException(
                    "G21.74 RECOVERY_OBJECT_IDENTITY_UNAVAILABLE_NO_GRANT"
                );
            census[i]=item;
            if(item!=null){
                changeTimes[i]=optionalRestartChangeTime(files[i]);
                // Protect the extra stat itself from a replace/unlink
                // before the ctime observation was taken.
                BasicFileAttributes after=admittedAccountFileEvidence(
                    files[i].getFileName().toString(),files[i]
                );
                if(!sameAdmittedAccountObject(item,after)||
                   !Objects.equals(item.creationTime(),
                       after.creationTime()))
                    throw new IOException(
                        "G21.74 RECOVERY_OBJECT_REPLACED_NO_GRANT"
                    );
            }
        }
        return new RestartObjectCensus(census,changeTimes);
    }

    private static void requireUnchangedRecoveryCensus(
        Path[] files,RestartObjectCensus initial
    )throws IOException{
        RestartObjectCensus latest=restartRecoveryCensus(files);
        if(latest.objects.length!=initial.objects.length)
            throw new IOException(
                "G21.74 RECOVERY_OBJECT_CENSUS_INVALID_NO_GRANT"
            );
        for(int i=0;i<initial.objects.length;i++){
            if(!sameAdmittedAccountObject(initial.objects[i],
                    latest.objects[i])||
               (initial.objects[i]!=null&&
                !Objects.equals(initial.objects[i].creationTime(),
                    latest.objects[i].creationTime())))
                throw new IOException(
                    "G21.74 RECOVERY_OBJECT_REPLACED_NO_GRANT"
                );
            if(!Objects.equals(initial.changeTimes[i],
                    latest.changeTimes[i]))
                throw new IOException(
                    "G21.75 RECOVERY_INPLACE_CHANGE_NO_GRANT"
                );
        }
    }

    /**
     * G21.77: enforce the original G21.72 advertised forensic byte caps
     * BEFORE the G21.71 Properties parser touches account contents.
     * A G21.48 marker takes precedence for admission, but an oversize
     * marker still rejects this optional forensic witness.
     */
    private static void requireBoundedRestartFiles(
        RestartObjectCensus census
    )throws IOException{
        for(int i=0;i<census.objects.length;i++){
            BasicFileAttributes object=census.objects[i];
            long limit=i==0
                ?RESTART_WITNESS_MAX_ACCOUNT_BYTES
                :i>=census.objects.length-2
                    ?RESTART_WITNESS_MAX_JOURNAL_BYTES
                    :RESTART_WITNESS_MAX_MARKER_BYTES;
            if(object!=null&&object.size()>limit)
                throw new IOException(
                    (i==0
                        ?"G21.77 RECOVERY_ACCOUNT_OVERSIZE_NO_GRANT"
                        :i==census.objects.length-1
                            ?"G21.87 RECOVERY_DISK_COMMIT_OVERSIZE_NO_GRANT"
                            :i==census.objects.length-2
                                ?"G21.85 RECOVERY_JOURNAL_OVERSIZE_NO_GRANT"
                                :"G21.77 RECOVERY_MARKER_OVERSIZE_NO_GRANT")
                );
        }
    }

    /**
     * G21.76: NOFOLLOW on the leaf account/marker is insufficient if
     * an ancestor directory is a symlink or replaced while the witness
     * is sampled. Keep a local (NOT portable-token) directory-inode
     * census up to the filesystem root.
     *
     * Do not compare directory mtime/ctime: other accounts can be
     * created or saved in the same directory independently.
     */
    private static final class RestartDirectoryAncestry {
        final Path[] directories;
        final BasicFileAttributes[] identities;
        RestartDirectoryAncestry(
            Path[] directories,BasicFileAttributes[] identities
        ){
            this.directories=directories;
            this.identities=identities;
        }
    }

    private static BasicFileAttributes restartRealDirectory(
        Path directory
    )throws IOException{
        BasicFileAttributes info=Files.readAttributes(
            directory,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!info.isDirectory()||info.isSymbolicLink()||
           info.fileKey()==null)
            throw new IOException(
                "G21.76 RECOVERY_DIRECTORY_ANCESTOR_UNSAFE_NO_GRANT "+
                directory
            );
        return info;
    }

    private static RestartDirectoryAncestry restartAncestryCensus(
        Path pinned
    )throws IOException{
        java.util.ArrayList<Path> all=new java.util.ArrayList<>();
        for(Path dir=pinned.getParent();dir!=null;dir=dir.getParent())
            all.add(dir);
        if(all.isEmpty())
            throw new IOException(
                "G21.76 RECOVERY_DIRECTORY_ANCESTOR_MISSING_NO_GRANT"
            );
        Path[] dirs=all.toArray(new Path[0]);
        BasicFileAttributes[] identities=
            new BasicFileAttributes[dirs.length];
        for(int i=0;i<dirs.length;i++)
            identities[i]=restartRealDirectory(dirs[i]);
        return new RestartDirectoryAncestry(dirs,identities);
    }

    private static void requireUnchangedRestartAncestry(
        RestartDirectoryAncestry original
    )throws IOException{
        for(int i=0;i<original.directories.length;i++){
            BasicFileAttributes now=restartRealDirectory(
                original.directories[i]
            );
            BasicFileAttributes before=original.identities[i];
            if(!Objects.equals(before.fileKey(),now.fileKey())||
               !Objects.equals(before.creationTime(),
                    now.creationTime()))
                throw new IOException(
                    "G21.76 RECOVERY_DIRECTORY_ANCESTOR_CHANGED_NO_GRANT "+
                    original.directories[i]
                );
        }
    }

    /**
     * G21.87 tokens use G2187 because the file-set changed;
     * older G2172/G2185 tokens are intentionally incompatible.
     * Can be saved as text and rechecked in a different process with
     * the SAME resolver root. No change to the account,
     * no marker cleanup, and no possible positive settlement authority.
     */
    String captureRestartContinuityTokenReadOnly(
        String username
    )throws IOException{
        final String account=clean(username);
        if(!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.72 noncanonical forensic account"
            );
        final Path pinned=normalizedPath(account);
        final PathResolver bound=other->{
            if(!account.equals(clean(other)))
                throw new IllegalArgumentException(
                    "G21.72 recovery account drift"
                );
            return pinned;
        };
        // G21.73: forensic observations must never remain queued
        // indefinitely behind another cooperating process's writer.
        // This bounds acquisition, not the subsequent disk read.
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(pinned,1500L,()->{
                RestartDirectoryAncestry ancestry=
                    restartAncestryCensus(pinned);
                Path[] files=restartRecoveryCensusPaths(pinned);
                RestartObjectCensus baseline=restartRecoveryCensus(
                    files
                );
                requireBoundedRestartFiles(baseline);
                String first=restartWitnessInsidePublication(
                    account,pinned,bound);
                requireUnchangedRecoveryCensus(files,baseline);
                requireUnchangedRestartAncestry(ancestry);
                // Test-only seam simulates a raw/uncooperative atomic
                // replacement with identical bytes while G21.39 is
                // still held by the observer.
                betweenRestartWitnessPasses.run(account);
                requireUnchangedRecoveryCensus(files,baseline);
                requireUnchangedRestartAncestry(ancestry);
                String second=restartWitnessInsidePublication(
                    account,pinned,bound);
                requireUnchangedRecoveryCensus(files,baseline);
                requireUnchangedRestartAncestry(ancestry);
                if(!first.equals(second)||!pinned.equals(
                        normalizedPath(account)))
                    throw new IOException(
                        "G21.72 RECOVERY_WITNESS_UNSTABLE_NO_GRANT"
                    );
                RestartRecoveryEvidence finalState=
                    inspectRestartRecoveryLocked(
                        account,pinned,bound,
                        RESTART_WITNESS_MAX_ACCOUNT_BYTES
                    );
                requireUnchangedRecoveryCensus(files,baseline);
                requireUnchangedRestartAncestry(ancestry);
                return RESTART_WITNESS_VERSION+"|"+account+"|"+
                    finalState.state.name()+"|"+first;
            });
    }

    RestartContinuityComparison compareRestartContinuityReadOnly(
        String username,String previousToken
    )throws IOException{
        String account=clean(username);
        if(previousToken==null)
            throw new IllegalArgumentException(
                "G21.72 missing portable forensic witness"
            );
        String[] fields=previousToken.split("\\|",-1);
        if(fields.length!=4||
           !RESTART_WITNESS_VERSION.equals(fields[0])||
           !account.equals(fields[1])||
           !fields[3].matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(
                "G21.72 invalid/foreign forensic witness"
            );
        try{
            RestartRecoveryEvidence.State.valueOf(fields[2]);
        }catch(IllegalArgumentException invalid){
            throw new IllegalArgumentException(
                "G21.72 invalid forensic witness classification",
                invalid
            );
        }
        String current=captureRestartContinuityTokenReadOnly(account);
        return new RestartContinuityComparison(
            current.equals(previousToken)
                ?RestartContinuityComparison.State
                    .UNCHANGED_FORENSICS_NO_GRANT
                :RestartContinuityComparison.State
                    .CHANGED_FORENSICS_QUARANTINE
        );
    }


    /**
     * G21.87: any disk COMMIT sidecar is an account-session veto,
     * including if raw test-only writes restored an older PREPARED image.
     * Called under the existing G21.59 account publication lock;
     * never read a second independently resolved account location.
     */
    private static void requireNoDiskCommitForSession(
        String account,Path selectedFile
    )throws IOException{
        Path commit=selectedFile.resolveSibling(
            selectedFile.getFileName().toString()+
            MailboxGuardedDiskCommitRecord.SUFFIX);
        try{
            // A symlink or malformed record counts as present, not safe.
            Files.readAttributes(
                commit,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException absent){
            return;
        }
        throw new IOException(
            "G21.87 DISK_COMMIT_RESTART_QUARANTINE_NO_GRANT "+
            "account="+account+" action=REJECT_SESSION");
    }

    private void requireUnfencedSessionLoad(
        String account,PathResolver markerPaths
    )throws IOException{
        if(new MailboxDurableReviewFence(markerPaths).present(account)||
           new MailboxStrictUncertainFence(markerPaths).present(account)||
           new MailboxStrictWriteIntentFence(markerPaths).present(account))
            throw new IOException(
                "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"+
                " account="+account+" action=REJECT_SESSION"
            );
    }

    /**
     * NOFOLLOW evidence is only an admission gate. File keys and file
     * timestamps are not cryptographic identity or a power-loss proof.
     */
    private static BasicFileAttributes admittedAccountFileEvidence(
        String account,Path file
    )throws IOException{
        final BasicFileAttributes attrs;
        try{
            attrs=Files.readAttributes(
                file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
        }catch(NoSuchFileException missing){
            return null;
        }
        if(!attrs.isRegularFile())
            throw new IOException(
                "G21.60 MAILBOX_SESSION_ACCOUNT_NONREGULAR"+
                " account="+account+" action=REJECT_SESSION"
            );
        return attrs;
    }

    private static boolean sameAdmittedAccountObject(
        BasicFileAttributes first,BasicFileAttributes last
    ){
        if(first==null||last==null)return first==null&&last==null;
        return Objects.equals(first.fileKey(),last.fileKey())&&
            first.size()==last.size()&&
            Objects.equals(first.lastModifiedTime(),
                           last.lastModifiedTime());
    }

    private static MessageDigest newAccountDigest()throws IOException{
        try{
            return MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException impossible){
            throw new IOException("SHA-256 unavailable",impossible);
        }
    }

    /**
     * Reject bytes beyond the configured forensic limit, including
     * when a raw writer grows a previously small file after its stat.
     * Normal account loads and the standalone G21.71 inspector retain
     * their preexisting, unbounded compatibility semantics.
     */
    private static final class BoundedRecoveryInputStream
        extends java.io.FilterInputStream{
        private final long maximum;
        private long consumed;
        BoundedRecoveryInputStream(InputStream input,long maximum){
            super(input);
            this.maximum=maximum;
        }
        @Override public int read()throws IOException{
            if(consumed>=maximum){
                int overflow=in.read();
                if(overflow!=-1)
                    throw new IOException(
                        "G21.77 RECOVERY_STREAM_OVERSIZE_NO_GRANT");
                return -1;
            }
            int result=in.read();
            if(result!=-1)consumed++;
            return result;
        }
        @Override public int read(byte[] data,int offset,int length)
            throws IOException{
            if(length==0)return 0;
            if(consumed>=maximum){
                int overflow=in.read();
                if(overflow!=-1)
                    throw new IOException(
                        "G21.77 RECOVERY_STREAM_OVERSIZE_NO_GRANT");
                return -1;
            }
            int count=in.read(
                data,offset,(int)Math.min((long)length,maximum-consumed)
            );
            if(count>0)consumed+=count;
            return count;
        }
    }

    /** Second NOFOLLOW read; not an atomic snapshot or ABA proof. */
    private static byte[] digestAdmittedAccountFile(
        Path file
    )throws IOException{
        return digestAdmittedAccountFile(file,Long.MAX_VALUE);
    }

    private static byte[] digestAdmittedAccountFile(
        Path file,long maximum
    )throws IOException{
        MessageDigest digest=newAccountDigest();
        try(InputStream raw=java.nio.channels.Channels.newInputStream(
                java.nio.channels.FileChannel.open(
                    file,StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS
                ))){
            InputStream limited=maximum==Long.MAX_VALUE
                ?raw:new BoundedRecoveryInputStream(raw,maximum);
            InputStream input=new DigestInputStream(limited,digest);
            byte[] buffer=new byte[8192];
            while(input.read(buffer)!=-1){}
        }
        return digest.digest();
    }

    private Optional<PlayerSnapshot> loadExactFile(
        String username,Path file
    )throws IOException{
        return loadExactFile(username,file,false,null);
    }

    private Optional<PlayerSnapshot> loadExactFile(
        String username,Path file,boolean noFollow,
        MessageDigest contentDigest
    )throws IOException{
        return loadExactFile(
            username,file,noFollow,contentDigest,Long.MAX_VALUE);
    }

    private Optional<PlayerSnapshot> loadExactFile(
        String username,Path file,boolean noFollow,
        MessageDigest contentDigest,long maximum
    )throws IOException{
        if(!Files.isRegularFile(file))
            return Optional.empty();

        Properties properties=
            new Properties();

        // The admitted decoder never follows a substituted symbolic
        // link after its NOFOLLOW metadata check. Raw forensic reading
        // preserves its pre-existing compatibility semantics.
        try(InputStream raw=noFollow
                ?java.nio.channels.Channels.newInputStream(
                    java.nio.channels.FileChannel.open(
                        file,StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS
                    ))
                :Files.newInputStream(file)){
            InputStream input=maximum==Long.MAX_VALUE
                ?raw:new BoundedRecoveryInputStream(raw,maximum);
            if(contentDigest==null)properties.load(input);
            else properties.load(new DigestInputStream(
                input,contentDigest
            ));
        }

        final PlayerSnapshot snapshot;

        try{
            snapshot=
                PlayerSnapshot.fromLegacyProperties(
                    username,
                    properties
                );
        }catch(IllegalArgumentException e){
            throw new IOException(
                "invalid player snapshot file="+
                file+
                " error="+e.getMessage(),
                e
            );
        }

        if(!snapshot.username().equalsIgnoreCase(
                clean(username)))
            throw new IOException(
                "snapshot username mismatch requested="+
                clean(username)+
                " stored="+snapshot.username()+
                " file="+file
            );

        return Optional.of(snapshot);
    }

    /**
     * G21.32 negative review-fence check for the actual file-backed
     * account source. Presence blocks login regardless of account bytes;
     * unreadable marker metadata fails closed through IOException.
     */
    boolean hasUnresolvedMailboxReviewFence(
        String username
    )throws IOException{
        String account=clean(username);
        return new MailboxDurableReviewFence(paths).present(account)||
            new MailboxStrictUncertainFence(paths).present(account)||
            new MailboxStrictWriteIntentFence(paths).present(account);
    }

    /**
     * G21.48 active-session policy distinguishes permanent manual-review
     * markers from the short-lived intent created by this session's own
     * in-flight strict persistence worker. A stranded intent is still a
     * session veto once its worker is no longer demonstrably in flight.
     */
    boolean hasPermanentMailboxReviewFence(String username)
        throws IOException{
        String account=clean(username);
        return new MailboxDurableReviewFence(paths).present(account)||
            new MailboxStrictUncertainFence(paths).present(account);
    }

    /** Read-only: no account file I/O or intent cancellation. */
    boolean hasStrictWriteIntent(String username)throws IOException{
        return new MailboxStrictWriteIntentFence(paths).present(
            clean(username)
        );
    }

    /**
     * Only the production WorldPlayerPersistence normal save path uses
     * this guarded entry point. Legacy/manual/forensic direct save() is
     * intentionally unchanged and MUST NOT be used as an admitted World
     * save or interpreted as settling a native Mailbox claim.
     */
    void saveForWorld(PlayerSnapshot snapshot)throws IOException{
        saveInternal(snapshot,true);
    }

    @Override public void save(
        PlayerSnapshot snapshot
    )throws IOException{
        saveInternal(snapshot,false);
    }

    private void saveInternal(
        PlayerSnapshot snapshot,boolean enforceWorldAdmission
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        if(snapshot.version()!=PlayerSnapshot.CURRENT_VERSION)
            throw new IOException(
                "unsupported snapshot version="+snapshot.version());
        // Pin the SAME resolved path for the raw cooperative admission
        // reservation and the eventual file replacement.
        Path file=normalizedPath(snapshot.username());
        try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                enforceWorldAdmission?null:
                LocalJvmAccountSessionLease.reserveRawWrite(
                    file,snapshot.username())){
            saveInternalSelected(snapshot,enforceWorldAdmission,file,raw);
        }
    }

    private void saveInternalSelected(
        PlayerSnapshot snapshot,boolean enforceWorldAdmission,
        Path file,LocalJvmAccountSessionLease.RawWriteReservation raw
    )throws IOException{
        // Snapshot per-save; callers must configure the opt-in before
        // sessions start, not change it in flight.
        final boolean forceRealWorldPostimage=enforceWorldAdmission&&
            Boolean.getBoolean(
                EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY);

        // G21.57: the guarded World save uses ONE account-file
        // location for both the actual replacement and negative markers.
        // A mutable resolver must never make review checks look at B while
        // the account FileLock/ATOMIC_MOVE targets A.
        final String worldAccount=clean(snapshot.username());
        final PathResolver guardedMarkerPaths=requested->{
            if(!worldAccount.equals(clean(requested)))
                throw new IllegalArgumentException(
                    "G21.57 marker account identity changed"
                );
            return file;
        };

        if(enforceWorldAdmission){
            // G21.232: once an existing admitted World owner has
            // acquired new hardlink aliases, deny additional World
            // postimages BEFORE allocating another temp.
            LocalJvmAccountSessionLease.requireNonHardlinkedAccount(file);
            // G21.171: do not even inspect sidecar paths through an
            // unsafe ancestor. Some providers raise NotDirectoryException
            // on marker reads before the old pre-temp check. Fail with
            // the explicit NOFOLLOW veto before any filesystem access
            // to child account/marker paths or World temp allocation.
            MailboxAccountPublicationCoordinator
                .requireSafeWorldTempAncestry(file,true);
            // G21.309: retained final-review evidence is an absolute
            // negative even for a previously admitted World writer.
            // Never allocate a new World temp for a quarantined leaf.
            LocalFinalSaveNegativeWitness
                .requireNoNegativeWitness(file);
            // G21.353: require original forced ACTIVE before temp
            // allocation. Without it a later failed witness could
            // follow an already-published account replacement.
            if(forceRealWorldPostimage&&
               LocalFinalSaveNegativeWitness.enabled()){
                LocalDurableAccountReviewIntentPrimitive
                    .requireReviewMarker(file);
                // G21.357: a preexisting ACTIVE hardlink alias
                // can mutate the same inode through another name.
                // Reject before allocating any World temp.
                LocalFinalSaveNegativeWitness.requireSingleObservedLink(
                    LocalDurableAccountReviewIntentPrimitive.markerPath(file),
                    "G21.357 WORLD_ACTIVE_REVIEW_HARDLINK_NEGATIVE");
                // G21.364: a selected account hardlink alias can
                // outlive replacing only its primary pathname. Refuse
                // even when the separate G21.230 gate is default-OFF.
                LocalFinalSaveNegativeWitness
                    .requireSingleObservedLinkIfPresent(file,
                        "G21.364 WORLD_ACCOUNT_EXISTING_ALIAS_NEGATIVE");
            }
            MailboxPreparedRestartAdmission.Decision admission=
                MailboxPreparedRestartAdmission.inspect(snapshot);
            if(!admission.admissionAllowed)
                throw new IOException(
                    "G21.36 MAILBOX_WORLD_SAVE_QUARANTINE"+
                    " account="+snapshot.username()+
                    " reason="+admission.state
                );
            requireUnfencedWorldSave(
                snapshot.username(),guardedMarkerPaths
            );
        }

        // G21.360-363: pin the existing account's exact bounded
        // physical preimage before serializing a new forced World temp.
        // Uncooperative RAW edits during serialization must not be
        // silently overwritten as a successful forced final save.
        // Absent first-time accounts remain a distinct valid preimage.
        final LocalOptInRawTemp.AccountPreimage worldAccountPreimage=
            forceRealWorldPostimage&&LocalFinalSaveNegativeWitness.enabled()
                ?LocalOptInRawTemp.captureAccountPreimage(file):null;

        Path parent=file.getParent();
        if(parent!=null)
            Files.createDirectories(parent);
        if(enforceWorldAdmission){
            // Allow a valid new directory tree, but no symlinked
            // ancestry when the World-owned temp is allocated.
            MailboxAccountPublicationCoordinator
                .requireSafeWorldTempAncestry(file,false);
        }

        Properties properties=
            snapshot.toLegacyProperties();

        properties.setProperty(
            "saved.at",
            Instant.now().toString()
        );

        // G21.170: a guarded World save MUST NOT serialize into the
        // same fixed .tmp leaf used by the intentionally unguarded
        // historical direct save(). Its publication lock only covers
        // final replacement, so a direct legacy save can otherwise
        // move/truncate the World temp before that lock is acquired.
        // Use an exclusively allocated sibling for guarded World only;
        // preserve original direct save() file layout and semantics.
        final boolean safeRawTemp=
            !enforceWorldAdmission&&LocalOptInRawTemp.enabled();
        final LocalOptInRawTemp.AccountPreimage accountPreimage=
            safeRawTemp&&LocalOptInRawTemp.forcePostimageEnabled()
                ?LocalOptInRawTemp.captureAccountPreimage(file):null;
        Path tmp=enforceWorldAdmission
            ?Files.createTempFile(
                parent,file.getFileName().toString()+".g21170-world-",
                ".tmp")
            :safeRawTemp
                ?LocalOptInRawTemp.create(file)
                :file.resolveSibling(
                    file.getFileName().toString()+".tmp"
                );

        // G21.172: the newly allocated World temp has an owner
        // identity distinct from the persistent legacy direct .tmp.
        // Record it before opening for serialization, then revalidate
        // its bytes under the final G21.39 World publication lock.
        final BasicFileAttributes originalWorldTemp=enforceWorldAdmission
            ?worldTempEvidence(tmp):null;
        final BasicFileAttributes originalRawTemp=safeRawTemp
            ?LocalOptInRawTemp.observe(tmp):null;
        boolean completed=false;

        try{
            try(OutputStream output=enforceWorldAdmission
                    ?Files.newOutputStream(
                        tmp,StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS)
                    :safeRawTemp
                        ?Files.newOutputStream(
                            tmp,StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            LinkOption.NOFOLLOW_LINKS)
                        :Files.newOutputStream(
                            tmp,StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)){
                properties.store(
                    output,
                    "SpawnPK LocalLab localhost account state"
                );
            }

            if(enforceWorldAdmission){
                // G21.192: no postimage can earn a forced receipt until
                // the exact World-owned serialized temp is forced.
                if(forceRealWorldPostimage)
                    forceOwnedWorldTemp(tmp,originalWorldTemp);
                requireOwnedWorldTemp(tmp,originalWorldTemp);
                final String sealedWorldTemp=restartFingerprintPart(
                    tmp,RESTART_WITNESS_MAX_ACCOUNT_BYTES);
                requireOwnedWorldTemp(tmp,originalWorldTemp);
                beforeWorldReplace.run(snapshot.username());
                // G21.38: the last review-fence check and account-file
                // replacement are one exclusive publication critical
                // section shared with cooperating G21.34 marker writers.
                // This does NOT coordinate direct manual save() calls.
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(file,()->{
                        // G21.309 second exact-path veto under the
                        // cooperating writer publication lock.
                        LocalFinalSaveNegativeWitness
                            .requireNoNegativeWitness(file);
                        if(forceRealWorldPostimage&&
                           LocalFinalSaveNegativeWitness.enabled())
                            LocalDurableAccountReviewIntentPrimitive
                                .requireReviewMarker(file);
                        requireUnfencedWorldSave(
                snapshot.username(),guardedMarkerPaths
            );
                        // G21.66: a different cooperating World must
                        // not overwrite a completed terminal account
                        // with an older PREPARED/autosave snapshot.
                        requireNoTerminalAccountPostimage(
                            worldAccount,file
                        );
                        final WorldActiveReviewWitness originalActiveReview=
                            forceRealWorldPostimage
                                ?observeWorldActiveReview(file):null;
                        insideWorldPublication.run(snapshot.username());
                        // G21.354: this is still before ATOMIC_MOVE,
                        // even if a foreign writer removes ACTIVE
                        // during the real publication cutpoint.
                        if(forceRealWorldPostimage&&
                           LocalFinalSaveNegativeWitness.enabled())
                            LocalDurableAccountReviewIntentPrimitive
                                .requireReviewMarker(file);
                        // G21.310: an uncooperative actor may publish
                        // a negative witness after initial admission
                        // and while this World temp was serialized.
                        LocalFinalSaveNegativeWitness
                            .requireNoNegativeWitness(file);
                        // G21.239: a lock leaf could be replaced AFTER
                        // the capture-bound worker witness. Reobserve
                        // G21.221 opt-in original lock object here, near
                        // the actual World account atomic replace.
                        LocalJvmAccountSessionLease
                            .requireCurrentLeaseLockAtWorldPublication(file);
                        // G21.172: an uncooperative writer can replace
                        // World-owned temp AFTER serialization and even
                        // inside the OS lock. Never publish a foreign
                        // symlink, foreign inode, or changed raw bytes.
                        requireOwnedWorldTemp(tmp,originalWorldTemp);
                        final String latest=restartFingerprintPart(
                            tmp,RESTART_WITNESS_MAX_ACCOUNT_BYTES);
                        requireOwnedWorldTemp(tmp,originalWorldTemp);
                        if(!sealedWorldTemp.equals(latest))
                            throw new IOException(
                                "G21.172 WORLD_TEMP_BYTES_CHANGED_UNSAFE_NO_AUTHORITY");
                        // Opt-in second NOFOLLOW link-count check at
                        // the closest existing World publication point.
                        // This is NOT a durable atomic inode fencing
                        // contract against nonparticipating writers.
                        LocalJvmAccountSessionLease
                            .requireNonHardlinkedAccount(file);
                        // Closest observed negative gate to actual
                        // account replacement; still NOT atomic
                        // exclusion of nonparticipating processes.
                        LocalFinalSaveNegativeWitness
                            .requireNoNegativeWitness(file);
                        // G21.355/356: a still-canonical ACTIVE marker may
                        // have changed inode or nonce DURING the guarded
                        // World publication callback. The post-move
                        // G21.275 check detects it too late to preserve
                        // the previous disk account. Fail BEFORE the
                        // account ATOMIC_MOVE on observed drift.
                        // This is NOT a global ownership epoch or ACK.
                        if(forceRealWorldPostimage)
                            requireWorldActiveReviewUnchanged(
                                file,originalActiveReview);
                        // G21.358: marker inode and bytes can remain
                        // identical while the link count increases
                        // inside the guarded publication callback.
                        // The post-move final witness rejects aliases
                        // too late to preserve the old account file.
                        if(forceRealWorldPostimage&&
                           LocalFinalSaveNegativeWitness.enabled())
                            LocalFinalSaveNegativeWitness
                                .requireSingleObservedLink(
                                    LocalDurableAccountReviewIntentPrimitive
                                        .markerPath(file),
                                    "G21.358 WORLD_ACTIVE_REVIEW_LATE_ALIAS_NEGATIVE");
                        // Exact-account preimage must still match just
                        // before the World ATOMIC_MOVE. This is a
                        // cooperative publication observation, NOT an
                        // atomic fence against later uncooperative writes.
                        if(worldAccountPreimage!=null){
                            try{
                                LocalOptInRawTemp
                                    .requireAccountPreimageUnchanged(
                                        file,worldAccountPreimage);
                            }catch(IOException drift){
                                throw new IOException(
                                    "G21.360 WORLD_ACCOUNT_PREIMAGE_CHANGED_BEFORE_MOVE",
                                    drift);
                            }
                            // G21.365: identity, bytes and mtime
                            // remain identical when an outsider adds
                            // a hardlink to the same account inode.
                            // This final negative gate is before move.
                            LocalFinalSaveNegativeWitness
                                .requireSingleObservedLinkIfPresent(file,
                                    "G21.365 WORLD_ACCOUNT_LATE_ALIAS_NEGATIVE");
                        }
                        replaceSnapshotTemp(tmp,file,
                            forceRealWorldPostimage);
                        if(forceRealWorldPostimage){
                            // A successful atomic rename is not a
                            // durable receipt. Force parent directory
                            // and independently verify exact postimage.
                            // G21.271: late uncooperative negative sidecar
                            // publication AFTER the actual account move
                            // must not be reported as a confirmed normal
                            // World save. Bytes may ALREADY be replaced.
                            // This is not rollback or durable owner ACK.
                            try{
                                afterForcedWorldMoveForTest.run(worldAccount);
                                forceAndVerifyWorldPostimage(
                                    file,sealedWorldTemp);
                                requireWorldActiveReviewUnchanged(
                                    file,originalActiveReview);
                                requireUnfencedWorldSave(
                                    worldAccount,guardedMarkerPaths);
                                LocalFinalSaveNegativeWitness
                                    .requireNoNegativeWitness(file);
                                // G21.273: the OLD account nlink gate
                                // ran before ATOMIC_MOVE. A foreign
                                // hardlink can name the newly published
                                // inode after replacement instead. With
                                // existing default-OFF G21.230 opt-in,
                                // observe NEW leaf nlink before normal
                                // World completion. Postmove denial is
                                // UNCERTAIN, never a rollback.
                                LocalJvmAccountSessionLease
                                    .requireNonHardlinkedAccount(file);
                            }catch(IOException uncertain){
                                throw new IOException(
                                    "G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW",
                                    uncertain);
                            }
                            forcedWorldPostimageCount.incrementAndGet();
                        }else{
                            // A negative witness added after the
                            // non-forced move makes completion
                            // uncertain; never claim normal success.
                            LocalFinalSaveNegativeWitness
                                .requireNoNegativeWitness(file);
                        }
                        return null;
                    });
            }else{
                final byte[] sealedRawTemp=safeRawTemp
                    ?LocalOptInRawTemp.seal(tmp,originalRawTemp):null;
                beforeRawReplace.run(snapshot.username());
                if(safeRawTemp)
                    LocalOptInRawTemp.requireSealed(
                        tmp,originalRawTemp,sealedRawTemp);
                // G21.235: the raw reservation's admission-time inode
                // check can become stale while its fixed .tmp is
                // serialized. Revalidate the exact selected file
                // immediately before its legacy replace. Still not
                // atomic fencing against an uncooperative external
                // hardlink operation after the last check.
                LocalJvmAccountSessionLease
                    .requireNonHardlinkedAccount(file);
                if(LocalOptInRawTemp.forcePostimageEnabled()){
                    LocalOptInRawTemp.forcePublish(
                        tmp,file,originalRawTemp,sealedRawTemp,raw,
                        snapshot.username(),accountPreimage);
                }else{
                    replaceSnapshotTemp(tmp,file,raw);
                }
            }

            completed=true;
        }finally{
            if(!completed&&
                (enforceWorldAdmission
                    ?stillOwnsWorldTemp(tmp,originalWorldTemp)
                    :!safeRawTemp||
                        LocalOptInRawTemp.stillOwns(tmp,originalRawTemp)))
                Files.deleteIfExists(tmp);
        }
    }

    /**
     * G21.172: pin the World-owned temp as one NOFOLLOW regular
     * filesystem object before any account publication. The JVM's
     * cooperating lock does not protect this temp from raw writers.
     * This is an observation, NOT an atomic raw-writer fence.
     */
    private static BasicFileAttributes worldTempEvidence(
        Path temp
    )throws IOException{
        final BasicFileAttributes attrs;
        try{
            attrs=Files.readAttributes(
                temp,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(IOException inaccessible){
            throw new IOException(
                "G21.172 WORLD_TEMP_IDENTITY_UNSAFE_NO_AUTHORITY",
                inaccessible);
        }
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
            attrs.fileKey()==null)
            throw new IOException(
                "G21.172 WORLD_TEMP_IDENTITY_UNSAFE_NO_AUTHORITY");
        return attrs;
    }

    private static boolean sameWorldTempObject(
        BasicFileAttributes original,BasicFileAttributes candidate
    ){
        return original!=null&&candidate!=null&&
            candidate.isRegularFile()&&!candidate.isSymbolicLink()&&
            Objects.equals(original.fileKey(),candidate.fileKey())&&
            Objects.equals(
                original.creationTime(),candidate.creationTime());
    }

    private static void requireOwnedWorldTemp(
        Path temp,BasicFileAttributes original
    )throws IOException{
        if(!sameWorldTempObject(original,worldTempEvidence(temp)))
            throw new IOException(
                "G21.172 WORLD_TEMP_IDENTITY_UNSAFE_NO_AUTHORITY");
    }

    /** On failed World save, never unlink another actor's temp leaf. */
    private static boolean stillOwnsWorldTemp(
        Path temp,BasicFileAttributes original
    ){
        try{
            return sameWorldTempObject(
                original,Files.readAttributes(
                    temp,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS));
        }catch(IOException inaccessible){
            return false;
        }
    }

    /**
     * G21.192: forced account World temp bytes, preserving original
     * NOFOLLOW inode evidence. This does not fence RAW path replacement.
     */
    private static void forceOwnedWorldTemp(
        Path temp,BasicFileAttributes original
    )throws IOException{
        requireOwnedWorldTemp(temp,original);
        try(java.nio.channels.FileChannel channel=
                java.nio.channels.FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)){
            channel.force(true);
        }
        requireOwnedWorldTemp(temp,original);
    }

    /**
     * Must be called INSIDE G21.39 exclusive publication section and
     * only after G21.192's strict atomic account replacement. No
     * REVIEW-intent retirement, no account owner/generation epoch.
     */
    /**
     * G21.275: an existing G21.188 ACTIVE review marker can be a
     * deliberately retained one-shot session quarantine (G21.193).
     * The earlier World writer did not retire it. Preserve this
     * compatibility ONLY if the exact pre-move marker remains
     * byte-for-byte and object-identical after publication.
     *
     * This is NEGATIVE evidence: it never authorizes a new login,
     * marker release, recovery, or trusted owner epoch.
     */
    private static final class WorldActiveReviewWitness{
        final BasicFileAttributes attributes;
        final byte[] bytes;
        WorldActiveReviewWitness(BasicFileAttributes attrs,byte[] data){
            this.attributes=attrs;
            this.bytes=data;
        }
    }

    private static WorldActiveReviewWitness observeWorldActiveReview(
        Path account
    )throws IOException{
        Path marker=LocalDurableAccountReviewIntentPrimitive
            .markerPath(account);
        final BasicFileAttributes before;
        try{
            before=Files.readAttributes(marker,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException absent){
            return null;
        }
        if(!before.isRegularFile()||before.isSymbolicLink()||
           before.fileKey()==null||before.size()>4096L)
            throw new IOException(
                "G21.275 WORLD_ACTIVE_REVIEW_MARKER_UNSAFE");
        // Existing ACTIVE is acceptable as a retained NEGATIVE only
        // if it is a valid, bounded durable marker. Do not infer
        // that it belongs to a new login or authorizes release.
        LocalDurableAccountReviewIntentPrimitive
            .requireReviewMarker(account);
        java.io.ByteArrayOutputStream bytes=
            new java.io.ByteArrayOutputStream();
        try(InputStream input=Files.newInputStream(marker,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            byte[] chunk=new byte[1024];
            for(int n;(n=input.read(chunk))!=-1;){
                if(bytes.size()+n>4096)
                    throw new IOException(
                        "G21.275 WORLD_ACTIVE_REVIEW_MARKER_OVERSIZE");
                bytes.write(chunk,0,n);
            }
        }
        BasicFileAttributes after=Files.readAttributes(marker,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||after.isSymbolicLink()||
           !Objects.equals(before.fileKey(),after.fileKey())||
           !Objects.equals(before.creationTime(),after.creationTime())||
           before.size()!=after.size()||
           !Objects.equals(before.lastModifiedTime(),
                           after.lastModifiedTime()))
            throw new IOException(
                "G21.275 WORLD_ACTIVE_REVIEW_MARKER_READ_DRIFT");
        return new WorldActiveReviewWitness(after,bytes.toByteArray());
    }

    private static void requireWorldActiveReviewUnchanged(
        Path account,WorldActiveReviewWitness original
    )throws IOException{
        WorldActiveReviewWitness now=observeWorldActiveReview(account);
        if(original==null&&now==null)return;
        if(original==null||now==null||
           !Objects.equals(original.attributes.fileKey(),
                           now.attributes.fileKey())||
           !Objects.equals(original.attributes.creationTime(),
                           now.attributes.creationTime())||
           original.attributes.size()!=now.attributes.size()||
           !Objects.equals(original.attributes.lastModifiedTime(),
                           now.attributes.lastModifiedTime())||
           !java.security.MessageDigest.isEqual(original.bytes,now.bytes))
            throw new IOException(
                "G21.275 WORLD_ACTIVE_REVIEW_MARKER_CHANGED_NO_ACK");
    }

    private static void forceAndVerifyWorldPostimage(
        Path file,String expectedSha
    )throws IOException{
        MailboxAccountPublicationCoordinator
            .requireSafeWorldTempAncestry(file,false);
        BasicFileAttributes afterMove=worldTempEvidence(file);
        try(java.nio.channels.FileChannel directory=
                java.nio.channels.FileChannel.open(
                    file.getParent(),StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS)){
            directory.force(true);
        }
        if(!sameWorldTempObject(afterMove,worldTempEvidence(file)))
            throw new IOException(
                "G21.192 FORCED_WORLD_POSTIMAGE_IDENTITY_CHANGED");
        final String postimage=restartFingerprintPart(
            file,RESTART_WITNESS_MAX_ACCOUNT_BYTES);
        if(!expectedSha.equals(postimage))
            throw new IOException(
                "G21.192 FORCED_WORLD_POSTIMAGE_BYTES_CHANGED");
    }

    private static void replaceSnapshotTemp(
        Path temp,Path file
    )throws IOException{
        replaceSnapshotTemp(temp,file,false);
    }

    private static void replaceSnapshotTemp(
        Path temp,Path file,boolean requireAtomic
    )throws IOException{
        replaceSnapshotTemp(temp,file,requireAtomic,null);
    }

    private static void replaceSnapshotTemp(
        Path temp,Path file,
        LocalJvmAccountSessionLease.RawWriteReservation raw
    )throws IOException{
        replaceSnapshotTemp(temp,file,false,raw);
    }

    private static void replaceSnapshotTemp(
        Path temp,Path file,boolean requireAtomic,
        LocalJvmAccountSessionLease.RawWriteReservation raw
    )throws IOException{
        if(raw!=null)raw.requireLockObjectContinuity();
        try{
            Files.move(
                temp,file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        }catch(AtomicMoveNotSupportedException unsupported){
            if(requireAtomic)
                throw new IOException(
                    "G21.192 FORCED_WORLD_ATOMIC_REPLACE_REQUIRED",
                    unsupported);
            // Preserve original default and raw writer compatibility;
            // no non-atomic fallback in G21.192 opt-in mode.
            // G21.245: recheck identity if atomic move was unsupported.
            if(raw!=null)raw.requireLockObjectContinuity();
            Files.move(
                temp,file,StandardCopyOption.REPLACE_EXISTING
            );
        }
    }


    /**
     * G21.66: read the pinned current disk account while holding this
     * account's G21.39 publication lock. An embedded terminal namespace
     * is a permanent negative World-save veto, even when corrupted,
     * incomplete or paired with a stale caller snapshot. This does not
     * change the intentionally unguarded raw forensic save() method.
     */
    static void requireNoTerminalAccountPostimage(
        String account,Path pinned
    )throws IOException{
        final BasicFileAttributes before;
        try{
            before=Files.readAttributes(
                pinned,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
        }catch(NoSuchFileException missing){
            return;
        }
        if(!before.isRegularFile())
            throw new IOException(
                "G21.66 WORLD_SAVE_DISK_ACCOUNT_NONREGULAR "+
                account+" action=REJECT_WORLD_SAVE"
            );
        Optional<PlayerSnapshot> observed=
            new FilePlayerRepository(a->{
                if(!account.equals(a))
                    throw new IllegalArgumentException(
                        "G21.66 guarded World save account changed"
                    );
                return pinned;
            }).load(account);
        BasicFileAttributes after=Files.readAttributes(
            pinned,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!after.isRegularFile()||
           !Objects.equals(before.fileKey(),after.fileKey())||
           before.size()!=after.size()||
           !Objects.equals(before.lastModifiedTime(),
                           after.lastModifiedTime()))
            throw new IOException(
                "G21.66 WORLD_SAVE_DISK_ACCOUNT_CHANGED "+
                account+" action=REJECT_WORLD_SAVE"
            );
        if(observed.isPresent()){
            String markerPrefix="extension."+
                MailboxAtomicTerminalSnapshot.NAMESPACE+".";
            for(String key:observed.get().values().keySet()){
                if(key.startsWith(markerPrefix))
                    throw new IOException(
                        "G21.66 WORLD_SAVE_TERMINAL_ACCOUNT_VETO "+
                        account+" action=REJECT_WORLD_SAVE"
                    );
            }
        }
    }

    private void requireUnfencedWorldSave(
        String account,PathResolver markerPaths
    )throws IOException{
        // G21.88: the disk COMMIT sidecar (including invalid/symlinked
        // records) is an immutable negative World-save fence. This
        // method is called before temp and again under the SAME G21.39
        // pinned-account publication lock before atomic replacement.
        Path selected=markerPaths.resolve(account)
            .toAbsolutePath().normalize();
        Path diskCommit=selected.resolveSibling(
            selected.getFileName().toString()+
            MailboxGuardedDiskCommitRecord.SUFFIX);
        if(Files.exists(diskCommit,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "G21.88 DISK_COMMIT_WORLD_SAVE_VETO account="+
                account+" action=REJECT_WORLD_SAVE");

        // The guarded World save must check ALL four negative marker
        // names against the locked/replaced account file, not a second
        // independently resolved account root.
        if(new MailboxDurableReviewFence(markerPaths).present(account)||
           new MailboxStrictUncertainFence(markerPaths).present(account)||
           new MailboxStrictWriteIntentFence(markerPaths).present(account))
            throw new IOException(
                "G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO"+
                " account="+account+" action=REJECT_WORLD_SAVE"
            );
    }

    /** G21.42: exact file-backed path bound to the World barrier. */
    Path accountFilePath(String username){
        return normalizedPath(username);
    }

    private Path normalizedPath(
        String username
    ){
        Path file=
            paths.resolve(
                clean(username)
            );

        if(file==null)
            throw new IllegalArgumentException(
                "repository path"
            );

        return file.toAbsolutePath().normalize();
    }

    private static String clean(
        String username
    ){
        return username==null
            ?""
            :username.trim().toLowerCase(
                Locale.ROOT
            );
    }
}
