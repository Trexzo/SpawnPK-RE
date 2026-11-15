package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * R73 OFFLINE-ONLY, intentionally conservative persistent write-intent
 * preflight. A cooperative new session cannot replace an R64 owner while
 * this account has *any* unretired prior write-intent evidence.
 *
 * REGISTRATION and TAKEOVER both acquire the SAME R60 OS FileLock.
 * The R73 durable per-account blocker is published BEFORE R72's INTENT
 * and the actual asynchronous save can be submitted. Holding R60 also
 * across R64.replaceOwnerUnderLock closes register-vs-takeover TOCTOU.
 *
 * No expiry, owner-PID inference, automatic marker cleanup, recovery
 * grant, login gate or live producer installation exists in R73.
 * Even a complete ACK is not evidence of a safe takeover without an
 * explicit future reconciliation/retirement protocol.
 */
final class R73OfflinePendingWriteAdmission {
    private static final String FORMAT="SpawnPK-R73-WriteAdmission-v1";
    private static final String R72_FORMAT=
        "SpawnPK-R72-InFlightEvidence-v1";
    private static final int MAX_MARKER_BYTES=4096;

    enum Status {
        BLOCK_LOCK_OR_PATH_INDETERMINATE,
        BLOCK_UNRESOLVED_WRITE,
        READY_FOR_OTHER_OFFLINE_CHECKS
    }

    static final class Review {
        final Status status;
        final String reason;
        Review(Status status,String reason){
            this.status=status;
            this.reason=reason;
        }
        boolean loginAuthorized(){return false;}
        boolean repairAuthorized(){return false;}
        boolean writeIntentRetired(){return false;}
        boolean quarantineReleased(){return false;}
    }

    private final Path journal,accounts,generations,sessions,evidence;

    R73OfflinePendingWriteAdmission(
        Path journal,Path accounts,Path generations,Path sessions,
        Path evidence
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(
            generations,"generations"
        );
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        this.evidence=Objects.requireNonNull(evidence,"evidence");
    }

