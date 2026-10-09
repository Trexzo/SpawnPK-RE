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
        this.paths=Objects.requireNonNull(paths,"paths");
        this.beforeWorldReplace=Objects.requireNonNull(
            beforeWorldReplace,"beforeWorldReplace"
        );
        this.insideWorldPublication=Objects.requireNonNull(
            insideWorldPublication,"insideWorldPublication"
        );
        this.afterWorldSessionRead=Objects.requireNonNull(
            afterWorldSessionRead,"afterWorldSessionRead"
        );
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
        // G21.59: make the entire admitted account observation one
        // cooperating account-local publication critical section. A
        // marker or account writer holding this lock must run wholly
        // BEFORE or wholly AFTER the negative checks and snapshot read.
        // This is not an atomic disk snapshot or a lock on raw save().
        return MailboxAccountPublicationCoordinator
            .withExclusivePublication(file,()->{
                requireUnfencedSessionLoad(account,markerPaths);
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
                requireUnfencedSessionLoad(account,markerPaths);
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

    /** Called with G21.39's cooperating account publication lock HELD. */
    private RestartRecoveryEvidence inspectRestartRecoveryLocked(
        String account,Path pinned,PathResolver sameFile
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
                MessageDigest firstDigest=newAccountDigest();
                Optional<PlayerSnapshot> read=loadExactFile(
                    account,pinned,true,firstDigest
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
                       digestAdmittedAccountFile(pinned))||
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

    private static final String RESTART_WITNESS_VERSION="G2172";
    private static final long RESTART_WITNESS_MAX_ACCOUNT_BYTES=64L*1024*1024;
    private static final long RESTART_WITNESS_MAX_MARKER_BYTES=4096L;
    // Exactly the original four negative sidecar paths; the account
    // publication .lock is NOT a transactional record or claim.
    private static final String[] RESTART_MARKER_SUFFIXES={
        ".g2132-mailbox-review",
        ".g2147-strict-postpublication-review",
        MailboxStrictUncertainFence.SUFFIX,
        MailboxStrictWriteIntentFence.SUFFIX
    };

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
            account,pinned,sameFile
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
                    marker,RESTART_WITNESS_MAX_MARKER_BYTES));
        }
        // Re-check all marker names, account path and G21.71's
        // authoritative classification before returning this witness.
        RestartRecoveryEvidence second=inspectRestartRecoveryLocked(
            account,pinned,sameFile
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
     * Can be saved by the caller as text and rechecked in a different
     * process with the SAME resolver root. No change to the account,
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
        return MailboxAccountPublicationCoordinator
            .withExclusivePublication(pinned,()->{
                String first=restartWitnessInsidePublication(
                    account,pinned,bound);
                String second=restartWitnessInsidePublication(
                    account,pinned,bound);
                if(!first.equals(second)||!pinned.equals(
                        normalizedPath(account)))
                    throw new IOException(
                        "G21.72 RECOVERY_WITNESS_UNSTABLE_NO_GRANT"
                    );
                RestartRecoveryEvidence finalState=
                    inspectRestartRecoveryLocked(
                        account,pinned,bound
                    );
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

    /** Second NOFOLLOW read; not an atomic snapshot or ABA proof. */
    private static byte[] digestAdmittedAccountFile(
        Path file
    )throws IOException{
        MessageDigest digest=newAccountDigest();
        try(InputStream input=new DigestInputStream(
                java.nio.channels.Channels.newInputStream(
                    java.nio.channels.FileChannel.open(
                        file,StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS
                    )),digest)){
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
        if(!Files.isRegularFile(file))
            return Optional.empty();

        Properties properties=
            new Properties();

        // The admitted decoder never follows a substituted symbolic
        // link after its NOFOLLOW metadata check. Raw forensic reading
        // preserves its pre-existing compatibility semantics.
        try(InputStream input=noFollow
                ?java.nio.channels.Channels.newInputStream(
                    java.nio.channels.FileChannel.open(
                        file,StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS
                    ))
                :Files.newInputStream(file)){
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
        Objects.requireNonNull(
            snapshot,
            "snapshot"
        );

        if(snapshot.version()!=
                PlayerSnapshot.CURRENT_VERSION)
            throw new IOException(
                "unsupported snapshot version="+
                snapshot.version()
            );

        Path file=
            normalizedPath(
                snapshot.username()
            );

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

        Path parent=file.getParent();
        if(parent!=null)
            Files.createDirectories(parent);

        Properties properties=
            snapshot.toLegacyProperties();

        properties.setProperty(
            "saved.at",
            Instant.now().toString()
        );

        Path tmp=
            file.resolveSibling(
                file.getFileName().toString()+
                ".tmp"
            );

        boolean completed=false;

        try{
            try(OutputStream output=
                    Files.newOutputStream(
                        tmp,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    )){
                properties.store(
                    output,
                    "SpawnPK LocalLab localhost account state"
                );
            }

            if(enforceWorldAdmission){
                beforeWorldReplace.run(snapshot.username());
                // G21.38: the last review-fence check and account-file
                // replacement are one exclusive publication critical
                // section shared with cooperating G21.34 marker writers.
                // This does NOT coordinate direct manual save() calls.
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(file,()->{
                        requireUnfencedWorldSave(
                snapshot.username(),guardedMarkerPaths
            );
                        // G21.66: a different cooperating World must
                        // not overwrite a completed terminal account
                        // with an older PREPARED/autosave snapshot.
                        requireNoTerminalAccountPostimage(
                            worldAccount,file
                        );
                        insideWorldPublication.run(snapshot.username());
                        replaceSnapshotTemp(tmp,file);
                        return null;
                    });
            }else{
                replaceSnapshotTemp(tmp,file);
            }

            completed=true;
        }finally{
            if(!completed)
                Files.deleteIfExists(tmp);
        }
    }

    private static void replaceSnapshotTemp(
        Path temp,Path file
    )throws IOException{
        try{
            Files.move(
                temp,file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        }catch(AtomicMoveNotSupportedException unsupported){
            // Retain original account-file writer compatibility. This
            // fallback is NOT used for the no-clobber review marker.
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