    /**
     * Explicit test-owned registration. Caller has already captured the
     * real WorldPlayer snapshot/ticket, but MUST NOT submit it to I/O
     * until this method succeeds. A durable R73 blocker is published
     * BEFORE delegating to R72's intent+R71 final seal under the same
     * held OS lock. If R72 registration fails, keep R73: fail closed.
     */
    UUID registerFinalBeforeQueueForTest(
        WorldPlayerPersistence.CapturedSave captured,
        R64OfflineSessionOwnership.Token expected,
        R72OfflineInFlightCommitEvidence recorder
    )throws IOException{
        Objects.requireNonNull(captured,"captured");
        Objects.requireNonNull(expected,"expected");
        Objects.requireNonNull(recorder,"recorder");
        return R60TradePairCoordinator.withExclusive(
            journal,false,()->{
                validateEvidenceRoot();
                R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                    journal,accounts,generations,sessions,expected
                );
                if(!expected.username.equals(
                        captured.ticket.snapshot.username()))
                    throw new IOException(
                        "R73_REGISTER_DENIED foreign captured account"
                    );
                requireClearUnderLock(expected.username);
                publishPermanentBlocker(
                    expected,captured.ticket
                );
                return recorder.registerFinalForTest(
                    captured,expected
                );
            }
        );
    }

    /**
     * Opt-in owner transfer: evidence check and R64 owner epoch increment
     * use ONE real R60 cross-JVM lock. Never call R64.replaceOwner() here;
     * it would reacquire the same OS lock and create a nested-lock error.
     */
    R64OfflineSessionOwnership.Token replaceOwnerIfClear(
        R64OfflineSessionOwnership.Token expectedPrior
    )throws IOException{
        Objects.requireNonNull(expectedPrior,"expected prior");
        return R60TradePairCoordinator.withExclusive(
            journal,false,()->{
                validateEvidenceRoot();
                requireClearUnderLock(expectedPrior.username);
                return R64OfflineSessionOwnership.replaceOwnerUnderLock(
                    journal,accounts,generations,sessions,
                    expectedPrior
                );
            }
        );
    }

    /** Read-only status. Even READY never grants an actual login. */
    Review inspect(String username){
        try{
            return R60TradePairCoordinator.withExclusive(
                journal,false,()->{
                    validateEvidenceRoot();
                    try{
                        requireClearUnderLock(username);
                    }catch(IOException blocked){
                        return new Review(
                            Status.BLOCK_UNRESOLVED_WRITE,
                            "R73_WRITE_EVIDENCE_OR_STAGE_UNRESOLVED "
                            +blocked.getMessage()
                        );
                    }
                    return new Review(
                        Status.READY_FOR_OTHER_OFFLINE_CHECKS,
                        "R73_EVIDENCE_CLEAR_OFFLINE_ONLY"
                    );
                }
            );
        }catch(IOException|RuntimeException denied){
            return new Review(
                Status.BLOCK_LOCK_OR_PATH_INDETERMINATE,
                "R73_LOCK_OR_EVIDENCE_ROOT_INDETERMINATE"
            );
        }
    }

    /**
     * Must hold R60. Fail closed on a durable R73 blocker, leftover
     * publication staging, any matching R72 evidence phase (even ACK),
     * missing R72 INTENT, malicious marker or unreadable directory.
     *
     * R72-only historical markers are checked defensively, but only
     * registration through THIS R73 lock protocol closes the race with
     * concurrent owner takeover.
     */
    private void requireClearUnderLock(String username)
            throws IOException{
        R60TradePairCoordinator.requireHeldOnCurrentThread(journal);
        validateUsername(username);
        Path marker=blocker(username);
        AtomicAccountPublisher.requireNoAmbiguousStage(marker);
        if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "R73_UNRESOLVED_WRITE_DENIED durable pre-admission intent"
            );

        Set<UUID> ids=new HashSet<>();
        try(DirectoryStream<Path> entries=
                Files.newDirectoryStream(canonicalEvidence())){
            for(Path entry:entries){
                String filename=entry.getFileName().toString();
                if(!filename.startsWith("r72-"))
                    continue;
                // Never ignore an unknown R72-shaped file, symlink,
                // staging file or partial phase: its account cannot be
                // confidently established from the filename alone.
                if(!Files.isRegularFile(
                        entry,LinkOption.NOFOLLOW_LINKS)||
                   Files.isSymbolicLink(entry)||
                   !filename.matches(
                       "r72-[0-9a-fA-F-]{36}-"+
                       "(INTENT|ENTERED|CANCEL_OBSERVED|"+
                       "ACK_OBSERVED|FAILURE_OBSERVED)\\.properties"))
                    throw new IOException(
                        "R73_UNRESOLVED_WRITE_DENIED ambiguous R72 entry"
                    );
                String idText=filename.substring(4,40);
                final UUID id;
                try{
                    id=UUID.fromString(idText);
                    if(!idText.equals(id.toString()))
                        throw new IllegalArgumentException(
                            "noncanonical UUID"
                        );
                }catch(IllegalArgumentException invalid){
                    throw new IOException(
                        "R73_UNRESOLVED_WRITE_DENIED malformed R72 UUID",
                        invalid
                    );
                }
                ids.add(id);
            }
        }
        for(UUID id:ids){
            Path intent=canonicalEvidence().resolve(
                "r72-"+id+"-INTENT.properties"
            );
            Properties meta=readIntentStrict(intent);
            // Any invalid/incomplete phase for any account blocks all:
            // it could represent this user's write with corrupt username.
            R72OfflineInFlightCommitEvidence.Review outcome=
                R72OfflineInFlightCommitEvidence.inspect(
                    canonicalEvidence(),id
                );
            if(outcome.status==
                    R72OfflineInFlightCommitEvidence.ReviewStatus
                        .BLOCK_AMBIGUOUS_EVIDENCE||
               outcome.status==
                    R72OfflineInFlightCommitEvidence.ReviewStatus
                        .BLOCK_UNREGISTERED)
                throw new IOException(
                    "R73_UNRESOLVED_WRITE_DENIED ambiguous R72 phases"
                );
            if(username.equals(meta.getProperty("username")))
                throw new IOException(
                    "R73_UNRESOLVED_WRITE_DENIED R72 "+
                    outcome.status
                );
        }
    }

    private void publishPermanentBlocker(
        R64OfflineSessionOwnership.Token owner,
        WorldPlayerPersistence.SaveTicket ticket
    )throws IOException{
        Path target=blocker(owner.username);
        AtomicAccountPublisher.requireNoAmbiguousStage(target);
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "R73_REGISTER_DENIED already registered"
            );
        Properties data=new Properties();
        data.setProperty("format",FORMAT);
        data.setProperty("username",owner.username);
        data.setProperty("owner",owner.owner.toString());
        data.setProperty("epoch",Long.toString(owner.epoch));
        data.setProperty(
            "generation",Long.toString(owner.account.generation)
        );
        data.setProperty(
            "journalRevision",
            Long.toString(owner.account.journalRevision)
        );
        data.setProperty(
            "beforeDigest",owner.account.snapshotDigest
        );
        data.setProperty(
            "afterDigest",
            R57TradePairJournal.digest(ticket.snapshot)
        );
        data.setProperty(
            "saveSequence",Long.toString(ticket.sequence)
        );
        AtomicAccountPublisher.publish(
            target,data,"R73 offline never-auto-retire write intent"
        );
    }

    private static Properties readIntentStrict(Path target)
            throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(target);
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||
           Files.isSymbolicLink(target)||
           Files.size(target)>MAX_MARKER_BYTES)
            throw new IOException(
                "R73_UNRESOLVED_WRITE_DENIED R72 intent missing/unsafe"
            );
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(target)){
            p.load(in);
        }
        if(p.size()!=11||
           !R72_FORMAT.equals(p.getProperty("format"))||
           !p.getProperty("username","").matches(
               "[a-z][a-z0-9_]{0,31}"
           ))
            throw new IOException(
                "R73_UNRESOLVED_WRITE_DENIED R72 intent schema"
            );
        return p;
    }

    private void validateEvidenceRoot()throws IOException{
        Path[] roots=new Path[]{
            journal.toAbsolutePath().normalize(),
            accounts.toAbsolutePath().normalize(),
            generations.toAbsolutePath().normalize(),
            sessions.toAbsolutePath().normalize(),
            canonicalEvidence()
        };
        for(int i=0;i<roots.length;i++){
            Path root=roots[i];
            for(Path p=root;p!=null;p=p.getParent())
                if(Files.isSymbolicLink(p))
                    throw new IOException(
                        "R73_PATH_DENIED symlink ancestor"
                    );
            BasicFileAttributes info=Files.readAttributes(
                root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
            if(!info.isDirectory())
                throw new IOException(
                    "R73_PATH_DENIED non-directory root"
                );
            for(int j=0;j<i;j++)
                if(root.startsWith(roots[j])||
                   roots[j].startsWith(root))
                    throw new IOException(
                        "R73_PATH_DENIED overlapping roots"
                    );
        }
    }

    private Path canonicalEvidence(){
        return evidence.toAbsolutePath().normalize();
    }
    private Path blocker(String username){
        return canonicalEvidence().resolve(
            "r73-"+username+".intent.properties"
        );
    }

    private static void validateUsername(String username)
            throws IOException{
        if(username==null||
           !username.matches("[a-z][a-z0-9_]{0,31}"))
            throw new IOException(
                "R73_PATH_DENIED invalid account name"
            );
    }
}
